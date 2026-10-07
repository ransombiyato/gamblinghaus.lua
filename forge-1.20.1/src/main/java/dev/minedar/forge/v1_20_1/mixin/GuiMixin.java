package dev.minedar.forge.v1_20_1.mixin;

import dev.minedar.forge.v1_20_1.FullscreenMapState;
import dev.minedar.forge.v1_20_1.MinedarClient;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD behaviour for LiDAR mode (sections 4, 72). The crosshair is handled by the
 * event handler; here we hide the whole vanilla HUD while the fullscreen map is
 * open, so only the LiDAR map interface remains. Menu and text rendering are left
 * alone.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void minedar$hideHudForFullscreenMap(CallbackInfo ci) {
        if (FullscreenMapState.isOpen()) {
            ci.cancel();
        }
    }
}
