package dev.minedar.core;

/**
 * Geometry for the minimap and fullscreen map (sections 60, 67-71). Pure maths so
 * orientation, chunk-grid line placement and the expansion animation are all
 * deterministic and testable without a graphics context.
 */
public final class MinimapLayout {

    /** Thin pink chunk-boundary lines (section 59). */
    public static final int CHUNK_LINE_COLOUR = 0xFF80B0;
    /** Thin blue frame (section 60). */
    public static final int FRAME_COLOUR = 0x3070FF;
    /** Cardinal labels: exactly these four, no diagonals (section 61). */
    public static final String[] CARDINALS = {"N", "S", "E", "W"};

    public static final int CENTRE_MARKER = 0xFFFFFF;

    /** Fullscreen expansion animation duration in seconds; deliberately very fast. */
    public static final double ANIMATION_SECONDS = 0.12;

    private MinimapLayout() {
    }

    /**
     * Screen direction (in degrees, 0 = up on screen) at which a cardinal lies,
     * given the player's yaw. Keeps N/S/E/W rotating with the map (section 71).
     */
    public static double cardinalScreenAngle(String cardinal, double playerYaw) {
        double worldAngle = switch (cardinal) {
            case "N" -> 0.0;   // -Z
            case "S" -> 180.0; // +Z
            case "E" -> 90.0;  // +X
            case "W" -> 270.0; // -X
            default -> 0.0;
        };
        // Screen-up points along the player's facing, so subtract the yaw.
        return normalise(worldAngle - playerYaw);
    }

    /** Chunk-boundary offset lines within a display of the given block radius. */
    public static int[] chunkLines(int radiusBlocks) {
        int first = Math.floorDiv(-radiusBlocks, 16) * 16;
        int last = radiusBlocks;
        int count = 0;
        for (int x = first; x <= last; x += 16) {
            count++;
        }
        int[] lines = new int[count];
        int i = 0;
        for (int x = first; x <= last; x += 16) {
            lines[i++] = x;
        }
        return lines;
    }

    /**
     * Scale factor from fullscreen map to minimap: a fixed multiplier, not a
     * second configurable radius (section 69).
     */
    public static double fullscreenScale(double multiplier) {
        return Math.max(1.0, multiplier);
    }

    /**
     * Expansion progress in [0,1] for the open/close animation. {@code elapsed} is
     * seconds since the transition began, {@code opening} picks the direction.
     */
    public static double animationProgress(double elapsed, boolean opening) {
        double t = Math.max(0.0, Math.min(1.0, elapsed / ANIMATION_SECONDS));
        // ease-out for a snappy expansion.
        double eased = 1.0 - Math.pow(1.0 - t, 3.0);
        return opening ? eased : 1.0 - eased;
    }

    /** Current interpolated radius between minimap and fullscreen sizes. */
    public static double interpolatedRadius(double minimapRadius, double fullscreenRadius,
                                            double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return minimapRadius + (fullscreenRadius - minimapRadius) * p;
    }

    /**
     * Squircle containment test in normalised [-1,1] coordinates. A superellipse
     * (rounded square) rather than a circle, matching the frame shape.
     */
    public static boolean insideSquircle(double nx, double nz, double exponent) {
        return Math.pow(Math.abs(nx), exponent) + Math.pow(Math.abs(nz), exponent) <= 1.0;
    }

    private static double normalise(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
