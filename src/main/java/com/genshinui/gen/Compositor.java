package com.genshinui.gen;

import com.genshinui.theme.CropRect;
import com.genshinui.theme.Theme;
import com.genshinui.theme.ThemePalette;
import com.genshinui.theme.UiTarget;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.function.IntFunction;

import static com.genshinui.theme.Colors2.*;

/**
 * Generates every themed texture from the theme art + palette. Pure Java2D (no Minecraft classes)
 * so it can run off-thread in game and in the offline preview tool.
 */
public final class Compositor {
    public final Theme theme;
    public final ThemePalette p;
    /** Pixels per logical GUI pixel of the generated textures. */
    public final int S;
    private final IntFunction<BufferedImage> images;

    public Compositor(Theme theme, int scale, IntFunction<BufferedImage> images) {
        this.theme = theme;
        this.p = theme.palette();
        this.S = scale;
        this.images = images;
    }

    // ------------------------------------------------------------------ art helpers

    public BufferedImage image(int index) {
        BufferedImage img = images.apply(index);
        if (img == null) img = images.apply(0);
        if (img == null) { // no art at all: soft gradient in theme colors
            img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setPaint(new GradientPaint(0, 0, new Color(p.accent), 512, 512, new Color(p.darkC)));
            g.fillRect(0, 0, 512, 512);
            g.dispose();
        }
        return img;
    }

    /** The crop for a target, scaled to w x h, for a UI piece of the given aspect. */
    public BufferedImage art(UiTarget target, double aspect, int w, int h) {
        CropRect crop = theme.crop(target);
        BufferedImage src = image(crop.image);
        Rectangle r = crop.toPixels(src.getWidth(), src.getHeight(), aspect);
        return scaleHQ(src, r, w, h);
    }

    public static BufferedImage scaleHQ(BufferedImage src, Rectangle r, int w, int h) {
        BufferedImage cur = src.getSubimage(r.x, r.y, r.width, r.height);
        int cw = r.width, ch = r.height;
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = resize(cur, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return resize(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h, Object interp) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.scale(S, S); // draw in logical pixels
        return g;
    }

    private static Color c(int argb) {
        return new Color(argb, true);
    }

    private static BufferedImage blank(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    public static Rectangle alphaBounds(BufferedImage img) {
        int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                if (a(img.getRGB(x, y)) > 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        if (maxX < 0) return new Rectangle(0, 0, img.getWidth(), img.getHeight());
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    // ------------------------------------------------------------------ containers

    /**
     * Art for a container texture. A connected recipe book and its screen (while the book is open) are both cut
     * from the book's wide crop: the book from the left 147/325, the screen from the right 176/325 (vanilla
     * leaves a 2 px gap between them), so the image runs across both panels.
     *
     * @param openBook non-null when generating the screen's "recipe book open" variant for that book
     */
    private BufferedImage containerArt(UiTarget target, Rectangle bb, UiTarget openBook) {
        boolean bookSide = target.isRecipeBook() && theme.isConnected(target);
        UiTarget book = bookSide ? target : openBook;
        if (book == null) return art(target, (double) bb.width / bb.height, bb.width * S, bb.height * S);
        CropRect crop = theme.crop(book);
        BufferedImage src = image(crop.image);
        Rectangle r = crop.toPixels(src.getWidth(), src.getHeight(), Theme.CONNECTED_ASPECT);
        double unit = r.width / (double) Theme.CONNECTED_W;
        Rectangle part = bookSide
                ? new Rectangle(r.x, r.y, (int) Math.round(147 * unit), r.height)
                : new Rectangle(r.x + (int) Math.round(149 * unit), r.y, (int) Math.round(176 * unit), r.height);
        part.width = Math.max(1, Math.min(part.width, src.getWidth() - part.x));
        return scaleHQ(src, part, bb.width * S, bb.height * S);
    }

    private enum Key { PANEL, SLOT, SLOT_DARK, LIGHT, SHADOW, BLACK, KEEP }

    private static boolean gray(int c, int v) {
        return Math.abs(r(c) - v) <= 2 && Math.abs(g(c) - v) <= 2 && Math.abs(b(c) - v) <= 2;
    }

    private static Key classify(int c) {
        if (gray(c, 0xC6)) return Key.PANEL;
        if (gray(c, 0x8B)) return Key.SLOT;
        if (gray(c, 0x37)) return Key.SLOT_DARK;
        if (gray(c, 0xFF)) return Key.LIGHT;
        if (gray(c, 0x55)) return Key.SHADOW;
        if (gray(c, 0x00) || gray(c, 0x21) || (r(c) < 12 && g(c) < 12 && b(c) < 20)) return Key.BLACK;
        return Key.KEEP;
    }

    /**
     * Re-skins a vanilla container texture: the vanilla GUI palette is used as a key so every container
     * (and any resource pack that keeps vanilla colors) maps automatically:
     * panel gray -> veiled art, slot gray -> tinted art window, bevels -> theme bevels, outline -> frame.
     */
    public BufferedImage container(BufferedImage van, UiTarget target) {
        return container(van, target, null);
    }

    /** @param openBook non-null for the variant of a screen drawn while that connected recipe book is open */
    public BufferedImage container(BufferedImage van, UiTarget target, UiTarget openBook) {
        Rectangle bb = alphaBounds(van);
        int vw = van.getWidth(), vh = van.getHeight();
        int W = vw * S;
        BufferedImage out = blank(W, vh * S);
        BufferedImage art = containerArt(target, bb, openBook);
        int aw = art.getWidth();
        int[] ap = art.getRGB(0, 0, aw, art.getHeight(), null, 0, aw);
        int[] vp = van.getRGB(0, 0, vw, vh, null, 0, vw);
        int[] op = new int[W * vh * S];
        // The recipe book's big recipe area is 373737 (normally a slot bevel): show it as a darker art window.
        boolean recipeBook = target.isRecipeBook();

        for (int vy = 0; vy < vh; vy++) {
            for (int vx = 0; vx < vw; vx++) {
                int v = vp[vy * vw + vx];
                if (a(v) == 0) continue;
                Key k = classify(v);
                boolean edge = false;
                if (k == Key.BLACK) {
                    edge = vx == 0 || vy == 0 || vx == vw - 1 || vy == vh - 1
                            || a(vp[vy * vw + vx - 1]) == 0 || a(vp[vy * vw + vx + 1]) == 0
                            || a(vp[(vy - 1) * vw + vx]) == 0 || a(vp[(vy + 1) * vw + vx]) == 0;
                }
                for (int sy = 0; sy < S; sy++) {
                    int oy = vy * S + sy, ay = oy - bb.y * S;
                    for (int sx = 0; sx < S; sx++) {
                        int ox = vx * S + sx, ax = ox - bb.x * S;
                        int artC = (ax >= 0 && ay >= 0 && ax < aw && ay < art.getHeight()) ? ap[ay * aw + ax] | 0xFF000000 : p.darkC;
                        op[oy * W + ox] = switch (k) {
                            case PANEL -> mix(artC, p.panelTint, p.panelStrength);
                            case SLOT -> mix(artC, p.slotTint, p.slotStrength);
                            case SLOT_DARK -> recipeBook ? mix(artC, p.window, 0.6) : mix(artC, p.bevelDark, 0.8);
                            case LIGHT -> mix(artC, p.bevelLight, 0.75);
                            case SHADOW -> mix(artC, p.bevelDark, 0.7);
                            case BLACK -> edge ? p.frame : mix(artC, p.window, 0.72);
                            case KEEP -> v;
                        };
                    }
                }
            }
        }
        out.setRGB(0, 0, W, vh * S, op, 0, W);
        return out;
    }

    /**
     * Vanilla draws an N-row chest from generic_54.png in two slices (top {@code rows*18+17} px, then the 96 px
     * player inventory from y=126). This builds that exact shape as one seamless 256x256 texture, so the art can be
     * mapped continuously (otherwise small chests cut the art in half).
     */
    public static BufferedImage stitchChest(BufferedImage generic54, int rows) {
        int f = generic54.getWidth() / 256; // vanilla scale (1 for vanilla, more for HD packs)
        BufferedImage out = new BufferedImage(256 * f, 256 * f, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int top = (rows * 18 + 17) * f;
        g.drawImage(generic54, 0, 0, 176 * f, top, 0, 0, 176 * f, top, null);
        g.drawImage(generic54, 0, top, 176 * f, top + 96 * f, 0, 126 * f, 176 * f, 222 * f, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ small GUI sprites

    /**
     * Recolors a small vanilla GUI sprite (recipe book tabs/slots/buttons, creative tabs, page arrows...) by its
     * vanilla palette into translucent theme colors, so the panel art shows through. Upscaled to S with nearest.
     */
    public BufferedImage recolorSprite(BufferedImage van) {
        int vw = van.getWidth(), vh = van.getHeight();
        int[] vp = van.getRGB(0, 0, vw, vh, null, 0, vw);
        int[] mapped = new int[vp.length];
        for (int i = 0; i < vp.length; i++) mapped[i] = mapSpritePixel(vp, vw, vh, i);
        BufferedImage out = blank(vw * S, vh * S);
        int W = vw * S;
        int[] op = new int[W * vh * S];
        for (int y = 0; y < vh * S; y++)
            for (int x = 0; x < W; x++) op[y * W + x] = mapped[(y / S) * vw + x / S];
        out.setRGB(0, 0, W, vh * S, op, 0, W);
        return out;
    }

    private int mapSpritePixel(int[] vp, int vw, int vh, int i) {
        int v = vp[i];
        int a = a(v);
        if (a == 0) return 0;
        int rgb = v & 0xFFFFFF;
        int hover = mixRgb(p.accent, p.lightC, 0.15);
        int out = switch (rgb) {
            case 0xC6C6C6 -> withAlpha(mixRgb(p.panelTint, p.accent, 0.12), 235);   // selected tab / panel
            case 0x8B8B8B, 0x8E8E8E -> withAlpha(p.slotTint, 150);                  // slot / unselected tab
            case 0x6A6A6A -> withAlpha(darker(p.slotTint, 0.35), 175);              // uncraftable slot
            case 0x373737, 0x383838 -> withAlpha(p.bevelDark, 210);
            case 0x555555 -> withAlpha(p.bevelDark, 200);
            case 0xFFFFFF -> withAlpha(p.bevelLight, 230);
            case 0xCCCCCC -> p.accent | 0xFF000000;                                  // craftable highlight
            case 0x8892C9 -> withAlpha(hover, 190);                                   // hovered background
            case 0x00073E, 0x00031E -> p.lightC | 0xFF000000;                         // hovered outline
            case 0x343E75, 0x5F647F, 0x444964 -> withAlpha(mixRgb(p.accent, p.darkC, 0.4), 230);
            case 0x218756 -> p.accent | 0xFF000000;                                   // filter "on" green
            case 0xAEC57A -> p.lightC | 0xFF000000;
            case 0x0E2725 -> p.darkC | 0xFF000000;
            case 0x000000 -> p.frame | 0xFF000000;
            default -> v;                                                             // icons, red marks: keep
        };
        return a < 255 ? withAlpha(out, a(out) * a / 255) : out;
    }

    // ------------------------------------------------------------------ hotbar

    public BufferedImage hotbar() {
        int w = 182, h = 22;
        BufferedImage out = blank(w * S, h * S);
        BufferedImage art = art(UiTarget.HOTBAR, (double) w / h, w * S, h * S);
        // tint art toward the dark color so items stay readable
        int[] ap = art.getRGB(0, 0, w * S, h * S, null, 0, w * S);
        double tint = p.dark ? 0.45 : 0.5;
        for (int i = 0; i < ap.length; i++) ap[i] = withAlpha(mix(ap[i] | 0xFF000000, p.darkC, tint), 238);
        art.setRGB(0, 0, w * S, h * S, ap, 0, w * S);

        Graphics2D g = gfx(out);
        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 4, 4);
        g.setClip(body);
        g.drawImage(art, 0, 0, w, h, null);
        g.setClip(null);
        // slots
        for (int i = 0; i < 9; i++) {
            double x = 2 + i * 20, y = 2;
            RoundRectangle2D slot = new RoundRectangle2D.Double(x + 0.5, y + 0.5, 17, 17, 2.5, 2.5);
            g.setColor(c(withAlpha(p.darkC, 95)));
            g.fill(slot);
            g.setStroke(new BasicStroke(0.5f));
            g.setColor(c(withAlpha(p.lightC, 120)));
            g.draw(slot);
            if (i < 8) diamond(g, x + 19.5, 11, 1.1, withAlpha(p.accent, 200), 0);
        }
        // frame
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(withAlpha(darker(p.darkC, 0.5), 230)));
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 4, 4));
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(c(withAlpha(p.accent, 230)));
        g.draw(new RoundRectangle2D.Double(1.25, 1.25, w - 2.5, h - 2.5, 3, 3));
        g.dispose();
        return out;
    }

    public BufferedImage hotbarSelection() {
        int w = 24, h = 23;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D box = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 3.5, 3.5);
        g.setColor(c(withAlpha(p.lightC, 40)));
        g.fill(box);
        // glow
        for (int i = 3; i >= 1; i--) {
            g.setStroke(new BasicStroke(i * 0.6f));
            g.setColor(c(withAlpha(p.accent, 50)));
            g.draw(box);
        }
        g.setStroke(new BasicStroke(1.1f));
        g.setColor(c(withAlpha(p.accent, 255)));
        g.draw(box);
        g.setStroke(new BasicStroke(0.45f));
        g.setColor(c(withAlpha(p.lightC, 230)));
        g.draw(new RoundRectangle2D.Double(2.2, 2.2, w - 4.4, h - 4.4, 2.5, 2.5));
        // ornaments: top + bottom diamonds, corner sparks
        diamond(g, w / 2.0, 1.2, 1.7, p.lightC | 0xFF000000, p.accent);
        diamond(g, w / 2.0, h - 1.2, 1.2, p.accent | 0xFF000000, 0);
        g.dispose();
        return out;
    }

    public BufferedImage offhand(BufferedImage van) {
        Rectangle bb = alphaBounds(van);
        BufferedImage out = blank(van.getWidth() * S, van.getHeight() * S);
        BufferedImage art = art(UiTarget.HOTBAR, 1.0, bb.width * S, bb.height * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D box = new RoundRectangle2D.Double(bb.x + 0.5, bb.y + 0.5, bb.width - 1, bb.height - 1, 4, 4);
        g.setClip(box);
        g.drawImage(art, bb.x, bb.y, bb.width, bb.height, null);
        g.setClip(null);
        g.setColor(c(withAlpha(p.darkC, 150)));
        g.fill(box);
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(withAlpha(darker(p.darkC, 0.5), 230)));
        g.draw(box);
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(c(withAlpha(p.accent, 230)));
        g.draw(new RoundRectangle2D.Double(bb.x + 1.25, bb.y + 1.25, bb.width - 2.5, bb.height - 2.5, 3, 3));
        g.dispose();
        return out;
    }

    private void diamond(Graphics2D g, double cx, double cy, double r, int fill, int outline) {
        Path2D d = new Path2D.Double();
        d.moveTo(cx, cy - r);
        d.lineTo(cx + r * 0.75, cy);
        d.lineTo(cx, cy + r);
        d.lineTo(cx - r * 0.75, cy);
        d.closePath();
        g.setColor(c(fill));
        g.fill(d);
        if (outline != 0) {
            g.setStroke(new BasicStroke(0.35f));
            g.setColor(c(outline | 0xFF000000));
            g.draw(d);
        }
    }

    // ------------------------------------------------------------------ experience bar

    public BufferedImage xpBackground() {
        int w = 182, h = 5;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D bar = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 3, 3);
        g.setColor(c(withAlpha(p.darkC, 210)));
        g.fill(bar);
        g.setStroke(new BasicStroke(0.5f));
        g.setColor(c(withAlpha(p.accent, 200)));
        g.draw(bar);
        for (int i = 1; i < 18; i++) {
            g.setColor(c(withAlpha(p.lightC, 50)));
            g.fill(new Rectangle2D.Double(i * 10.1, 1.5, 0.35, 2));
        }
        g.dispose();
        return out;
    }

    public BufferedImage xpProgress() {
        int w = 182, h = 5;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D bar = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 3, 3);
        g.setPaint(new LinearGradientPaint(0, 0, w, 0, new float[]{0f, 0.6f, 1f},
                new Color[]{c(mixRgb(p.accent, p.darkC, 0.25)), c(p.accent), c(p.lightC)}));
        g.fill(bar);
        g.setColor(c(withAlpha(0xFFFFFFFF, 110)));
        g.fill(new RoundRectangle2D.Double(1.5, 1, w - 3, 1, 1, 1));
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ widgets

    /** state: 0 normal, 1 highlighted (hovered/focused), 2 disabled. 200x20 nine-slice, border 3. */
    public BufferedImage button(int state) {
        int w = 200, h = 20;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 4, 4);
        int top, bottom, border;
        int alpha = 225;
        switch (state) {
            case 1 -> {
                top = mixRgb(p.darkC, p.accent, 0.45);
                bottom = mixRgb(p.darkC, p.accent, 0.25);
                border = p.lightC;
            }
            case 2 -> {
                top = mixRgb(p.darkC, 0xFF404040, 0.6);
                bottom = mixRgb(p.darkC, 0xFF202020, 0.6);
                border = 0xFF6A6A70;
                alpha = 170;
            }
            default -> {
                top = mixRgb(p.darkC, p.accent, 0.18);
                bottom = p.darkC;
                border = p.accent;
            }
        }
        g.setPaint(new GradientPaint(0, 0, c(withAlpha(top, alpha)), 0, h, c(withAlpha(bottom, alpha))));
        g.fill(body);
        g.setColor(c(withAlpha(0xFFFFFFFF, state == 2 ? 15 : 40)));
        g.fill(new RoundRectangle2D.Double(1.5, 1.5, w - 3, 1, 1, 1));
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(withAlpha(border, state == 2 ? 140 : 235)));
        g.draw(body);
        if (state != 2) {
            diamond(g, 1.6, h / 2.0, 1.3, withAlpha(state == 1 ? p.lightC : p.accent, 255), 0);
            diamond(g, w - 1.6, h / 2.0, 1.3, withAlpha(state == 1 ? p.lightC : p.accent, 255), 0);
        }
        g.dispose();
        return out;
    }

    /** text field / slider track: 200x20 nine-slice, border 1. */
    public BufferedImage field(boolean highlighted, boolean slider) {
        int w = 200, h = 20;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        Rectangle2D body = new Rectangle2D.Double(0.5, 0.5, w - 1, h - 1);
        g.setColor(c(withAlpha(darker(p.darkC, 0.35), slider ? 200 : 235)));
        g.fill(body);
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(withAlpha(highlighted ? p.lightC : p.accent, highlighted ? 255 : 190)));
        g.draw(body);
        g.dispose();
        return out;
    }

    /** slider handle: 8x20 nine-slice. */
    public BufferedImage sliderHandle(boolean highlighted) {
        int w = 8, h = 20;
        BufferedImage out = blank(w * S, h * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D body = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 2.5, 2.5);
        int base = highlighted ? p.lightC : p.accent;
        g.setPaint(new GradientPaint(0, 0, c(lighter(base, 0.25)), 0, h, c(mixRgb(base, p.darkC, 0.3))));
        g.fill(body);
        g.setStroke(new BasicStroke(1f));
        g.setColor(c(darker(p.darkC, 0.4)));
        g.draw(body);
        g.dispose();
        return out;
    }

    /** tooltip background: 100x100 nine-slice, border 9 (visible area 8..92). */
    public BufferedImage tooltipBackground() {
        BufferedImage out = blank(100 * S, 100 * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D body = new RoundRectangle2D.Double(8, 8, 84, 84, 4, 4);
        g.setPaint(new GradientPaint(0, 8, c(withAlpha(mixRgb(p.darkC, p.accent, 0.12), 242)), 0, 92, c(withAlpha(darker(p.darkC, 0.3), 242))));
        g.fill(body);
        g.dispose();
        return out;
    }

    /** tooltip frame: 100x100 nine-slice, border 10, inner stretched. */
    public BufferedImage tooltipFrame() {
        BufferedImage out = blank(100 * S, 100 * S);
        Graphics2D g = gfx(out);
        RoundRectangle2D f = new RoundRectangle2D.Double(9, 9, 82, 82, 3, 3);
        g.setStroke(new BasicStroke(0.9f));
        g.setPaint(new GradientPaint(0, 9, c(p.accent), 0, 91, c(withAlpha(mixRgb(p.accent, p.darkC, 0.55), 255))));
        g.draw(f);
        g.setStroke(new BasicStroke(0.35f));
        g.setColor(c(withAlpha(p.lightC, 90)));
        g.draw(new RoundRectangle2D.Double(10.2, 10.2, 79.6, 79.6, 2, 2));
        diamond(g, 9, 9, 1.4, p.lightC | 0xFF000000, p.accent);
        diamond(g, 91, 9, 1.4, p.lightC | 0xFF000000, p.accent);
        diamond(g, 9, 91, 1.4, p.lightC | 0xFF000000, p.accent);
        diamond(g, 91, 91, 1.4, p.lightC | 0xFF000000, p.accent);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ HUD icons (9x9 logical)

    public enum IconPart { CONTAINER, CONTAINER_BLINK, FULL, HALF_LEFT, HALF_RIGHT }

    public static Shape iconShape(String style) {
        switch (style) {
            case "heart": {
                Area a = new Area(new Ellipse2D.Double(0.75, 1.2, 4.0, 4.0));
                a.add(new Area(new Ellipse2D.Double(4.25, 1.2, 4.0, 4.0)));
                Path2D t = new Path2D.Double();
                t.moveTo(1.0, 4.0);
                t.lineTo(8.0, 4.0);
                t.lineTo(4.5, 8.3);
                t.closePath();
                a.add(new Area(t));
                return a;
            }
            case "moon": {
                Area a = new Area(new Ellipse2D.Double(0.7, 0.7, 7.6, 7.6));
                a.subtract(new Area(new Ellipse2D.Double(3.3, -0.4, 6.4, 6.4)));
                return a;
            }
            case "petal": {
                Path2D s = new Path2D.Double();
                s.moveTo(4.5, 8.4);
                s.curveTo(1.0, 6.6, 0.3, 3.2, 2.4, 0.8);
                s.curveTo(3.2, 0.9, 3.9, 1.4, 4.5, 2.4);
                s.curveTo(5.1, 1.4, 5.8, 0.9, 6.6, 0.8);
                s.curveTo(8.7, 3.2, 8.0, 6.6, 4.5, 8.4);
                s.closePath();
                return s;
            }
            case "drop": {
                Path2D s = new Path2D.Double();
                s.moveTo(4.5, 0.5);
                s.curveTo(6.0, 2.8, 7.6, 4.2, 7.6, 5.6);
                s.curveTo(7.6, 7.4, 6.2, 8.5, 4.5, 8.5);
                s.curveTo(2.8, 8.5, 1.4, 7.4, 1.4, 5.6);
                s.curveTo(1.4, 4.2, 3.0, 2.8, 4.5, 0.5);
                s.closePath();
                return s;
            }
            case "leaf": {
                Shape e = new Ellipse2D.Double(0.2, 2.6, 8.6, 3.8);
                return AffineTransform.getRotateInstance(-Math.PI / 4, 4.5, 4.5).createTransformedShape(e);
            }
            default: { // star / sparkle
                Path2D s = new Path2D.Double();
                double cx = 4.5, cy = 4.5;
                for (int i = 0; i < 8; i++) {
                    double ang = -Math.PI / 2 + i * Math.PI / 4;
                    double rad = (i % 2 == 0) ? 4.1 : 1.5;
                    double x = cx + Math.cos(ang) * rad, y = cy + Math.sin(ang) * rad;
                    if (i == 0) s.moveTo(x, y); else s.lineTo(x, y);
                }
                s.closePath();
                return s;
            }
        }
    }

    public BufferedImage icon(String style, IconPart part, int color) {
        BufferedImage out = blank(9 * S, 9 * S);
        Graphics2D g = gfx(out);
        Shape shape = iconShape(style);
        BasicStroke outline = new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        switch (part) {
            case CONTAINER, CONTAINER_BLINK -> {
                g.setColor(c(withAlpha(mixRgb(p.darkC, 0xFF202020, 0.5), 220)));
                g.fill(shape);
                g.setStroke(outline);
                g.setColor(c(part == IconPart.CONTAINER_BLINK ? 0xFFFFFFFF : darker(p.darkC, 0.65)));
                g.draw(shape);
            }
            default -> {
                Area fill = new Area(shape);
                fill.subtract(new Area(outline.createStrokedShape(shape)));
                if (part == IconPart.HALF_LEFT) fill.intersect(new Area(new Rectangle2D.Double(0, 0, 4.5, 9)));
                if (part == IconPart.HALF_RIGHT) fill.intersect(new Area(new Rectangle2D.Double(4.5, 0, 4.5, 9)));
                g.setPaint(new GradientPaint(1, 1, c(lighter(color, 0.45)), 7.5f, 8, c(darker(color, 0.15))));
                g.fill(fill);
                g.setClip(fill);
                g.setColor(c(withAlpha(0xFFFFFFFF, 150)));
                g.fill(new Ellipse2D.Double(2.0, 1.8, 2.0, 1.4));
                g.setClip(null);
            }
        }
        g.dispose();
        return out;
    }

    /** Tints a grayscale-ish vanilla sprite (armor) toward the accent hue; keeps pixel art. */
    public BufferedImage tint(BufferedImage van, int color) {
        BufferedImage out = blank(van.getWidth(), van.getHeight());
        float hue = hsb(color)[0];
        for (int y = 0; y < van.getHeight(); y++)
            for (int x = 0; x < van.getWidth(); x++) {
                int v = van.getRGB(x, y);
                if (a(v) == 0) continue;
                float[] hsb = hsb(v);
                int n = fromHsb(hue, Math.min(1f, hsb[1] + 0.35f), hsb[2]);
                out.setRGB(x, y, withAlpha(n, a(v)));
            }
        return out;
    }

    // ------------------------------------------------------------------ particles / icons / menu

    /** 64x64 sprite used by the animated drift overlay. */
    public BufferedImage particleSprite() {
        BufferedImage out = blank(64, 64);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        switch (theme.particle) {
            case "feather" -> {
                g.rotate(-Math.PI / 4, 32, 32);
                Path2D vane = new Path2D.Double();
                vane.moveTo(32, 4);
                vane.curveTo(44, 14, 44, 40, 33, 56);
                vane.lineTo(31, 56);
                vane.curveTo(18, 42, 20, 14, 32, 4);
                vane.closePath();
                g.setPaint(new GradientPaint(32, 4, c(withAlpha(0xFFFFFFFF, 250)), 32, 56, c(withAlpha(p.lightC, 170))));
                g.fill(vane);
                g.setStroke(new BasicStroke(1.2f));
                g.setColor(c(withAlpha(mixRgb(p.accent, p.lightC, 0.3), 200)));
                for (int i = 0; i < 7; i++) {
                    double y = 14 + i * 5.5;
                    g.draw(new Line2D.Double(32, y + 4, 25 + i * 0.3, y));
                    g.draw(new Line2D.Double(32, y + 4, 39 - i * 0.3, y));
                }
                g.setStroke(new BasicStroke(1.6f));
                g.setColor(c(withAlpha(p.accent, 230)));
                g.draw(new Line2D.Double(32, 8, 32, 62));
            }
            case "petal" -> {
                g.scale(64 / 9.0, 64 / 9.0);
                Shape s = iconShape("petal");
                g.setPaint(new GradientPaint(4.5f, 0, c(lighter(p.accent, 0.6)), 4.5f, 9, c(p.accent)));
                g.fill(s);
            }
            case "bubble" -> {
                g.setPaint(new RadialGradientPaint(28, 26, 30, new float[]{0f, 0.75f, 1f},
                        new Color[]{c(withAlpha(p.lightC, 30)), c(withAlpha(p.lightC, 60)), c(withAlpha(p.accent, 200))}));
                g.fill(new Ellipse2D.Double(4, 4, 56, 56));
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(c(withAlpha(p.lightC, 220)));
                g.draw(new Ellipse2D.Double(4, 4, 56, 56));
                g.setColor(c(withAlpha(0xFFFFFFFF, 230)));
                g.fill(new Ellipse2D.Double(16, 14, 12, 8));
            }
            case "leaf" -> {
                g.scale(64 / 9.0, 64 / 9.0);
                Shape s = iconShape("leaf");
                g.setPaint(new GradientPaint(0, 9, c(darker(p.accent, 0.1)), 9, 0, c(lighter(p.accent, 0.4))));
                g.fill(s);
                g.setStroke(new BasicStroke(0.3f));
                g.setColor(c(withAlpha(lighter(p.accent, 0.7), 220)));
                g.draw(new Line2D.Double(1.6, 7.4, 7.4, 1.6));
            }
            default -> {
                g.setPaint(new RadialGradientPaint(32, 32, 30, new float[]{0f, 0.25f, 1f},
                        new Color[]{c(0xFFFFFFFF), c(withAlpha(p.lightC, 180)), c(withAlpha(p.accent, 0))}));
                g.fill(new Ellipse2D.Double(2, 2, 60, 60));
                g.scale(64 / 9.0, 64 / 9.0);
                g.setColor(c(0xFFFFFFFF));
                g.fill(iconShape("star"));
            }
        }
        g.dispose();
        return out;
    }

    public BufferedImage packIcon() {
        BufferedImage art = art(UiTarget.INVENTORY, 1.0, 128, 128);
        Graphics2D g = art.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(6f));
        g.setColor(c(p.accent));
        g.draw(new RoundRectangle2D.Double(3, 3, 122, 122, 16, 16));
        g.dispose();
        return art;
    }

    public BufferedImage menuArt() {
        CropRect crop = theme.crop(UiTarget.MENU);
        BufferedImage src = image(crop.image);
        Rectangle r = crop.toPixels(src.getWidth(), src.getHeight(), UiTarget.MENU.aspect());
        int w = Math.min(1920, r.width);
        int h = (int) Math.round(w / UiTarget.MENU.aspect());
        return scaleHQ(src, r, w, h);
    }
}
