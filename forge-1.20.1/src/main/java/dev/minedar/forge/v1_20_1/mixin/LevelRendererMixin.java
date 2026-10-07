package dev.minedar.forge.v1_20_1.mixin;

import dev.minedar.forge.v1_20_1.MinedarClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses ordinary terrain rendering while LiDAR mode is active (section 4).
 * The point cloud is drawn instead, so the normal block faces underneath must not
 * appear. This is a render-only change; the world itself is untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void minedar$hideWorldInLidarMode(CallbackInfo ci) {
        if (MinedarClient.get().controller().isLidarOn()) {
            ci.cancel();
        }
    }
}
