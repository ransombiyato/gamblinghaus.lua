package dev.minedar.core;

/** Test helper: converts the first retained dot's intensity back to a fraction. */
final class PointIntensity {

    private PointIntensity() {
    }

    static double of(ScanResult result) {
        if (result.hits().isEmpty()) {
            return 0.0;
        }
        return result.hits().get(0).intensity / 255.0;
    }
}
