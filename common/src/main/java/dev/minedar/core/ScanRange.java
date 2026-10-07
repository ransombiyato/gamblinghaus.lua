package dev.minedar.core;

/**
 * Holds the live scan-radius and scan-distance state (sections 6-7). Radius
 * changes happen continuously from the shift/ctrl keys, so this is intentionally
 * tiny and lock-free enough to be read every frame.
 *
 * <p>Radius and minimap display radius are deliberately separate concepts; the
 * minimap radius lives in {@link MinedarConfig}.
 */
public final class ScanRange {

    public static final double MIN_RADIUS = 4.0;
    public static final double MAX_RADIUS = 96.0;
    public static final double DEFAULT_RADIUS = 24.0;
    /** Blocks per key-hold tick while scanning. */
    public static final double RADIUS_STEP = 0.75;

    private double radius = DEFAULT_RADIUS;
    private double maxDistance = 128.0;

    public double radius() {
        return radius;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double maxDistance) {
        this.maxDistance = Math.max(8.0, maxDistance);
    }

    public void increase(double amount) {
        radius = Math.min(MAX_RADIUS, radius + amount);
    }

    public void decrease(double amount) {
        radius = Math.max(MIN_RADIUS, radius - amount);
    }

    /** Larger radius means wider coverage and sparser density (section 6). */
    public double pointDensityFactor() {
        double t = (radius - MIN_RADIUS) / (MAX_RADIUS - MIN_RADIUS);
        return 1.0 - 0.75 * Math.max(0.0, Math.min(1.0, t));
    }

    public void setRadius(double value) {
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value));
    }

    public void reset() {
        radius = DEFAULT_RADIUS;
    }
}
