package com.genshinui.theme;

import java.awt.image.BufferedImage;

/** Picks accent / dark / light colors and a light-or-dark mode from an image. */
public final class PaletteExtractor {
    private PaletteExtractor() {}

    public record Result(String accent, String dark, String light, String mode) {}

    public static Result extract(BufferedImage img) {
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 160);
        double[] hueWeight = new double[36];
        double lumSum = 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int c = img.getRGB(x, y);
                if (Colors2.a(c) < 128) continue;
                float[] hsb = Colors2.hsb(c);
                lumSum += Colors2.luminance(c);
                n++;
                // weight vivid, mid-bright pixels; ignore near-gray
                double w = hsb[1] * hsb[1] * (1 - Math.abs(hsb[2] - 0.7));
                if (hsb[1] < 0.15 || hsb[2] < 0.15) continue;
                hueWeight[(int) (hsb[0] * 36) % 36] += w;
            }
        }
        // smooth histogram (circular) and pick peak
        int best = 0;
        double bestW = -1;
        for (int i = 0; i < 36; i++) {
            double w = hueWeight[i] * 2 + hueWeight[(i + 35) % 36] + hueWeight[(i + 1) % 36];
            if (w > bestW) { bestW = w; best = i; }
        }
        float hue = (best + 0.5f) / 36f;
        if (bestW <= 0) hue = 0.72f; // no color at all: default lavender
        double avgLum = n == 0 ? 0.5 : lumSum / n;
        String mode = avgLum > 0.5 ? "light" : "dark";
        int accent = Colors2.fromHsb(hue, 0.45f, 0.95f);
        int dark = Colors2.fromHsb(hue, 0.55f, 0.20f);
        int light = Colors2.fromHsb(hue, 0.08f, 0.98f);
        return new Result(Colors2.hex(accent), Colors2.hex(dark), Colors2.hex(light), mode);
    }
}
