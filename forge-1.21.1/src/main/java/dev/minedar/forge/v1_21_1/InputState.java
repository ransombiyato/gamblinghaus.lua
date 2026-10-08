package dev.minedar.forge.v1_21_1;

import dev.minedar.core.ScannerController;
import org.lwjgl.glfw.GLFW;

/**
 * Key/mouse state for the 1.21.1 scanner (section 3). Reads the live GLFW input
 * state each tick rather than tracking its own key events, so held modifiers
 * behave exactly like the GMod LiDAR (live radius changes while scanning).
 */
public final class InputState {

    private InputState() {
    }

    static boolean isDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    static boolean isMouseDown(long window, int button) {
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }
}
