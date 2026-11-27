package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Chests, barrels, ender chests, etc.: vanilla draws generic_54.png in two slices, which would cut the theme art
 * apart on anything smaller than a double chest. With a theme active, the first blit draws the seamless texture for
 * this row count at full height and the second one is emptied.
 * <p>
 * blit args: (pipeline, texture, x, y, u, v, width, height, texW, texH). The target is matched by name only so the
 * same code works whether RenderPipeline lives in com.mojang.blaze3d (26.2) or com.mojang.renderpearl (26.3).
 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow @Final private int containerRows;

    private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit";

    @ModifyArgs(method = "extractBackground", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void genshinui$top(Args args) {
        if (!(ThemeManager.live() && ThemeManager.chestsLoaded)) return;
        args.set(1, ThemeManager.chestTexture(containerRows));
        args.set(4, 0.0F);
        args.set(5, 0.0F);
        args.set(7, containerRows * 18 + 17 + 96);
    }

    @ModifyArgs(method = "extractBackground", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void genshinui$bottom(Args args) {
        if (!(ThemeManager.live() && ThemeManager.chestsLoaded)) return;
        args.set(6, 0); // zero width: draws nothing
        args.set(7, 0);
    }
}
