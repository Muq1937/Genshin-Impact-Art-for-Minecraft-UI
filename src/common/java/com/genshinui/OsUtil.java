package com.genshinui;

import java.nio.file.Path;
import java.util.Locale;

/** Opens a folder in the system file manager (Minecraft 26.x no longer exposes a helper for this). */
public final class OsUtil {
    private OsUtil() {}

    public static void open(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String target = path.toAbsolutePath().toString();
        String[] cmd = os.contains("win") ? new String[]{"explorer", target}
                : os.contains("mac") ? new String[]{"open", target}
                : new String[]{"xdg-open", target};
        try {
            new ProcessBuilder(cmd).start();
        } catch (Exception e) {
            System.err.println("[GenshinUI] could not open " + target + ": " + e);
        }
    }
}
