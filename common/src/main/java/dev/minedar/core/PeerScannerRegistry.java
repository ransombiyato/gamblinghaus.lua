package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks other MiNEDAR-equipped players who are actively scanning, for the
 * minimap radar indicator (section 66). This is client-side, ephemeral state -
 * never persistent scan data - and it degrades to nothing when no compatible
 * peers are known, which is the expected case without server participation
 * (section 78). We do not invent any server gameplay state.
 */
public final class PeerScannerRegistry {

    /** Fixed indicator size in minimap pixels (not the peer's actual radius). */
    public static final double INDICATOR_RADIUS_PX = 3.0;
    /** Red tint for the radar blob. */
    public static final int INDICATOR_COLOUR = 0xFF5050;
    /** Full pulse period in seconds. */
    public static final double PULSE_PERIOD = 1.2;

    /** A peer known to be actively scanning right now. */
    public static final class Peer {
        public final String id;
        public final double x;
        public final double z;

        public Peer(String id, double x, double z) {
            this.id = id;
            this.x = x;
            this.z = z;
        }
    }

    private final List<Peer> active = new ArrayList<>();

    /** Replaces the current set of actively-scanning peers. */
    public void update(List<Peer> peers) {
        active.clear();
        if (peers != null) {
            active.addAll(peers);
        }
    }

    public List<Peer> activePeers() {
        return active;
    }

    public void clear() {
        active.clear();
    }

    /** Radar pulse in [0.5, 1.0] so the indicator gently breathes (section 66). */
    public static double pulse(double timeSeconds) {
        double phase = (timeSeconds % PULSE_PERIOD) / PULSE_PERIOD;
        return 0.5 + 0.5 * Math.abs(Math.sin(Math.PI * phase));
    }

    /**
     * Indicator position in minimap pixels relative to centre, given the peer's
     * offset in blocks, the map radius in blocks and the display radius in pixels.
     * Returns null when the peer is outside the displayed area.
     */
    public static double[] screenOffset(double relX, double relZ, double mapRadiusBlocks,
                                        double displayRadiusPx) {
        if (mapRadiusBlocks <= 0) {
            return null;
        }
        double nx = relX / mapRadiusBlocks;
        double nz = relZ / mapRadiusBlocks;
        if (!MinimapLayout.insideSquircle(nx, nz, 4.0)) {
            return null;
        }
        return new double[] {nx * displayRadiusPx, nz * displayRadiusPx};
    }
}
