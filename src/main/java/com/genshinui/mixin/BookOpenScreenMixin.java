package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * When a theme connects the recipe book to this screen, the screen has two images: its own (book closed) and the
 * right half of the shared book+screen image (book open beside it). Swap to the latter while the book is open.
 */
@Mixin({InventoryScreen.class, CraftingScreen.class, AbstractFurnaceScreen.class})
public abstract class BookOpenScreenMixin {
    @ModifyArg(method = "drawBackground", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"))
    private Identifier genshinui$bookOpenVariant(Identifier texture) {
        if (!ThemeManager.live()) return texture;
        Identifier variant = ThemeManager.bookOpenTextures.get(texture);
        if (variant == null) return texture;
        RecipeBookWidget<?> book = ((RecipeBookScreenAccessor) this).genshinui$getRecipeBook();
        boolean beside = book != null && book.isOpen() && !((RecipeBookWidgetAccessor) book).genshinui$isNarrow();
        return beside ? variant : texture;
    }
}
