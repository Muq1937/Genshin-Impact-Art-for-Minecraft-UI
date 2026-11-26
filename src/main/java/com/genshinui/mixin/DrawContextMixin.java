package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import com.genshinui.theme.ThemePalette;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Container labels ("Inventory", "Crafting", chest names...) are drawn in vanilla dark gray, which is
 * unreadable on dark art. While a container draws its foreground, swap that exact color for the theme's text color.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void genshinui$labelColor(TextRenderer tr, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (!ThemeManager.labelsActive || color != Colors.DARK_GRAY) return;
        ThemePalette p = ThemeManager.palette();
        if (p == null) return;
        int newColor = p.text | 0xFF000000;
        if (newColor == color) return;
        ci.cancel();
        ((DrawContext) (Object) this).drawText(tr, text, x, y, newColor, p.dark);
    }
}
