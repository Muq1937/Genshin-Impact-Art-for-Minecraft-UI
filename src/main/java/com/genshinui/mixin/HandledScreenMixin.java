package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import com.genshinui.fx.ParticleFx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    private static final String DRAW_FOREGROUND =
            "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawForeground(Lnet/minecraft/client/gui/DrawContext;II)V";

    @Inject(method = "renderMain", at = @At(value = "INVOKE", target = DRAW_FOREGROUND))
    private void genshinui$beginLabels(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ThemeManager.labelsActive = ThemeManager.live() && ThemeManager.settings.recolorLabels;
    }

    @Inject(method = "renderMain", at = @At(value = "INVOKE", target = DRAW_FOREGROUND, shift = At.Shift.AFTER))
    private void genshinui$endLabels(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ThemeManager.labelsActive = false;
    }

    /** Particles drift over the panel art but under slots and items. */
    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void genshinui$particles(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ParticleFx.render(context, "panel", x + 3, y + 3, backgroundWidth - 6, backgroundHeight - 6, 9, 1f);
    }
}
