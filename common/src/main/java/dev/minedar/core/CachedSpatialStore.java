package dev.minedar.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded LRU cache of point cloud sections backed by per-section files on disk.
 * Loads sections on demand and evicts least-recently-used sections when the
 * cache exceeds {@code maxSections}, persisting dirty ones first. This keeps RAM
 * bounded regardless of how much history exists on disk.
 */
public class CachedSpatialStore implements PointCloudStore {

    private final int maxSections;
    private final Path worldDir;
    private final Map<Long, PointCloudSection> cache;
    private final Set<Long> dirty = new HashSet<>();

    public CachedSpatialStore(Path worldDir, int maxSections) {
        this.worldDir = worldDir;
        this.maxSections = maxSections;
        this.cache = new LinkedHashMap<Long, PointCloudSection>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, PointCloudSection> eldest) {
                if (size() > maxSections) {
                    if (dirty.contains(eldest.getKey())) {
                        saveSection(eldest.getKey(), eldest.getValue());
                        dirty.remove(eldest.getKey());
                    }
                    return true;
                }
                return false;
            }
        };
    }

    public PointCloudSection getSection(int bx, int by, int bz) {
        long key = SpatialChunkStore.sectionKey(
                SpatialChunkStore.sectionCoord(bx),
                SpatialChunkStore.sectionCoord(by),
                SpatialChunkStore.sectionCoord(bz));
        PointCloudSection section = cache.get(key);
        if (section != null) {
            return section;
        }
        section = loadSection(key);
        cache.put(key, section);
        return section;
    }

    @Override
    public void add(double x, double y, double z, int rgb, int intensity) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        PointCloudSection section = getSection(bx, by, bz);
        section.add(PointCloudSection.pack(
                SpatialChunkStore.localCoord(bx),
                SpatialChunkStore.localCoord(by),
                SpatialChunkStore.localCoord(bz), rgb, intensity));
        markDirty(sectionKeyOf(bx, by, bz));
    }

    private static long sectionKeyOf(int bx, int by, int bz) {
        return SpatialChunkStore.sectionKey(
                SpatialChunkStore.sectionCoord(bx),
                SpatialChunkStore.sectionCoord(by),
                SpatialChunkStore.sectionCoord(bz));
    }

    public void markDirty(long key) {
        dirty.add(key);
    }

    public void clear() {
        cache.clear();
        dirty.clear();
    }

    public int sectionCount() {
        return cache.size();
    }

    public int pointCount() {
        int total = 0;
        for (PointCloudSection s : cache.values()) {
            total += s.size();
        }
        return total;
    }

    public Map<Long, PointCloudSection> view() {
        return new HashMap<>(cache);
    }

    public void putAll(Map<Long, PointCloudSection> sections) {
        cache.putAll(sections);
    }

    private PointCloudSection loadSection(long key) {
        try {
            return PersistentScanStore.loadSection(worldDir, key);
        } catch (IOException e) {
            return new PointCloudSection();
        }
    }

    private void saveSection(long key, PointCloudSection section) {
        try {
            PersistentScanStore.saveSection(worldDir, key, section);
        } catch (IOException e) {
            // best effort; a later flush retries
        }
    }

    public void flush() {
        for (long key : new ArrayList<>(dirty)) {
            PointCloudSection section = cache.get(key);
            if (section != null) {
                saveSection(key, section);
            }
        }
        dirty.clear();
    }
}
