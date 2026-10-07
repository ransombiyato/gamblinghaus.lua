package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PointCloudSectionTest {

    @Test
    void packUnpackRoundTrip() {
        long p = PointCloudSection.pack(1, 2, 3, 0xABCDEF, 200);
        assertEquals(1, PointCloudSection.localX(p));
        assertEquals(2, PointCloudSection.localY(p));
        assertEquals(3, PointCloudSection.localZ(p));
        assertEquals(0xABCDEF, PointCloudSection.rgb(p));
        assertEquals(200, PointCloudSection.intensity(p));
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> PointCloudSection.pack(16, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PointCloudSection.pack(-1, 0, 0, 0, 0));
    }

    @Test
    void growsAndKeepsAllPoints() {
        PointCloudSection s = new PointCloudSection(2);
        for (int i = 0; i < 100; i++) {
            s.add(PointCloudSection.pack(i % 16, 0, 0, 0xFFFFFF, i % 256));
        }
        assertEquals(100, s.size());
        assertEquals(i_intensity(99), PointCloudSection.intensity(s.get(99)));
    }

    private static int i_intensity(int v) {
        return v % 256;
    }

    @Test
    void copyIntoWritesAtOffset() {
        PointCloudSection s = new PointCloudSection();
        s.add(PointCloudSection.pack(0, 0, 0, 0x111111, 1));
        s.add(PointCloudSection.pack(1, 1, 1, 0x222222, 2));
        long[] out = new long[4];
        int n = s.copyInto(out, 1);
        assertEquals(2, n);
        assertEquals(0x222222, PointCloudSection.rgb(out[2]));
        assertEquals(0L, out[0]);
    }

    @Test
    void getOutOfBoundsThrows() {
        PointCloudSection s = new PointCloudSection();
        assertThrows(IndexOutOfBoundsException.class, () -> s.get(0));
        assertTrue(s.isEmpty());
    }
}
