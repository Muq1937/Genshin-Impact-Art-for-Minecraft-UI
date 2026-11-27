package com.genshinui.gui;

import com.genshinui.NativeFileDialog;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** System "open files" dialog (26.3 no longer ships LWJGL tinyfd). Runs off the render thread. */
public final class FileDialogs {
    private static volatile boolean open;
    private static String lastDir = System.getProperty("user.home");

    private FileDialogs() {}

    /**
     * Shows the dialog (several files can be selected); {@code onPicked} runs on the client thread
     * with the chosen files if the user picks any.
     */
    public static void pickImages(Consumer<List<Path>> onPicked) {
        if (open) return;
        open = true;
        Thread t = new Thread(() -> {
            List<Path> paths;
            try {
                paths = NativeFileDialog.openImages("Choose character art (you can select several)", lastDir);
            } finally {
                open = false;
            }
            if (paths.isEmpty()) return;
            Path parent = paths.get(0).getParent();
            if (parent != null) lastDir = parent.toString();
            Minecraft.getInstance().execute(() -> onPicked.accept(paths));
        }, "GenshinUI-FileDialog");
        t.setDaemon(true);
        t.start();
    }

    public static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif")
                || n.endsWith(".pkg");
    }
}
