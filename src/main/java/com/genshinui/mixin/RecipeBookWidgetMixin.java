package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The inventory, crafting table and furnaces share one recipe book texture. With a theme active, use the
 * crafting-table or furnace version (each with its own art crop) depending on the open screen.
 */
@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {
    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"))
    private Identifier genshinui$perScreenTexture(Identifier texture) {
        if (!ThemeManager.live() || !ThemeManager.recipeBooksLoaded) return texture;
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof CraftingScreen) return ThemeManager.RECIPE_BOOK_CRAFTING;
        if (screen instanceof AbstractFurnaceScreen<?>) return ThemeManager.RECIPE_BOOK_FURNACE;
        return texture; // inventory: the vanilla path, re-skinned with the "Recipe Book: Inventory" crop
    }
}
