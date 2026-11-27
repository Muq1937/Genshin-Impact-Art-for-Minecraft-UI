package com.genshinui.gui;

import com.genshinui.ThemeManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Uploads BufferedImages as GUI textures (render thread only). */
public final class DynTex {
    private DynTex() {}

    public record Tex(Identifier id, int width, int height) {}

    public static Tex upload(String key, BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage ni = new NativeImage(w, h, true);
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) ni.setPixel(x, y, px[y * w + x]); // ARGB
        Identifier id = ThemeManager.id("dyn/" + key);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "genshinui:" + key, ni));
        return new Tex(id, w, h);
    }

    public static void release(Tex t) {
        if (t != null) Minecraft.getInstance().getTextureManager().release(t.id());
    }

    /** Downscales so the longest side is at most {@code max}. */
    public static BufferedImage fit(BufferedImage src, int max) {
        int m = Math.max(src.getWidth(), src.getHeight());
        if (m <= max) return src;
        double f = (double) max / m;
        int w = Math.max(1, (int) Math.round(src.getWidth() * f)), h = Math.max(1, (int) Math.round(src.getHeight() * f));
        BufferedImage cur = src;
        int cw = src.getWidth(), ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = draw(cur, cw, ch);
        }
        return draw(cur, w, h);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
