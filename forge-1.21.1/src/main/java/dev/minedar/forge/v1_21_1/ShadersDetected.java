package dev.minedar.forge.v1_21_1;

import java.lang.reflect.Method;

/**
 * Detects and, where possible, toggles an external shader pack (Iris/Oculus)
 * without taking a hard dependency on it (section 4). All calls are reflective so
 * MiNEDAR loads cleanly whether or not a shader mod is installed.
 *
 * <p>Limitation, documented deliberately (section 82): if no supported shader mod
 * is present, or its API differs, we cannot disable shaders. We then do nothing
 * and never touch the player's configuration, which is the safe outcome.
 */
final class ShadersDetected {

    private static boolean probed;
    private static boolean present;
    private static Method setShadersEnabled;
    private static Object shaderApi;

    private ShadersDetected() {
    }

    /** True when a supported shader mod is currently applying a pack. */
    static boolean isShaderPackActive() {
        probe();
        if (!present || shaderApi == null) {
            return false;
        }
        try {
            Method inUse = shaderApi.getClass().getMethod("isShaderPackInUse");
            Object result = inUse.invoke(shaderApi);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Attempts to enable/disable shaders; returns true when the action applied. */
    static boolean setShadersEnabled(boolean enabled) {
        probe();
        if (!present || shaderApi == null || setShadersEnabled == null) {
            return false;
        }
        try {
            setShadersEnabled.invoke(shaderApi, enabled);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        // Iris on Fabric/Forge, Oculus on Forge: both expose net.irisshaders...
        String[] candidates = {
                "net.irisshaders.iris.api.v0.IrisApi"
        };
        for (String name : candidates) {
            try {
                Class<?> clazz = Class.forName(name);
                Method getInstance = clazz.getMethod("getInstance");
                shaderApi = getInstance.invoke(null);
                setShadersEnabled = clazz.getMethod("setShadersEnabled", boolean.class);
                present = true;
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Not installed: fall through and stay absent.
            }
        }
    }
}
