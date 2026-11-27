package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The inventory, crafting table and furnaces share one recipe book texture. With a theme active, use the
 * crafting-table or furnace version (each with its own art crop) depending on the open screen.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @ModifyArg(method = "extractRenderState", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit"))
    private Identifier genshinui$perScreenTexture(Identifier texture) {
        if (!ThemeManager.live() || !ThemeManager.recipeBooksLoaded) return texture;
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof CraftingScreen) return ThemeManager.RECIPE_BOOK_CRAFTING;
        if (screen instanceof AbstractFurnaceScreen<?>) return ThemeManager.RECIPE_BOOK_FURNACE;
        return texture; // inventory: the vanilla path, re-skinned with the "Recipe Book: Inventory" crop
    }
}
