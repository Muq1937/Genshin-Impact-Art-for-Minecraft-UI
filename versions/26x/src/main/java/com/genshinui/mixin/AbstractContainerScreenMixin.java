package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import com.genshinui.fx.ParticleFx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Shadow @Final protected int imageHeight;

    private static final String EXTRACT_LABELS =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V";

    /** Particles drift over the panel art (drawn in the background pass) but under slots and items. */
    @Inject(method = "extractContents", at = @At("HEAD"))
    private void genshinui$particles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ParticleFx.render(graphics, "panel", leftPos + 3, topPos + 3, imageWidth - 6, imageHeight - 6, 9, 1f);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = EXTRACT_LABELS))
    private void genshinui$beginLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ThemeManager.labelsActive = ThemeManager.live() && ThemeManager.settings.recolorLabels;
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = EXTRACT_LABELS, shift = At.Shift.AFTER))
    private void genshinui$endLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ThemeManager.labelsActive = false;
    }
}
