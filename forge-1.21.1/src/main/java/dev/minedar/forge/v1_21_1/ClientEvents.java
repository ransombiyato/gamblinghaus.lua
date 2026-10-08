package dev.minedar.forge.v1_21_1;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minedar.core.ScannerController;
import dev.minedar.core.ShaderStateManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client render/session hooks for Forge 1.21.1: draws the point cloud, manages
 * the crosshair (section 4), disables/restores shaders around LiDAR mode
 * (section 4), and flushes scan data on disconnect (section 11).
 *
 * <p>1.21 removed {@code RenderGuiEvent}/{@code RenderGuiOverlayEvent} in favour
 * of the named {@code LayeredDraw} HUD. The MiNEDAR overlay is therefore added
 * as its own layer above the vanilla layers, which both guarantees it runs in
 * LiDAR mode (where the crosshair layer is suppressed) and lets the Gui mixin
 * blank the whole vanilla HUD behind the fullscreen map.
 */
@Mod.EventBusSubscriber(modid = MinedarMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private static final ResourceLocation MINEDAR_OVERLAY =
            ResourceLocation.fromNamespaceAndPath(MinedarMod.MOD_ID, "overlay");

    private static final LiDARRenderer RENDERER = new LiDARRenderer();
    private static final ShaderStateManager SHADERS = new ShaderStateManager();
    private static final MinimapRenderer MINIMAP = new MinimapRenderer();

    private ClientEvents() {
    }

    /** Registers the MiNEDAR HUD layer; must run on the mod event bus. */
    public static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                ClientEvents::onAddGuiOverlayLayers);
    }

    private static void onAddGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(MINEDAR_OVERLAY,
                (guiGraphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    int w = mc.getWindow().getGuiScaledWidth();
                    int h = mc.getWindow().getGuiScaledHeight();
                    MINIMAP.renderMinimap(guiGraphics, w, h, delta.getGameTimeDeltaPartialTick(true));
                    MINIMAP.renderCrosshair(guiGraphics, w, h);
                    MINIMAP.renderFullscreen(guiGraphics, w, h, delta.getGameTimeDeltaPartialTick(true));
                });
    }

    /**
     * Draws the point cloud once the level render has returned. {@code AFTER_LEVEL}
     * is the only stage that still fires while LiDAR hides the world: the earlier
     * stages are dispatched from inside {@code LevelRenderer.renderLevel}, which is
     * cancelled in LiDAR mode. Because the level pass is skipped, this handler also
     * reproduces the two frame-setup steps it would otherwise have done — clearing
     * the colour buffer and clearing the fog — so the hidden world is replaced by
     * the LiDAR cloud rather than the previous frame (section 4).
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ScannerController c = MinedarClient.get().controller();
        if (!c.isLidarOn()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc.gameRenderer;

        FogRenderer.setupColor(event.getCamera(), event.getPartialTick(), mc.level,
                mc.options.getEffectiveRenderDistance(),
                gameRenderer.getDarkenWorldAmount(event.getPartialTick()));
        FogRenderer.levelFogColor();
        RenderSystem.clear(16640, Minecraft.ON_OSX);

        var cam = event.getCamera();
        float yawRad = (float) Math.toRadians(cam.getYRot());
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        RENDERER.render(cam, event.getProjectionMatrix(),
                MinedarMod.config().scanDistance, rightX, rightZ);

        FogRenderer.setupNoFog();
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
