package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialTransmissionTest {

    private final MaterialRules rules = new MaterialRules();
    private final SignalPropagator propagator = new SignalPropagator(rules);

    @Test
    void glassRetainsThirtyToFortyFivePercent() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("glass", 0, 0, 0, Colour.WHITE)), result);
        assertEquals(1, result.hitCount());
        double retained = PointIntensity.of(result);
        assertBetween(0.30, 0.45, retained);
        assertBetween(0.55, 0.70, result.transmitted());
    }

    @Test
    void stainedGlassKeepsTheBlocksOwnColour() {
        ScanResult stained = new ScanResult();
        // Red stained glass: the adapter passes the block's actual colour as base.
        propagator.propagate(List.of(
                new SignalPropagator.Layer("stained_glass", 0, 0, 0, 0xFF0000)), stained);
        assertEquals(1, stained.hitCount());
        // Transmission behaviour matches plain glass...
        assertBetween(0.30, 0.45, PointIntensity.of(stained));
        // ...but the retained dot keeps the glass's own colour, not white.
        assertEquals(0xFF0000, stained.hits().get(0).rgb);
    }

    @Test
    void multipleGlassLayersCompound() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(
                new SignalPropagator.Layer("glass", 0, 0, 0, Colour.WHITE),
                new SignalPropagator.Layer("glass", 0, 0, 1, Colour.WHITE),
                new SignalPropagator.Layer("glass", 0, 0, 2, Colour.WHITE)), result);
        assertEquals(3, result.hitCount());
        // 0.62^3 ~= 0.238 surviving signal after three panes.
        assertBetween(0.20, 0.28, result.transmitted());
    }

    @Test
    void powderSnowConsumesEverything() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(
                new SignalPropagator.Layer("powder_snow", 0, 0, 0, Colour.WHITE),
                new SignalPropagator.Layer("stone", 0, 0, 1, Colour.WHITE)), result);
        assertEquals(0.0, result.transmitted(), 1e-9);
        // Only the powder snow retains dots; the ray stops before the stone.
        assertEquals(1, result.hitCount());
    }

    @Test
    void leavesBlockMostButPassSome() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("leaves", 0, 0, 0, Colour.WHITE)), result);
        assertEquals(1, result.hitCount());
        assertBetween(0.10, 0.20, result.transmitted());
        assertBetween(0.80, 0.90, PointIntensity.of(result));
    }

    @Test
    void snowPassesRoughlyTwoThirds() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("snow", 0, 0, 0, Colour.WHITE)), result);
        assertBetween(0.60, 0.75, result.transmitted());
        assertBetween(0.25, 0.40, PointIntensity.of(result));
    }

    @Test
    void cobwebPassesAboutHalf() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("cobweb", 0, 0, 0, Colour.WHITE)), result);
        assertBetween(0.40, 0.50, result.transmitted());
        assertBetween(0.50, 0.60, PointIntensity.of(result));
    }

    @Test
    void weatherConsumesEverything() {
        ScanResult result = new ScanResult();
        propagator.propagate(List.of(
                new SignalPropagator.Layer("weather", 0, 0, 0, Colour.WHITE),
                new SignalPropagator.Layer("stone", 0, 0, 1, Colour.WHITE)), result);
        assertEquals(0.0, result.transmitted(), 1e-9);
        assertEquals(1, result.hitCount());
    }

    @Test
    void explosionPassesMoreThanGlass() {
        MaterialProfile explosion = rules.resolve("explosion");
        MaterialProfile glass = rules.resolve("glass");
        org.junit.jupiter.api.Assertions.assertTrue(explosion.passThrough() > glass.passThrough());
    }

    @Test
    void unknownMaterialFallsBackToSolid() {
        MaterialProfile profile = rules.resolve("some_modded_block_xyz");
        assertEquals(0.0, profile.passThrough(), 1e-9);
        assertEquals(1.0, profile.retain(), 1e-9);
    }

    @Test
    void registryCanBeExtendedForModdedFluids() {
        rules.register("mymod:acid", MaterialProfile.LAVA);
        assertEquals(MaterialProfile.LAVA, rules.resolve("mymod:acid"));
    }

    @Test
    void waterAndLavaHaveTheirOwnColours() {
        ScanResult water = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("water", 0, 0, 0, Colour.WHITE)), water);
        ScanResult lava = new ScanResult();
        propagator.propagate(List.of(new SignalPropagator.Layer("lava", 0, 0, 0, Colour.WHITE)), lava);
        org.junit.jupiter.api.Assertions.assertTrue(Colour.blue(water.hits().get(0).rgb)
                > Colour.red(water.hits().get(0).rgb));
        org.junit.jupiter.api.Assertions.assertTrue(Colour.red(lava.hits().get(0).rgb)
                > Colour.blue(lava.hits().get(0).rgb));
    }

    private static void assertBetween(double lo, double hi, double actual) {
        org.junit.jupiter.api.Assertions.assertTrue(actual >= lo && actual <= hi,
                "expected " + lo + " <= " + actual + " <= " + hi);
    }
}
