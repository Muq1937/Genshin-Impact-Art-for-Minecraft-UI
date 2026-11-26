package com.genshinui;

import com.genshinui.gen.PackBuilder;
import com.genshinui.gen.VanillaSource;
import com.genshinui.theme.Theme;
import com.genshinui.theme.ThemePalette;
import com.genshinui.theme.ThemeStore;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/** Owns settings, the theme store, the active theme, and building/enabling the generated resource pack. */
public final class ThemeManager {
    public static final String MOD_ID = "genshinui";
    public static final String PACK_DIR_NAME = "GenshinUI-Theme";
    public static final String PACK_ID = "file/" + PACK_DIR_NAME;
    public static final Identifier MENU_ART = Identifier.of(MOD_ID, "textures/gui/menu_art.png");
    public static final Identifier PARTICLE = Identifier.of(MOD_ID, "textures/gui/particle.png");
    public static final Identifier RECIPE_BOOK_CRAFTING = Identifier.of(MOD_ID, "textures/gui/recipe_book_crafting.png");
    public static final Identifier RECIPE_BOOK_FURNACE = Identifier.of(MOD_ID, "textures/gui/recipe_book_furnace.png");
    /** Per-screen recipe book textures are loaded. */
    public static volatile boolean recipeBooksLoaded;
    /** Vanilla screen texture -> its "recipe book open" variant (only for connected recipe books). */
    public static volatile java.util.Map<Identifier, Identifier> bookOpenTextures = java.util.Map.of();
    private static final Identifier[] CHESTS = new Identifier[7];
    /** Seamless per-row-count chest textures are loaded (packs built by older versions don't have them). */
    public static volatile boolean chestsLoaded;

    public static Identifier chestTexture(int rows) {
        rows = Math.max(1, Math.min(6, rows));
        if (CHESTS[rows] == null) CHESTS[rows] = Identifier.of(MOD_ID, "textures/gui/chest_" + rows + ".png");
        return CHESTS[rows];
    }

    public static ModSettings settings;
    public static ThemeStore store;
    private static Theme active;
    private static ThemePalette palette;
    /** True when the generated pack's textures are actually loaded (set by the reload listener). */
    public static volatile boolean assetsLoaded;
    public static volatile boolean building;
    /** Set while a container screen draws its labels (see HandledScreenMixin / DrawContextMixin). */
    public static boolean labelsActive;
    public static volatile String status = "";

    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GenshinUI-Worker");
        t.setDaemon(true);
        return t;
    });

    private ThemeManager() {}

    public static void init() {
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        settings = ModSettings.load(cfg.resolve("settings.json"));
        store = new ThemeStore(cfg.resolve("themes"));
        boolean firstRun = settings.installedSamples.isEmpty();
        installSamples();
        if (firstRun && settings.activeTheme == null && store.get("columbina") != null) {
            settings.activeTheme = "columbina";
            settings.save();
        }
        setActive(store.get(settings.activeTheme));
    }

    public static Theme active() {
        return active;
    }

    public static ThemePalette palette() {
        return palette;
    }

    /** Theme is selected and its generated textures are live. */
    public static boolean live() {
        return active != null && assetsLoaded;
    }

    private static void setActive(Theme t) {
        active = t;
        palette = t == null ? null : t.palette();
    }

    // ------------------------------------------------------------------ samples

    private static void installSamples() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (mod.isEmpty()) return;
        Optional<Path> samples = mod.get().findPath("genshinui_samples");
        if (samples.isEmpty()) return;
        try (Stream<Path> dirs = Files.list(samples.get())) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString().replace("/", "");
                if (settings.installedSamples.contains(id)) continue;
                Path target = store.root().resolve(id);
                if (!Files.exists(target)) {
                    Files.createDirectories(target);
                    try (Stream<Path> files = Files.list(dir)) {
                        for (Path f : files.toList()) {
                            Files.copy(f, target.resolve(f.getFileName().toString().replace("/", "")));
                        }
                    }
                    Files.writeString(target.resolve(".sample"), "bundled sample theme");
                }
                settings.installedSamples.add(id);
            }
            settings.save();
        } catch (Exception e) {
            GenshinUIClient.LOGGER.error("Failed to install sample themes", e);
        }
    }

    // ------------------------------------------------------------------ vanilla assets

    public static final VanillaSource VANILLA = new VanillaSource() {
        private InputSupplier<InputStream> open(String path) {
            return MinecraftClient.getInstance().getDefaultResourcePack()
                    .open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla(path));
        }

        @Override
        public BufferedImage image(String path) {
            InputSupplier<InputStream> s = open(path);
            if (s == null) return null;
            try (InputStream in = s.get()) {
                return ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public String text(String path) {
            InputSupplier<InputStream> s = open(path);
            if (s == null) return null;
            try (InputStream in = s.get()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
        }
    };

    // ------------------------------------------------------------------ apply / disable

    public static Path packDir() {
        return MinecraftClient.getInstance().getResourcePackDir().resolve(PACK_DIR_NAME);
    }

    private static String stamp(Theme t) {
        return ThemeStore.fingerprint(t, settings.textureScale, PackBuilder.GEN_VERSION);
    }

    /** Rebuilds the pack if the active theme changed since it was generated (called once the title screen shows). */
    public static void ensureUpToDate() {
        if (active == null) return;
        String current = "";
        try {
            Path f = packDir().resolve("genshinui_stamp.txt");
            if (Files.exists(f)) current = Files.readString(f);
        } catch (Exception ignored) {
        }
        boolean enabled = MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().contains(PACK_ID);
        if (!current.equals(stamp(active)) || !enabled) apply(active);
    }

    /** Generates the pack for {@code theme} off-thread, then enables it and reloads resources. */
    public static void apply(Theme theme) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (building) return;
        building = true;
        status = "Building \"" + theme.name + "\"...";
        settings.activeTheme = theme.id;
        settings.save();
        setActive(theme);
        int format = SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
        int scale = settings.textureScale;
        WORKER.submit(() -> {
            try {
                long t0 = System.currentTimeMillis();
                new PackBuilder(theme, VANILLA, scale, i -> store.image(theme, i), packDir()).build(format, stamp(theme));
                GenshinUIClient.LOGGER.info("Built theme pack '{}' in {} ms", theme.id, System.currentTimeMillis() - t0);
                client.execute(() -> {
                    enablePack(client, true);
                    status = "";
                    building = false;
                    SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                            Text.literal("✦ " + theme.name), Text.literal("Genshin UI theme applied"));
                });
            } catch (Throwable e) {
                GenshinUIClient.LOGGER.error("Theme build failed", e);
                client.execute(() -> {
                    status = "Build failed: " + e.getMessage();
                    building = false;
                });
            }
        });
    }

    public static void disable() {
        settings.activeTheme = null;
        settings.save();
        setActive(null);
        enablePack(MinecraftClient.getInstance(), false);
    }

    private static void enablePack(MinecraftClient client, boolean enable) {
        ResourcePackManager rpm = client.getResourcePackManager();
        rpm.scanPacks();
        List<String> ids = new ArrayList<>(rpm.getEnabledIds());
        ids.remove(PACK_ID);
        if (enable) ids.add(PACK_ID); // last = highest priority
        rpm.setEnabledProfiles(ids);
        client.options.resourcePacks.clear();
        for (ResourcePackProfile p : rpm.getEnabledProfiles()) {
            if (!p.isPinned()) client.options.resourcePacks.add(p.getId());
        }
        client.options.write();
        client.reloadResources();
    }

    /** Called after every resource reload. */
    public static void onResourcesReloaded(net.minecraft.resource.ResourceManager rm) {
        boolean enabled = MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().contains(PACK_ID);
        assetsLoaded = enabled && rm.getResource(MENU_ART).isPresent() && rm.getResource(PARTICLE).isPresent();
        chestsLoaded = assetsLoaded && rm.getResource(chestTexture(3)).isPresent();
        recipeBooksLoaded = assetsLoaded && rm.getResource(RECIPE_BOOK_CRAFTING).isPresent()
                && rm.getResource(RECIPE_BOOK_FURNACE).isPresent();
        java.util.Map<Identifier, Identifier> open = new java.util.HashMap<>();
        if (assetsLoaded) {
            for (com.genshinui.theme.UiTarget host : new com.genshinui.theme.UiTarget[]{
                    com.genshinui.theme.UiTarget.INVENTORY, com.genshinui.theme.UiTarget.CRAFTING, com.genshinui.theme.UiTarget.FURNACE}) {
                for (String name : host.textures) {
                    Identifier variant = Identifier.of(MOD_ID, "textures/gui/book_open/" + name + ".png");
                    if (rm.getResource(variant).isPresent()) open.put(Identifier.ofVanilla("textures/gui/" + name + ".png"), variant);
                }
            }
        }
        bookOpenTextures = open;
    }

    /** Re-reads the active theme from disk (after editing). */
    public static void refreshActive() {
        if (active != null) setActive(store.get(active.id));
    }
}
