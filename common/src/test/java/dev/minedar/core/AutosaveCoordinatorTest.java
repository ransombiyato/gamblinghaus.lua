package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutosaveCoordinatorTest {

    @Test
    void nothingHappensWhileClean() {
        AutosaveCoordinator a = new AutosaveCoordinator(5);
        for (int i = 0; i < 10; i++) {
            assertFalse(a.tick());
        }
    }

    @Test
    void flushesAfterQuietPeriod() {
        AutosaveCoordinator a = new AutosaveCoordinator(3);
        a.markDirty();
        assertFalse(a.tick());
        assertFalse(a.tick());
        assertTrue(a.tick()); // third tick since dirty
        a.markFlushed();
        assertFalse(a.tick());
        assertEquals(1, a.flushCount());
    }

    @Test
    void flushNowWritesPendingChangesImmediately() {
        AutosaveCoordinator a = new AutosaveCoordinator(100);
        a.markDirty();
        assertTrue(a.flushNow());
        assertEquals(1, a.flushCount());
        // Second call with nothing pending does not write again.
        assertFalse(a.flushNow());
    }

    @Test
    void repeatedDirtyTicksRestartTheDebounce() {
        AutosaveCoordinator a = new AutosaveCoordinator(3);
        a.markDirty();
        assertFalse(a.tick());
        a.markDirty(); // more changes arrived
        assertFalse(a.tick());
        assertFalse(a.tick());
        assertTrue(a.tick());
    }
}
