package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointCloudRendererTest {

    private static final class Capture implements PointCloudRenderer.QuadSink {
        final List<Float> xs = new ArrayList<>();
        final List<Float> ys = new ArrayList<>();
        final List<Float> zs = new ArrayList<>();
        final List<Float> rightsX = new ArrayList<>();
        final List<Float> rightsZ = new ArrayList<>();
        final List<Float> halves = new ArrayList<>();
        final List<Float> intensities = new ArrayList<>();
        final List<Integer> colours = new ArrayList<>();
        int quads;

        @Override
        public void quad(float x, float y, float z,
                         float rightX, float rightZ, float halfY,
                         float intensity, int rgb) {
            xs.add(x); ys.add(y); zs.add(z);
            rightsX.add(rightX); rightsZ.add(rightZ);
            halves.add(halfY); intensities.add(intensity);
            colours.add(rgb);
            quads++;
        }

        // The four corners of dot i, derived the same way the loader derives them.
        float cornerX(int i, int c) {
            float ox = rightsX.get(i) * halves.get(i);
            return xs.get(i) + ((c == 0 || c == 3) ? -ox : ox);
        }

        float cornerY(int i, int c) {
            float h = halves.get(i);
            return ys.get(i) + ((c < 2) ? -h : h);
        }

        float cornerZ(int i, int c) {
            float oz = rightsZ.get(i) * halves.get(i);
            return zs.get(i) + ((c == 0 || c == 3) ? -oz : oz);
        }
    }

    @Test
    void emptyStoreEmitsNothing() {
        SpatialChunkStore store = new SpatialChunkStore();
        Capture cap = new Capture();
        int n = PointCloudRenderer.forEachVisibleDot(store, 0, 0, 0, 64, 1, 0, cap);
        assertEquals(0, n);
        assertEquals(0, cap.quads);
    }

    @Test
    void visiblePointEmitsOneQuadAroundItsCentre() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(3, 4, 5, 0xFF0000, 255);

        Capture cap = new Capture();
        // Camera at the origin, right vector = +X.
        int n = PointCloudRenderer.forEachVisibleDot(store, 0, 0, 0, 64, 1, 0, cap);
        assertEquals(1, n);
        assertEquals(1, cap.quads);
        assertEquals(0xFF0000, cap.colours.get(0));

        // The dot centre is the block centre (3.5, 4.5, 5.5); with the screen
        // right along +X the four corners straddle it by the dot half-extent,
        // which grows with intensity (255 -> scale 1, so 0.6 + 0.8) and with the
        // dot's distance from the camera (here sqrt(3.5^2 + 4.5^2 + 5.5^2)).
        float dist = (float) Math.sqrt(3.5 * 3.5 + 4.5 * 4.5 + 5.5 * 5.5);
        float h = PointCloudRenderer.DOT_SIZE * 1.4f * dist;
        assertEquals(3.5f, cap.xs.get(0), 1e-5f);
        assertEquals(4.5f, cap.ys.get(0), 1e-5f);
        assertEquals(5.5f, cap.zs.get(0), 1e-5f);
        assertEquals(1.0f, cap.rightsX.get(0), 1e-5f);
        assertEquals(0.0f, cap.rightsZ.get(0), 1e-5f);
        assertEquals(h, cap.halves.get(0), 1e-5f);
        assertEquals(3.5f - h, cap.cornerX(0, 0), 1e-5f);
        assertEquals(3.5f + h, cap.cornerX(0, 1), 1e-5f);
        assertEquals(4.5f - h, cap.cornerY(0, 0), 1e-5f);
        assertEquals(4.5f + h, cap.cornerY(0, 3), 1e-5f);
    }

    @Test
    void quadIsUprightAndCentredOnThePoint() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(0, 0, 0, 0x00FF00, 255);

        Capture cap = new Capture();
        PointCloudRenderer.forEachVisibleDot(store, 0, 0, 0, 64, 0.7071, 0.7071, cap);

        float cx = (cap.cornerX(0, 0) + cap.cornerX(0, 2)) / 2.0f;
        float cy = (cap.cornerY(0, 0) + cap.cornerY(0, 2)) / 2.0f;
        float cz = (cap.cornerZ(0, 0) + cap.cornerZ(0, 2)) / 2.0f;
        assertEquals(0.5f, cx, 1e-4f);
        assertEquals(0.5f, cy, 1e-4f);
        assertEquals(0.5f, cz, 1e-4f);
        // Top and bottom edges keep the same y for both corners -> upright quad.
        assertEquals(cap.cornerY(0, 0), cap.cornerY(0, 1), 1e-6f);
        assertEquals(cap.cornerY(0, 2), cap.cornerY(0, 3), 1e-6f);
    }

    @Test
    void frameDotCountIsBoundedByBudget() {
        SpatialChunkStore store = new SpatialChunkStore();
        // Pack many distinct points into one section so the budget must stride.
        int perAxis = 16; // SIZE, so every local slot in a section is filled
        int target = PointCloudRenderer.MAX_DOTS_PER_FRAME * 2;
        int written = 0;
        for (int sy = 0; written < target; sy++) {
            for (int ly = 0; ly < perAxis && written < target; ly++) {
                for (int lz = 0; lz < perAxis && written < target; lz++) {
                    for (int lx = 0; lx < perAxis && written < target; lx++) {
                        store.addBlock((sy * 16) + lx, ly, lz, 0xFFFFFF, 255);
                        written++;
                    }
                }
            }
        }

        Capture cap = new Capture();
        int n = PointCloudRenderer.forEachVisibleDot(store, 0, 0, 0, 100_000, 1, 0, cap);
        assertTrue(n <= PointCloudRenderer.MAX_DOTS_PER_FRAME,
                "emitted " + n + " dots, budget is " + PointCloudRenderer.MAX_DOTS_PER_FRAME);
        assertTrue(n > 0);
        assertEquals(n, cap.quads);
    }

    @Test
    void farSectionsAreCulled() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(0, 0, 0, 0xFFFFFF, 255);          // section (0,0,0)
        store.addBlock(200, 0, 0, 0xFFFFFF, 255);        // section (12,0,0)

        Capture cap = new Capture();
        int n = PointCloudRenderer.forEachVisibleDot(store, 0, 0, 0, 32, 1, 0, cap);
        assertEquals(1, n, "only the near section is within 32 blocks");
    }

    @Test
    void intensityScalesDotHeight() {
        assertEquals(PointCloudRenderer.MIN_INTENSITY, PointCloudRenderer.intensityScale(0), 1e-6f);
        assertEquals(1.0f, PointCloudRenderer.intensityScale(255), 1e-6f);
        assertTrue(PointCloudRenderer.intensityScale(128) < 1.0f);
        assertTrue(PointCloudRenderer.intensityScale(1) >= PointCloudRenderer.MIN_INTENSITY);
    }

    @Test
    void degenerateRightVectorFallsBackToX() {
        double[] r = PointCloudRenderer.screenRight(0, 0);
        assertEquals(1.0, r[0], 1e-9);
        assertEquals(0.0, r[1], 1e-9);
    }

    @Test
    void signedSectionKeysDecodeToNegativeCoordinates() {
        long key = SpatialChunkStore.sectionKey(-2, -3, -4);
        int sx = PointCloudRenderer.signExtend((int) ((key >> 42) & 0x3FFFFF), 22);
        int sy = PointCloudRenderer.signExtend((int) ((key >> 22) & 0xFFFFF), 20);
        int sz = PointCloudRenderer.signExtend((int) (key & 0x3FFFFF), 22);
        assertEquals(-2, sx);
        assertEquals(-3, sy);
        assertEquals(-4, sz);
    }

    @Test
    void sectionVisibleChecksCentreDistance() {
        assertTrue(PointCloudRenderer.sectionVisible(0, 0, 0, 8, 8, 8, 20));
        assertFalse(PointCloudRenderer.sectionVisible(0, 0, 0, 100, 0, 0, 20));
    }
}
