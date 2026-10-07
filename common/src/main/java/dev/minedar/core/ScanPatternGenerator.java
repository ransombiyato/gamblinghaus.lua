package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces the GMod-like scan pattern: a forward cone swept as a golden-angle
 * spiral so successive ticks fill the cone evenly rather than in visible rings.
 * Point count scales with the scan radius so a small radius yields dense dots
 * and a large radius yields sparser coverage (sections 6-7, 8).
 *
 * <p>Scan speed is intentionally fixed and not exposed (section 7).
 */
public final class ScanPatternGenerator {

    /** Half-angle of the forward scan cone, in radians. */
    public static final double CONE_HALF_ANGLE = Math.toRadians(45.0);
    private static final int BASE_COUNT = 640;
    private static final double GOLDEN = Math.PI * (3.0 - Math.sqrt(5.0));

    private ScanPatternGenerator() {
    }

    /** Direction vectors as {dx, dy, dz}, unit length, in model space. */
    public static List<double[]> directions(double densityFactor, int maxPoints) {
        int count = (int) Math.round(BASE_COUNT * clamp01(densityFactor));
        count = Math.max(1, Math.min(maxPoints, count));
        List<double[]> out = new ArrayList<>(count);
        double cosLimit = Math.cos(CONE_HALF_ANGLE);
        for (int i = 0; i < count; i++) {
            // Uniform sampling of a spherical cap via z in [cosLimit, 1].
            double z = 1.0 - (1.0 - cosLimit) * (i + 0.5) / count;
            double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            double theta = GOLDEN * i;
            double dx = Math.cos(theta) * r;
            double dy = Math.sin(theta) * r;
            out.add(new double[] {dx, dy, z});
        }
        return out;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}
