package com.genshinui.gui;

import com.genshinui.GenshinUIClient;
import com.genshinui.ThemeManager;
import com.genshinui.we.WallpaperEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Browses the local Wallpaper Engine workshop folder and unpacks the chosen wallpaper's art
 * (scene.pkg textures; image files for web wallpapers; preview as a last resort).
 */
public class WallpaperBrowserScreen extends Screen {
    private static final ExecutorService EXTRACT = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GenshinUI-WallpaperEngine");
        t.setDaemon(true);
        return t;
    });
    private static final int CELL_W = 116, CELL_H = 84, THUMB_W = 110, THUMB_H = 62;

    private final Screen parent;
    /** Gets the wallpaper title and its art (best first); runs on the minecraft thread. */
    private final BiConsumer<String, List<BufferedImage>> onPicked;

    private List<WallpaperEngine.Item> items;
    private List<WallpaperEngine.Item> shown = List.of();
    private List<Path> workshopDirs = List.of();
    private final Map<String, DynTex.Tex> thumbs = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private EditBox search;
    private double scroll;
    private String status = "Looking for Wallpaper Engine...";
    private boolean busy;

    private int gridX, gridY, gridW, gridH, cols;

    public WallpaperBrowserScreen(Screen parent, BiConsumer<String, List<BufferedImage>> onPicked) {
        super(Component.literal("Wallpaper Engine"));
        this.parent = parent;
        this.onPicked = onPicked;
    }

    @Override
    protected void init() {
        gridY = 44;
        gridW = width - 20;
        gridH = height - gridY - 42;
        cols = Math.max(1, gridW / CELL_W);
        gridX = 10 + (gridW - cols * CELL_W) / 2;

        String q = search != null ? search.getValue() : "";
        search = addRenderableWidget(new EditBox(font, width / 2 - 100, 22, 200, 16, Component.literal("Search")));
        search.setHint(Component.literal("Search wallpapers..."));
        search.setValue(q);
        search.setResponder(s -> filter());
        addRenderableWidget(Button.builder(Component.literal("Open Workshop Folder"), b -> {
            if (!workshopDirs.isEmpty()) com.genshinui.OsUtil.open(workshopDirs.get(0));
        }).bounds(width / 2 - 154, height - 24, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, height - 24, 150, 20).build());

        if (items == null) {
            items = List.of();
            ThemeSelectScreen.THUMBS.submit(() -> {
                List<Path> dirs = WallpaperEngine.workshopDirs();
                List<WallpaperEngine.Item> found = WallpaperEngine.listItems(dirs);
                minecraft.execute(() -> {
                    workshopDirs = dirs;
                    items = found;
                    status = dirs.isEmpty()
                            ? "Wallpaper Engine workshop folder not found (steamapps/workshop/content/431960)"
                            : found.size() + " wallpapers • click one to use its art";
                    filter();
                });
            });
        } else {
            filter();
        }
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = items.stream().filter(it -> q.isEmpty() || it.title().toLowerCase(Locale.ROOT).contains(q) || it.id().contains(q)).toList();
        scroll = Math.min(scroll, maxScroll());
    }

    private double maxScroll() {
        int rows = (shown.size() + cols - 1) / cols;
        return Math.max(0, rows * CELL_H - gridH);
    }

    private void requestThumb(WallpaperEngine.Item it) {
        if (it.preview() == null || !requested.add(it.id())) return;
        ThemeSelectScreen.THUMBS.submit(() -> {
            try {
                BufferedImage img = ImageIO.read(it.preview().toFile()); // first frame for GIFs
                if (img == null) return;
                BufferedImage small = DynTex.fit(img, 220);
                minecraft.execute(() -> {
                    if (minecraft.gui.screen() == this) thumbs.put(it.id(), DynTex.upload("we_" + it.id(), small));
                });
            } catch (Exception ignored) {
            }
        });
    }

    public void pick(WallpaperEngine.Item it) {
        if (busy) return;
        busy = true;
        status = "Unpacking \"" + it.title() + "\"...";
        EXTRACT.submit(() -> {
            try {
                long t0 = System.currentTimeMillis();
                List<BufferedImage> art = WallpaperEngine.extractArt(it.dir(), 4);
                GenshinUIClient.LOGGER.info("Unpacked {} image(s) from wallpaper {} in {} ms", art.size(), it.id(), System.currentTimeMillis() - t0);
                minecraft.execute(() -> {
                    busy = false;
                    if (art.isEmpty()) {
                        status = "No usable art found in \"" + it.title() + "\"";
                    } else {
                        onPicked.accept(it.title(), art);
                    }
                });
            } catch (Throwable e) {
                GenshinUIClient.LOGGER.warn("Wallpaper unpack failed", e);
                minecraft.execute(() -> {
                    busy = false;
                    status = "Could not unpack: " + e.getMessage();
                });
            }
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (super.mouseClicked(event, doubleClick)) return true;
        WallpaperEngine.Item it = itemAt(mx, my);
        if (it != null && button == 0) {
            pick(it);
            return true;
        }
        return false;
    }

    private WallpaperEngine.Item itemAt(double mx, double my) {
        if (mx < gridX || my < gridY || mx >= gridX + cols * CELL_W || my >= gridY + gridH) return null;
        int col = (int) ((mx - gridX) / CELL_W), row = (int) ((my - gridY + scroll) / CELL_H);
        int idx = row * cols + col;
        return idx >= 0 && idx < shown.size() ? shown.get(idx) : null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - v * CELL_H / 2));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        thumbs.values().forEach(DynTex::release);
        thumbs.clear();
        requested.clear();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x90000000);
        ctx.centeredText(font, "✦ Wallpaper Engine ✦", width / 2, 8, 0xFFB7A6FF);

        ctx.enableScissor(0, gridY, width, gridY + gridH);
        WallpaperEngine.Item hovered = busy ? null : itemAt(mouseX, mouseY);
        int firstRow = (int) (scroll / CELL_H);
        int lastRow = (int) ((scroll + gridH) / CELL_H);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= shown.size()) break;
                WallpaperEngine.Item it = shown.get(idx);
                int x = gridX + col * CELL_W, y = gridY + row * CELL_H - (int) scroll;
                requestThumb(it);
                boolean hov = it == hovered;
                ctx.fill(x, y, x + CELL_W - 6, y + CELL_H - 4, hov ? 0x60B7A6FF : 0x50000000);
                DynTex.Tex t = thumbs.get(it.id());
                if (t != null) ThemeSelectScreen.drawFit(ctx, t, x + 3, y + 3, THUMB_W - 6, THUMB_H, true);
                else ctx.fill(x + 3, y + 3, x + THUMB_W - 3, y + 3 + THUMB_H, 0xFF1A1A28);
                if (!"scene".equals(it.type())) {
                    String tag = it.type();
                    ctx.fill(x + 3, y + 3, x + 7 + font.width(tag), y + 13, 0xC0000000);
                    ctx.text(font, tag, x + 5, y + 4, 0xFFFFD070);
                }
                ctx.text(font, font.plainSubstrByWidth(it.title(), CELL_W - 10), x + 3, y + THUMB_H + 6,
                        hov ? 0xFFFFFFFF : 0xFFC8C8D4);
                if (hov) ctx.outline(x - 1, y - 1, CELL_W - 4, CELL_H - 2, 0xFFB7A6FF);
            }
        }
        ctx.disableScissor();

        ctx.centeredText(font, status, width / 2, gridY + gridH + 4, busy ? 0xFFFFE070 : 0xFF9A9AAA);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (hovered != null) {
            ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(hovered.title()),
                    Component.literal(hovered.type() + " • " + hovered.id()).withColor(0xA0A0B0)), mouseX, mouseY);
        }
        // keep the thumbnail cache in check
        if (thumbs.size() > 400) {
            thumbs.values().forEach(DynTex::release);
            thumbs.clear();
            requested.clear();
        }
        if (ThemeManager.building) {
            ctx.text(font, ThemeManager.status, 6, 8, 0xFFFFE070);
        }
    }
}
