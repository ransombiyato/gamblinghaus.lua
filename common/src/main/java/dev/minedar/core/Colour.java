package dev.minedar.core;

/** Packed RGB helpers. Colours are always 0xRRGGBB ints. */
public final class Colour {

    public static final int WHITE = 0xFFFFFF;

    private Colour() {
    }

    public static int rgb(int r, int g, int b) {
        return ((clamp(r) & 0xFF) << 16) | ((clamp(g) & 0xFF) << 8) | (clamp(b) & 0xFF);
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int blue(int rgb) {
        return rgb & 0xFF;
    }

    /** Linear blend from {@code a} to {@code b}; {@code t} in [0,1]. */
    public static int blend(int a, int b, double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(red(a) + (red(b) - red(a)) * c);
        int g = (int) Math.round(green(a) + (green(b) - green(a)) * c);
        int bl = (int) Math.round(blue(a) + (blue(b) - blue(a)) * c);
        return rgb(r, g, bl);
    }

    /**
     * Scales brightness by an intensity in [0,1] over a dark base. Used so a
     * weak (heavily transmitted) signal renders as a dimmer dot rather than an
     * indistinguishable full-bright one.
     */
    public static int dim(int rgb, double intensity) {
        double c = Math.max(0.0, Math.min(1.0, intensity));
        return rgb((int) (red(rgb) * c), (int) (green(rgb) * c), (int) (blue(rgb) * c));
    }

    /** Smooth blue to cyan to green to yellow to red heat gradient; t in [0,1]. */
    public static int heat(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        if (c < 0.25) {
            return blend(0x2030FF, 0x00E0E0, c / 0.25);
        }
        if (c < 0.5) {
            return blend(0x00E0E0, 0x30E030, (c - 0.25) / 0.25);
        }
        if (c < 0.75) {
            return blend(0x30E030, 0xE0E020, (c - 0.5) / 0.25);
        }
        return blend(0xE0E020, 0xE02020, (c - 0.75) / 0.25);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
