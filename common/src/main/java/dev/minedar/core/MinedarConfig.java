package dev.minedar.core;

/**
 * MiNEDAR's persisted settings (sections 6, 53-54). Only user-facing knobs live
 * here, and intentionally excludes anything the spec wants fixed: point
 * appearance, scan speed and minimap/fullscreen zoom are not configurable.
 */
public final class MinedarConfig {

    /** Whether the heatmap minimap is drawn. Persisted; independent of LiDAR. */
    public boolean minimapVisible = true;
    /** Minimap display radius in blocks (separate from scan radius). */
    public int minimapRadiusBlocks = 128;
    /** Default scan radius used when a session starts. */
    public double defaultScanRadius = ScanRange.DEFAULT_RADIUS;
    /** Maximum scan distance in blocks. */
    public double scanDistance = 128.0;
    /** Whether scans are written to the persistent store automatically. */
    public boolean autosave = true;
    /** Fixed multiplier from minimap radius to fullscreen map radius (section 69). */
    public double fullscreenRadiusMultiplier = 4.0;

    public static MinedarConfig defaults() {
        return new MinedarConfig();
    }

    /** Clamps every field into its legal range; returns {@code this}. */
    public MinedarConfig sanitised() {
        if (minimapRadiusBlocks < 16) {
            minimapRadiusBlocks = 16;
        } else if (minimapRadiusBlocks > 1024) {
            minimapRadiusBlocks = 1024;
        }
        if (defaultScanRadius < ScanRange.MIN_RADIUS) {
            defaultScanRadius = ScanRange.MIN_RADIUS;
        } else if (defaultScanRadius > ScanRange.MAX_RADIUS) {
            defaultScanRadius = ScanRange.MAX_RADIUS;
        }
        if (scanDistance < 8.0) {
            scanDistance = 8.0;
        } else if (scanDistance > 512.0) {
            scanDistance = 512.0;
        }
        if (fullscreenRadiusMultiplier < 1.0) {
            fullscreenRadiusMultiplier = 1.0;
        } else if (fullscreenRadiusMultiplier > 16.0) {
            fullscreenRadiusMultiplier = 16.0;
        }
        return this;
    }
}
