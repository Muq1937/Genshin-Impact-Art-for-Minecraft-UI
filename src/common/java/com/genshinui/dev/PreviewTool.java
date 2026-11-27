package com.genshinui.dev;

import com.genshinui.gen.PackBuilder;
import com.genshinui.gen.VanillaSource;
import com.genshinui.theme.Theme;
import com.genshinui.theme.ThemePalette;
import com.genshinui.theme.ThemeStore;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Dev-only: builds each theme's pack outside Minecraft and renders mock screens for quick visual checks.
 * Run with {@code gradlew previewThemes}.
 */
public final class PreviewTool {
    static final int S = 4;

    public static void main(String[] args) throws Exception {
        Path themes = Path.of(args[0]);
        ZipFile jar = new ZipFile(args[1]);
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        VanillaSource src = new VanillaSource() {
            public BufferedImage image(String path) {
                ZipEntry e = jar.getEntry("assets/minecraft/" + path);
                if (e == null) return null;
                try (InputStream in = jar.getInputStream(e)) {
                    return ImageIO.read(in);
                } catch (IOException ex) {
                    return null;
                }
            }

            public String text(String path) {
                ZipEntry e = jar.getEntry("assets/minecraft/" + path);
                if (e == null) return null;
                try (InputStream in = jar.getInputStream(e)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    return null;
                }
            }
        };
        try (var dirs = Files.list(themes)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                Theme t = ThemeStore.load(dir);
                if (t == null) continue;
                long t0 = System.currentTimeMillis();
                Path pack = out.resolve(t.id).resolve("pack");
                PackBuilder b = new PackBuilder(t, src, S, i -> {
                    try {
                        return i < t.images.size() ? ThemeStore.readImage(dir.resolve(t.images.get(i))) : null;
                    } catch (IOException e) {
                        return null;
                    }
                }, pack);
                b.build(64, "preview");
                System.out.println(t.id + ": pack built in " + (System.currentTimeMillis() - t0) + " ms");
                mockHud(t, pack, out.resolve(t.id + "_hud.png"));
                mockMenu(t, pack, out.resolve(t.id + "_menu.png"));
                mockContainers(t, pack, out.resolve(t.id + "_containers.png"));
            }
        }
    }

    static BufferedImage tex(Path pack, String rel) throws IOException {
        return ImageIO.read(pack.resolve(rel).toFile());
    }

    static final String GUI = "assets/minecraft/textures/gui/";

    /** Draws logical region (u,v,w,h) of an S-scaled texture at logical (x,y) with GUI scale G. */
    static void blit(Graphics2D g, BufferedImage img, int G, int x, int y, int u, int v, int w, int h) {
        g.drawImage(img, x * G, y * G, (x + w) * G, (y + h) * G, u * S, v * S, (u + w) * S, (v + h) * S, null);
    }

    static void stretch(Graphics2D g, BufferedImage img, int G, double x, double y, double w, double h) {
        g.drawImage(img, (int) (x * G), (int) (y * G), (int) (w * G), (int) (h * G), null);
    }

    /** Nine-slice draw of a sprite with logical size sw x sh and uniform border. */
    static void nine(Graphics2D g, BufferedImage img, int G, int sw, int sh, int border, int x, int y, int w, int h) {
        int[] xs = {0, border, sw - border, sw};
        int[] ys = {0, border, sh - border, sh};
        int[] dx = {x, x + border, x + w - border, x + w};
        int[] dy = {y, y + border, y + h - border, y + h};
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                g.drawImage(img, dx[i] * G, dy[j] * G, dx[i + 1] * G, dy[j + 1] * G,
                        xs[i] * S, ys[j] * S, xs[i + 1] * S, ys[j + 1] * S, null);
    }

    static Graphics2D canvas(BufferedImage c) {
        Graphics2D g = c.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        return g;
    }

    static void world(Graphics2D g, int w, int h) {
        // fake world: grass/sky gradient, darkened like the in-game container background
        g.setPaint(new GradientPaint(0, 0, new Color(0x7FA8E0), 0, h, new Color(0x4D7A3A)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, w, h);
    }

    static void mockHud(Theme t, Path pack, Path file) throws IOException {
        int G = 3, LW = 427, LH = 240;
        BufferedImage c = new BufferedImage(LW * G, LH * G, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(c);
        world(g, c.getWidth(), c.getHeight());
        ThemePalette p = t.palette();
        BufferedImage inv = tex(pack, GUI + "container/inventory.png");
        int ix = (LW - 176) / 2, iy = (LH - 166) / 2 - 18;
        blit(g, inv, G, ix, iy, 0, 0, 176, 166);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 8 * G));
        g.setColor(new Color(p.text, true));
        g.drawString("Crafting", (ix + 97) * G, (iy + 6 + 7) * G);

        int hx = (LW - 182) / 2, hy = LH - 22;
        blit(g, tex(pack, GUI + "sprites/hud/hotbar.png"), G, hx, hy, 0, 0, 182, 22);
        blit(g, tex(pack, GUI + "sprites/hud/hotbar_selection.png"), G, hx - 1 + 2 * 20, hy - 1, 0, 0, 24, 23);
        blit(g, tex(pack, GUI + "sprites/hud/hotbar_offhand_left.png"), G, hx - 29, LH - 23, 0, 0, 29, 24);
        BufferedImage xpb = tex(pack, GUI + "sprites/hud/experience_bar_background.png");
        BufferedImage xpp = tex(pack, GUI + "sprites/hud/experience_bar_progress.png");
        blit(g, xpb, G, hx, LH - 29, 0, 0, 182, 5);
        blit(g, xpp, G, hx, LH - 29, 0, 0, 110, 5);
        BufferedImage hc = tex(pack, GUI + "sprites/hud/heart/container.png");
        BufferedImage hf = tex(pack, GUI + "sprites/hud/heart/full.png");
        BufferedImage hh = tex(pack, GUI + "sprites/hud/heart/half.png");
        BufferedImage fe = tex(pack, GUI + "sprites/hud/food_empty.png");
        BufferedImage ff = tex(pack, GUI + "sprites/hud/food_full.png");
        BufferedImage fh = tex(pack, GUI + "sprites/hud/food_half.png");
        BufferedImage af = tex(pack, GUI + "sprites/hud/armor_full.png");
        for (int i = 0; i < 10; i++) {
            int x = hx + i * 8, y = LH - 39;
            stretch(g, hc, G, x, y, 9, 9);
            stretch(g, i < 7 ? hf : i == 7 ? hh : null, G, x, y, 9, 9);
            int fx = hx + 182 - 9 - i * 8;
            stretch(g, fe, G, fx, y, 9, 9);
            stretch(g, i < 6 ? ff : i == 6 ? fh : null, G, fx, y, 9, 9);
            if (i < 5) stretch(g, af, G, x, y - 10, 9, 9);
        }
        g.dispose();
        ImageIO.write(c, "png", file.toFile());
    }

    static void mockMenu(Theme t, Path pack, Path file) throws IOException {
        int G = 3, LW = 427, LH = 240;
        BufferedImage c = new BufferedImage(LW * G, LH * G, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(c);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(tex(pack, "assets/genshinui/textures/gui/menu_art.png"), 0, 0, c.getWidth(), c.getHeight(), null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        BufferedImage[] btn = {tex(pack, GUI + "sprites/widget/button.png"), tex(pack, GUI + "sprites/widget/button_highlighted.png"), tex(pack, GUI + "sprites/widget/button_disabled.png")};
        String[] labels = {"Singleplayer", "Multiplayer (hover)", "Realms (disabled)"};
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8 * G));
        for (int i = 0; i < 3; i++) {
            int x = LW / 2 - 100, y = 80 + i * 24;
            nine(g, btn[i], G, 200, 20, 3, x, y, 200, 20);
            g.setColor(i == 2 ? new Color(0xA0A0A0) : Color.WHITE);
            g.drawString(labels[i], (x + 100) * G - g.getFontMetrics().stringWidth(labels[i]) / 2, (y + 13) * G);
        }
        nine(g, btn[0], G, 200, 20, 3, LW / 2 - 100, 160, 98, 20);
        nine(g, btn[0], G, 200, 20, 3, LW / 2 + 2, 160, 98, 20);
        nine(g, tex(pack, GUI + "sprites/widget/text_field.png"), G, 200, 20, 1, LW / 2 - 100, 186, 200, 20);
        nine(g, tex(pack, GUI + "sprites/widget/slider.png"), G, 200, 20, 1, LW / 2 - 100, 210, 200, 20);
        nine(g, tex(pack, GUI + "sprites/widget/slider_handle.png"), G, 8, 20, 2, LW / 2 - 20, 210, 8, 20);
        // tooltip
        int tx = 20, ty = 20, tw = 90, th = 30;
        int bx = tx - 12, by = ty - 12, bw = tw + 24, bh = th + 24;
        nine(g, tex(pack, GUI + "sprites/tooltip/background.png"), G, 100, 100, 9, bx, by, bw, bh);
        nine(g, tex(pack, GUI + "sprites/tooltip/frame.png"), G, 100, 100, 10, bx, by, bw, bh);
        g.setColor(Color.WHITE);
        g.drawString("Diamond Sword", tx * G, (ty + 8) * G);
        g.setColor(new Color(0x5555FF));
        g.drawString("+7 Attack Damage", tx * G, (ty + 22) * G);
        // particle sprite sample
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        BufferedImage part = tex(pack, "assets/genshinui/textures/gui/particle.png");
        for (int i = 0; i < 5; i++) g.drawImage(part, (330 + i * 17) * G, 20 * G, 14 * G, 14 * G, null);
        g.drawImage(tex(pack, "pack.png"), 330 * G, 40 * G, 64 * G, 64 * G, null);
        g.dispose();
        ImageIO.write(c, "png", file.toFile());
    }

    static void mockContainers(Theme t, Path pack, Path file) throws IOException {
        int G = 2;
        String[] names = {"generic_54", "furnace", "crafting_table", "creative_inventory/tab_items", "anvil", "villager", "hopper", "beacon"};
        int cols = 4, cw = 280, ch = 230;
        BufferedImage c = new BufferedImage(cols * cw * G, 2 * ch * G, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(c);
        world(g, c.getWidth(), c.getHeight());
        for (int i = 0; i < names.length; i++) {
            BufferedImage img = tex(pack, GUI + "container/" + names[i] + ".png");
            Rectangle bb = com.genshinui.gen.Compositor.alphaBounds(img);
            int x = (i % cols) * cw + 4, y = (i / cols) * ch + 4;
            g.drawImage(img, x * G, y * G, (x + bb.width / S) * G, (y + bb.height / S) * G, 0, 0, bb.width, bb.height, null);
        }
        g.dispose();
        ImageIO.write(c, "png", file.toFile());
    }
}
