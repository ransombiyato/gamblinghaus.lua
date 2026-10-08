package dev.minedar.fabric.v1_20_1;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minedar.core.ScannerController;
import dev.minedar.core.ShaderStateManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Client render/session hooks for Fabric 1.20.1: draws the point cloud, manages
 * the crosshair (section 4), disables/restores shaders around LiDAR mode
 * (section 4), and flushes scan data on disconnect (section 11).
 *
 * <p>The point cloud is drawn from inside {@code LevelRendererMixin} (the Fabric
 * {@code WorldRenderEvents} callbacks are dispatched by the very method we cancel
 * to hide the world in LiDAR mode, so they never fire while scanning), and this
 * class owns the HUD and session hooks.
 */
public final class ClientEvents {

    private static final LiDARRenderer RENDERER = new LiDARRenderer();
    private static final ShaderStateManager SHADERS = new ShaderStateManager();
    private static final MinimapRenderer MINIMAP = new MinimapRenderer();

    private ClientEvents() {
    }

    /**
     * Draws the point cloud. Called from {@code LevelRendererMixin} in place of
     * the cancelled level pass, so it also reproduces the two frame-setup steps
     * that pass would have done — clearing the colour buffer and the fog — and the
     * hidden world is replaced by the LiDAR cloud rather than the previous frame.
     */
    public static void renderPointCloud() {
        ScannerController c = MinedarClient.get().controller();
        if (!c.isLidarOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc.gameRenderer;
        var cam = gameRenderer.getMainCamera();
        float partial = mc.getFrameTime();

        FogRenderer.setupColor(cam, partial, mc.level,
                mc.options.getEffectiveRenderDistance(),
                gameRenderer.getDarkenWorldAmount(partial));
        FogRenderer.levelFogColor();
        RenderSystem.clear(16640, Minecraft.ON_OSX);

        float yawRad = (float) Math.toRadians(cam.getYRot());
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RENDERER.render(cam, projection, MinedarMod.config().scanDistance, rightX, rightZ);

        FogRenderer.setupNoFog();
    }

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, partial) -> {
            if (FullscreenMapState.isOpen()) {
                return; // the Gui mixin already blanks the HUD
            }
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            MINIMAP.renderMinimap(guiGraphics, w, h, partial);
            MINIMAP.renderCrosshair(guiGraphics, w, h);
            MINIMAP.renderFullscreen(guiGraphics, w, h, partial);
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ScannerController c = MinedarClient.get().controller();
            // Disable shaders while LiDAR runs, restoring on exit (section 4). A
            // documented no-op when no shader mod is present (section 82).
            ShaderStateManager.Action action =
                    SHADERS.sync(c.isLidarOn(), ShadersDetected.isShaderPackActive());
            switch (action) {
                case DISABLE -> ShadersDetected.setShadersEnabled(false);
                case ENABLE -> ShadersDetected.setShadersEnabled(true);
                case NONE -> { }
            }
            // Fullscreen map pauses the world in single-player (section 73).
            if (FullscreenMapState.isOpen() && mc.isLocalServer()) {
                mc.pauseGame(false);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                MinedarClient.get().flushIfNeeded());
    }

    public static MinimapRenderer minimapRenderer() {
        return MINIMAP;
    }
}
