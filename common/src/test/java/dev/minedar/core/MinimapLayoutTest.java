package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinimapLayoutTest {

    @Test
    void onlyFourCardinalsExist() {
        assertEquals(4, MinimapLayout.CARDINALS.length);
        assertEquals("N", MinimapLayout.CARDINALS[0]);
        // No diagonal labels such as NE/NW/SE/SW.
        for (String c : MinimapLayout.CARDINALS) {
            assertTrue(c.length() == 1, "only N/S/E/W are allowed");
        }
    }

    @Test
    void cardinalsRotateWithPlayerYaw() {
        // Facing north: N is straight up on screen (angle 0).
        assertEquals(0.0, MinimapLayout.cardinalScreenAngle("N", 0.0), 1e-9);
        // Facing east (yaw 90): N moves to the left (270).
        assertEquals(270.0, MinimapLayout.cardinalScreenAngle("N", 90.0), 1e-9);
    }

    @Test
    void chunkLinesFallOnChunkBoundaries() {
        int[] lines = MinimapLayout.chunkLines(128);
        assertTrue(lines.length > 1);
        for (int line : lines) {
            assertEquals(0, Math.floorMod(line, 16), "lines must sit on chunk boundaries");
        }
        assertEquals(0, lines[0] <= 0 ? 0 : 1);
    }

    @Test
    void squircleIsRoundedNotCircular() {
        // A point outside the unit circle but inside the squircle is included.
        double nx = 0.8;
        double nz = 0.8;
        assertTrue(Math.hypot(nx, nz) > 1.0, "test point lies outside a circle");
        assertTrue(MinimapLayout.insideSquircle(nx, nz, 4.0),
                "corner region belongs to a rounded square, not a circle");
        assertFalse(MinimapLayout.insideSquircle(1.2, 0.0, 4.0));
        assertTrue(MinimapLayout.insideSquircle(0.0, 0.0, 4.0));
    }

    @Test
    void fullscreenUsesAFixedMultiplier() {
        assertEquals(4.0, MinimapLayout.fullscreenScale(4.0), 1e-9);
        assertEquals(1.0, MinimapLayout.fullscreenScale(0.5), 1e-9,
                "multiplier is never allowed below 1");
    }

    @Test
    void animationIsFastAndEased() {
        assertTrue(MinimapLayout.ANIMATION_SECONDS <= 0.2, "expansion must be very fast");
        assertEquals(0.0, MinimapLayout.animationProgress(0.0, true), 1e-9);
        assertEquals(1.0, MinimapLayout.animationProgress(10.0, true), 1e-9);
        double mid = MinimapLayout.animationProgress(MinimapLayout.ANIMATION_SECONDS / 2, true);
        assertTrue(mid > 0.4 && mid < 1.0);
    }

    @Test
    void closingAnimationRunsInReverse() {
        assertEquals(1.0, MinimapLayout.animationProgress(0.0, false), 1e-9);
        assertEquals(0.0, MinimapLayout.animationProgress(10.0, false), 1e-9);
    }

    @Test
    void radiusInterpolatesBetweenSizes() {
        assertEquals(60.0, MinimapLayout.interpolatedRadius(60, 400, 0.0), 1e-9);
        assertEquals(400.0, MinimapLayout.interpolatedRadius(60, 400, 1.0), 1e-9);
        assertEquals(230.0, MinimapLayout.interpolatedRadius(60, 400, 0.5), 1e-9);
    }
}
