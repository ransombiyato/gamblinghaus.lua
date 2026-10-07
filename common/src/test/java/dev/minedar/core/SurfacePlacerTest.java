package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SurfacePlacerTest {

    private static PixelSurface onePixel(int argb) {
        return new PixelSurface(1, 1, new int[] {argb});
    }

    @Test
    void singlePixelSitsAtSurfaceCentre() {
        // 1x1 surface, 1m x 1m, at origin on the XY plane, normal +Z.
        SurfacePlacer placer = SurfacePlacer.builder()
                .origin(10, 64, 20)
                .right(1, 0, 0)
                .up(0, 1, 0)
                .size(1, 1)
                .inset(0)
                .build();
        List<SurfacePlacer.Placed> pts = placer.place(onePixel(0xFFFFFFFF), 128, 1);
        assertEquals(1, pts.size());
        SurfacePlacer.Placed p = pts.get(0);
        assertEquals(10.5, p.x(), 1e-6);
        assertEquals(64.5, p.y(), 1e-6);
        assertEquals(20.0, p.z(), 1e-6);
    }

    @Test
    void topLeftPixelMapsToHighUAndHighV() {
        int[] px = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        SurfacePlacer placer = SurfacePlacer.builder()
                .origin(0, 0, 0).right(1, 0, 0).up(0, 1, 0).size(2, 2).inset(0).build();
        List<SurfacePlacer.Placed> pts = placer.place(new PixelSurface(2, 2, px), 128, 1);
        assertEquals(4, pts.size());
        // Pixel (0,0) is the top-left: small u, large y offset (v grows downward).
        SurfacePlacer.Placed first = pts.get(0);
        assertEquals(0.5, first.x(), 1e-6);
        assertEquals(1.5, first.y(), 1e-6);
    }

    @Test
    void insetPushesDotsAlongTheNormal() {
        SurfacePlacer flat = SurfacePlacer.builder()
                .origin(0, 0, 0).right(1, 0, 0).up(0, 1, 0).size(1, 1).inset(0).build();
        SurfacePlacer proud = SurfacePlacer.builder()
                .origin(0, 0, 0).right(1, 0, 0).up(0, 1, 0).size(1, 1).inset(0.25).build();
        double z0 = flat.place(onePixel(0xFFFFFFFF), 128, 1).get(0).z();
        double z1 = proud.place(onePixel(0xFFFFFFFF), 128, 1).get(0).z();
        assertEquals(0.0, z0, 1e-6);
        assertEquals(0.25, z1, 1e-6);
    }

    @Test
    void stepReducesPointDensity() {
        int[] px = new int[8 * 8];
        java.util.Arrays.fill(px, 0xFFFFFFFF);
        PixelSurface surface = new PixelSurface(8, 8, px);
        SurfacePlacer placer = SurfacePlacer.builder()
                .origin(0, 0, 0).right(1, 0, 0).up(0, 1, 0).size(1, 1).build();
        assertEquals(64, placer.place(surface, 128, 1).size());
        assertEquals(16, placer.place(surface, 128, 2).size());
    }

    @Test
    void axesAreNormalisedSoRawVectorsWork() {
        SurfacePlacer placer = SurfacePlacer.builder()
                .origin(0, 0, 0)
                .right(100, 0, 0)
                .up(0, 50, 0)
                .size(1, 1)
                .inset(0)
                .build();
        SurfacePlacer.Placed p = placer.place(onePixel(0xFFFFFFFF), 128, 1).get(0);
        assertEquals(0.5, p.x(), 1e-6);
        assertEquals(0.5, p.y(), 1e-6);
        assertTrue(Math.abs(p.z()) < 1e-6);
    }
}
