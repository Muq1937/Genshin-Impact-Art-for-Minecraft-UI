package com.genshinui.gui;

import com.genshinui.GenshinUIClient;
import com.genshinui.ThemeManager;
import com.genshinui.theme.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Edits one theme: per-UI crop (drag / scroll / corner-resize on the art, aspect locked to that UI),
 * art images (file dialog or drag & drop), colors (manual or auto from art) and style.
 */
public class ThemeEditorScreen extends Screen {
    private static final int COL_W = 132;
    /** Target list row height; shrinks on short windows so the left column fits. */
    private int ROW = 12;

    private final Screen parent;
    private final Theme work;
    private UiTarget target = UiTarget.INVENTORY;

    /** Uploaded (downscaled) art per image index, plus original size for crop math. */
    private record Art(DynTex.Tex tex, int origW, int origH) {}

    private final Map<Integer, Art> arts = new HashMap<>();
    private final Set<Integer> loading = new HashSet<>();
    private DynTex.Tex preview;
    private long previewDue = 1;
    private String message = "";

    private int leftX, rightX, centerX, centerW, top, canvasY, canvasH;
    private static final int STRIP_H = 30, THUMB_W = 48, THUMB_H = 27;
    private int stripScroll;
    private ButtonWidget connectBtn, modeBtn, particleBtn, iconBtn;
    private TextFieldWidget nameField, charField, accentField, darkField, lightField;

    private enum Drag { NONE, MOVE, RESIZE }

    private Drag drag = Drag.NONE;
    private double dragOffX, dragOffY;

    public ThemeEditorScreen(Screen parent, Theme theme) {
        super(Text.literal("Theme Editor"));
        this.parent = parent;
        this.work = ThemeStore.GSON.fromJson(ThemeStore.GSON.toJson(theme), Theme.class);
        this.work.id = theme.id;
        this.work.dir = theme.dir;
        this.work.sample = theme.sample;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        leftX = 8;
        rightX = width - 8 - COL_W;
        centerX = leftX + COL_W + 8;
        centerW = rightX - 8 - centerX;
        top = 24;
        canvasY = top + STRIP_H + 4;
        canvasH = (int) ((height - canvasY - 30) * 0.62);

        // left: art controls below the target list
        // fit list + 3 button rows (16 px each) above the bottom bar
        ROW = Math.max(9, Math.min(12, (height - 30 - top - 6 - 3 * 17) / UiTarget.values().length));
        int y = top + UiTarget.values().length * ROW + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Image..."), b -> FileDialogs.pickImages(this::addArt))
                .dimensions(leftX, y, COL_W / 2 - 1, 15).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wallpaper..."),
                        b -> client.setScreen(new WallpaperBrowserScreen(this, this::addWallpaper)))
                .dimensions(leftX + COL_W / 2 + 1, y, COL_W / 2 - 1, 15).build());
        y += 17;
        addDrawableChild(ButtonWidget.builder(Text.literal("Use This Crop For All"), b -> cropForAll())
                .dimensions(leftX, y, COL_W, 15).build());
        y += 17;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset Crop"), b -> {
            Art a = art(work.crop(target).image);
            if (a == null) return;
            CropRect c = CropRect.fill(a.origW, a.origH, work.aspect(target));
            c.image = work.crop(target).image;
            work.crops.put(target.id, c);
            changed();
        }).dimensions(leftX, y, COL_W, 15).build());

        // right: identity, colors, style
        y = top;
        int lw = 34, fw = COL_W - lw;
        nameField = field(rightX + lw, y, fw, work.name, s -> work.name = s);
        y += 16;
        charField = field(rightX + lw, y, fw, work.character, s -> work.character = s);
        y += 18;
        modeBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            work.mode = work.isDark() ? "light" : "dark";
            changed();
        }).dimensions(rightX, y, COL_W, 16).build());
        y += 18;
        accentField = colorField(rightX + lw, y, fw - 14, () -> work.accent, s -> work.accent = s);
        y += 16;
        darkField = colorField(rightX + lw, y, fw - 14, () -> work.dark, s -> work.dark = s);
        y += 16;
        lightField = colorField(rightX + lw, y, fw - 14, () -> work.light, s -> work.light = s);
        y += 18;
        addDrawableChild(ButtonWidget.builder(Text.literal("Auto Colors From Art"), b -> autoColors()).dimensions(rightX, y, COL_W, 16).build());
        y += 18;
        particleBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            work.particle = next(Theme.PARTICLES, work.particle);
            changed();
        }).dimensions(rightX, y, COL_W, 16).build());
        y += 18;
        iconBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            work.icons = next(Theme.ICONS, work.icons);
            changed();
        }).dimensions(rightX, y, COL_W, 16).build());
        y += 18;
        addDrawableChild(new PercentSlider(rightX, y, COL_W, 16, "Panel veil", work.panelStrength, v -> work.panelStrength = (float) v));
        y += 18;
        addDrawableChild(new PercentSlider(rightX, y, COL_W, 16, "Slot tint", work.slotStrength, v -> work.slotStrength = (float) v));

        // bottom
        int bw = 96, bx = width / 2 - (bw * 3 + 8) / 2, by = height - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Apply"), b -> save(true)).dimensions(bx, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save(false)).dimensions(bx + bw + 4, by, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close()).dimensions(bx + 2 * (bw + 4), by, bw, 20).build());

        int py = canvasY + canvasH + 4;
        connectBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> toggleConnect())
                .dimensions(centerX + centerW - Math.min(150, centerW), py, Math.min(150, centerW), 14).build());
        refreshLabels();
        for (int k = 0; k < work.images.size(); k++) ensureArt(k);
        previewDue = 1;
    }

    private TextFieldWidget field(int x, int y, int w, String value, Consumer<String> set) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 14, Text.empty());
        f.setMaxLength(48);
        f.setText(value);
        f.setChangedListener(set);
        return addDrawableChild(f);
    }

    private TextFieldWidget colorField(int x, int y, int w, java.util.function.Supplier<String> get, Consumer<String> set) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 14, Text.empty());
        f.setMaxLength(7);
        f.setText(get.get());
        f.setChangedListener(s -> {
            if (Colors2.parse(s, 0) != 0 && s.trim().length() >= 6) {
                set.accept(s.trim().startsWith("#") ? s.trim().toUpperCase(Locale.ROOT) : "#" + s.trim().toUpperCase(Locale.ROOT));
                changed();
            }
        });
        return addDrawableChild(f);
    }

    private static String next(String[] values, String cur) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) return values[(i + 1) % values.length];
        return values[0];
    }

    private void refreshLabels() {
        int n = Math.max(1, work.images.size());
        UiTarget book = recipeBookFor(target);
        connectBtn.visible = book != null;
        if (book != null) connectBtn.setMessage(Text.literal((centerW < 150 ? "Link book + screen: " : "Connect recipe book + screen: ") + (work.isConnected(book) ? "ON" : "OFF")));
        modeBtn.setMessage(Text.literal("Mode: " + (work.isDark() ? "Dark (light text)" : "Light (dark text)")));
        particleBtn.setMessage(Text.literal("Particles: " + work.particle));
        iconBtn.setMessage(Text.literal("Hunger icons: " + work.icons));
    }

    /** The recipe book that can sit next to this target (or the target itself if it is one). */
    private static UiTarget recipeBookFor(UiTarget t) {
        if (t.isRecipeBook()) return t;
        for (UiTarget b : UiTarget.values()) if (b.recipeHost() == t) return b;
        return null;
    }

    private void toggleConnect() {
        UiTarget book = recipeBookFor(target);
        if (book == null) return;
        UiTarget host = book.recipeHost();
        boolean on = !work.isConnected(book);
        work.setConnected(book, on);
        CropRect c = work.crop(book); // the shared image when on, the book's own crop when off
        Art a = art(c.image);
        if (a != null) c.clamp(a.origW, a.origH, work.aspect(book));
        target = book;
        message = on ? "Crop this image across both panels (book open). " + host.label + " keeps its own image for book closed."
                : "Recipe book has its own crop again";
        changed();
    }

    private void changed() {
        refreshLabels();
        previewDue = Util.getMeasuringTimeMs() + 150;
    }

    // ------------------------------------------------------------------ art

    private Art art(int index) {
        Art a = arts.get(index);
        if (a == null) ensureArt(index);
        return a;
    }

    /** Bumped when image indices shift, so stale async loads are dropped. */
    private int artGen;

    private void ensureArt(int index) {
        if (arts.containsKey(index) || loading.contains(index) || index >= work.images.size()) return;
        loading.add(index);
        int gen = artGen;
        String file = work.images.get(index);
        ThemeSelectScreen.THUMBS.submit(() -> {
            BufferedImage img = ThemeManager.store.image(work, index);
            if (img == null) return;
            BufferedImage small = DynTex.fit(img, 1024);
            client.execute(() -> {
                if (gen != artGen) return;
                loading.remove(index);
                arts.put(index, new Art(DynTex.upload("editor_art_" + ThemeStore.slug(work.id + "_" + file), small), img.getWidth(), img.getHeight()));
            });
        });
    }

    /** Uses theme image #i for the selected UI. */
    private void useImage(int i) {
        CropRect c = work.crop(target);
        if (c.image == i) return;
        c.image = i;
        Art a = art(i);
        if (a != null) c.clamp(a.origW, a.origH, work.aspect(target));
        message = "Image " + (i + 1) + " → " + target.label;
        changed();
    }

    /** Removes image #i from the theme (the file stays on disk until the theme is deleted). */
    private void removeImage(int i) {
        if (work.images.size() <= 1 || i < 0 || i >= work.images.size()) return;
        work.images.remove(i);
        for (CropRect c : work.crops.values()) {
            if (c.image == i) c.image = 0;
            else if (c.image > i) c.image--;
        }
        arts.values().forEach(a -> DynTex.release(a.tex));
        arts.clear();
        loading.clear();
        artGen++;
        for (int k = 0; k < work.images.size(); k++) ensureArt(k);
        message = "Removed image " + (i + 1);
        changed();
    }

    /** UIs that currently use image #i. */
    private List<String> usersOf(int i) {
        List<String> l = new ArrayList<>();
        for (UiTarget t : UiTarget.values()) if (work.crop(t).image == i) l.add(t.label);
        return l;
    }

    private interface ArtLoader {
        List<BufferedImage> load() throws IOException;
    }

    private void addArt(List<Path> files) {
        String label = files.size() == 1 ? files.get(0).getFileName().toString() : files.size() + " files";
        importArt(label, () -> {
            List<BufferedImage> all = new ArrayList<>();
            for (Path f : files) all.addAll(ThemeStore.readArt(f));
            return all;
        });
    }

    private void addWallpaper(String title, List<BufferedImage> art) {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(this);
        importArt(title, () -> art);
    }

    /** Reads + saves art off-thread, then adds it to the theme (first image becomes this UI's art). */
    private void importArt(String label, ArtLoader loader) {
        message = "Importing " + label + "...";
        Path dir = work.dir;
        ThemeSelectScreen.THUMBS.submit(() -> {
            try {
                List<BufferedImage> art = loader.load();
                if (art.isEmpty()) throw new IOException("No usable art found");
                List<String> names = new ArrayList<>();
                for (BufferedImage img : art) names.add(ThemeStore.saveImage(dir, img));
                int w = art.get(0).getWidth(), h = art.get(0).getHeight();
                client.execute(() -> onArtImported(label, names, w, h));
            } catch (Exception e) {
                GenshinUIClient.LOGGER.warn("Add art failed", e);
                client.execute(() -> message = "Import failed: " + e.getMessage());
            }
        });
    }

    private void onArtImported(String label, List<String> names, int w, int h) {
        boolean first = work.images.isEmpty();
        int idx = work.images.size();
        work.images.addAll(names);
        for (UiTarget t : first ? UiTarget.values() : new UiTarget[]{target}) {
            CropRect c = CropRect.fill(w, h, work.aspect(t));
            c.image = idx;
            work.crops.put(t.id, c);
        }
        for (int k = idx; k < work.images.size(); k++) ensureArt(k);
        stripScroll = Math.max(0, work.images.size() + 1 - stripCapacity());
        message = "Added " + names.size() + " image(s) from " + label + " → " + (first ? "all UIs" : target.label)
                + (names.size() > 1 ? " • click a thumbnail above to use it" : "");
        changed();
    }

    private void cropForAll() {
        CropRect src = work.crop(target);
        Art a = art(src.image);
        if (a == null) return;
        for (UiTarget t : UiTarget.values()) {
            if (t == target) continue;
            CropRect c = src.copy();
            if (t.kind != target.kind) c.h = 1.0; // different shapes: keep center, fill
            c.clamp(a.origW, a.origH, work.aspect(t));
            work.crops.put(t.id, c);
        }
        message = "Applied this art + center to every UI";
        changed();
    }

    private void autoColors() {
        BufferedImage img = ThemeManager.store.image(work, work.crop(UiTarget.INVENTORY).image);
        if (img == null) return;
        PaletteExtractor.Result r = PaletteExtractor.extract(img);
        work.mode = r.mode();
        accentField.setText(r.accent());
        darkField.setText(r.dark());
        lightField.setText(r.light());
        work.accent = r.accent();
        work.dark = r.dark();
        work.light = r.light();
        changed();
    }

    // ------------------------------------------------------------------ save

    private void save(boolean apply) {
        if (work.name.isBlank()) work.name = "Untitled";
        try {
            ThemeManager.store.save(work);
            boolean isActive = ThemeManager.active() != null && ThemeManager.active().id.equals(work.id);
            if (apply || isActive) ThemeManager.apply(ThemeManager.store.get(work.id));
            close();
        } catch (IOException e) {
            message = "Save failed: " + e.getMessage();
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        arts.values().forEach(a -> DynTex.release(a.tex));
        arts.clear();
        loading.clear();
        DynTex.release(preview);
        preview = null;
    }

    @Override
    public void tick() {
        if (previewDue != 0 && Util.getMeasuringTimeMs() >= previewDue) {
            previewDue = 0;
            Theme snapshot = ThemeStore.GSON.fromJson(ThemeStore.GSON.toJson(work), Theme.class);
            snapshot.id = work.id;
            snapshot.dir = work.dir;
            UiTarget t = target;
            Previews.request(() -> Previews.target(snapshot, t), img -> {
                DynTex.release(preview);
                preview = DynTex.upload("editor_preview", img);
            });
        }
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        List<Path> imgs = paths.stream().filter(FileDialogs::isImage).toList();
        if (!imgs.isEmpty()) {
            addArt(imgs);
            return;
        }
        message = "Drop a PNG / JPG image or a Wallpaper Engine scene.pkg";
    }

    // ------------------------------------------------------------------ image strip

    /** Number of tiles (thumbnails + the "+" tile) that fit in the strip. */
    private int stripCapacity() {
        return Math.max(2, (centerW - 4) / (THUMB_W + 4));
    }

    /** Tile index (0..images.size(), where images.size() is the "+" tile) under the mouse, or -1. */
    private int stripTileAt(double mx, double my) {
        if (my < top + 1 || my >= top + 1 + THUMB_H || mx < centerX + 2) return -1;
        int col = (int) ((mx - centerX - 2) / (THUMB_W + 4));
        if (col >= stripCapacity() || mx - centerX - 2 - col * (THUMB_W + 4) >= THUMB_W) return -1;
        int idx = col + stripScroll;
        return idx <= work.images.size() ? idx : -1;
    }

    private int tileX(int idx) {
        return centerX + 2 + (idx - stripScroll) * (THUMB_W + 4);
    }

    private boolean overRemove(int idx, double mx, double my) {
        int x = tileX(idx);
        return work.images.size() > 1 && idx < work.images.size() && mx >= x + THUMB_W - 9 && my < top + 1 + 9;
    }

    private void renderStrip(DrawContext ctx, int mouseX, int mouseY) {
        ThemePalette pal = work.palette();
        int cap = stripCapacity();
        stripScroll = Math.max(0, Math.min(stripScroll, work.images.size() + 1 - cap));
        int hovered = stripTileAt(mouseX, mouseY);
        int current = work.crop(target).image;
        for (int idx = stripScroll; idx <= work.images.size() && idx < stripScroll + cap; idx++) {
            int x = tileX(idx), y = top + 1;
            boolean hov = idx == hovered;
            if (idx == work.images.size()) { // "+" tile
                ctx.fill(x, y, x + THUMB_W, y + THUMB_H, hov ? 0x60FFFFFF : 0x40FFFFFF);
                ctx.drawBorder(x, y, THUMB_W, THUMB_H, 0x80FFFFFF);
                ctx.drawCenteredTextWithShadow(textRenderer, "+ Add", x + THUMB_W / 2, y + 10, 0xFFFFFFFF);
                continue;
            }
            Art a = art(idx);
            if (a != null) ThemeSelectScreen.drawFit(ctx, a.tex, x, y, THUMB_W, THUMB_H, true);
            else ctx.fill(x, y, x + THUMB_W, y + THUMB_H, 0xFF202030);
            boolean sel = idx == current;
            ctx.drawBorder(x - 1, y - 1, THUMB_W + 2, THUMB_H + 2, sel ? (pal.accent | 0xFF000000) : (hov ? 0xFFFFFFFF : 0x60FFFFFF));
            if (sel) ctx.drawBorder(x - 2, y - 2, THUMB_W + 4, THUMB_H + 4, pal.accent | 0xFF000000);
            ctx.fill(x, y + THUMB_H - 9, x + 10, y + THUMB_H, 0xB0000000);
            ctx.drawTextWithShadow(textRenderer, String.valueOf(idx + 1), x + 2, y + THUMB_H - 8, 0xFFFFFFFF);
            if (hov && work.images.size() > 1) {
                boolean rm = overRemove(idx, mouseX, mouseY);
                ctx.fill(x + THUMB_W - 9, y, x + THUMB_W, y + 9, rm ? 0xFFC03030 : 0xB0000000);
                ctx.drawTextWithShadow(textRenderer, "×", x + THUMB_W - 7, y, 0xFFFFFFFF);
            }
        }
        if (stripScroll > 0) ctx.drawTextWithShadow(textRenderer, "◀", centerX - 7, top + 10, 0xFFFFFFFF);
        if (stripScroll + cap < work.images.size() + 1)
            ctx.drawTextWithShadow(textRenderer, "▶", centerX + centerW + 1, top + 10, 0xFFFFFFFF);

        if (hovered >= 0) {
            List<Text> tip = new ArrayList<>();
            if (hovered == work.images.size()) {
                tip.add(Text.literal("Add images (you can pick several)"));
                tip.add(Text.literal("Then click one to use it for " + target.label).withColor(0xA0A0B0));
            } else if (overRemove(hovered, mouseX, mouseY)) {
                tip.add(Text.literal("Remove image " + (hovered + 1) + " from this theme"));
            } else {
                tip.add(Text.literal("Image " + (hovered + 1) + ": click to use for " + target.label));
                List<String> users = usersOf(hovered);
                tip.add(Text.literal(users.isEmpty() ? "Not used yet" : "Used by: " + String.join(", ", users)).withColor(0xA0A0B0));
            }
            ctx.drawTooltip(textRenderer, tip, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ canvas geometry

    /** Screen rect where the art is drawn. */
    private double[] imageRect(Art a) {
        int cx = centerX, cy = canvasY, cw = centerW, ch = canvasH;
        double s = Math.min((double) cw / a.origW, (double) ch / a.origH);
        double dw = a.origW * s, dh = a.origH * s;
        return new double[]{cx + (cw - dw) / 2, cy + (ch - dh) / 2, dw, dh, s};
    }

    /** Screen rect of the current crop. */
    private double[] cropRect(Art a) {
        double[] ir = imageRect(a);
        Rectangle r = work.crop(target).toPixels(a.origW, a.origH, work.aspect(target));
        return new double[]{ir[0] + r.x * ir[4], ir[1] + r.y * ir[4], r.width * ir[4], r.height * ir[4]};
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= centerX && mx < centerX + centerW && my >= canvasY && my < canvasY + canvasH;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        // image strip (arrows scroll it when it doesn't fit)
        if (my >= top && my < top + STRIP_H) {
            if (mx >= centerX - 8 && mx < centerX) {
                stripScroll = Math.max(0, stripScroll - 1);
                return true;
            }
            if (mx >= centerX + centerW && mx < centerX + centerW + 8) {
                stripScroll++;
                return true;
            }
        }
        int tile = stripTileAt(mx, my);
        if (tile >= 0) {
            if (tile == work.images.size()) FileDialogs.pickImages(this::addArt);
            else if (overRemove(tile, mx, my) || button == 1) removeImage(tile);
            else useImage(tile);
            return true;
        }
        // target list
        if (mx >= leftX && mx < leftX + COL_W && my >= top && my < top + UiTarget.values().length * ROW) {
            target = UiTarget.values()[(int) ((my - top) / ROW)];
            if (target.isRecipeBook() && work.isConnected(target)) {
                message = "Crop across both panels • shown while the recipe book is open";
            } else if (work.connectedBookOf(target) != null) {
                message = target.label + ": image shown while the recipe book is closed";
            }
            ensureArt(work.crop(target).image);
            changed();
            return true;
        }
        Art a = art(work.crop(target).image);
        if (a != null && inCanvas(mx, my) && button == 0) {
            double[] cr = cropRect(a);
            double cx = cr[0] + cr[2] / 2, cy = cr[1] + cr[3] / 2;
            boolean nearCorner = false;
            for (double[] c : new double[][]{{cr[0], cr[1]}, {cr[0] + cr[2], cr[1]}, {cr[0], cr[1] + cr[3]}, {cr[0] + cr[2], cr[1] + cr[3]}})
                if (Math.abs(mx - c[0]) <= 5 && Math.abs(my - c[1]) <= 5) nearCorner = true;
            if (nearCorner) {
                drag = Drag.RESIZE;
            } else {
                drag = Drag.MOVE;
                if (mx >= cr[0] && mx <= cr[0] + cr[2] && my >= cr[1] && my <= cr[1] + cr[3]) {
                    dragOffX = mx - cx;
                    dragOffY = my - cy;
                } else { // click outside: jump crop center here
                    dragOffX = 0;
                    dragOffY = 0;
                    moveCropTo(a, mx, my);
                }
            }
            return true;
        }
        return false;
    }

    private void moveCropTo(Art a, double mx, double my) {
        double[] ir = imageRect(a);
        CropRect c = work.crop(target);
        c.cx = (mx - dragOffX - ir[0]) / ir[2];
        c.cy = (my - dragOffY - ir[1]) / ir[3];
        c.clamp(a.origW, a.origH, work.aspect(target));
        changed();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Art a = art(work.crop(target).image);
        if (drag != Drag.NONE && a != null) {
            if (drag == Drag.MOVE) {
                moveCropTo(a, mx, my);
            } else {
                double[] ir = imageRect(a);
                double[] cr = cropRect(a);
                double ccx = cr[0] + cr[2] / 2, ccy = cr[1] + cr[3] / 2;
                double hScreen = Math.max(2 * Math.abs(my - ccy), 2 * Math.abs(mx - ccx) / work.aspect(target));
                CropRect c = work.crop(target);
                c.h = Math.max(0.03, hScreen / ir[3]);
                c.clamp(a.origW, a.origH, work.aspect(target));
                changed();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = Drag.NONE;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (my >= top && my < top + STRIP_H && mx >= centerX - 8 && mx < centerX + centerW + 8) {
            stripScroll = Math.max(0, stripScroll - (int) Math.signum(v));
            return true;
        }
        Art a = art(work.crop(target).image);
        if (a != null && inCanvas(mx, my)) {
            CropRect c = work.crop(target);
            c.h = Math.max(0.03, Math.min(1.0, c.h * (v > 0 ? 0.92 : 1.0 / 0.92)));
            c.clamp(a.origW, a.origH, work.aspect(target));
            changed();
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ThemePalette pal = work.palette();
        int accent = pal.accent | 0xFF000000;
        ctx.fill(0, 0, width, height, 0x90000000);
        ctx.drawTextWithShadow(textRenderer, "✦ Theme Editor", leftX, 8, accent);
        String hint = "Drag to move • scroll / drag a corner to resize • drop an image to add art";
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(hint, centerW), centerX + centerW / 2, 8, 0xFF9A9AAA);

        // target list
        UiTarget[] ts = UiTarget.values();
        for (int i = 0; i < ts.length; i++) {
            int ry = top + i * ROW;
            boolean hover = mouseX >= leftX && mouseX < leftX + COL_W && mouseY >= ry && mouseY < ry + ROW;
            if (ts[i] == target) ctx.fill(leftX, ry, leftX + COL_W, ry + ROW, (pal.accent & 0xFFFFFF) | 0x70000000);
            else if (hover) ctx.fill(leftX, ry, leftX + COL_W, ry + ROW, 0x30FFFFFF);
            boolean linked = ts[i].isRecipeBook() && work.isConnected(ts[i]);
            if (linked) ctx.drawTextWithShadow(textRenderer, "⇄", leftX + COL_W - 9, ry + (ROW - 8) / 2, pal.accent | 0xFF000000);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(ts[i].label, COL_W - (linked ? 14 : 6)), leftX + 3, ry + (ROW - 8) / 2,
                    ts[i] == target ? 0xFFFFFFFF : 0xFFC0C0CC);
        }

        // right column labels + swatches
        int lx = rightX;
        ctx.drawTextWithShadow(textRenderer, "Name", lx, top + 3, 0xFFC0C0CC);
        ctx.drawTextWithShadow(textRenderer, "Char.", lx, top + 19, 0xFFC0C0CC);
        int cy = top + 16 + 18 + 18;
        String[] cl = {"Accent", "Dark", "Light"};
        int[] cc = {pal.accent, pal.darkC, pal.lightC};
        for (int i = 0; i < 3; i++) {
            int yy = cy + i * 16;
            ctx.drawTextWithShadow(textRenderer, cl[i], lx, yy + 3, 0xFFC0C0CC);
            ctx.fill(rightX + COL_W - 12, yy + 1, rightX + COL_W, yy + 13, cc[i] | 0xFF000000);
            ctx.drawBorder(rightX + COL_W - 13, yy, 14, 14, 0xFF000000);
        }

        // canvas
        ctx.fill(centerX, canvasY, centerX + centerW, canvasY + canvasH, 0xC0101018);
        renderStrip(ctx, mouseX, mouseY);
        Art a = art(work.crop(target).image);
        if (work.images.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No art yet: Add Image..., Wallpaper Eng., or drop an image here",
                    centerX + centerW / 2, canvasY + canvasH / 2, 0xFFFFFFFF);
        } else if (a == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Loading art...", centerX + centerW / 2, canvasY + canvasH / 2, 0xFFFFFFFF);
        } else {
            double[] ir = imageRect(a);
            int ix = (int) ir[0], iy = (int) ir[1], iw = (int) ir[2], ih = (int) ir[3];
            ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, a.tex.id(), ix, iy, 0, 0, iw, ih,
                    a.tex.width(), a.tex.height(), a.tex.width(), a.tex.height());
            double[] cr = cropRect(a);
            int x0 = (int) Math.round(cr[0]), y0 = (int) Math.round(cr[1]);
            int x1 = (int) Math.round(cr[0] + cr[2]), y1 = (int) Math.round(cr[1] + cr[3]);
            int dim = 0xA0000000;
            ctx.fill(ix, iy, ix + iw, y0, dim);
            ctx.fill(ix, y1, ix + iw, iy + ih, dim);
            ctx.fill(ix, y0, x0, y1, dim);
            ctx.fill(x1, y0, ix + iw, y1, dim);
            ctx.drawBorder(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, accent);
            ctx.drawBorder(x0, y0, x1 - x0, y1 - y0, 0x80FFFFFF);
            for (int[] c : new int[][]{{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}})
                ctx.fill(c[0] - 2, c[1] - 2, c[0] + 3, c[1] + 3, 0xFFFFFFFF);
            String info = work.isConnected(target)
                    ? target.recipeHost().label + " + Recipe Book  (" + Theme.CONNECTED_W + "×" + Theme.CONNECTED_H + ")"
                    : target.label + (work.connectedBookOf(target) != null ? " (book closed)" : "") + "  (" + target.width + "×" + target.height + ")";
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, Math.max(20, x1 - x0 - 6)), x0 + 3, Math.max(canvasY + 2, y0 + 3), 0xFFFFFFFF);
        }
        ctx.drawBorder(centerX - 1, canvasY - 1, centerW + 2, canvasH + 2, (pal.accent & 0xFFFFFF) | 0x80000000);

        // live preview of the selected UI
        int py = canvasY + canvasH + 6, ph = height - 30 - py;
        ctx.drawTextWithShadow(textRenderer, "Preview", centerX, py, 0xFF9A9AAA);
        if (preview != null && ph > 20) {
            ThemeSelectScreen.drawFit(ctx, preview, centerX, py + 14, centerW, ph - 14, false);
        }

        if (!message.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, COL_W * 2), leftX, height - 12, 0xFFFFE070);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Slider showing a 0-100% value. */
    private class PercentSlider extends SliderWidget {
        private final String label;
        private final DoubleConsumer set;

        PercentSlider(int x, int y, int w, int h, String label, double value, DoubleConsumer set) {
            super(x, y, w, h, Text.empty(), value);
            this.label = label;
            this.set = set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            set.accept(value);
            changed();
        }
    }
}
