package dev.minedar.core;

import dev.minedar.core.PointCloudSection;

/**
 * A storage for LiDAR point cloud data that supports adding points.
 * This interface is used by {@link ScanEngine} for scan output.
 */
public interface PointCloudStore {
    /**
     * Adds a point at the given world coordinates with color and intensity.
     * The point will be placed in the appropriate spatial section.
     */
    void add(double x, double y, double z, int rgb, int intensity);
}