package com.genshinui.mixin;

import com.genshinui.ThemeManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chests, barrels, ender chests, etc.: vanilla draws generic_54.png in two slices, which would cut the theme art
 * apart on anything smaller than a double chest. With a theme active, draw the seamless texture for this row count.
 */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {
    @Shadow @Final private int rows;

    private GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void genshinui$seamlessChest(DrawContext context, float deltaTicks, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ThemeManager.live() || !ThemeManager.chestsLoaded) return;
        ci.cancel();
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, ThemeManager.chestTexture(rows), i, j, 0.0F, 0.0F,
                this.backgroundWidth, rows * 18 + 17 + 96, 256, 256);
    }
}
