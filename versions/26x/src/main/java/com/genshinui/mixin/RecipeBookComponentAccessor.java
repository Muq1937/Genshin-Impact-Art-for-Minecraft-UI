package com.genshinui.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    /** True when the window is too narrow and the book is drawn over the screen instead of beside it. */
    @Accessor("widthTooNarrow")
    boolean genshinui$isNarrow();
}
