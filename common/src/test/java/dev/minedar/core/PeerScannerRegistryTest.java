package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PeerScannerRegistryTest {

    @Test
    void registryStartsEmptyAndDegradesGracefully() {
        PeerScannerRegistry registry = new PeerScannerRegistry();
        assertTrue(registry.activePeers().isEmpty(),
                "with no compatible peers the radar simply shows nothing");
    }

    @Test
    void peersAreShownOnlyWhileActivelyScanning() {
        PeerScannerRegistry registry = new PeerScannerRegistry();
        registry.update(List.of(new PeerScannerRegistry.Peer("a", 10, 0)));
        assertEquals(1, registry.activePeers().size());
        registry.update(List.of());
        assertTrue(registry.activePeers().isEmpty());
    }

    @Test
    void indicatorSizeIsFixedNotTheRealRadius() {
        // The blip has a fixed pixel size regardless of the peer's scan radius.
        assertEquals(3.0, PeerScannerRegistry.INDICATOR_RADIUS_PX, 1e-9);
    }

    @Test
    void pulseBreathesWithinBounds() {
        for (double t = 0; t < 5; t += 0.05) {
            double p = PeerScannerRegistry.pulse(t);
            assertTrue(p >= 0.5 - 1e-9 && p <= 1.0 + 1e-9, "pulse must gently breathe");
        }
    }

    @Test
    void offMapPeersGetNoIndicator() {
        assertNull(PeerScannerRegistry.screenOffset(1000, 0, 128, 60));
        assertNull(PeerScannerRegistry.screenOffset(0, 0, 0, 60));
    }

    @Test
    void onMapPeersGetAnIndicator() {
        double[] off = PeerScannerRegistry.screenOffset(0, 0, 128, 60);
        assertTrue(off != null);
        assertEquals(0.0, off[0], 1e-9);
        assertEquals(0.0, off[1], 1e-9);
    }

    @Test
    void indicatorIsNotPersistentScanData() {
        PeerScannerRegistry registry = new PeerScannerRegistry();
        registry.update(List.of(new PeerScannerRegistry.Peer("a", 1, 1)));
        registry.clear();
        assertFalse(registry.activePeers().iterator().hasNext());
    }
}
