package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PixelSurfaceTest {

    /** 2x2: opaque red, transparent, opaque green, semi-transparent blue. */
    private static PixelSurface fixture() {
        int[] px = {
                0xFFFF0000, 0x00123456,
                0xFF00FF00, 0x800000FF,
        };
        return new PixelSurface(2, 2, px);
    }

    @Test
    void opaquePixelsAreSampledAndTransparentOnesAreNot() {
        List<int[]> s = fixture().samples(128);
        // red, green, blue(semi 0x80 >= 128); the fully transparent one is skipped
        assertEquals(3, s.size());
        for (int[] p : s) {
            assertTrue(PixelSurface.alphaOf(fixture().argbAt(p[0], p[1])) >= 128);
        }
    }

    @Test
    void thresholdExcludesSemiTransparentPixelsWhenRaised() {
        List<int[]> s = fixture().samples(200);
        assertEquals(2, s.size(), "0x80 alpha must fall below a 200 threshold");
    }

    @Test
    void rgbDropsAlpha() {
        assertEquals(0x123456, PixelSurface.rgbOf(0xFF123456));
    }

    @Test
    void rejectsUndersizedBuffers() {
        assertThrows(IllegalArgumentException.class, () -> new PixelSurface(2, 2, new int[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> new PixelSurface(0, 4, new int[0]));
    }
}
