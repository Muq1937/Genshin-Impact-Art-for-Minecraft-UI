package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the rotating panorama (title screen and out-of-world menus) with the theme art, slowly drifting. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void genshinui$menuArt(DrawContext context, float delta, CallbackInfo ci) {
        if (!ThemeManager.live() || !ThemeManager.settings.menuArt) return;
        ci.cancel();
        // Virtual 1600x900 texture space (the generated image is always 16:9; UVs are normalized).
        final int TW = 1600, TH = 900;
        double t = Util.getMeasuringTimeMs() / 1000.0;
        double zoom = 1.06 + 0.04 * Math.sin(t / 9.0);
        double sa = (double) width / height;
        double rw, rh;
        if (sa > (double) TW / TH) {
            rw = TW / zoom;
            rh = rw / sa;
        } else {
            rh = TH / zoom;
            rw = rh * sa;
        }
        double u = (TW - rw) / 2 + Math.sin(t / 13.0) * (TW - rw) * 0.4;
        double v = (TH - rh) / 2 + Math.cos(t / 17.0) * (TH - rh) * 0.4;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ThemeManager.MENU_ART, 0, 0, (float) u, (float) v,
                width, height, (int) rw, (int) rh, TW, TH);
    }
}
