package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(c.isContinuousScanning());
    }

    @Test
    void unequipCancelsActiveScan() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        c.unequip();
        assertFalse(c.isContinuousScanning());
        assertEquals(0, c.tick());
    }

    @Test
    void scanningOnlyHappensWhenEquippedAndLidarOn() {
        ScannerController c = new ScannerController();
        c.equip();
        c.startContinuousScan(); // lidar off -> ignored
        assertEquals(0, c.tick());

        c.enableLidar();
        c.startContinuousScan();
        assertEquals(1, c.tick());
    }

    @Test
    void disabledLidarNeverGeneratesScanData() {
        ScannerController c = new ScannerController();
        c.enableLidar();
        c.equip();
        c.startContinuousScan();
        c.disableLidar(true);
        assertEquals(0, c.tick());
        assertFalse(c.isContinuousScanning());
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
        assertTrue(c.tick() > 0);
        assertEquals(0, c.tick()); // consumed, no continuous scan active
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
