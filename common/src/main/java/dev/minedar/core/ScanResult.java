package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of casting one LiDAR signal through the world: a list of hit points
 * and the signal that survived to the end of the ray. Immutable-ish and cheap
 * to allocate per ray step; the heavy per-point path appends straight into a
 * {@link PointCloudSection}.
 */
public final class ScanResult {

    /** A single LiDAR hit: absolute world position plus colour/intensity. */
    public static final class Hit {
        public final double x;
        public final double y;
        public final double z;
        public final int rgb;
        public final int intensity;

        public Hit(double x, double y, double z, int rgb, int intensity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
            this.intensity = intensity;
        }
    }

    private final List<Hit> hits = new ArrayList<>(8);
    /** Fraction of the original signal still travelling after the last medium. */
    private double transmitted = 1.0;

    public void add(double x, double y, double z, int rgb, int intensity) {
        hits.add(new Hit(x, y, z, rgb, intensity));
    }

    public List<Hit> hits() {
        return hits;
    }

    public int hitCount() {
        return hits.size();
    }

    public double transmitted() {
        return transmitted;
    }

    public void setTransmitted(double transmitted) {
        this.transmitted = transmitted;
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }
}
