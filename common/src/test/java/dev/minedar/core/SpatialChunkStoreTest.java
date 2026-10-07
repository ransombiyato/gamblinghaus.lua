package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SpatialChunkStoreTest {

    @Test
    void routesPointsIntoCorrectSections() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(0, 0, 0, Colour.WHITE, 255);      // section (0,0,0)
        store.addBlock(15, 15, 15, Colour.WHITE, 255);   // still (0,0,0)
        store.addBlock(16, 0, 0, Colour.WHITE, 255);     // section (1,0,0)
        store.addBlock(-1, 0, 0, Colour.WHITE, 255);     // section (-1,0,0)
        assertEquals(3, store.sectionCount());
        assertEquals(2, store.section(0, 0, 0).size());
        assertEquals(1, store.section(1, 0, 0).size());
        assertEquals(1, store.section(-1, 0, 0).size());
    }

    @Test
    void negativeCoordinatesUseArithmeticShift() {
        // -1 >> 4 == -1, i.e. the section to the negative side, not section 0.
        assertEquals(-1, SpatialChunkStore.sectionCoord(-1));
        assertEquals(15, SpatialChunkStore.localCoord(-1));
        assertEquals(-1, SpatialChunkStore.sectionCoord(-16));
        assertEquals(0, SpatialChunkStore.localCoord(-16));
    }

    @Test
    void packedCoordinatesRoundTripThroughStorage() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(33, -5, 47, 0x123456, 99); // section (2,-1,2), local (1,11,15)
        PointCloudSection s = store.section(2, -1, 2);
        assertNotNull(s);
        long p = s.get(0);
        assertEquals(1, PointCloudSection.localX(p));
        assertEquals(11, PointCloudSection.localY(p));
        assertEquals(15, PointCloudSection.localZ(p));
        assertEquals(0x123456, PointCloudSection.rgb(p));
    }

    @Test
    void replaceSectionSupersedesOnlyThatRegion() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.addBlock(0, 0, 0, Colour.WHITE, 255);
        store.addBlock(100, 0, 0, Colour.WHITE, 255);

        PointCloudSection fresh = new PointCloudSection();
        fresh.add(PointCloudSection.pack(1, 1, 1, 0xFF0000, 255));
        store.replaceSection(0, 0, 0, fresh);

        assertEquals(1, store.section(0, 0, 0).size());
        assertEquals(0xFF0000, PointCloudSection.rgb(store.section(0, 0, 0).get(0)));
        // History elsewhere is untouched by rescanning one section.
        assertEquals(1, store.section(SpatialChunkStore.sectionCoord(100), 0, 0).size());
    }

    @Test
    void pointCountAggregatesAllSections() {
        SpatialChunkStore store = new SpatialChunkStore();
        for (int i = 0; i < 10; i++) {
            store.addBlock(i, 0, 0, Colour.WHITE, 255);
        }
        assertEquals(10, store.pointCount());
    }

    @Test
    void worldPositionsMapToDistinctSections() {
        SpatialChunkStore store = new SpatialChunkStore();
        store.add(0.5, 0.5, 0.5, Colour.WHITE, 255);
        store.add(0.5, 32.5, 0.5, Colour.WHITE, 255);
        assertNotEquals(store.keys().get(0), store.keys().get(1));
        assertEquals(2, store.sectionCount());
    }
}
