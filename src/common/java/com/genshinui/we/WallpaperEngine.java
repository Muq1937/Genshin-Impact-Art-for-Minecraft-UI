package com.genshinui.we;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads Wallpaper Engine workshop items: finds the library, lists wallpapers, and unpacks the
 * art from scene wallpapers (scene.pkg -> .tex textures; LZ4 + DXT1/3/5 / RGBA / R8 / RG88 / embedded PNG/JPG).
 * Pure Java, no Minecraft classes.
 */
public final class WallpaperEngine {
    public static final String APP_ID = "431960";

    private WallpaperEngine() {}

    // ------------------------------------------------------------------ library discovery

    public record Item(String id, Path dir, String title, String type, Path preview, long modified) {}

    /** All "steamapps/workshop/content/431960" folders on this machine. */
    public static List<Path> workshopDirs() {
        LinkedHashSet<Path> steamRoots = new LinkedHashSet<>();
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null) steamRoots.add(Path.of(pf86, "Steam"));
        String pf = System.getenv("ProgramFiles");
        if (pf != null) steamRoots.add(Path.of(pf, "Steam"));
        String regPath = registrySteamPath();
        if (regPath != null) steamRoots.add(Path.of(regPath));
        steamRoots.add(Path.of(System.getProperty("user.home"), ".steam", "steam"));
        steamRoots.add(Path.of(System.getProperty("user.home"), ".local", "share", "Steam"));
        steamRoots.add(Path.of(System.getProperty("user.home"), "Library", "Application Support", "Steam"));

        LinkedHashSet<Path> libraries = new LinkedHashSet<>(steamRoots);
        Pattern p = Pattern.compile("\"path\"\\s+\"([^\"]+)\"");
        for (Path root : steamRoots) {
            Path vdf = root.resolve("steamapps").resolve("libraryfolders.vdf");
            try {
                if (!Files.isRegularFile(vdf)) continue;
                Matcher m = p.matcher(Files.readString(vdf));
                while (m.find()) libraries.add(Path.of(m.group(1).replace("\\\\", "\\")));
            } catch (Exception ignored) {
            }
        }
        // common secondary-drive library names
        for (char d = 'C'; d <= 'H'; d++) libraries.add(Path.of(d + ":\\SteamLibrary"));

        List<Path> out = new ArrayList<>();
        for (Path lib : libraries) {
            Path ws = lib.resolve("steamapps").resolve("workshop").resolve("content").resolve(APP_ID);
            try {
                Path real = ws.toRealPath();
                if (Files.isDirectory(real) && !out.contains(real)) out.add(real);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String registrySteamPath() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        try {
            Process pr = new ProcessBuilder("reg", "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
                    .redirectErrorStream(true).start();
            String s = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            pr.waitFor();
            Matcher m = Pattern.compile("SteamPath\\s+REG_SZ\\s+(.+)").matcher(s);
            return m.find() ? m.group(1).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Lists wallpapers in the given workshop folders, newest first. */
    public static List<Item> listItems(List<Path> workshopDirs) {
        List<Item> items = new ArrayList<>();
        for (Path ws : workshopDirs) {
            try (Stream<Path> s = Files.list(ws)) {
                for (Path dir : s.filter(Files::isDirectory).toList()) {
                    Item it = readItem(dir);
                    if (it != null) items.add(it);
                }
            } catch (IOException ignored) {
            }
        }
        items.sort(Comparator.comparingLong(Item::modified).reversed());
        return items;
    }

    public static Item readItem(Path dir) {
        Path pj = dir.resolve("project.json");
        if (!Files.isRegularFile(pj)) return null; // leftover folder of an unsubscribed wallpaper (shader cache only)
        String title = dir.getFileName().toString(), type = "unknown";
        Path preview = null;
        try {
            if (Files.isRegularFile(pj)) {
                JsonObject o = JsonParser.parseString(Files.readString(pj, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("title")) title = o.get("title").getAsString();
                if (o.has("type")) type = o.get("type").getAsString().toLowerCase(Locale.ROOT);
                if (o.has("preview")) {
                    Path pv = dir.resolve(o.get("preview").getAsString());
                    if (Files.isRegularFile(pv)) preview = pv;
                }
            }
            if (preview == null) {
                for (String n : new String[]{"preview.jpg", "preview.png", "preview.gif"}) {
                    if (Files.isRegularFile(dir.resolve(n))) {
                        preview = dir.resolve(n);
                        break;
                    }
                }
            }
            long mod = Files.getLastModifiedTime(dir).toMillis();
            return new Item(dir.getFileName().toString(), dir, title, type, preview, mod);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ art extraction

    /** A decoded texture and how much of it is opaque (0..1). */
    public record Texture(String name, BufferedImage image, double coverage) {}

    /**
     * Best art from a workshop item, best first: for scenes, the big non-mask textures from scene.pkg
     * (fully opaque backgrounds first); for anything else, the largest image file in the folder or the preview.
     */
    public static List<BufferedImage> extractArt(Path itemDir, int max) throws IOException {
        Path pkg = itemDir.resolve("scene.pkg");
        List<BufferedImage> out = new ArrayList<>();
        if (Files.isRegularFile(pkg)) out.addAll(bestFromPkg(pkg, max));
        if (out.isEmpty()) {
            // web / plain image wallpapers: largest image file in the folder
            try (Stream<Path> s = Files.walk(itemDir, 3)) {
                List<Path> imgs = s.filter(Files::isRegularFile).filter(f -> {
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    return (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")) && !n.startsWith("preview");
                }).sorted(Comparator.comparingLong((Path f) -> {
                    try {
                        return Files.size(f);
                    } catch (IOException e) {
                        return 0L;
                    }
                }).reversed()).limit(max).toList();
                for (Path f : imgs) {
                    BufferedImage img = ImageIO.read(f.toFile());
                    if (img != null && Math.min(img.getWidth(), img.getHeight()) >= 400) out.add(img);
                }
            }
        }
        if (out.isEmpty()) {
            Item it = readItem(itemDir);
            if (it != null && it.preview() != null) {
                BufferedImage img = ImageIO.read(it.preview().toFile());
                if (img != null) out.add(img);
            }
        }
        return out;
    }

    public static List<BufferedImage> bestFromPkg(Path pkg, int max) throws IOException {
        List<Texture> texs = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : readPkg(pkg).entrySet()) {
            String name = e.getKey().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".tex") || name.contains("mask") || name.contains("/effects/") || name.contains("noise")) continue;
            try {
                BufferedImage img = decodeTex(e.getValue());
                if (img == null || Math.min(img.getWidth(), img.getHeight()) < 400) continue;
                texs.add(new Texture(e.getKey(), img, coverage(img)));
            } catch (Exception ignored) { // unsupported / video textures
            }
        }
        // prefer opaque backgrounds, then size
        texs.sort(Comparator.comparingDouble((Texture t) -> -(t.coverage() >= 0.97 ? 2 : t.coverage()))
                .thenComparingLong(t -> -(long) t.image().getWidth() * t.image().getHeight()));
        List<BufferedImage> out = new ArrayList<>();
        for (Texture t : texs) {
            if (out.size() >= max) break;
            if (t.coverage() < 0.2) continue; // particles, sparkles, small decals
            out.add(t.coverage() < 0.97 ? flatten(t.image()) : t.image());
        }
        return out;
    }

    private static double coverage(BufferedImage img) {
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 128), n = 0, opaque = 0;
        for (int y = 0; y < img.getHeight(); y += step)
            for (int x = 0; x < img.getWidth(); x += step) {
                n++;
                if ((img.getRGB(x, y) >>> 24) > 200) opaque++;
            }
        return n == 0 ? 0 : (double) opaque / n;
    }

    private static BufferedImage flatten(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(0x15142A));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ PKG

    public static LinkedHashMap<String, byte[]> readPkg(Path pkg) throws IOException {
        byte[] data = Files.readAllBytes(pkg);
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        String magic = str(b);
        if (!magic.startsWith("PKGV")) throw new IOException("Not a Wallpaper Engine package");
        int count = b.getInt();
        List<Object[]> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new Object[]{str(b), b.getInt(), b.getInt()});
        int base = b.position();
        LinkedHashMap<String, byte[]> out = new LinkedHashMap<>();
        for (Object[] e : entries) {
            int off = (int) e[1], len = (int) e[2];
            if (base + off + len > data.length || off < 0 || len < 0) continue;
            out.put((String) e[0], Arrays.copyOfRange(data, base + off, base + off + len));
        }
        return out;
    }

    private static String str(ByteBuffer b) {
        int len = b.getInt();
        byte[] s = new byte[len];
        b.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ TEX

    private static String cstr(ByteBuffer b) {
        StringBuilder sb = new StringBuilder();
        byte c;
        while ((c = b.get()) != 0) sb.append((char) c);
        return sb.toString();
    }

    /** Decodes the first image (largest mip) of a .tex file. */
    public static BufferedImage decodeTex(byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        String m1 = cstr(b), m2 = cstr(b);
        if (!m1.startsWith("TEXV") || !m2.startsWith("TEXI")) throw new IOException("not a TEX");
        int format = b.getInt();
        b.getInt(); // flags
        b.getInt(); // texture width (padded)
        b.getInt(); // texture height
        int imgW = b.getInt(), imgH = b.getInt();
        b.getInt(); // unknown
        String cont = cstr(b);
        int imageCount = b.getInt();
        int ver = Integer.parseInt(cont.substring(4));
        int freeImage = -1;
        if (ver >= 3) freeImage = b.getInt();
        if (ver >= 4 && b.getInt() != 0) throw new IOException("video texture");
        if (imageCount < 1) return null;
        int mipCount = b.getInt();
        if (mipCount < 1) return null;
        int w = b.getInt(), h = b.getInt();
        boolean lz4 = false;
        int rawSize = 0;
        if (ver >= 2) {
            lz4 = b.getInt() != 0;
            rawSize = b.getInt();
        }
        int len = b.getInt();
        byte[] bytes = new byte[len];
        b.get(bytes);
        if (lz4) bytes = lz4(bytes, rawSize);

        BufferedImage img;
        if (freeImage != -1) {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new IOException("unsupported embedded image");
        } else {
            img = switch (format) {
                case 0 -> rgba(bytes, w, h);
                case 4 -> dxt(bytes, w, h, 5);
                case 6 -> dxt(bytes, w, h, 3);
                case 7 -> dxt(bytes, w, h, 1);
                case 8 -> rg88(bytes, w, h);
                case 9 -> r8(bytes, w, h);
                default -> throw new IOException("unsupported format " + format);
            };
        }
        if (imgW > 0 && imgH > 0 && imgW <= img.getWidth() && imgH <= img.getHeight()
                && (imgW != img.getWidth() || imgH != img.getHeight())) {
            img = img.getSubimage(0, 0, imgW, imgH);
        }
        return img;
    }

    static byte[] lz4(byte[] src, int outSize) throws IOException {
        byte[] dst = new byte[outSize];
        int si = 0, di = 0;
        while (si < src.length) {
            int token = src[si++] & 0xFF;
            int lit = token >>> 4;
            if (lit == 15) {
                int x;
                do {
                    x = src[si++] & 0xFF;
                    lit += x;
                } while (x == 255);
            }
            System.arraycopy(src, si, dst, di, lit);
            si += lit;
            di += lit;
            if (si >= src.length) break;
            int offset = (src[si] & 0xFF) | ((src[si + 1] & 0xFF) << 8);
            si += 2;
            int ml = token & 15;
            if (ml == 15) {
                int x;
                do {
                    x = src[si++] & 0xFF;
                    ml += x;
                } while (x == 255);
            }
            ml += 4;
            int from = di - offset;
            if (from < 0) throw new IOException("corrupt lz4");
            for (int k = 0; k < ml; k++) dst[di++] = dst[from + k];
        }
        if (di != outSize) throw new IOException("lz4 size mismatch");
        return dst;
    }

    private static BufferedImage rgba(byte[] d, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length && i * 4 + 3 < d.length; i++)
            px[i] = ((d[i * 4 + 3] & 0xFF) << 24) | ((d[i * 4] & 0xFF) << 16) | ((d[i * 4 + 1] & 0xFF) << 8) | (d[i * 4 + 2] & 0xFF);
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    private static BufferedImage rg88(byte[] d, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length && i * 2 + 1 < d.length; i++) {
            int l = d[i * 2] & 0xFF, a = d[i * 2 + 1] & 0xFF;
            px[i] = (a << 24) | (l << 16) | (l << 8) | l;
        }
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    private static BufferedImage r8(byte[] d, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length && i < d.length; i++) {
            int l = d[i] & 0xFF;
            px[i] = 0xFF000000 | (l << 16) | (l << 8) | l;
        }
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    /** BC1 (DXT1), BC2 (DXT3) or BC3 (DXT5). */
    private static BufferedImage dxt(byte[] d, int w, int h, int kind) {
        int[] px = new int[w * h];
        int bw = (w + 3) / 4, bh = (h + 3) / 4;
        int blockSize = kind == 1 ? 8 : 16;
        int[] colors = new int[4];
        int[] alphas = new int[16];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int o = (by * bw + bx) * blockSize;
                if (o + blockSize > d.length) continue;
                Arrays.fill(alphas, 255);
                if (kind == 3) {
                    for (int i = 0; i < 16; i++) {
                        int v = (d[o + i / 2] >> ((i & 1) * 4)) & 0xF;
                        alphas[i] = v * 17;
                    }
                    o += 8;
                } else if (kind == 5) {
                    int a0 = d[o] & 0xFF, a1 = d[o + 1] & 0xFF;
                    int[] pal = new int[8];
                    pal[0] = a0;
                    pal[1] = a1;
                    if (a0 > a1) for (int i = 1; i < 7; i++) pal[i + 1] = ((7 - i) * a0 + i * a1) / 7;
                    else {
                        for (int i = 1; i < 5; i++) pal[i + 1] = ((5 - i) * a0 + i * a1) / 5;
                        pal[6] = 0;
                        pal[7] = 255;
                    }
                    long bits = 0;
                    for (int i = 0; i < 6; i++) bits |= (long) (d[o + 2 + i] & 0xFF) << (8 * i);
                    for (int i = 0; i < 16; i++) alphas[i] = pal[(int) ((bits >> (3 * i)) & 7)];
                    o += 8;
                }
                int c0 = (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
                int c1 = (d[o + 2] & 0xFF) | ((d[o + 3] & 0xFF) << 8);
                colors[0] = rgb565(c0);
                colors[1] = rgb565(c1);
                boolean four = kind != 1 || c0 > c1;
                if (four) {
                    colors[2] = lerp(colors[0], colors[1], 2, 1);
                    colors[3] = lerp(colors[0], colors[1], 1, 2);
                } else {
                    colors[2] = lerp(colors[0], colors[1], 1, 1);
                    colors[3] = 0; // transparent black
                }
                int idx = (d[o + 4] & 0xFF) | ((d[o + 5] & 0xFF) << 8) | ((d[o + 6] & 0xFF) << 16) | ((d[o + 7] & 0xFF) << 24);
                for (int i = 0; i < 16; i++) {
                    int x = bx * 4 + (i & 3), y = by * 4 + (i >> 2);
                    if (x >= w || y >= h) continue;
                    int ci = (idx >>> (2 * i)) & 3;
                    int c = colors[ci];
                    int a = (!four && ci == 3) ? 0 : alphas[i];
                    px[y * w + x] = (a << 24) | (c & 0xFFFFFF);
                }
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        return img;
    }

    private static int rgb565(int c) {
        int r = (c >> 11) & 31, g = (c >> 5) & 63, b = c & 31;
        return ((r * 255 / 31) << 16) | ((g * 255 / 63) << 8) | (b * 255 / 31);
    }

    private static int lerp(int a, int b, int wa, int wb) {
        int s = wa + wb;
        int r = (((a >> 16) & 255) * wa + ((b >> 16) & 255) * wb) / s;
        int g = (((a >> 8) & 255) * wa + ((b >> 8) & 255) * wb) / s;
        int bl = ((a & 255) * wa + (b & 255) * wb) / s;
        return (r << 16) | (g << 8) | bl;
    }
}
