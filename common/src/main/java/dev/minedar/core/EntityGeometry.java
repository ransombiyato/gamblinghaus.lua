package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns entity model geometry into LiDAR dots (sections 40-44). Applied to the
 * real per-part model boxes the loader adapter extracts, so limbs, horns, ears,
 * tails and armour appear as geometry rather than a single hitbox.
 *
 * <p>Sampling is deliberately sparse: each box contributes a small fixed number
 * of interior points, biased towards the outer shell so silhouettes read
 * correctly, and boxes that are too thin to matter contribute one point. This
 * keeps a scanned mob in the same density class as the world around it.
 */
public final class EntityGeometry {

    /** Points sampled along the longest axis of a box. */
    private static final int SAMPLES_PER_BOX = 5;
    /** Boxes thinner than this along every axis collapse to a single point. */
    private static final double THIN_THRESHOLD = 0.04;

    private EntityGeometry() {
    }

    /** One oriented-box part of an entity model, already in world space. */
    public static final class Box {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;
        /** Colour for this part (category colour, possibly part-tinted). */
        public final int rgb;

        public Box(double minX, double minY, double minZ,
                   double maxX, double maxY, double maxZ, int rgb) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.rgb = rgb;
        }

        double sizeX() {
            return maxX - minX;
        }

        double sizeY() {
            return maxY - minY;
        }

        double sizeZ() {
            return maxZ - minZ;
        }
    }

    /** Samples a list of world-space model boxes into entity dots. */
    public static List<WorldSampler.SampledEntity> sample(List<Box> boxes, int fallbackRgb) {
        List<WorldSampler.SampledEntity> out = new ArrayList<>();
        if (boxes == null || boxes.isEmpty()) {
            return out;
        }
        for (Box box : boxes) {
            double sx = box.sizeX();
            double sy = box.sizeY();
            double sz = box.sizeZ();
            if (sx < THIN_THRESHOLD && sy < THIN_THRESHOLD && sz < THIN_THRESHOLD) {
                out.add(new WorldSampler.SampledEntity(
                        (box.minX + box.maxX) * 0.5,
                        (box.minY + box.maxY) * 0.5,
                        (box.minZ + box.maxZ) * 0.5,
                        box.rgb == 0 ? fallbackRgb : box.rgb));
                continue;
            }
            // Walk the longest axis; place the other two at the shell edge so the
            // silhouette, not the interior, dominates the cloud.
            int n = SAMPLES_PER_BOX;
            for (int i = 0; i < n; i++) {
                double t = (i + 0.5) / n;
                out.add(new WorldSampler.SampledEntity(
                        shell(box.minX, box.maxX, t, sx, sy, sz),
                        shell(box.minY, box.maxY, t, sy, sx, sz),
                        shell(box.minZ, box.maxZ, t, sz, sx, sy),
                        box.rgb == 0 ? fallbackRgb : box.rgb));
            }
        }
        return out;
    }

    /**
     * Positions a coordinate along its axis. The longest axis is distributed
     * linearly; the two shorter axes hug the outer 20% band so corners and edges
     * are captured instead of a solid inner core.
     */
    private static double shell(double min, double max, double t, double axisSize,
                                double otherA, double otherB) {
        boolean dominant = axisSize >= otherA && axisSize >= otherB;
        if (dominant) {
            return min + (max - min) * t;
        }
        // Alternate near the two faces.
        double edge = (t < 0.5) ? 0.12 : 0.88;
        return min + (max - min) * edge;
    }

    /** True when the axis-aligned box around two points could contain the ray. */
    public static boolean maybeIntersects(double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ,
                                          Ray ray, double maxDistance) {
        // Slab test against the ray's finite segment.
        double invx = ray.dx == 0 ? Double.POSITIVE_INFINITY : 1.0 / ray.dx;
        double invy = ray.dy == 0 ? Double.POSITIVE_INFINITY : 1.0 / ray.dy;
        double invz = ray.dz == 0 ? Double.POSITIVE_INFINITY : 1.0 / ray.dz;
        double t1 = (minX - ray.ox) * invx;
        double t2 = (maxX - ray.ox) * invx;
        double tmin = Math.min(t1, t2);
        double tmax = Math.max(t1, t2);
        t1 = (minY - ray.oy) * invy;
        t2 = (maxY - ray.oy) * invy;
        tmin = Math.max(tmin, Math.min(t1, t2));
        tmax = Math.min(tmax, Math.max(t1, t2));
        t1 = (minZ - ray.oz) * invz;
        t2 = (maxZ - ray.oz) * invz;
        tmin = Math.max(tmin, Math.min(t1, t2));
        tmax = Math.min(tmax, Math.max(t1, t2));
        return tmax >= Math.max(tmin, 0.0) && tmin <= maxDistance;
    }
}
