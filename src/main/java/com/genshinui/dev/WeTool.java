package com.genshinui.dev;

import com.genshinui.we.WallpaperEngine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dev-only: {@code gradlew weTest -Pids=123,456} extracts art from workshop items into build/we-test. */
public final class WeTool {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        List<Path> ws = WallpaperEngine.workshopDirs();
        System.out.println("workshop dirs: " + ws);
        System.out.println("items: " + WallpaperEngine.listItems(ws).size());
        for (int i = 1; i < args.length; i++) {
            for (Path w : ws) {
                Path dir = w.resolve(args[i]);
                if (!Files.isDirectory(dir)) continue;
                long t0 = System.currentTimeMillis();
                List<BufferedImage> imgs = WallpaperEngine.extractArt(dir, 3);
                System.out.println(args[i] + ": " + imgs.size() + " image(s) in " + (System.currentTimeMillis() - t0) + " ms");
                for (int k = 0; k < imgs.size(); k++) {
                    BufferedImage im = imgs.get(k);
                    System.out.println("   " + im.getWidth() + "x" + im.getHeight());
                    ImageIO.write(im, "png", out.resolve(args[i] + "_" + k + ".png").toFile());
                }
            }
        }
    }
}
