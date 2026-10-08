package dev.minedar.core;

import java.util.Map;

/**
 * Builds the camera-facing dot quads for the LiDAR point cloud (sections 8,
 * 74-75). This is the loader-independent half of point rendering: it walks the
 * spatial store, culls sections beyond the scan distance, and emits each visible
 * dot's four corners relative to the camera. The loader adapter supplies the
 * model-view matrix and the vertex buffer, so the geometry maths stays pure and
 * unit-testable while no per-point object is ever allocated.
 *
 * <p>Dots are quads in the camera's screen plane rather than point sprites: the
 * screen basis comes from the camera yaw, which keeps every dot upright and
 * equally sized regardless of where it sits in the view (section 8).
 */
public final class PointCloudRenderer {

    /**
     * Screen-space half-extent of a dot, expressed as world units at one block
     * from the camera. Scaling by the dot's distance keeps the projected size
     * constant, so a dot is equally sized wherever it sits in the view and a
     * distant scan still reads as a cloud instead of vanishing (section 8).
     */
    public static final float DOT_SIZE = 0.006f;
    /** Floor so a heavily transmitted dot stays faintly visible. */
    public static final float MIN_INTENSITY = 0.15f;

    /**
     * Upper bound on the number of dots emitted in one frame. A historically
     * grown cloud can hold hundreds of thousands of points; emitting every one
     * each frame into a single vertex buffer grows without limit and stalls the
     * render thread. Sampling down to this budget keeps a frame's vertex work
     * bounded regardless of how much history is on disk (sections 74-75).
     */
    public static final int MAX_DOTS_PER_FRAME = 50_000;

    /**
     * Receives one dot in camera-relative coordinates plus the data the loader
     * needs to build its quad: the screen-right unit vector, the dot's half
     * height, its normalised intensity and its colour. Passing these instead of
     * four literal corners lets the loader expand the quad inline, so the core
     * never allocates a per-point object.
     */
    public interface QuadSink {
        void quad(float x, float y, float z,
                  float rightX, float rightZ, float halfY,
                  float intensity, int rgb);
    }

    private PointCloudRenderer() {
    }

    /**
     * True when a section's centre is within {@code maxDistance} of the camera,
     * the cheap distance cull applied before any per-point work (section 74).
     */
    public static boolean sectionVisible(double baseX, double baseY, double baseZ,
                                         double camX, double camY, double camZ,
                                         double maxDistance) {
        double dcx = baseX + PointCloudSection.SIZE * 0.5 - camX;
        double dcy = baseY + PointCloudSection.SIZE * 0.5 - camY;
        double dcz = baseZ + PointCloudSection.SIZE * 0.5 - camZ;
        return dcx * dcx + dcy * dcy + dcz * dcz <= maxDistance * maxDistance;
    }

    /** Unit screen-right vector from the camera yaw, normalised for safety. */
    public static double[] screenRight(double camRightX, double camRightZ) {
        double len = Math.sqrt(camRightX * camRightX + camRightZ * camRightZ);
        if (len < 1e-6) {
            return new double[] {1.0, 0.0};
        }
        return new double[] {camRightX / len, camRightZ / len};
    }

    /** Intensity in [MIN_INTENSITY, 1]; a weak signal renders dimmer (section 8). */
    public static float intensityScale(int intensity) {
        return Math.max(MIN_INTENSITY, (intensity & 0xFF) / 255.0f);
    }

    /**
     * Emits every visible dot as a camera-relative quad. Returns the number of
     * dots emitted, which callers use to skip buffer work when nothing is
     * visible. Only sections the client currently holds are walked.
     */
    public static int forEachVisibleDot(SpatialChunkStore cloud,
                                        double camX, double camY, double camZ,
                                        double maxDistance,
                                        double camRightX, double camRightZ,
                                        QuadSink sink) {
        if (cloud == null || cloud.sectionCount() == 0) {
            return 0;
        }
        double[] right = screenRight(camRightX, camRightZ);
        float rx = (float) right[0];
        float rz = (float) right[1];

        // Cap this frame's work. When the cloud exceeds the budget, stride
        // through the flattened point list so the visible dots are spread evenly
        // rather than drawn from whichever sections happen to come first.
        long total = cloud.pointCount();
        if (total <= 0) {
            return 0;
        }
        long step = Math.max(1, (total + MAX_DOTS_PER_FRAME - 1) / MAX_DOTS_PER_FRAME);

        int emitted = 0;
        long index = 0;
        for (Map.Entry<Long, PointCloudSection> entry : cloud.view().entrySet()) {
            long key = entry.getKey();
            int sx = signExtend((int) ((key >> 42) & 0x3FFFFF), 22);
            int sy = signExtend((int) ((key >> 22) & 0xFFFFF), 20);
            int sz = signExtend((int) (key & 0x3FFFFF), 22);

            PointCloudSection section = entry.getValue();
            int n = section.size();

            double baseX = sx << 4;
            double baseY = sy << 4;
            double baseZ = sz << 4;
            if (!sectionVisible(baseX, baseY, baseZ, camX, camY, camZ, maxDistance)) {
                index += n;
                continue;
            }

            long[] raw = section.raw();
            for (int i = 0; i < n; i++, index++) {
                if (index % step != 0) {
                    continue;
                }
                long p = raw[i];
                float wx = (float) (baseX + PointCloudSection.localX(p) + 0.5) - (float) camX;
                float wy = (float) (baseY + PointCloudSection.localY(p) + 0.5) - (float) camY;
                float wz = (float) (baseZ + PointCloudSection.localZ(p) + 0.5) - (float) camZ;

                float intensity = intensityScale(PointCloudSection.intensity(p));
                // Perspective divide keeps the projected dot size constant: the
                // quad's world extent grows with distance so it stays the same
                // number of pixels near and far.
                float dist = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                float h = DOT_SIZE * (0.6f + 0.8f * intensity) * Math.max(1.0f, dist);

                sink.quad(wx, wy, wz, rx, rz, h, intensity, PointCloudSection.rgb(p));
                emitted++;
            }
        }
        return emitted;
    }

    /** Sign-extends a packed section coordinate back to its signed value. */
    static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }
}
