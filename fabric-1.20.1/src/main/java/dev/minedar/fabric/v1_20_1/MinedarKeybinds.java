package dev.minedar.fabric.v1_20_1;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minedar.core.ScannerController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Input for the 1.20.1 scanner (sections 3, 67). Held modifiers are read live
 * from GLFW so scan-radius changes feel continuous like the GMod LiDAR, and the
 * fullscreen-map keybind defaults to UNBOUND as required.
 *
 * <p>LiDAR toggles on {@code G} rather than {@code L}: vanilla already binds
 * {@code L} to Advancements, and a shared key would fire both mappings. {@code G}
 * is free and matches the GMod LiDAR heritage. Fabric registers the mappings
 * through {@link KeyBindingHelper} during client init and ticks them from
 * {@link ClientTickEvents}.
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

    /** Called from the client initializer; registers keys and the tick hook. */
    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_LIDAR);
        KeyBindingHelper.registerKeyBinding(FULLSCREEN_MAP);
        KeyBindingHelper.registerKeyBinding(TOGGLE_MINIMAP);
        ClientTickEvents.END_CLIENT_TICK.register(MinedarKeybinds::onClientTick);
    }

    private static void onClientTick(Minecraft mc) {
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
