package dev.minedar.forge.v1_20_1;

/**
 * Fullscreen-map open/close state with the fast expansion animation (sections
 * 67-71). Pausing in single-player (section 73) is handled by the event handler;
 * this only tracks whether the map is open and the animation clock.
 */
public final class FullscreenMapState {

    private static boolean open;
    private static long openedAtNanos;
    private static final double ANIMATION_SECONDS = 0.12;

    private FullscreenMapState() {
    }

    public static void toggle() {
        setOpen(!open);
    }

    public static void setOpen(boolean value) {
        open = value;
        openedAtNanos = System.nanoTime();
    }

    public static boolean isOpen() {
        return open;
    }

    /** Animation progress in [0,1]; ~0.12s transition in either direction. */
    public static double progress() {
        double elapsed = (System.nanoTime() - openedAtNanos) / 1_000_000_000.0;
        double t = Math.max(0.0, Math.min(1.0, elapsed / ANIMATION_SECONDS));
        double eased = 1.0 - Math.pow(1.0 - t, 3.0);
        return open ? eased : 1.0 - eased;
    }
}
