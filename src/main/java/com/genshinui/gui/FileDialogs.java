package com.genshinui.gui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Native "open file" dialog (via LWJGL tinyfd, bundled with Minecraft). Runs off the render thread. */
public final class FileDialogs {
    private static volatile boolean open;
    private static String lastDir = System.getProperty("user.home") + java.io.File.separator;

    private FileDialogs() {}

    /**
     * Shows the dialog (several files can be selected); {@code onPicked} runs on the client thread
     * with the chosen files if the user picks any.
     */
    public static void pickImages(Consumer<List<Path>> onPicked) {
        if (open) return;
        open = true;
        Thread t = new Thread(() -> {
            String res;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                String[] patterns = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.pkg"};
                PointerBuffer filters = stack.mallocPointer(patterns.length);
                for (String p : patterns) filters.put(stack.UTF8(p));
                filters.flip();
                res = TinyFileDialogs.tinyfd_openFileDialog("Choose character art (you can select several)", lastDir, filters,
                        "Images or Wallpaper Engine scene.pkg", true);
            } catch (Throwable e) {
                res = null;
            } finally {
                open = false;
            }
            if (res == null || res.isBlank()) return;
            List<Path> paths = new ArrayList<>();
            for (String s : res.split("\\|")) if (!s.isBlank()) paths.add(Path.of(s));
            if (paths.isEmpty()) return;
            Path parent = paths.get(0).getParent();
            if (parent != null) lastDir = parent + java.io.File.separator;
            MinecraftClient.getInstance().execute(() -> onPicked.accept(paths));
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
