package com.genshinui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Themes on disk: one folder per theme containing theme.json and its images. */
public final class ThemeStore {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int MAX_IMAGE_SIZE = 4096;

    private final Path root;
    private final Map<String, CachedImage> imageCache = new ConcurrentHashMap<>();

    private record CachedImage(long mtime, BufferedImage img) {}

    public ThemeStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public List<Theme> list() {
        List<Theme> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.list(root)) {
            for (Path dir : s.filter(Files::isDirectory).toList()) {
                Theme t = load(dir);
                if (t != null) out.add(t);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        out.sort(Comparator.comparing((Theme t) -> t.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    public Theme get(String id) {
        if (id == null) return null;
        Path dir = root.resolve(id);
        return Files.isDirectory(dir) ? load(dir) : null;
    }

    public static Theme load(Path dir) {
        Path json = dir.resolve("theme.json");
        if (!Files.isRegularFile(json)) return null;
        try (Reader r = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            Theme t = GSON.fromJson(r, Theme.class);
            if (t == null) return null;
            if (t.images == null) t.images = new ArrayList<>();
            if (t.crops == null) t.crops = new LinkedHashMap<>();
            if (t.connectedBooks == null) t.connectedBooks = new ArrayList<>();
            t.id = dir.getFileName().toString();
            t.dir = dir;
            t.sample = Files.exists(dir.resolve(".sample"));
            return t;
        } catch (Exception e) {
            System.err.println("[GenshinUI] Bad theme " + dir + ": " + e);
            return null;
        }
    }

    public void save(Theme t) throws IOException {
        Files.createDirectories(t.dir);
        Files.writeString(t.dir.resolve("theme.json"), GSON.toJson(t), StandardCharsets.UTF_8);
    }

    public Theme create(String name) throws IOException {
        Files.createDirectories(root);
        String base = slug(name);
        String id = base;
        for (int i = 2; Files.exists(root.resolve(id)); i++) id = base + "_" + i;
        Theme t = new Theme();
        t.name = name;
        t.id = id;
        t.dir = root.resolve(id);
        save(t);
        return t;
    }

    public Theme duplicate(Theme src) throws IOException {
        Theme t = create(src.name + " copy");
        Path dir = t.dir;
        for (String img : src.images) Files.copy(src.dir.resolve(img), dir.resolve(img));
        Theme copy = GSON.fromJson(GSON.toJson(src), Theme.class);
        copy.name = src.name + " (copy)";
        copy.id = t.id;
        copy.dir = dir;
        save(copy);
        return copy;
    }

    public void delete(Theme t) throws IOException {
        if (t.dir == null || !t.dir.startsWith(root)) return;
        try (Stream<Path> s = Files.walk(t.dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    /**
     * Copies an image into the theme folder (re-encoding to PNG when needed and capping its size),
     * appends it to {@link Theme#images} and returns its index.
     */
    public int importImage(Theme t, Path file) throws IOException {
        BufferedImage img = readImage(file);
        if (img == null) throw new IOException("Not a supported image (PNG, JPG, BMP or GIF): " + file.getFileName());
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean keepBytes = (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                && Math.max(img.getWidth(), img.getHeight()) <= MAX_IMAGE_SIZE;
        String ext = keepBytes ? lower.substring(lower.lastIndexOf('.')) : ".png";
        String name;
        int n = t.images.size() + 1;
        do {
            name = "image" + n++ + ext;
        } while (Files.exists(t.dir.resolve(name)));
        if (keepBytes) {
            Files.copy(file, t.dir.resolve(name));
        } else {
            ImageIO.write(capSize(img), "png", t.dir.resolve(name).toFile());
        }
        t.images.add(name);
        return t.images.size() - 1;
    }

    /** Saves an in-memory image (e.g. unpacked from Wallpaper Engine) into the theme; returns its index. */
    public int importImage(Theme t, BufferedImage img) throws IOException {
        t.images.add(saveImage(t.dir, img));
        return t.images.size() - 1;
    }

    /** Writes an image into a theme folder under a fresh name (JPG if opaque, else PNG). Thread-safe. */
    public static synchronized String saveImage(Path dir, BufferedImage img) throws IOException {
        img = capSize(img);
        boolean opaque = !img.getColorModel().hasAlpha();
        if (!opaque) { // check actual pixels
            opaque = true;
            int step = Math.max(1, img.getWidth() / 128);
            outer:
            for (int y = 0; y < img.getHeight(); y += step)
                for (int x = 0; x < img.getWidth(); x += step)
                    if ((img.getRGB(x, y) >>> 24) < 250) { opaque = false; break outer; }
        }
        String ext = opaque ? ".jpg" : ".png";
        String name;
        int n = 1;
        do {
            name = "image" + n++;
        } while (Files.exists(dir.resolve(name + ".jpg")) || Files.exists(dir.resolve(name + ".png")));
        name += ext;
        Files.createDirectories(dir);
        if (opaque) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            javax.imageio.ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.92f);
            Files.deleteIfExists(dir.resolve(name));
            try (var out = ImageIO.createImageOutputStream(dir.resolve(name).toFile())) {
                w.setOutput(out);
                w.write(null, new javax.imageio.IIOImage(rgb, null, null), p);
            } finally {
                w.dispose();
            }
        } else {
            ImageIO.write(img, "png", dir.resolve(name).toFile());
        }
        return name;
    }

    /** Reads art from a file: normal images, or a Wallpaper Engine scene.pkg (best layers first). */
    public static java.util.List<BufferedImage> readArt(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".pkg")) return com.genshinui.we.WallpaperEngine.bestFromPkg(file, 4);
        BufferedImage img = readImage(file);
        if (img == null) throw new IOException("Not a supported image (PNG, JPG, BMP, GIF or Wallpaper Engine .pkg): " + file.getFileName());
        return java.util.List.of(img);
    }

    public static BufferedImage readImage(Path file) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) return null;
        if (img.getType() != BufferedImage.TYPE_INT_ARGB && img.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage conv = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = conv.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = conv;
        }
        return img;
    }

    private static BufferedImage capSize(BufferedImage img) {
        int m = Math.max(img.getWidth(), img.getHeight());
        if (m <= MAX_IMAGE_SIZE) return img;
        double f = (double) MAX_IMAGE_SIZE / m;
        int w = (int) Math.round(img.getWidth() * f), h = (int) Math.round(img.getHeight() * f);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Loads (and caches) image #index of a theme. Returns null when missing. */
    public BufferedImage image(Theme t, int index) {
        if (index < 0 || index >= t.images.size()) return null;
        Path f = t.dir.resolve(t.images.get(index));
        try {
            long mtime = Files.getLastModifiedTime(f).toMillis();
            String key = f.toAbsolutePath().toString();
            CachedImage c = imageCache.get(key);
            if (c != null && c.mtime == mtime) return c.img;
            BufferedImage img = readImage(f);
            if (img != null) imageCache.put(key, new CachedImage(mtime, img));
            return img;
        } catch (IOException e) {
            return null;
        }
    }

    public void clearCache() {
        imageCache.clear();
    }

    /** Stable fingerprint of a theme's settings + images, used to know when a pack must be rebuilt. */
    public static String fingerprint(Theme t, int scale, int genVersion) {
        StringBuilder sb = new StringBuilder(GSON.toJson(t)).append('|').append(scale).append('|').append(genVersion);
        for (String img : t.images) {
            try {
                Path f = t.dir.resolve(img);
                sb.append('|').append(img).append(Files.size(f)).append(Files.getLastModifiedTime(f).toMillis());
            } catch (IOException ignored) {
            }
        }
        return t.id + ":" + Integer.toHexString(sb.toString().hashCode());
    }

    public static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "theme" : s;
    }
}
