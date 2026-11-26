package com.genshinui;

import com.genshinui.theme.ThemeStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/genshinui/settings.json */
public class ModSettings {
    /** Theme folder id, or null when theming is off. */
    public String activeTheme = null;
    /** Pixels per GUI pixel of generated textures (2 = lighter on VRAM, 4 = sharp at GUI scale 4). */
    public int textureScale = 4;
    public boolean particles = true;
    public boolean menuArt = true;
    public boolean recolorLabels = true;
    /** Ids of sample themes already installed (so deleted samples stay deleted). */
    public java.util.List<String> installedSamples = new java.util.ArrayList<>();

    private transient Path file;

    public static ModSettings load(Path file) {
        ModSettings s = null;
        try {
            if (Files.exists(file)) s = ThemeStore.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModSettings.class);
        } catch (Exception e) {
            System.err.println("[GenshinUI] settings unreadable, using defaults: " + e);
        }
        if (s == null) s = new ModSettings();
        if (s.installedSamples == null) s.installedSamples = new java.util.ArrayList<>();
        s.textureScale = Math.max(1, Math.min(8, s.textureScale));
        s.file = file;
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, ThemeStore.GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[GenshinUI] could not save settings: " + e);
        }
    }
}
