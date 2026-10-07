package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a {@link PixelSurface} into the world as LiDAR points (sections 46-47).
 *
 * <p>A surface is a planar patch with an origin and two orthonormal in-plane
 * axes (right and up). That covers signs and paintings on any wall, maps on
 * item frames, and held-item icons: the loader computes the frame, this maps
 * pixels to world coordinates. Surface pixel (0,0) is the top-left; the emitted
 * points are offset by {@code inset} so a sign's dots sit just off its face.
 */
public final class SurfacePlacer {

    /** One world-space dot from a surface. */
    public record Placed(double x, double y, double z, int rgb) {
    }

    private final double originX;
    private final double originY;
    private final double originZ;
    private final double rightX;
    private final double rightY;
    private final double rightZ;
    private final double upX;
    private final double upY;
    private final double upZ;
    private final double widthMetres;
    private final double heightMetres;
    private final double inset;

    private SurfacePlacer(Builder b) {
        this.originX = b.originX;
        this.originY = b.originY;
        this.originZ = b.originZ;
        this.rightX = b.rightX;
        this.rightY = b.rightY;
        this.rightZ = b.rightZ;
        this.upX = b.upX;
        this.upY = b.upY;
        this.upZ = b.upZ;
        this.widthMetres = b.widthMetres;
        this.heightMetres = b.heightMetres;
        this.inset = b.inset;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Maps every visible pixel of the surface to a world point. */
    public List<Placed> place(PixelSurface surface, int alphaThreshold, int step) {
        List<Placed> out = new ArrayList<>();
        placeInto(surface, alphaThreshold, step, out);
        return out;
    }

    /** Same as {@link #place} but appends into a caller-owned list. */
    public void placeInto(PixelSurface surface, int alphaThreshold, int step, List<Placed> out) {
        double uStep = widthMetres / surface.width();
        double vStep = heightMetres / surface.height();
        surface.forEachOpaque(alphaThreshold, step, (px, py, rgb) -> {
            // Pixel centres. Origin is the bottom-left, right is the +u axis and
            // up is the +v axis, so a surface pixel y measured from the TOP is
            // placed at (height - v) along up.
            double u = (px + 0.5) * uStep;
            double v = (py + 0.5) * vStep;
            double fromBottom = heightMetres - v;
            double nx = rightY * upZ - rightZ * upY;
            double ny = rightZ * upX - rightX * upZ;
            double nz = rightX * upY - rightY * upX;
            out.add(new Placed(
                    originX + rightX * u + upX * fromBottom + nx * inset,
                    originY + rightY * u + upY * fromBottom + ny * inset,
                    originZ + rightZ * u + upZ * fromBottom + nz * inset,
                    rgb));
        });
    }

    /** Fluent builder; axes are normalised on build so callers can pass raw vectors. */
    public static final class Builder {
        private double originX;
        private double originY;
        private double originZ;
        private double rightX = 1;
        private double rightY;
        private double rightZ;
        private double upX;
        private double upY = 1;
        private double upZ;
        private double widthMetres = 1;
        private double heightMetres = 1;
        private double inset = 0.012;

        public Builder origin(double x, double y, double z) {
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            return this;
        }

        public Builder right(double x, double y, double z) {
            this.rightX = x;
            this.rightY = y;
            this.rightZ = z;
            return this;
        }

        public Builder up(double x, double y, double z) {
            this.upX = x;
            this.upY = y;
            this.upZ = z;
            return this;
        }

        public Builder size(double widthMetres, double heightMetres) {
            this.widthMetres = widthMetres;
            this.heightMetres = heightMetres;
            return this;
        }

        public Builder inset(double inset) {
            this.inset = inset;
            return this;
        }

        public SurfacePlacer build() {
            normalise();
            return new SurfacePlacer(this);
        }

        private void normalise() {
            double rl = Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
            if (rl > 1e-9) {
                rightX /= rl;
                rightY /= rl;
                rightZ /= rl;
            }
            double ul = Math.sqrt(upX * upX + upY * upY + upZ * upZ);
            if (ul > 1e-9) {
                upX /= ul;
                upY /= ul;
                upZ /= ul;
            }
        }
    }
}
