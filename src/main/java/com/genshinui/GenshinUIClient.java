package com.genshinui;

import com.genshinui.fx.ParticleFx;
import com.genshinui.gui.ThemeSelectScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenshinUIClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("GenshinUI");
    private static KeyBinding openKey;
    private static boolean checkedPack;

    @Override
    public void onInitializeClient() {
        ThemeManager.init();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.genshinui.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.genshinui"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ThemeSelectScreen(null));
            }
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(ThemeManager.MOD_ID, "theme_assets");
            }

            @Override
            public void reload(ResourceManager manager) {
                ThemeManager.onResourcesReloaded(manager);
            }
        });

        // Animated drift over the hotbar, like the art in the inventory.
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.of(ThemeManager.MOD_ID, "hotbar_fx"),
                (context, tickCounter) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null || mc.player.isSpectator()) return;
                    int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
                    ParticleFx.render(context, "hotbar", w / 2 - 90, h - 21, 180, 20, 4, 0.8f);
                });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                if (!checkedPack) {
                    checkedPack = true;
                    ThemeManager.ensureUpToDate();
                }
                Screens.getButtons(screen).add(themeButton(screen, 6, 6));
            } else if (screen instanceof GameMenuScreen gm && gm.shouldShowMenu()) {
                Screens.getButtons(screen).add(themeButton(screen, 6, 6));
            }
        });
    }

    private static ButtonWidget themeButton(net.minecraft.client.gui.screen.Screen parent, int x, int y) {
        return ButtonWidget.builder(Text.literal("✦ Genshin UI"), b -> MinecraftClient.getInstance().setScreen(new ThemeSelectScreen(parent)))
                .dimensions(x, y, 90, 20).build();
    }
}
