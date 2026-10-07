package dev.minedar.core;

/**
 * Pure shader lifecycle bookkeeping (section 4). It never touches a graphics
 * API itself; the loader adapter reports whether shaders were active, performs
 * the actual enable/disable, and this class guarantees the invariant that the
 * player's original shader state is always restored - even across repeated
 * enable/disable cycles or a failed disable.
 */
public final class ShaderStateManager {

    /** What the adapter should do next on the graphics side. */
    public enum Action {
        /** Nothing to change. */
        NONE,
        /** Shaders must be disabled now. */
        DISABLE,
        /** Shaders must be re-enabled now. */
        ENABLE
    }

    private boolean lidarActive;
    private boolean shadersWereEnabledBeforeLidar;
    private boolean disableAttempted;

    public boolean isLidarActive() {
        return lidarActive;
    }

    public boolean shadersWereEnabled() {
        return shadersWereEnabledBeforeLidar;
    }

    /** Call when LiDAR activates; {@code shadersCurrentlyEnabled} from the adapter. */
    public Action engage(boolean shadersCurrentlyEnabled) {
        lidarActive = true;
        shadersWereEnabledBeforeLidar = shadersCurrentlyEnabled;
        disableAttempted = shadersCurrentlyEnabled;
        return shadersCurrentlyEnabled ? Action.DISABLE : Action.NONE;
    }

    /**
     * Call when LiDAR deactivates. Returns ENABLE only if shaders were on before,
     * so we never switch on shaders the player had off.
     */
    public Action disengage() {
        lidarActive = false;
        boolean shouldRestore = shadersWereEnabledBeforeLidar;
        shadersWereEnabledBeforeLidar = false;
        disableAttempted = false;
        return shouldRestore ? Action.ENABLE : Action.NONE;
    }

    /** Confirms a disable actually happened, for adapters that need retries. */
    public boolean wasDisableAttempted() {
        return disableAttempted;
    }

    /** Recovery hook: if the adapter failed to disable shaders, it can report
     * that and we keep the "restore" intent so the state is still safe.
     */
    public void reportDisableFailed() {
        shadersWereEnabledBeforeLidar = true;
    }

    /**
     * Idempotent transition helper: call every tick with the current LiDAR state
     * and whether shaders are currently enabled. Returns the action the adapter
     * must perform, or {@link Action#NONE}. Handles repeated calls without
     * re-triggering, so a scan-running tick loop cannot thrash shader state.
     */
    public Action sync(boolean lidarShouldBeActive, boolean shadersCurrentlyEnabled) {
        if (lidarShouldBeActive && !lidarActive) {
            return engage(shadersCurrentlyEnabled);
        }
        if (!lidarShouldBeActive && lidarActive) {
            return disengage();
        }
        return Action.NONE;
    }
}
