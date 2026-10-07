package dev.minedar.core;

/**
 * The loader-independent view of the world the scan engine casts against
 * (section 81). The Forge/Fabric adapters implement this over the real client
 * world; tests implement it with deterministic fixtures. Returning {@code null}
 * from {@link #blockAt} means "nothing here" (air / unloaded chunk), which lets
 * scans fail gracefully instead of crashing (section 79).
 */
public interface WorldSampler {

    /** What a ray should do at one block position. */
    final class SampledBlock {
        public final String materialKey;
        public final int rgb;
        /** True when the ray stops here (opaque solid). */
        public final boolean solid;
        /** Optional special-block tint colour, or -1 for none. */
        public final int tint;
        public final double tintStrength;

        public SampledBlock(String materialKey, int rgb, boolean solid, int tint, double tintStrength) {
            this.materialKey = materialKey;
            this.rgb = rgb;
            this.solid = solid;
            this.tint = tint;
            this.tintStrength = tintStrength;
        }

        public static SampledBlock solid(String materialKey, int rgb) {
            return new SampledBlock(materialKey, rgb, true, -1, 0.0);
        }

        public static SampledBlock transparent(String materialKey, int rgb) {
            return new SampledBlock(materialKey, rgb, false, -1, 0.0);
        }
    }

    /** Geometry sampled for one entity part (sections 40-44). */
    final class SampledEntity {
        public final double x;
        public final double y;
        public final double z;
        public final int rgb;

        public SampledEntity(double x, double y, double z, int rgb) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rgb = rgb;
        }
    }

    /** One scannable particle sample (sections 29-34, 46). */
    final class SampledParticle {
        public final double x;
        public final double y;
        public final double z;
        public final String materialKey;
        public final int rgb;

        public SampledParticle(double x, double y, double z, String materialKey, int rgb) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.materialKey = materialKey;
            this.rgb = rgb;
        }
    }

    /**
     * Block at integer position, or {@code null} for air/unloaded. Implementations
     * ignore ordinary lighting, so darkness never blocks a scan (section 49).
     */
    SampledBlock blockAt(int x, int y, int z);

    /**
     * Entity model geometry that the given ray passes near, or an empty list.
     * Entity samples are relative to the ray so scans only collect what they hit.
     */
    default java.util.List<SampledEntity> entitiesAlong(Ray ray, double maxDistance) {
        return java.util.List.of();
    }

    /** Particle samples the ray intersects (particles are point-like). */
    default java.util.List<SampledParticle> particlesAlong(Ray ray, double maxDistance) {
        return java.util.List.of();
    }
}
