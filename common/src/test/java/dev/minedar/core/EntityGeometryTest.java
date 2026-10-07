package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntityGeometryTest {

    private static EntityGeometry.Box box(double x0, double y0, double z0,
                                          double x1, double y1, double z1, int rgb) {
        return new EntityGeometry.Box(x0, y0, z0, x1, y1, z1, rgb);
    }

    @Test
    void limbsBecomeMultiplePointsNotASingleCentre() {
        // A tall thin leg: geometry along Y, so several Y samples must appear.
        List<WorldSampler.SampledEntity> pts =
                EntityGeometry.sample(List.of(box(-0.1, 0, -0.1, 0.1, 0.5, 0.1, 0xAABBCC)), -1);
        assertTrue(pts.size() > 1, "a leg is real geometry, not one hitbox point");
        long distinctY = pts.stream().map(p -> Math.round(p.y * 100)).distinct().count();
        assertTrue(distinctY > 1, "points must be spread along the limb");
    }

    @Test
    void pointsStayInsideTheBox() {
        EntityGeometry.Box b = box(1, 2, 3, 4, 6, 5, 0x112233);
        for (WorldSampler.SampledEntity p : EntityGeometry.sample(List.of(b), -1)) {
            assertTrue(p.x >= 1 - 1e-9 && p.x <= 4 + 1e-9);
            assertTrue(p.y >= 2 - 1e-9 && p.y <= 6 + 1e-9);
            assertTrue(p.z >= 3 - 1e-9 && p.z <= 5 + 1e-9);
        }
    }

    @Test
    void tinyPartsCollapseToASinglePoint() {
        List<WorldSampler.SampledEntity> pts =
                EntityGeometry.sample(List.of(box(0, 0, 0, 0.01, 0.01, 0.01, 0xFF0000)), -1);
        assertEquals(1, pts.size());
    }

    @Test
    void fallbackColourUsedWhenPartHasNone() {
        List<WorldSampler.SampledEntity> pts =
                EntityGeometry.sample(List.of(box(0, 0, 0, 0.1, 0.5, 0.1, 0)), 0x00FF00);
        assertTrue(pts.stream().allMatch(p -> p.rgb == 0x00FF00));
    }

    @Test
    void emptyModelProducesNoDots() {
        assertTrue(EntityGeometry.sample(List.of(), 0xFFFFFF).isEmpty());
        assertTrue(EntityGeometry.sample(null, 0xFFFFFF).isEmpty());
    }

    @Test
    void rayBoxTestAcceptsHitsAndRejectsMisses() {
        Ray hit = new Ray(0, 0, -5, 0, 0, 1);
        assertTrue(EntityGeometry.maybeIntersects(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5, hit, 20));
        Ray miss = new Ray(10, 0, -5, 0, 0, 1);
        assertFalse(EntityGeometry.maybeIntersects(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5, miss, 20));
    }

    @Test
    void rayBoxTestRejectsBoxesBehindTheScanner() {
        Ray ray = new Ray(0, 0, 0, 0, 0, 1);
        assertFalse(EntityGeometry.maybeIntersects(-0.5, -0.5, -5.5, 0.5, 0.5, -4.5, ray, 20));
    }

    @Test
    void rayBoxTestRespectsMaxDistance() {
        Ray ray = new Ray(0, 0, 0, 0, 0, 1);
        assertFalse(EntityGeometry.maybeIntersects(99, -0.5, -0.5, 100, 0.5, 0.5, ray, 20));
    }
}
