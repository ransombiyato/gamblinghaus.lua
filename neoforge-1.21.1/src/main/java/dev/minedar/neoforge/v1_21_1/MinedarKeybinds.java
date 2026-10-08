package dev.minedar.neoforge.v1_21_1;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minedar.core.ScannerController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the 1.20.1 scanner (sections 3, 67). Held modifiers are read live
 * from GLFW so scan-radius changes feel continuous like the GMod LiDAR, and the
 * fullscreen-map keybind defaults to UNBOUND as required.
 *
 * <p>LiDAR toggles on {@code G} rather than {@code L}: vanilla already binds
 * {@code L} to Advancements, and a shared key would fire both mappings. {@code G}
 * is free and matches the GMod LiDAR heritage.
 *
 * <p>The key-mapping registration must be on the MOD event bus: Forge fires
 * {@link RegisterKeyMappingsEvent} there only, whereas {@code @EventBusSubscriber}
 * auto-subscribes to the game bus. Registering on the wrong bus silently drops
 * every keybind, so that one listener is wired explicitly via {@link #register()}
 * while tick handling stays on the game bus.
 */

public final class MinedarKeybinds {

    public static final String CATEGORY = "key.categories.minedar";

    public static final KeyMapping TOGGLE_LIDAR = new KeyMapping(
            "key.minedar.toggle_lidar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping FULLSCREEN_MAP = new KeyMapping(
            "key.minedar.fullscreen_map", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping TOGGLE_MINIMAP = new KeyMapping(
            "key.minedar.toggle_minimap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    private static boolean burstEdge;
    private static boolean lastRightDown;

    private MinedarKeybinds() {
    }

    /** Called from the mod constructor; wires the key-mapping registration. */
    public static void register(IEventBus modBus) {
        modBus.addListener(MinedarKeybinds::onRegisterKeyMappings);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_LIDAR);
        event.register(FULLSCREEN_MAP);
        event.register(TOGGLE_MINIMAP);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        handleDiscreteKeys(mc);
    }

    private static void handleDiscreteKeys(Minecraft mc) {
        ScannerController c = MinedarClient.get().controller();
        while (TOGGLE_LIDAR.consumeClick()) {
            if (c.isLidarOn()) {
                // Flush pending data BEFORE disabling rendering (section 5).
                MinedarClient.get().flushIfNeeded();
                c.unequip();
                c.disableLidar(true);
            } else {
                c.enableLidar();
                c.equip();
            }
        }
        while (TOGGLE_MINIMAP.consumeClick()) {
            MinedarMod.config().minimapVisible = !MinedarMod.config().minimapVisible;
            MinedarMod.saveConfig();
        }
        while (FULLSCREEN_MAP.consumeClick()) {
            FullscreenMapState.toggle();
        }
    }

    /** Called every tick; reads live held state into the controller. */
    public static void applyHeldState(ScannerController c) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();

        boolean shift = InputState.isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputState.isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrl = InputState.isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputState.isDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean f = InputState.isDown(window, GLFW.GLFW_KEY_F);

        c.setIncreaseRadiusHeld(shift && c.isLidarOn());
        c.setDecreaseRadiusHeld(ctrl && c.isLidarOn());
        c.setZooming(f && c.isLidarOn());

        // LMB = continuous scan, RMB = burst. Only in LiDAR mode with scanner out.
        boolean scanning = c.isLidarOn() && c.isScannerEquipped();
        if (scanning && InputState.isMouseDown(window, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            c.startContinuousScan();
        } else {
            c.stopContinuousScan();
        }

        boolean rightDown = scanning
                && InputState.isMouseDown(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (rightDown && !lastRightDown) {
            c.requestBurstScan();
        }
        lastRightDown = rightDown;
    }
}
