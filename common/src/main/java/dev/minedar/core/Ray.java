package dev.minedar.core;

/** A ray: origin plus a unit direction. Immutable and cheap. */
public final class Ray {

    public final double ox;
    public final double oy;
    public final double oz;
    public final double dx;
    public final double dy;
    public final double dz;

    public Ray(double ox, double oy, double oz, double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0.0) {
            throw new IllegalArgumentException("zero-length direction");
        }
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.dx = dx / len;
        this.dy = dy / len;
        this.dz = dz / len;
    }

    public double pointX(double t) {
        return ox + dx * t;
    }

    public double pointY(double t) {
        return oy + dy * t;
    }

    public double pointZ(double t) {
        return oz + dz * t;
    }
}
