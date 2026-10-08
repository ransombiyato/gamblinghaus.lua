package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the scanner/LiDAR state machine independently of any Minecraft classes.
 * The loader adapter drives it from input events and reads its state to decide
 * what to render. Keeping it pure means the ordering rules that matter for
 * correctness are directly testable.
 *
 * <p>Key invariant: pending scan data is always flushed BEFORE LiDAR rendering
 * is disabled, never the other way around.
 */
public final class ScannerController {

    private boolean lidarOn;
    private boolean scannerEquipped;
    private boolean increaseRadiusHeld;
    private boolean decreaseRadiusHeld;
    private boolean zooming;

    private final ScanRange range = new ScanRange();
    private int flushCount;

    // Input state flags set by the adapter.
    private boolean continuousScanning;
    private boolean burstRequested;

    // Scanning state.
    private ScanMode mode = ScanMode.IDLE;
    private List<double[]> currentPattern;
    private int patternIndex;
    private double lastRadius = -1;
    private double lastDensityFactor = -1;

    public enum ScanMode { IDLE, CONTINUOUS, BURST }

    public boolean isLidarOn() {
        return lidarOn;
    }

    public boolean isScannerEquipped() {
        return scannerEquipped;
    }

    public boolean isZooming() {
        return zooming;
    }

    public ScanRange range() {
        return range;
    }

    public int flushCount() {
        return flushCount;
    }

    public ScanMode mode() {
        return mode;
    }

    public void enableLidar() {
        lidarOn = true;
    }

    public boolean disableLidar(boolean flushPending) {
        if (flushPending) {
            flushCount++;
        }
        lidarOn = false;
        continuousScanning = false;
        burstRequested = false;
        stopScan();
        return flushPending;
    }

    public void toggleLidar(boolean flushPending) {
        if (lidarOn) {
            disableLidar(flushPending);
        } else {
            enableLidar();
        }
    }

    public void equip() {
        scannerEquipped = true;
        cancelScan();
        range.reset();
    }

    public void unequip() {
        scannerEquipped = false;
        cancelScan();
    }

    public void cancelScan() {
        continuousScanning = false;
        burstRequested = false;
        stopScan();
    }

    public void startContinuousScan() {
        if (lidarOn && scannerEquipped) {
            continuousScanning = true;
        }
    }

    public void stopContinuousScan() {
        continuousScanning = false;
    }

    public void requestBurstScan() {
        if (lidarOn && scannerEquipped) {
            burstRequested = true;
        }
    }

    public void setZooming(boolean zooming) {
        this.zooming = zooming;
    }

    public void setIncreaseRadiusHeld(boolean held) {
        this.increaseRadiusHeld = held;
    }

    public void setDecreaseRadiusHeld(boolean held) {
        this.decreaseRadiusHeld = held;
    }

    /** Advances one client tick and selects the scanning mode. */
    public void tick() {
        if (increaseRadiusHeld) {
            range.increase(ScanRange.RADIUS_STEP);
        }
        if (decreaseRadiusHeld) {
            range.decrease(ScanRange.RADIUS_STEP);
        }

        ScanMode desired = ScanMode.IDLE;
        if (lidarOn && scannerEquipped) {
            if (continuousScanning) {
                desired = ScanMode.CONTINUOUS;
            } else if (burstRequested) {
                desired = ScanMode.BURST;
            }
        }
        burstRequested = false;

        if (desired != mode) {
            if (desired == ScanMode.IDLE) {
                stopScan();
            } else {
                startScan();
            }
        } else if (mode != ScanMode.IDLE && radiusChanged()) {
            startScan();
        }
        mode = desired;
    }

    private boolean radiusChanged() {
        double densityFactor = range.pointDensityFactor();
        if (range.radius() != lastRadius || densityFactor != lastDensityFactor) {
            lastRadius = range.radius();
            lastDensityFactor = densityFactor;
            return true;
        }
        return false;
    }

    private void startScan() {
        patternIndex = 0;
        lastRadius = range.radius();
        lastDensityFactor = range.pointDensityFactor();
        currentPattern = ScanPatternGenerator.directions(lastDensityFactor, Integer.MAX_VALUE);
    }

    private void stopScan() {
        mode = ScanMode.IDLE;
        currentPattern = null;
        patternIndex = 0;
    }

    public boolean isScanning() {
        return mode != ScanMode.IDLE && currentPattern != null;
    }

    /**
     * Returns a batch of ray directions to cast this tick, or null when no scan
     * is active / a burst has completed.
     */
    public List<double[]> getBatch(int batchSize) {
        if (mode == ScanMode.IDLE || currentPattern == null || currentPattern.isEmpty()) {
            return null;
        }
        int remaining = currentPattern.size() - patternIndex;
        if (remaining <= 0) {
            if (mode == ScanMode.CONTINUOUS) {
                patternIndex = 0;
                remaining = currentPattern.size();
            } else {
                stopScan();
                return null;
            }
        }
        int take = Math.min(batchSize, remaining);
        List<double[]> batch = new ArrayList<>(currentPattern.subList(patternIndex, patternIndex + take));
        patternIndex += take;
        return batch;
    }

    public Crosshair crosshair() {
        if (lidarOn) {
            return scannerEquipped ? Crosshair.DOT : Crosshair.NONE;
        }
        return Crosshair.NORMAL;
    }

    public enum Crosshair {
        NORMAL,
        NONE,
        DOT
    }

    public List<Object> snapshot() {
        List<Object> s = new ArrayList<>();
        s.add(lidarOn);
        s.add(scannerEquipped);
        s.add(mode);
        s.add(range.radius());
        return s;
    }
}
