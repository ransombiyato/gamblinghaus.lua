package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinimapDensityStoreTest {

    @Test
    void accumulatesDensityInCells() {
        MinimapDensityStore store = new MinimapDensityStore(32, 1.0);
        store.add(0, 0, 5);
        store.add(1, 1, 7); // same 4x4 cell
        assertEquals(12, store.densityAt(0, 0));
    }

    @Test
    void hardBoundaryBeyondRadiusIsNotRecorded() {
        // radius 8 cells => 32 blocks; a point at 100 blocks is simply dropped.
        MinimapDensityStore store = new MinimapDensityStore(8, 1.0);
        store.add(100, 0, 10);
        assertEquals(0, store.densityAt(25, 0));
        assertTrue(store.nonZeroCells().isEmpty());
    }

    @Test
    void densityIsCappedSoHotCellsDoNotGrowForever() {
        MinimapDensityStore store = new MinimapDensityStore(4, 1.0);
        for (int i = 0; i < 1000; i++) {
            store.add(0, 0, 1);
        }
        assertEquals(MinimapDensityStore.Caps.MAX_DENSITY, store.densityAt(0, 0));
        assertEquals(1.0, store.normalised(0, 0), 1e-9);
    }

    @Test
    void heatGradientIsSmoothAndOrdered() {
        MinimapDensityStore store = new MinimapDensityStore(8, 1.0);
        store.add(0, 0, 1);
        store.add(8, 0, 32);
        store.add(16, 0, 64);

        int cool = store.heatColour(0, 0);
        int warm = store.heatColour(2, 0);
        int hot = store.heatColour(4, 0);

        // Sparse -> blue/cyan, dense -> yellow/red.
        assertTrue(Colour.red(hot) > Colour.red(cool), "hot should be redder than cool");
        assertTrue(Colour.blue(cool) >= Colour.blue(hot), "cool should be bluer");
        // Smooth ramp: adjacent intensities should not be wildly different.
        assertTrue(Math.abs(Colour.red(warm) - Colour.red(cool)) < 200);
        assertTrue(Math.abs(Colour.red(hot) - Colour.red(warm)) < 200);
    }

    @Test
    void decayReducesDensityOverTime() {
        MinimapDensityStore store = new MinimapDensityStore(4, 0.5);
        store.add(0, 0, 64);
        store.decayStep();
        assertEquals(32, store.densityAt(0, 0));
        store.decayStep();
        assertEquals(16, store.densityAt(0, 0));
    }

    @Test
    void nonZeroCellsReportsOnlyPopulatedCells() {
        MinimapDensityStore store = new MinimapDensityStore(4, 1.0);
        store.add(0, 0, 1);
        store.add(8, 8, 1);
        assertEquals(2, store.nonZeroCells().size());
    }
}
