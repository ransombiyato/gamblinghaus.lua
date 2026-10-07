package dev.minedar.core;

import java.util.List;

/**
 * Walks a LiDAR signal through an ordered sequence of media along one ray and
 * reports what is retained on each layer and what survives to the end. This is
 * the pure implementation of the transmission model (sections 12-36) that the
 * per-version raycasters feed with real world hits.
 */
public final class SignalPropagator {

    /** One medium the ray passes through, with where it retained dots. */
    public static final class Layer {
        public final String materialKey;
        public final double x;
        public final double y;
        public final double z;
        public final int baseRgb;

        public Layer(String materialKey, double x, double y, double z, int baseRgb) {
            this.materialKey = materialKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.baseRgb = baseRgb;
        }
    }

    private final MaterialRules rules;

    public SignalPropagator(MaterialRules rules) {
        this.rules = rules;
    }

    /**
     * Propagates an initial signal of 1.0 through the layers in order.
     * Retained dots are appended to {@code out}; the surviving signal is stored
     * on {@code out}.
     */
    public void propagate(List<Layer> layers, ScanResult out) {
        double signal = 1.0;
        for (Layer layer : layers) {
            if (signal <= 0.0) {
                break;
            }
            MaterialProfile profile = rules.resolve(layer.materialKey);
            double retained = signal * profile.retain();
            if (retained > 0.0) {
                int colour = profile.tintedColour(layer.baseRgb);
                int intensity = (int) Math.round(255.0 * Math.min(1.0, retained));
                out.add(layer.x, layer.y, layer.z, colour, intensity);
            }
            signal = profile.transmit(signal);
        }
        out.setTransmitted(signal);
    }
}
