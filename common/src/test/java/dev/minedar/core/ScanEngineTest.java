package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanEngineTest {

    /** A deterministic test world: a floor of stone with named media columns. */
    private static final class FakeWorld implements WorldSampler {
        final java.util.Map<Long, SampledBlock> blocks = new java.util.HashMap<>();
        final List<SampledEntity> entities = new ArrayList<>();
        final List<SampledParticle> particles = new ArrayList<>();

        void put(int x, int y, int z, SampledBlock b) {
            blocks.put(key(x, y, z), b);
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0xFFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        @Override
        public SampledBlock blockAt(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        @Override
        public List<SampledEntity> entitiesAlong(Ray ray, double maxDistance) {
            return entities;
        }

        @Override
        public List<SampledParticle> particlesAlong(Ray ray, double maxDistance) {
            return particles;
        }
    }

    private ScanEngine engine() {
        return new ScanEngine(new MaterialRules());
    }

    @Test
    void stopsAtFirstSolidBlock() {
        FakeWorld world = new FakeWorld();
        world.put(5, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        world.put(9, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        ScanResult r = engine().cast(
                new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, new SpatialChunkStore(), null, 0, 0);
        assertEquals(1, r.hitCount(), "ray must stop at the first solid block");
    }

    @Test
    void passesThroughGlassThenHitsWall() {
        FakeWorld world = new FakeWorld();
        world.put(2, 0, 0, WorldSampler.SampledBlock.transparent("glass", Colour.WHITE));
        world.put(5, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        ScanResult r = engine().cast(
                new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, new SpatialChunkStore(), null, 0, 0);
        assertEquals(2, r.hitCount(), "glass retains dots and the wall stops the ray");
    }

    @Test
    void rainConsumesSignalBeforeItReachesGeometry() {
        FakeWorld world = new FakeWorld();
        world.put(3, 0, 0, WorldSampler.SampledBlock.transparent("weather", Colour.WHITE));
        world.put(6, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        ScanResult r = engine().cast(
                new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, new SpatialChunkStore(), null, 0, 0);
        assertEquals(1, r.hitCount(), "weather consumes all signal, wall is never reached");
        assertEquals(0.0, r.transmitted(), 1e-9);
    }

    @Test
    void dotsAreWrittenIntoTheSpatialStore() {
        FakeWorld world = new FakeWorld();
        world.put(3, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        SpatialChunkStore store = new SpatialChunkStore();
        engine().cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, store, null, 0, 0);
        assertEquals(1, store.pointCount());
    }

    @Test
    void dotsAlsoFeedTheMinimapDensity() {
        FakeWorld world = new FakeWorld();
        world.put(3, 0, 3, WorldSampler.SampledBlock.solid("stone", 0x888888));
        MinimapDensityStore minimap = new MinimapDensityStore(64, 1.0);
        engine().cast(new Ray(0.5, 0.5, 0.5, 1, 0, 1), world, 16,
                new SpatialChunkStore(), minimap, 0.5, 0.5);
        assertTrue(minimap.densityAt(0, 0) > 0);
    }

    @Test
    void noScanDataWhenRayHitsNothing() {
        FakeWorld world = new FakeWorld();
        SpatialChunkStore store = new SpatialChunkStore();
        ScanResult r = engine().cast(
                new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, store, null, 0, 0);
        assertEquals(0, r.hitCount());
        assertEquals(0, store.pointCount());
        assertEquals(1.0, r.transmitted(), 1e-9);
    }

    @Test
    void unloadedChunksDoNotCrashScanning() {
        FakeWorld world = new FakeWorld();
        world.put(3, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        // A ray fired into empty space returns cleanly rather than throwing.
        ScanResult r = engine().cast(
                new Ray(0.5, 0.5, 0.5, -1, -1, -1), world, 16, new SpatialChunkStore(), null, 0, 0);
        assertTrue(r.hitCount() >= 0);
    }

    @Test
    void entitiesAndParticlesAreScanned() {
        FakeWorld world = new FakeWorld();
        world.put(10, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        world.entities.add(new WorldSampler.SampledEntity(2, 1, 1, EntityCategoriser.HOSTILE_RED));
        world.particles.add(new WorldSampler.SampledParticle(1.5, 1.5, 0.5, "smoke", 0x999999));

        SpatialChunkStore store = new SpatialChunkStore();
        engine().cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, store, null, 0, 0);
        // stone + entity + smoke particle.
        assertEquals(3, store.pointCount());
    }

    @Test
    void historicalBehaviourAccumulatesRatherThanReplaces() {
        // Scan a block at A, then scan again after it "moved" to B.
        FakeWorld world = new FakeWorld();
        SpatialChunkStore store = new SpatialChunkStore();
        ScanEngine engine = engine();

        world.put(4, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        engine.cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, store, null, 0, 0);

        world.blocks.clear();
        world.put(8, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        engine.cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, store, null, 0, 0);

        // Old dots at A remain; new dots at B are added. History, not a live map.
        assertEquals(2, store.pointCount());
    }

    @Test
    void differentRadiusChangesCoverageNotCorrectness() {
        FakeWorld world = new FakeWorld();
        world.put(40, 0, 0, WorldSampler.SampledBlock.solid("stone", 0x888888));
        SpatialChunkStore near = new SpatialChunkStore();
        SpatialChunkStore far = new SpatialChunkStore();
        ScanEngine engine = engine();

        engine.cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 16, near, null, 0, 0);   // short distance
        engine.cast(new Ray(0.5, 0.5, 0.5, 1, 0, 0), world, 64, far, null, 0, 0);    // long distance

        assertEquals(0, near.pointCount(), "short scan cannot reach the wall");
        assertEquals(1, far.pointCount(), "long scan reaches the wall");
    }
}
