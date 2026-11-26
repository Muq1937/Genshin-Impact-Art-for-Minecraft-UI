package com.genshinui.gui;

import com.genshinui.GenshinUIClient;
import com.genshinui.ThemeManager;
import com.genshinui.theme.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lists themes with thumbnails and a live preview; apply / edit / create from an image. */
public class ThemeSelectScreen extends Screen {
    static final ExecutorService THUMBS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GenshinUI-Thumbs");
        t.setDaemon(true);
        return t;
    });
    private static final int ROW_H = 32;

    private final Screen parent;
    private List<Theme> themes = new ArrayList<>();
    private int selected = -1;
    private double scroll;
    private final Map<String, DynTex.Tex> thumbs = new HashMap<>();
    private DynTex.Tex preview;
    private String previewFor;
    private String message = "";
    private long lastClick;

    private int listX, listY, listW, listH, prevX, prevY, prevW, prevH;
    private ButtonWidget applyBtn, editBtn, dupBtn, delBtn, particlesBtn;

    public ThemeSelectScreen(Screen parent) {
        super(Text.literal("Genshin UI Themes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String keep = selected >= 0 && selected < themes.size() ? themes.get(selected).id : null;
        themes = ThemeManager.store.list();
        if (keep == null && ThemeManager.active() != null) keep = ThemeManager.active().id;
        selected = themes.isEmpty() ? -1 : 0;
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).id.equals(keep)) selected = i;

        listX = 10;
        listY = 26;
        listW = Math.min(190, (int) (width * 0.4));
        listH = height - listY - 58;
        prevX = listX + listW + 10;
        prevY = listY;
        prevW = width - prevX - 10;
        prevH = listH - 22;

        int bw = Math.min(104, (width - 20 - 4 * 4) / 5);
        int bx = (width - (bw * 5 + 16)) / 2;
        int y1 = height - 50, y2 = height - 26;
        applyBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), b -> applyOrDisable()).dimensions(bx, y1, bw, 20).build());
        editBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Edit / Crop..."), b -> edit()).dimensions(bx + (bw + 4), y1, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("New from Image..."), b -> FileDialogs.pickImages(this::newFromImages))
                .dimensions(bx + 2 * (bw + 4), y1, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wallpaper Engine..."),
                        b -> client.setScreen(new WallpaperBrowserScreen(this, this::newFromWallpaper)))
                .dimensions(bx + 3 * (bw + 4), y1, bw, 20).build());
        dupBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Duplicate"), b -> duplicate()).dimensions(bx + 4 * (bw + 4), y1, bw, 20).build());
        int bw2 = (bw * 5 + 16 - 12) / 4;
        delBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> delete()).dimensions(bx, y2, bw2, 20).build());
        particlesBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            ThemeManager.settings.particles = !ThemeManager.settings.particles;
            ThemeManager.settings.save();
            updateButtons();
        }).dimensions(bx + (bw2 + 4), y2, bw2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Open Folder"), b -> {
            try {
                java.nio.file.Files.createDirectories(ThemeManager.store.root());
            } catch (IOException ignored) {
            }
            Util.getOperatingSystem().open(ThemeManager.store.root());
        }).dimensions(bx + 2 * (bw2 + 4), y2, bw2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(bx + 3 * (bw2 + 4), y2, bw2, 20).build());

        for (Theme t : themes) loadThumb(t);
        updateButtons();
        requestPreview();
    }

    private Theme sel() {
        return selected >= 0 && selected < themes.size() ? themes.get(selected) : null;
    }

    private boolean isActive(Theme t) {
        return t != null && ThemeManager.active() != null && ThemeManager.active().id.equals(t.id);
    }

    private void updateButtons() {
        Theme t = sel();
        boolean has = t != null;
        applyBtn.active = has && !ThemeManager.building;
        applyBtn.setMessage(Text.literal(isActive(t) ? "Turn Off" : "Apply"));
        editBtn.active = has;
        dupBtn.active = has;
        delBtn.active = has && !isActive(t);
        particlesBtn.setMessage(Text.literal("Particles: " + (ThemeManager.settings.particles ? "On" : "Off")));
    }

    private void loadThumb(Theme t) {
        if (thumbs.containsKey(t.id)) return;
        thumbs.put(t.id, null);
        THUMBS.submit(() -> {
            BufferedImage img = ThemeManager.store.image(t, 0);
            if (img == null) return;
            BufferedImage small = DynTex.fit(img, 192);
            client.execute(() -> thumbs.put(t.id, DynTex.upload("thumb_" + t.id, small)));
        });
    }

    private void requestPreview() {
        Theme t = sel();
        if (t == null || t.id.equals(previewFor)) return;
        previewFor = t.id;
        Previews.request(() -> Previews.overview(t), img -> {
            DynTex.release(preview);
            preview = DynTex.upload("overview", img);
        });
    }

    // ------------------------------------------------------------------ actions

    private void applyOrDisable() {
        Theme t = sel();
        if (t == null) return;
        if (isActive(t)) ThemeManager.disable();
        else ThemeManager.apply(t);
        updateButtons();
    }

    private void edit() {
        Theme t = sel();
        if (t != null) client.setScreen(new ThemeEditorScreen(this, t));
    }

    private void duplicate() {
        Theme t = sel();
        if (t == null) return;
        try {
            Theme copy = ThemeManager.store.duplicate(t);
            previewFor = null;
            selected = -1;
            clearAndInit();
            for (int i = 0; i < themes.size(); i++) if (themes.get(i).id.equals(copy.id)) selected = i;
            requestPreview();
            updateButtons();
        } catch (IOException e) {
            message = "Duplicate failed: " + e.getMessage();
        }
    }

    private void delete() {
        Theme t = sel();
        if (t == null || isActive(t)) return;
        client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(ok -> {
            if (ok) {
                try {
                    ThemeManager.store.delete(t);
                    DynTex.release(thumbs.remove(t.id));
                } catch (IOException e) {
                    message = "Delete failed: " + e.getMessage();
                }
            }
            client.setScreen(this);
        }, Text.literal("Delete theme \"" + t.name + "\"?"), Text.literal("Its folder and images will be removed.")));
    }

    /** Creates one theme from image files (or Wallpaper Engine scene.pkg files); all of them become its images. */
    void newFromImages(List<Path> files) {
        Path file = files.get(0);
        String base = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        if (base.equalsIgnoreCase("scene") && file.getParent() != null) { // scene.pkg: use the wallpaper's title
            var item = com.genshinui.we.WallpaperEngine.readItem(file.getParent());
            if (item != null) base = item.title();
        }
        newTheme(base, () -> {
            List<BufferedImage> all = new ArrayList<>();
            for (Path f : files) all.addAll(ThemeStore.readArt(f));
            return all;
        });
    }

    /** Wallpaper Engine browser result. */
    public void newFromWallpaper(String title, List<BufferedImage> art) {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(this); // also initializes this.client
        newTheme(title, () -> art);
    }

    private interface ArtLoader {
        List<BufferedImage> load() throws IOException;
    }

    /** Creates a theme off-thread: saves the art, auto-picks colors and crops, then opens the crop editor. */
    private void newTheme(String rawName, ArtLoader loader) {
        String name = rawName
                .replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)|【[^】]*】", " ")      // [tags] (notes) 【tags】
                .replaceAll("(?i)genshin\\s*impact|原神|live\\s*wallpaper|audio\\s*responsive|\\b\\d+\\s*k\\b|\\b(?:hd|uhd)\\b", " ")
                .replace('_', ' ').replaceAll("[|/\\\\•·]", " ").replaceAll("\\s+-\\s+|^\\s*-|-\\s*$", " ")
                .replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) name = rawName.trim();
        if (name.length() > 40) name = name.substring(0, 40).trim();
        if (name.isEmpty()) name = "My Theme";
        String finalName = name;
        message = "Importing art...";
        THUMBS.submit(() -> {
            try {
                List<BufferedImage> art = loader.load();
                if (art.isEmpty()) throw new IOException("No usable art found");
                Theme t = ThemeManager.store.create(finalName);
                for (BufferedImage img : art) ThemeManager.store.importImage(t, img);
                configureNewTheme(t, art.get(0));
                ThemeManager.store.save(t);
                client.execute(() -> {
                    message = "";
                    client.setScreen(new ThemeEditorScreen(this, t));
                });
            } catch (Exception e) {
                GenshinUIClient.LOGGER.warn("Import failed", e);
                client.execute(() -> message = "Import failed: " + e.getMessage());
            }
        });
    }

    private static void configureNewTheme(Theme t, BufferedImage img) {
        {
            PaletteExtractor.Result pal = PaletteExtractor.extract(img);
            t.accent = pal.accent();
            t.dark = pal.dark();
            t.light = pal.light();
            t.mode = pal.mode();
            t.particle = "spark";
            t.icons = "star";
            for (UiTarget target : UiTarget.values()) {
                CropRect c = CropRect.fill(img.getWidth(), img.getHeight(), target.aspect());
                if (target.kind == UiTarget.Kind.CONTAINER) { // slightly zoomed, biased toward the top (faces)
                    c.h = Math.min(c.h, 0.9);
                    c.cy = 0.45;
                    c.clamp(img.getWidth(), img.getHeight(), target.aspect());
                }
                t.crops.put(target.id, c);
            }
        }
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        List<Path> imgs = paths.stream().filter(FileDialogs::isImage).toList();
        if (!imgs.isEmpty()) {
            newFromImages(imgs);
            return;
        }
        message = "Drop a PNG / JPG image or a Wallpaper Engine scene.pkg to create a theme";
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        thumbs.values().forEach(DynTex::release);
        thumbs.clear();
        DynTex.release(preview);
        preview = null;
        previewFor = null;
    }

    @Override
    public void tick() {
        updateButtons();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + scroll) / ROW_H);
            if (idx >= 0 && idx < themes.size()) {
                boolean dbl = idx == selected && Util.getMeasuringTimeMs() - lastClick < 300;
                selected = idx;
                lastClick = Util.getMeasuringTimeMs();
                updateButtons();
                requestPreview();
                if (dbl && !isActive(sel())) applyOrDisable();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        double max = Math.max(0, themes.size() * ROW_H - listH);
        scroll = Math.max(0, Math.min(max, scroll - v * ROW_H));
        return true;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ThemePalette pal = ThemeManager.palette();
        int accent = pal != null ? pal.accent | 0xFF000000 : 0xFFB7A6FF;
        ctx.drawCenteredTextWithShadow(textRenderer, "✦ Genshin UI Themes ✦", width / 2, 9, accent);

        // list
        ctx.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xA0000000);
        ctx.drawBorder(listX - 2, listY - 2, listW + 4, listH + 4, accent & 0x80FFFFFF);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < themes.size(); i++) {
            int ry = listY + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            Theme t = themes.get(i);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= ry && mouseY < ry + ROW_H && mouseY >= listY && mouseY < listY + listH;
            if (i == selected) ctx.fill(listX, ry, listX + listW, ry + ROW_H, (Colors2.parse(t.accent, accent) & 0xFFFFFF) | 0x55000000);
            else if (hover) ctx.fill(listX, ry, listX + listW, ry + ROW_H, 0x30FFFFFF);
            DynTex.Tex th = thumbs.get(t.id);
            int tw = 48, thh = 27;
            if (th != null) {
                drawFit(ctx, th, listX + 3, ry + 2, tw, thh, true);
            } else {
                ctx.fill(listX + 3, ry + 2, listX + 3 + tw, ry + 2 + thh, 0xFF202030);
            }
            ctx.drawBorder(listX + 2, ry + 1, tw + 2, thh + 2, Colors2.parse(t.accent, accent));
            int tx = listX + tw + 8;
            String name = (isActive(t) ? "✦ " : "") + t.name;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, listW - tw - 12), tx, ry + 6, 0xFFFFFFFF);
            String sub = t.character.isBlank() ? (t.sample ? "sample" : "custom") : t.character;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(sub, listW - tw - 12), tx, ry + 17, 0xFFA8A8B8);
        }
        ctx.disableScissor();
        if (themes.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No themes yet", listX + listW / 2, listY + 20, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "Click \"New from Image...\"", listX + listW / 2, listY + 32, 0xFFA0A0A0);
        }

        // preview
        ctx.fill(prevX - 1, prevY - 1, prevX + prevW + 1, prevY + prevH + 1, 0x80000000);
        if (preview != null) drawFit(ctx, preview, prevX + 4, prevY + 4, prevW - 8, prevH - 8, false);
        else if (sel() != null) ctx.drawCenteredTextWithShadow(textRenderer, "Rendering preview...", prevX + prevW / 2, prevY + prevH / 2, 0xFFFFFFFF);
        Theme t = sel();
        if (t != null) {
            int iy = prevY + prevH + 4;
            int sx = prevX;
            for (String hex : new String[]{t.accent, t.dark, t.light}) {
                ctx.fill(sx, iy, sx + 10, iy + 10, Colors2.parse(hex, 0xFF808080));
                ctx.drawBorder(sx - 1, iy - 1, 12, 12, 0xFF000000);
                sx += 13;
            }
            String info = t.mode + " • " + t.particle + " • " + t.icons + " • " + t.images.size() + " image(s)";
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, prevW - 45), sx + 4, iy + 1, 0xFFD0D0D8);
        }

        String status = ThemeManager.building ? ThemeManager.status : (!message.isEmpty() ? message : ThemeManager.status);
        if (status.isEmpty()) status = "Tip: drag & drop an image here to make a new theme • Double-click to apply";
        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 62 + 2, ThemeManager.building ? 0xFFFFE070 : 0xFF9090A0);

        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Draws a dynamic texture scaled to fit (or cover, when {@code cover}) the box. */
    static void drawFit(DrawContext ctx, DynTex.Tex tex, int x, int y, int w, int h, boolean cover) {
        double s = cover ? Math.max((double) w / tex.width(), (double) h / tex.height())
                : Math.min((double) w / tex.width(), (double) h / tex.height());
        if (cover) {
            int rw = (int) Math.round(w / s), rh = (int) Math.round(h / s);
            int u = (tex.width() - rw) / 2, v = (tex.height() - rh) / 2;
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex.id(), x, y, u, v, w, h, rw, rh, tex.width(), tex.height());
        } else {
            int dw = (int) Math.round(tex.width() * s), dh = (int) Math.round(tex.height() * s);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex.id(), x + (w - dw) / 2, y + (h - dh) / 2, 0, 0, dw, dh,
                    tex.width(), tex.height(), tex.width(), tex.height());
        }
    }
}
