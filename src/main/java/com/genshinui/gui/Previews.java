package com.genshinui.gui;

import com.genshinui.ThemeManager;
import com.genshinui.gen.Compositor;
import com.genshinui.theme.Theme;
import com.genshinui.theme.UiTarget;
import net.minecraft.client.MinecraftClient;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.genshinui.gen.Compositor.IconPart.*;

/** Renders small previews of a theme off-thread (latest request wins). */
public final class Previews {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GenshinUI-Preview");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger GEN = new AtomicInteger();
    private static final int S = 2;

    private Previews() {}

    /** Runs {@code job} off-thread and hands the image to {@code done} on the client thread, unless superseded. */
    public static void request(java.util.function.Supplier<BufferedImage> job, Consumer<BufferedImage> done) {
        int gen = GEN.incrementAndGet();
        EXEC.submit(() -> {
            if (gen != GEN.get()) return;
            try {
                BufferedImage img = job.get();
                if (img != null && gen == GEN.get()) MinecraftClient.getInstance().execute(() -> {
                    if (gen == GEN.get()) done.accept(img);
                });
            } catch (Throwable e) {
                com.genshinui.GenshinUIClient.LOGGER.warn("Preview failed", e);
            }
        });
    }

    private static Compositor compositor(Theme t) {
        return new Compositor(t, S, i -> ThemeManager.store.image(t, i));
    }

    /** One UI target as it will look. */
    public static BufferedImage target(Theme t, UiTarget target) {
        Compositor c = compositor(t);
        return switch (target.kind) {
            case CONTAINER -> {
                BufferedImage van = ThemeManager.VANILLA.image("textures/gui/" + target.textures.get(0) + ".png");
                if (van == null) yield null;
                if (t.isConnected(target)) { // connected book: book + screen as they sit in game while open (2 px gap)
                    UiTarget hostT = target.recipeHost();
                    BufferedImage hostVan = ThemeManager.VANILLA.image("textures/gui/" + hostT.textures.get(0) + ".png");
                    if (hostVan != null) {
                        BufferedImage rb = c.container(van, target).getSubimage(S, S, 147 * S, 166 * S);
                        BufferedImage host = c.container(hostVan, hostT, target).getSubimage(0, 0, 176 * S, 166 * S);
                        BufferedImage both = new BufferedImage(Theme.CONNECTED_W * S, 166 * S, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = both.createGraphics();
                        g.drawImage(rb, 0, 0, null);
                        g.drawImage(host, 149 * S, 0, null);
                        g.dispose();
                        yield both;
                    }
                }
                if (target == UiTarget.CHEST) { // small chest (most common) next to the double chest
                    BufferedImage small = c.container(Compositor.stitchChest(van, 3), target);
                    BufferedImage big = c.container(van, target);
                    int gap = 8 * S, h = 222 * S;
                    BufferedImage both = new BufferedImage(176 * S * 2 + gap, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = both.createGraphics();
                    g.drawImage(small.getSubimage(0, 0, 176 * S, 167 * S), 0, (h - 167 * S) / 2, null);
                    g.drawImage(big.getSubimage(0, 0, 176 * S, 222 * S), 176 * S + gap, 0, null);
                    g.dispose();
                    yield both;
                }
                BufferedImage full = c.container(van, target);
                java.awt.Rectangle bb = Compositor.alphaBounds(van);
                yield full.getSubimage(bb.x * S, bb.y * S, bb.width * S, bb.height * S);
            }
            case HOTBAR -> {
                BufferedImage bar = c.hotbar();
                Graphics2D g = bar.createGraphics();
                g.drawImage(c.hotbarSelection(), -S, -S, null);
                g.dispose();
                yield bar;
            }
            case MENU -> DynTex.fit(c.menuArt(), 480);
        };
    }

    /** Inventory + HUD overview used on the theme list screen. */
    public static BufferedImage overview(Theme t) {
        Compositor c = compositor(t);
        BufferedImage van = ThemeManager.VANILLA.image("textures/gui/container/inventory.png");
        int W = 182 * S, H = (166 + 8 + 10 + 7 + 22) * S;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (van != null) {
            BufferedImage inv = c.container(van, UiTarget.INVENTORY);
            g.drawImage(inv, 3 * S, 0, 3 * S + 176 * S, 166 * S, 0, 0, 176 * S, 166 * S, null);
        }
        int y = (166 + 8) * S;
        BufferedImage hc = c.icon("heart", CONTAINER, 0), hf = c.icon("heart", FULL, c.p.heart);
        BufferedImage fe = c.icon(t.icons, CONTAINER, 0);
        BufferedImage ff = c.icon(t.icons, FULL, com.genshinui.theme.Colors2.mixRgb(c.p.accent, c.p.lightC, 0.2));
        for (int i = 0; i < 10; i++) {
            g.drawImage(hc, i * 8 * S, y, null);
            if (i < 8) g.drawImage(hf, i * 8 * S, y, null);
            int fx = (182 - 9 - i * 8) * S;
            g.drawImage(fe, fx, y, null);
            if (i < 7) g.drawImage(ff, fx, y, null);
        }
        y += 10 * S;
        g.drawImage(c.xpBackground(), 0, y, null);
        BufferedImage xp = c.xpProgress();
        g.drawImage(xp, 0, y, 120 * S, y + 5 * S, 0, 0, 120 * S, 5 * S, null);
        y += 7 * S;
        g.drawImage(c.hotbar(), 0, y, null);
        g.drawImage(c.hotbarSelection(), -S + 20 * S, y - S, null);
        g.dispose();
        return out;
    }
}
