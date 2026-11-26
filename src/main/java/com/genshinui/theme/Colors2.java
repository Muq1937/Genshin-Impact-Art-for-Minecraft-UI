package com.genshinui.theme;

import java.awt.Color;

/** ARGB color helpers (all ints are 0xAARRGGBB). */
public final class Colors2 {
    private Colors2() {}

    public static int a(int c) { return (c >>> 24) & 0xFF; }
    public static int r(int c) { return (c >> 16) & 0xFF; }
    public static int g(int c) { return (c >> 8) & 0xFF; }
    public static int b(int c) { return c & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    public static int withAlpha(int c, int alpha) {
        return (clamp(alpha) << 24) | (c & 0xFFFFFF);
    }

    /** Linear mix of RGB; result alpha = alpha of {@code a} mixed toward alpha of {@code b}. */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        return argb(
                (int) Math.round(a(a) + (a(b) - a(a)) * t),
                (int) Math.round(r(a) + (r(b) - r(a)) * t),
                (int) Math.round(g(a) + (g(b) - g(a)) * t),
                (int) Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** Mix RGB only, keep alpha of {@code a}. */
    public static int mixRgb(int a, int b, double t) {
        return withAlpha(mix(a | 0xFF000000, b | 0xFF000000, t), a(a));
    }

    /** Source-over composite of {@code top} onto {@code bottom}. */
    public static int over(int bottom, int top) {
        int ta = a(top);
        if (ta == 255) return top;
        if (ta == 0) return bottom;
        int ba = a(bottom);
        double fa = ta / 255.0, fb = ba / 255.0 * (1 - fa);
        double oa = fa + fb;
        if (oa <= 0) return 0;
        return argb((int) Math.round(oa * 255),
                (int) Math.round((r(top) * fa + r(bottom) * fb) / oa),
                (int) Math.round((g(top) * fa + g(bottom) * fb) / oa),
                (int) Math.round((b(top) * fa + b(bottom) * fb) / oa));
    }

    public static double luminance(int c) {
        return (0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)) / 255.0;
    }

    public static int parse(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    public static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    public static float[] hsb(int c) {
        return Color.RGBtoHSB(r(c), g(c), b(c), null);
    }

    public static int fromHsb(float h, float s, float v) {
        return 0xFF000000 | (Color.HSBtoRGB(h, Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, v))) & 0xFFFFFF);
    }

    public static int darker(int c, double f) {
        return mixRgb(c, 0xFF000000, f);
    }

    public static int lighter(int c, double f) {
        return mixRgb(c, 0xFFFFFFFF, f);
    }
}
