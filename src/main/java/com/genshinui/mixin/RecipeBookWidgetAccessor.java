package com.genshinui.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetAccessor {
    /** True when the window is too narrow and the book is drawn over the screen instead of beside it. */
    @Accessor("narrow")
    boolean genshinui$isNarrow();
}
