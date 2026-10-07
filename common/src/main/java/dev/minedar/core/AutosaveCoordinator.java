package dev.minedar.core;

/**
 * Decides when pending scan changes should be committed (section 11). Rather
 * than writing every point, it debounces: changes are dirty-marked and flushed
 * once a quiet period or a hard trigger (lidar off, dimension change,
 * disconnect, shutdown) is reached. Flushing is a caller-supplied action so the
 * policy stays pure and testable, and the actual IO stays on the loader side.
 */
public final class AutosaveCoordinator {

    private final int quietTicksBeforeFlush;
    private int dirtyTicks = -1;
    private int flushCount;

    public AutosaveCoordinator(int quietTicksBeforeFlush) {
        this.quietTicksBeforeFlush = Math.max(1, quietTicksBeforeFlush);
    }

    public int flushCount() {
        return flushCount;
    }

    public boolean isDirty() {
        return dirtyTicks >= 0;
    }

    /** Marks that scan data changed this tick. */
    public void markDirty() {
        dirtyTicks = 0;
    }

    /**
     * Advances one tick. Returns true when a debounced flush is due, in which
     * case the caller performs the write and should call {@link #markFlushed}.
     */
    public boolean tick() {
        if (dirtyTicks < 0) {
            return false;
        }
        dirtyTicks++;
        if (dirtyTicks >= quietTicksBeforeFlush) {
            return true;
        }
        return false;
    }

    public void markFlushed() {
        dirtyTicks = -1;
        flushCount++;
    }

    /**
     * Hard flush for lidar-off / dimension-change / disconnect / shutdown.
     * Returns whether a write is required and clears the dirty state.
     */
    public boolean flushNow() {
        if (dirtyTicks >= 0) {
            dirtyTicks = -1;
            flushCount++;
            return true;
        }
        return false;
    }
}
