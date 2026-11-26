package com.genshinui.gametest;

import com.genshinui.ThemeManager;
import com.genshinui.gui.ThemeEditorScreen;
import com.genshinui.gui.ThemeSelectScreen;
import com.genshinui.gui.WallpaperBrowserScreen;
import com.genshinui.we.WallpaperEngine;
import com.genshinui.theme.Theme;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/** Boots the client with the default theme, then screenshots the themed screens. */
public class GenshinUIClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitForScreen(TitleScreen.class);
        // first run: Columbina sample is activated and its pack gets built + loaded
        context.waitFor(client -> ThemeManager.live() && !ThemeManager.building, 20 * 60);
        context.waitTicks(40);
        context.takeScreenshot("01_title");

        context.setScreen(() -> new ThemeSelectScreen(null));
        context.waitTicks(60);
        context.takeScreenshot("02_theme_select");

        Theme active = context.computeOnClient(c -> ThemeManager.active());
        context.setScreen(() -> new ThemeEditorScreen(null, active));
        context.waitTicks(60);
        context.takeScreenshot("03_theme_editor");
        context.setScreen(() -> null);

        // Wallpaper Engine: browse the local workshop folder, unpack a scene wallpaper into a new theme
        java.util.List<java.nio.file.Path> ws = WallpaperEngine.workshopDirs();
        if (!ws.isEmpty() && java.nio.file.Files.isDirectory(ws.get(0).resolve("3484084483"))) {
            context.setScreen(() -> {
                ThemeSelectScreen sel = new ThemeSelectScreen(null);
                return new WallpaperBrowserScreen(sel, sel::newFromWallpaper);
            });
            context.waitTicks(120);
            context.takeScreenshot("09_wallpaper_browser");
            context.runOnClient(c -> ((WallpaperBrowserScreen) c.currentScreen)
                    .pick(WallpaperEngine.readItem(ws.get(0).resolve("3484084483"))));
            context.waitForScreen(ThemeEditorScreen.class);
            context.waitTicks(80);
            context.takeScreenshot("10_editor_from_wallpaper");
            context.setScreen(() -> null);
        }

        try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(w -> w.setCheatsEnabled(true)).create()) {
            sp.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            context.takeScreenshot("04_hud");

            context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
            context.waitTicks(40);
            context.takeScreenshot("05_inventory");

            // small (3-row) and large chests: art must be seamless
            for (int rows : new int[]{3, 6}) {
                context.runOnClient(c -> c.setScreen(new net.minecraft.client.gui.screen.ingame.GenericContainerScreen(
                        rows == 3 ? net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(0, c.player.getInventory())
                                : net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x6(0, c.player.getInventory()),
                        c.player.getInventory(), net.minecraft.text.Text.literal("Chest"))));
                context.waitTicks(30);
                context.takeScreenshot("05a_chest_" + rows);
            }

            context.runOnClient(c -> {
                c.player.getRecipeBook().setGuiOpen(net.minecraft.recipe.book.RecipeBookType.CRAFTING, true);
                c.setScreen(new InventoryScreen(c.player));
            });
            context.waitTicks(40);
            context.takeScreenshot("05b_recipe_book");

            context.runOnClient(c -> c.setScreen(new net.minecraft.client.gui.screen.ingame.CraftingScreen(
                    new net.minecraft.screen.CraftingScreenHandler(0, c.player.getInventory()),
                    c.player.getInventory(), net.minecraft.text.Text.literal("Crafting"))));
            context.waitTicks(30);
            context.takeScreenshot("05b2_recipe_book_crafting");

            context.runOnClient(c -> {
                c.player.getRecipeBook().setGuiOpen(net.minecraft.recipe.book.RecipeBookType.FURNACE, true);
                c.setScreen(new net.minecraft.client.gui.screen.ingame.FurnaceScreen(
                        new net.minecraft.screen.FurnaceScreenHandler(0, c.player.getInventory()),
                        c.player.getInventory(), net.minecraft.text.Text.literal("Furnace")));
            });
            context.waitTicks(30);
            context.takeScreenshot("05b3_recipe_book_furnace");

            context.runOnClient(c -> c.player.networkHandler.sendChatCommand("gamemode creative"));
            context.waitTicks(20);
            context.runOnClient(c -> c.setScreen(new net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen(
                    c.player, c.player.networkHandler.getEnabledFeatures(), false)));
            context.waitTicks(40);
            context.takeScreenshot("05c_creative");
            context.runOnClient(c -> c.player.networkHandler.sendChatCommand("gamemode survival"));
            context.waitTicks(10);

            context.setScreen(() -> new GameMenuScreen(true));
            context.waitTicks(20);
            context.takeScreenshot("06_pause");
            context.setScreen(() -> null);

            // switch theme in-world to verify live re-skinning
            context.runOnClient(c -> ThemeManager.apply(ThemeManager.store.get("yae_miko")));
            context.waitFor(c -> !ThemeManager.building && ThemeManager.live()
                    && ThemeManager.active().id.equals("yae_miko"), 20 * 60);
            context.waitTicks(60);
            context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
            context.waitTicks(40);
            context.takeScreenshot("07_inventory_yae");
            context.setScreen(() -> null);
            context.waitTicks(10);
            context.takeScreenshot("08_hud_yae");
        }
    }
}
