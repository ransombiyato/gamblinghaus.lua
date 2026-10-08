package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the scanner/LiDAR state machine independently of any Minecraft classes
 * (sections 4-5, 7, 74). The loader adapter drives it from input events and
 * reads its state to decide what to render. Keeping it pure means the ordering
 * rules that matter for correctness are directly testable.
 *
 * <p>Key invariant (section 5): pending scan data is always flushed BEFORE
 * LiDAR rendering is disabled, never the other way around.
 */
public final class ScannerController {

    private boolean lidarOn;
    private boolean scannerEquipped;
    private boolean increaseRadiusHeld;
    private boolean decreaseRadiusHeld;
    private boolean zooming;

    private final ScanRange range = new ScanRange();
    private int flushCount;

    // Scanning state
    private ScanMode mode = ScanMode.IDLE;
    private List<double[]> currentPattern;
    private int patternIndex;
    private double lastRadius;
    private double lastDensityFactor;

    public enum ScanMode { IDLE, CONTINUOUS, BURST }
package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the scanner/LiDAR state machine independently of any Minecraft classes
 * (sections 4-5, 7, 74). The loader adapter drives it from input events and
 * reads its state to decide what to render. Keeping it pure means the ordering
 * rules that matter for correctness are directly testable.
 *
 * <p>Key invariant (section 5): pending scan data is always flushed BEFORE
 * LiDAR rendering is disabled, never the other way around.
 */
public final class ScannerController {

    private boolean lidarOn;
    private boolean scannerEquipped;
public boolean isLidarOn() {
        return lidarOn;
    }

    public boolean isScannerEquipped() {
        return scannerEquipped;
    }

    public ScanRange range() {
        return range;
    }

    /** Number of flushes requested, used by tests and the adapter. */
    public int flushCount() {
        return flushCount;
    }

    /**
     * Enables LiDAR mode. Flush-on-disable is handled in {@link #disableLidar},
     * so enabling is a plain transition.
     */
    public void enableLidar() {
        lidarOn = true;
    }

    /**
     * Disables LiDAR: first flush pending scan data (when requested), then stop
     * rendering. Returns whether a flush was recorded, which the caller uses to
     * perform the actual disk write.
     */
    public boolean disableLidar(boolean flushPending) {
        if (flushPending) {
            flushCount++;
        }
        lidarOn = false;
        continuousScanning = false;
        burstRequested = false;
        mode = ScanMode.IDLE;
        currentPattern = null;
        patternIndex = 0;
        return flushPending;
    }

    public void toggleLidar(boolean flushPending) {
        if (lidarOn) {
            disableLidar(flushPending);
        } else {
            enableLidar();
        }
    }

    /** Equipping resets radius and cancels any active scan (section 5). */
    public void equip() {
        scannerEquipped = true;
        cancelScan();
        range.reset();
    }

    /** Unequipping cancels any active scan (section 5). */
    public void unequip() {
        scannerEquipped = false;
        cancelScan();
    }

    public void cancelScan() {
        mode = ScanMode.IDLE;
        currentPattern = null;
        patternIndex = 0;
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

    /** Called each client tick to update radius and determine scanning mode. */
    public void tick() {
        // Update radius from held keys
        if (increaseRadiusHeld) {
            range.increase(ScanRange.RADIUS_STEP);
        }
        if (decreaseRadiusHeld) {
            range.decrease(ScanRange.RADIUS_STEP);
        }

        // Determine desired scanning mode based on input
        ScanMode desiredMode = ScanMode.IDLE;
        if (lidarOn && scannerEquipped) {
            if (continuousScanning) {
                desiredMode = ScanMode.CONTINUOUS;
            } else if (burstRequested) {
                desiredMode = ScanMode.BURST;
            }
        }

        // Consume burst request so it's a one-shot
        if (burstRequested) {
            burstRequested = false;
        }

        // If mode changed, start new scan pattern
        if (desiredMode != mode) {
            if (desiredMode != ScanMode.IDLE) {
                startScan(desiredMode);
            } else {
                stopScan();
            }
        } else if (mode != ScanMode.IDLE && radiusChanged()) {
            // Radius changed during scan: restart pattern to adapt
            startScan(mode);
        }

        mode = desiredMode;
    }

    private boolean radiusChanged() {
        double densityFactor = range.pointDensityFactor();
        if (range.radius() != lastRadius || densityFactor != lastDensityFactor) {
            lastRadius = range.radius();
public ScanMode mode() {
        return mode;
    }
            lastDensityFactor = densityFactor;
            return true;
        }
        return false;
    }
/** Crosshair state (section 4): none when LiDAR on without scanner, dot when held. */
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

    /** Returns a defensive copy of everything a test cares about. */
    public List<Object> snapshot() {
        List<Object> s = new ArrayList<>();
        s.add(lidarOn);
        s.add(scannerEquipped);
        s.add(mode);
        s.add(range.radius());
        return s;
    }

    private void startScan(ScanMode mode) {
        currentPattern = null;
        patternIndex = 0;
        double densityFactor = range.pointDensityFactor();
        // Generate the full cone pattern for the current radius/density.
        currentPattern = ScanPatternGenerator.directions(densityFactor, Integer.MAX_VALUE);
    }

    private void stopScan() {
        mode = ScanMode.IDLE;
        currentPattern = null;
        patternIndex = 0;
    }

    /** Returns true if a scan is in progress (mode not IDLE). */
    public boolean isScanning() {
        return mode != ScanMode.IDLE;
    }

    /**
     * Returns a batch of ray directions to cast this tick. Returns null if
     * scan is complete (for burst mode) or no scan active.
     */
    public List<double[]> getBatch(int batchSize) {
        if (mode == ScanMode.IDLE || currentPattern == null) {
            return null;
        }
        int remaining = currentPattern.size() - patternIndex;
        if (remaining <= 0) {
            // Pattern exhausted
            if (mode == ScanMode.CONTINUOUS) {
                // Continuous: wrap around and continue
                patternIndex = 0;
                remaining = currentPattern.size();
            } else {
                // Burst complete
                stopScan();
                return null;
            }
        }
        int take = Math.min(batchSize, remaining);
        List<double[]> batch = new ArrayList<>(currentPattern.subList(patternIndex, patternIndex + take));
        patternIndex += take;
        return batch;
    }
    }

    // Input state flags (set by applyHeldState)
    private boolean continuousScanning;
    private boolean burstRequested;
    private boolean continuousScanning;
    private boolean burstScanRequested;
    private boolean zooming;
    private boolean increaseRadiusHeld;
    private boolean decreaseRadiusHeld;

    private final ScanRange range = new ScanRange();
    private int flushCount;
    private int scanTickCount;

    public boolean isLidarOn() {
        return lidarOn;
    }

    public boolean isScannerEquipped() {
        return scannerEquipped;
    }

    public boolean isContinuousScanning() {
        return continuousScanning;
    }

    public boolean isZooming() {
        return zooming;
    }

    public ScanRange range() {
        return range;
    }

    /** Number of flushes requested, used by tests and the adapter. */
    public int flushCount() {
        return flushCount;
    }

    public int scanTickCount() {
        return scanTickCount;
    }

    /**
     * Enables LiDAR mode. Flush-on-disable is handled in {@link #disableLidar},
     * so enabling is a plain transition.
     */
    public void enableLidar() {
        lidarOn = true;
    }

    /**
     * Disables LiDAR: first flush pending scan data (when requested), then stop
     * rendering. Returns whether a flush was recorded, which the caller uses to
     * perform the actual disk write.
     */
    public boolean disableLidar(boolean flushPending) {
        if (flushPending) {
            flushCount++;
        }
        lidarOn = false;
        continuousScanning = false;
        burstScanRequested = false;
        return flushPending;
    }

    public void toggleLidar(boolean flushPending) {
        if (lidarOn) {
            disableLidar(flushPending);
        } else {
            enableLidar();
        }
    }

    /** Equipping resets radius and cancels any active scan (section 5). */
    public void equip() {
        scannerEquipped = true;
        cancelScan();
        range.reset();
    }

    /** Unequipping cancels any active scan (section 5). */
    public void unequip() {
        scannerEquipped = false;
        cancelScan();
    }

    public void cancelScan() {
        continuousScanning = false;
        burstScanRequested = false;
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
            burstScanRequested = true;
        }
    }

    /** Consumes the one-shot burst flag; returns true exactly once per request. */
    public boolean consumeBurstRequest() {
        boolean b = burstScanRequested;
        burstScanRequested = false;
        return b;
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

    /**
     * Advances one client tick: applies live radius changes and reports how many
     * scan rays should be cast this tick. Radius changes only apply while a scan
     * is active, matching GMod's live feel.
     */
    public int tick() {
        if (increaseRadiusHeld) {
            range.increase(ScanRange.RADIUS_STEP);
        }
        if (decreaseRadiusHeld) {
            range.decrease(ScanRange.RADIUS_STEP);
        }
        int rays = 0;
        if (lidarOn && scannerEquipped && continuousScanning) {
            rays = 1;
        }
        if (lidarOn && scannerEquipped && consumeBurstRequest()) {
            rays += burstRayCount();
        }
        scanTickCount += rays;
        return rays;
    }

    private int burstRayCount() {
        return 8;
    }

    /** Convenience for adapters: the pattern directions to cast this tick. */
    public List<double[]> directionsFor(int rays) {
        return ScanPatternGenerator.directions(range.pointDensityFactor(), Math.max(1, rays * 64));
    }

    /** Crosshair state (section 4): none when LiDAR on without scanner, dot when held. */
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

    /** Returns a defensive copy of everything a test cares about. */
    public List<Object> snapshot() {
        List<Object> s = new ArrayList<>();
        s.add(lidarOn);
        s.add(scannerEquipped);
        s.add(continuousScanning);
        s.add(range.radius());
        return s;
    }
}
