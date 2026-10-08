package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScannerControllerTest {

    @Test
    void startsOffAndUnequipped() {
        ScannerController c = new ScannerController();
        assertFalse(c.isLidarOn());
        assertFalse(c.isScannerEquipped());
        assertEquals(ScannerController.Crosshair.NORMAL, c.crosshair());
    }

    @Test
    void lidarOnWithoutScannerHidesCrosshair() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        assertEquals(ScannerController.Crosshair.NONE, c.crosshair());
    }

    @Test
    void lidarOnWithScannerShowsDotCrosshair() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        assertEquals(ScannerController.Crosshair.DOT, c.crosshair());
    }

    @Test
    void equipResetsRadiusAndCancelsScan() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        c.setIncreaseRadiusHeld(true);
        c.tick();
        c.tick();
        assertTrue(c.range().radius() > ScanRange.DEFAULT_RADIUS);

        c.equip(); // re-equip must cancel and reset
        assertEquals(ScanRange.DEFAULT_RADIUS, c.range().radius(), 1e-9);
        assertFalse(c.isScanning());
    }

    @Test
    void unequipCancelsActiveScan() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        c.tick();
        assertTrue(c.isScanning());
        c.unequip();
        assertFalse(c.isScanning());
    }

    @Test
    void scanningOnlyHappensWhenEquippedAndLidarOn() {
        ScannerController c = new ScannerController();
        c.equip();
        c.startContinuousScan(); // lidar off -> ignored
        c.tick();
        assertFalse(c.isScanning());

        c.enableLidar();
        c.startContinuousScan();
        c.tick();
        assertTrue(c.isScanning());
        assertEquals(ScannerController.ScanMode.CONTINUOUS, c.mode());
    }

    @Test
    void disabledLidarNeverGeneratesScanData() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        c.tick();
        c.disableLidar(true);
        c.tick();
        assertFalse(c.isScanning());
    }

    @Test
    void flushHappensBeforeLidarTurnsOff() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        boolean flushed = c.disableLidar(true);
        assertTrue(flushed);
        assertEquals(1, c.flushCount());
        assertFalse(c.isLidarOn());
    }

    @Test
    void stoppingWithoutPendingDataDoesNotFlush() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        assertFalse(c.disableLidar(false));
        assertEquals(0, c.flushCount());
    }

    @Test
    void burstScanIsOneShot() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.requestBurstScan();
        c.tick();
        assertTrue(c.isScanning());
        assertEquals(ScannerController.ScanMode.BURST, c.mode());
        assertNotNull(c.getBatch(50));
        // No continuous scan: after the burst is consumed the controller idles.
        c.tick();
        assertEquals(ScannerController.ScanMode.IDLE, c.mode());
    }

    @Test
    void radiusKeysChangeRadiusLive() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        double before = c.range().radius();
        c.setIncreaseRadiusHeld(true);
        c.tick();
        c.setIncreaseRadiusHeld(false);
        assertTrue(c.range().radius() > before);

        c.setDecreaseRadiusHeld(true);
        c.tick();
        c.setDecreaseRadiusHeld(false);
        assertEquals(before, c.range().radius(), 1e-9);
    }

    @Test
    void radiusIsClampedToLimits() {
        ScanRange r = new ScanRange();
        for (int i = 0; i < 1000; i++) {
            r.increase(ScanRange.RADIUS_STEP);
        }
        assertEquals(ScanRange.MAX_RADIUS, r.radius(), 1e-9);
        for (int i = 0; i < 1000; i++) {
            r.decrease(ScanRange.RADIUS_STEP);
        }
        assertEquals(ScanRange.MIN_RADIUS, r.radius(), 1e-9);
    }

    @Test
    void largerRadiusMeansSparserDensity() {
        ScanRange r = new ScanRange();
        r.setRadius(ScanRange.MIN_RADIUS);
        double dense = r.pointDensityFactor();
        r.setRadius(ScanRange.MAX_RADIUS);
        double sparse = r.pointDensityFactor();
        assertTrue(sparse < dense);
    }
}
