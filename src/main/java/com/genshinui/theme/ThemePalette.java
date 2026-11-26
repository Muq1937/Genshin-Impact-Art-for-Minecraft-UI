package com.genshinui.theme;

import static com.genshinui.theme.Colors2.*;

/** All concrete colors derived from a theme's 3 base colors + mode. */
public final class ThemePalette {
    public final boolean dark;
    public final int accent, darkC, lightC;
    public final int heart, text;
    public final int panelTint, slotTint, frame, bevelLight, bevelDark, window;
    public final float panelStrength, slotStrength;

    public ThemePalette(Theme t) {
        dark = t.isDark();
        accent = parse(t.accent, 0xFFB7A6FF);
        darkC = parse(t.dark, 0xFF16163A);
        lightC = parse(t.light, 0xFFEEE9FF);
        heart = parse(t.heart, mixRgb(accent, 0xFFFF4060, 0.15));
        panelStrength = t.panelStrength;
        slotStrength = t.slotStrength;
        if (dark) {
            panelTint = darkC;
            slotTint = darker(darkC, 0.35);
            frame = mixRgb(accent, lightC, 0.25);
            bevelLight = mixRgb(accent, lightC, 0.45);
            bevelDark = darker(darkC, 0.45);
            window = darker(darkC, 0.3);
            text = parse(t.text, lightC);
        } else {
            panelTint = lightC;
            slotTint = mixRgb(lightC, accent, 0.30);
            frame = mixRgb(accent, darkC, 0.35);
            bevelLight = 0xFFFFFFFF;
            bevelDark = mixRgb(accent, darkC, 0.55);
            window = mixRgb(darkC, accent, 0.25);
            text = parse(t.text, darker(darkC, 0.2));
        }
    }
}
