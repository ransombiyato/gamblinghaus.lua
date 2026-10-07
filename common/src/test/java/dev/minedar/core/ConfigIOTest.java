package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfigIOTest {

    @Test
    void roundTripsAllFields() {
        MinedarConfig c = new MinedarConfig();
        c.minimapVisible = false;
        c.minimapRadiusBlocks = 200;
        c.defaultScanRadius = 40;
        c.scanDistance = 96;
        c.autosave = false;
        c.fullscreenRadiusMultiplier = 6;

        MinedarConfig back = ConfigIO.fromJson(ConfigIO.toJson(c));
        assertFalse(back.minimapVisible);
        assertEquals(200, back.minimapRadiusBlocks);
        assertEquals(40, back.defaultScanRadius, 1e-9);
        assertEquals(96, back.scanDistance, 1e-9);
        assertFalse(back.autosave);
        assertEquals(6, back.fullscreenRadiusMultiplier, 1e-9);
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        MinedarConfig c = ConfigIO.fromJson("{ this is not json ");
        assertEquals(128, c.minimapRadiusBlocks);
        assertTrue(c.minimapVisible);
    }

    @Test
    void ignoresUnknownKeys() {
        MinedarConfig c = ConfigIO.fromJson("{\"futureKey\": 123, \"minimapRadiusBlocks\": 64}");
        assertEquals(64, c.minimapRadiusBlocks);
    }

    @Test
    void sanitisesOutOfRangeValuesOnLoad() {
        MinedarConfig c = ConfigIO.fromJson(
                "{\"minimapRadiusBlocks\": 99999, \"defaultScanRadius\": -50, \"scanDistance\": 1}");
        assertEquals(1024, c.minimapRadiusBlocks);
        assertEquals(ScanRange.MIN_RADIUS, c.defaultScanRadius, 1e-9);
        assertEquals(8.0, c.scanDistance, 1e-9);
    }

    @Test
    void emittedJsonIsValidLooking() {
        String json = ConfigIO.toJson(new MinedarConfig());
        assertTrue(json.trim().startsWith("{"));
        assertTrue(json.trim().endsWith("}"));
        assertTrue(json.contains("\"minimapVisible\": true"));
    }
}
