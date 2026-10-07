package dev.minedar.forge.v1_20_1;

import dev.minedar.core.ScannerController;
import dev.minedar.core.ShaderStateManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client render/session hooks for Forge 1.20.1: draws the point cloud, manages
 * the crosshair (section 4), disables/restores shaders around LiDAR mode
 * (section 4), and flushes scan data on disconnect (section 11).
 */
@Mod.EventBusSubscriber(modid = MinedarMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private static final LiDARRenderer RENDERER = new LiDARRenderer();
    private static final ShaderStateManager SHADERS = new ShaderStateManager();
    private static final MinimapRenderer MINIMAP = new MinimapRenderer();

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ScannerController c = MinedarClient.get().controller();
        if (!c.isLidarOn()) {
            return;
        }
        var cam = event.getCamera();
        float yawRad = (float) Math.toRadians(cam.getYRot());
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        RENDERER.render(event.getPoseStack(), cam, MinedarMod.config().scanDistance,
                rightX, rightZ);
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        ScannerController c = MinedarClient.get().controller();
        if (FullscreenMapState.isOpen()) {
            return; // the Gui mixin already blanks the HUD
        }
        String id = event.getOverlay().id().getPath();
        if (id.equals("crosshair")) {
            if (c.isLidarOn()) {
                // LiDAR ON: no normal crosshair; a small white dot is drawn by
                // MinimapRenderer when the scanner is held (section 4).
                event.setCanceled(true);
            }
            return;
        }
        if (c.isLidarOn() && (id.equals("hotbar") || id.equals("player_health")
                || id.equals("food_level") || id.equals("experience_bar"))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiEventPost(RenderGuiEvent.Post event) {
        // Draw MiNEDAR's own overlay once the HUD pass is done. This must NOT
        // hang off the crosshair overlay's Post hook: in LiDAR mode the crosshair
        // Pre is canceled, which suppresses its Post and would silently drop the
        // minimap/heatmap exactly when the player is scanning.
        Minecraft mc = Minecraft.getInstance();
        var g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float partial = event.getPartialTick();
        MINIMAP.renderMinimap(g, w, h, partial);
        MINIMAP.renderCrosshair(g, w, h);
        MINIMAP.renderFullscreen(g, w, h, partial);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ScannerController c = MinedarClient.get().controller();
        // Record the player's shader state, disabling shaders while LiDAR runs and
        // restoring it on exit (section 4). If no shader mod is present this is a
        // documented no-op and the player's config is never touched.
        ShaderStateManager.Action action = SHADERS.sync(c.isLidarOn(), ShadersDetected.isShaderPackActive());
        switch (action) {
            case DISABLE -> ShadersDetected.setShadersEnabled(false);
            case ENABLE -> ShadersDetected.setShadersEnabled(true);
            case NONE -> { }
        }
        // Fullscreen map pauses the world in single-player (section 73).
        Minecraft mc = Minecraft.getInstance();
        if (FullscreenMapState.isOpen() && mc.isLocalServer()) {
            mc.pauseGame(false);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MinedarClient.get().flushIfNeeded();
    }

    public static MinimapRenderer minimapRenderer() {
        return MINIMAP;
    }
}
