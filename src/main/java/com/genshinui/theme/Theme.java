package com.genshinui.theme;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A UI theme, stored as theme.json inside its own folder next to its images. */
public class Theme {
    public static final String[] PARTICLES = {"feather", "petal", "bubble", "leaf", "spark", "none"};
    public static final String[] ICONS = {"moon", "petal", "drop", "leaf", "star"};

    public String name = "New Theme";
    public String character = "";
    /** "dark" = dark veil + light text, "light" = light veil + dark text. */
    public String mode = "dark";
    public String accent = "#B7A6FF";
    public String dark = "#16163A";
    public String light = "#EEE9FF";
    /** Optional overrides; null = derived from the palette. */
    public String heart = null;
    public String text = null;
    /** How strongly the panel art is veiled with the tint color (0 = raw art, 1 = flat color). */
    public float panelStrength = 0.35f;
    /** How strongly slots are tinted. */
    public float slotStrength = 0.5f;
    public String particle = "feather";
    /** Shape used for hunger (and heart if "heart") icons. */
    public String icons = "moon";
    /** Image file names relative to the theme folder. */
    public List<String> images = new ArrayList<>();
    /** Crop per {@link UiTarget#id}. Missing entries fall back to a centered fill crop of image 0. */
    public Map<String, CropRect> crops = new LinkedHashMap<>();

    // ---- runtime only
    public transient String id;
    public transient Path dir;
    public transient boolean sample;

    /**
     * Recipe book targets (ids) drawn as one continuous image with their screen. The host screen's crop then
     * covers both panels (book on the left, screen on the right).
     */
    public List<String> connectedBooks = new ArrayList<>();

    /** Book (147) + vanilla gap (2) + screen (176) by 166. */
    public static final int CONNECTED_W = 147 + 2 + 176, CONNECTED_H = 166;
    public static final double CONNECTED_ASPECT = (double) CONNECTED_W / CONNECTED_H;

    public boolean isConnected(UiTarget book) {
        return book.isRecipeBook() && connectedBooks != null && connectedBooks.contains(book.id);
    }

    /** The connected recipe book of a host screen target, or null. */
    public UiTarget connectedBookOf(UiTarget host) {
        for (UiTarget t : UiTarget.values()) if (t.recipeHost() == host && isConnected(t)) return t;
        return null;
    }

    public void setConnected(UiTarget book, boolean on) {
        if (connectedBooks == null) connectedBooks = new ArrayList<>();
        connectedBooks.remove(book.id);
        if (on) connectedBooks.add(book.id);
    }

    /**
     * Crop aspect actually used for a target: a connected recipe book is edited as one wide image spanning the
     * book and its screen (used while the book is open). The screen's own crop is used while the book is closed.
     */
    public double aspect(UiTarget t) {
        return isConnected(t) ? CONNECTED_ASPECT : t.aspect();
    }

    public boolean isDark() {
        return !"light".equalsIgnoreCase(mode);
    }

    public CropRect crop(UiTarget t) {
        if (isConnected(t)) { // the shared book+screen image
            String key = t.id + "_linked";
            CropRect c = crops.get(key);
            if (c == null) {
                CropRect host = crops.get(t.recipeHost().id);
                c = host != null ? host.copy() : new CropRect();
                crops.put(key, c);
            }
            if (c.image < 0 || c.image >= Math.max(1, images.size())) c.image = 0;
            return c;
        }
        CropRect c = crops.get(t.id);
        if (c == null) {
            UiTarget fb = t.cropFallback();
            c = fb != null && crops.containsKey(fb.id) ? crops.get(fb.id).copy() : new CropRect();
            crops.put(t.id, c);
        }
        if (c.image < 0 || c.image >= Math.max(1, images.size())) c.image = 0;
        return c;
    }

    public ThemePalette palette() {
        return new ThemePalette(this);
    }
}
