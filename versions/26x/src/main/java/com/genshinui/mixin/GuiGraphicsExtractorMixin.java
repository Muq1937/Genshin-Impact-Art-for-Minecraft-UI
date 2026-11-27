package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import com.genshinui.theme.ThemePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container labels ("Inventory", "Crafting", chest names...) are drawn in vanilla dark gray, which is unreadable on
 * dark art. While a container draws its labels, swap that exact color for the theme's text color.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    private static final int DARK_GRAY = 0xFF404040;

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void genshinui$labelColor(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (!ThemeManager.labelsActive || color != DARK_GRAY) return;
        ThemePalette p = ThemeManager.palette();
        if (p == null) return;
        int newColor = p.text | 0xFF000000;
        if (newColor == color) return;
        ci.cancel();
        ((GuiGraphicsExtractor) (Object) this).text(font, text, x, y, newColor, p.dark);
    }
}
