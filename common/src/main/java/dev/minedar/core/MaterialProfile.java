package dev.minedar.core;

/**
 * How a medium interacts with the LiDAR signal. {@code passThrough} is the
 * fraction of the incoming signal that continues past the medium; {@code retain}
 * is the fraction of the incoming signal left behind as dots on the geometry.
 * They do not have to sum to 1: a medium may also absorb signal entirely.
 *
 * <p>{@code tint} is applied to retained dots when {@code tintStrength > 0}.
 */
public record MaterialProfile(
        String name,
        double passThrough,
        double retain,
        int tint,
        double tintStrength
) {

    public static final MaterialProfile SOLID = new MaterialProfile("solid", 0.0, 1.0, -1, 0.0);
    /** Glass keeps ~30-45% of signal as small dots and passes the rest. */
    public static final MaterialProfile GLASS = new MaterialProfile("glass", 0.62, 0.38, -1, 0.0);
    /** Stained glass uses the block's own colour as the dot colour. */
    public static final MaterialProfile STAINED_GLASS = new MaterialProfile("stained_glass", 0.62, 0.38, -1, 0.0);
    public static final MaterialProfile WATER = new MaterialProfile("water", 0.45, 0.55, 0x3050C8, 0.55);
    public static final MaterialProfile LAVA = new MaterialProfile("lava", 0.20, 0.80, 0xE05010, 0.60);
    public static final MaterialProfile LEAVES = new MaterialProfile("leaves", 0.15, 0.85, 0x3C8C3C, 0.35);
    public static final MaterialProfile ICE = new MaterialProfile("ice", 0.55, 0.45, 0xA8D8F0, 0.25);
    public static final MaterialProfile SNOW = new MaterialProfile("snow", 0.675, 0.325, 0xB0C8E0, 0.20);
    public static final MaterialProfile POWDER_SNOW = new MaterialProfile("powder_snow", 0.0, 1.0, 0xC0D8F0, 0.15);
    public static final MaterialProfile COBWEB = new MaterialProfile("cobweb", 0.45, 0.55, -1, 0.0);
    public static final MaterialProfile FIRE = new MaterialProfile("fire", 0.35, 0.65, 0xFF9020, 0.50);
    public static final MaterialProfile EXPLOSION = new MaterialProfile("explosion", 0.80, 0.20, -1, 0.0);
    public static final MaterialProfile SMOKE = new MaterialProfile("smoke", 0.50, 0.50, -1, 0.0);
    public static final MaterialProfile PORTAL = new MaterialProfile("portal", 0.25, 0.75, 0x8B30C8, 0.55);
    public static final MaterialProfile END_PORTAL = new MaterialProfile("end_portal", 0.30, 0.70, 0x103040, 0.45);
    public static final MaterialProfile CLOUD = new MaterialProfile("cloud", 0.45, 0.55, 0xC8D8F0, 0.35);
    public static final MaterialProfile WEATHER = new MaterialProfile("weather", 0.0, 1.0, 0xC8D0E0, 0.15);

    /** Applies {@code incoming} signal to this profile, returning the survivor. */
    public double transmit(double incoming) {
        return incoming * passThrough;
    }

    public int tintedColour(int baseRgb) {
        if (tint < 0 || tintStrength <= 0) {
            return baseRgb;
        }
        return Colour.blend(baseRgb, tint, tintStrength);
    }
}
