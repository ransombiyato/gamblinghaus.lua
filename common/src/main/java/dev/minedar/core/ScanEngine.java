package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Casts one LiDAR ray through the world using voxel DDA traversal, then applies
 * material transmission and writes the retained dots into the point cloud
 * (sections 8, 12, 74-75). Also gathers entity and particle samples the ray
 * passes through (sections 29-34, 40-44).
 *
 * <p>Everything here is allocation-conscious: the per-ray layer list and result
 * are reused between rays, and only retained dots become stored data.
 */
public final class ScanEngine {

    private final MaterialRules rules;
    private final SignalPropagator propagator;
    /** Reused per-ray scratch so scanning does not allocate a list per ray. */
    private final List<SignalPropagator.Layer> layers = new ArrayList<>(16);

    public ScanEngine(MaterialRules rules) {
        this.rules = rules;
        this.propagator = new SignalPropagator(rules);
    }

    /**
     * Casts one ray and writes retained dots into {@code out} and, when
     * {@code minimap} is non-null, into the density store relative to
     * {@code playerX/playerZ}.
     */
    public ScanResult cast(Ray ray, WorldSampler world, double maxDistance,
                           SpatialChunkStore out, MinimapDensityStore minimap,
                           double playerX, double playerZ) {
        ScanResult result = new ScanResult();
        layers.clear();

        traverse(ray, world, maxDistance, layers);
        propagator.propagate(layers, result);
        List<ScanResult.Hit> hits = result.hits();

        for (int i = 0; i < hits.size(); i++) {
            ScanResult.Hit h = hits.get(i);
            out.add(h.x, h.y, h.z, h.rgb, h.intensity);
            if (minimap != null) {
                minimap.add(h.x - playerX, h.z - playerZ, 1);
            }
        }

        // Entities and particles are geometry too; they are not affected by the
        // block transmission chain but obey their own category colours.
        for (WorldSampler.SampledEntity e : world.entitiesAlong(ray, maxDistance)) {
            out.add(e.x, e.y, e.z, e.rgb, 255);
            if (minimap != null) {
                minimap.add(e.x - playerX, e.z - playerZ, 1);
            }
        }
        for (WorldSampler.SampledParticle p : world.particlesAlong(ray, maxDistance)) {
            MaterialProfile profile = rules.resolve(p.materialKey);
            if (profile.passThrough() >= 1.0) {
                continue; // fully transmissive: consumes nothing
            }
            int intensity = (int) Math.round(255.0 * Math.min(1.0, profile.retain()));
            out.add(p.x, p.y, p.z, profile.tintedColour(p.rgb), Math.max(1, intensity));
            if (minimap != null) {
                minimap.add(p.x - playerX, p.z - playerZ, 1);
            }
        }
        return result;
    }

    /** Voxel DDA: walks cell boundaries in order, collecting media layers. */
    private void traverse(Ray ray, WorldSampler world, double maxDistance,
                          List<SignalPropagator.Layer> into) {
        int x = (int) Math.floor(ray.ox);
        int y = (int) Math.floor(ray.oy);
        int z = (int) Math.floor(ray.oz);

        int stepX = ray.dx > 0 ? 1 : ray.dx < 0 ? -1 : 0;
        int stepY = ray.dy > 0 ? 1 : ray.dy < 0 ? -1 : 0;
        int stepZ = ray.dz > 0 ? 1 : ray.dz < 0 ? -1 : 0;

        double tDeltaX = ray.dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / ray.dx);
        double tDeltaY = ray.dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / ray.dy);
        double tDeltaZ = ray.dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / ray.dz);

        double tMaxX = boundaryT(ray.ox, ray.dx, x, stepX);
        double tMaxY = boundaryT(ray.oy, ray.dy, y, stepY);
        double tMaxZ = boundaryT(ray.oz, ray.dz, z, stepZ);

        double t = 0.0;
        int guard = 0;
        int maxSteps = (int) (maxDistance * 3) + 8;

        while (t <= maxDistance && guard++ < maxSteps) {
            WorldSampler.SampledBlock block = world.blockAt(x, y, z);
            if (block != null) {
                int rgb = block.tint >= 0
                        ? Colour.blend(block.rgb, block.tint, block.tintStrength)
                        : block.rgb;
                into.add(new SignalPropagator.Layer(
                        block.materialKey, x + 0.5, y + 0.5, z + 0.5, rgb));
                if (block.solid) {
                    return; // opaque: signal cannot continue past this cell
                }
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
    }

    /** Distance along the ray to the next integer boundary on one axis. */
    private static double boundaryT(double origin, double dir, int cell, int step) {
        if (dir == 0 || step == 0) {
            return Double.MAX_VALUE;
        }
        double boundary = step > 0 ? cell + 1 : cell;
        return (boundary - origin) / dir;
    }

    public MaterialRules rules() {
        return rules;
    }
}
