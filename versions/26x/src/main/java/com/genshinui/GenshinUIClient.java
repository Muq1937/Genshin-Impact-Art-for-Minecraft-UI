package com.genshinui;

import com.genshinui.fx.ParticleFx;
import com.genshinui.gui.ThemeSelectScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenshinUIClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("GenshinUI");
    private static KeyMapping openKey;
    private static boolean checkedPack;

    @Override
    public void onInitializeClient() {
        ThemeManager.init();

        KeyMapping.Category category = KeyMapping.Category.register(ThemeManager.id("main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.genshinui.open", InputConstants.KEY_U, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new ThemeSelectScreen(null));
            }
        });

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ThemeManager.id("theme_assets"),
                (ResourceManagerReloadListener) ThemeManager::onResourcesReloaded);

        // Animated drift over the hotbar, like the art in the inventory.
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ThemeManager.id("hotbar_fx"),
                (graphics, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.player.isSpectator()) return;
                    int w = graphics.guiWidth(), h = graphics.guiHeight();
                    ParticleFx.render(graphics, "hotbar", w / 2 - 90, h - 21, 180, 20, 4, 0.8f);
                });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                if (!checkedPack) {
                    checkedPack = true;
                    ThemeManager.ensureUpToDate();
                }
                Screens.getWidgets(screen).add(themeButton(screen, 6, 6));
            } else if (screen instanceof PauseScreen ps && ps.showsPauseMenu()) {
                Screens.getWidgets(screen).add(themeButton(screen, 6, 6));
            }
        });
    }

    private static Button themeButton(Screen parent, int x, int y) {
        return Button.builder(Component.literal("✦ Genshin UI"), b -> Minecraft.getInstance().gui.setScreen(new ThemeSelectScreen(parent)))
                .bounds(x, y, 90, 20).build();
    }
}
