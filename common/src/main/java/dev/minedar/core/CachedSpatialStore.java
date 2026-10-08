package dev.minedar.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**\n * A spatial cache for point cloud data that loads sections on demand and evicts\n * least-recently-used sections when the cache exceeds a maximum size. Dirty\n * sections are persisted to disk automatically.\n *\n * <p>This provides the bounded memory behavior required for low-RAM usage\n * while retaining persistence across sessions.\n */\n public class CachedSpatialStore implements PointCloudStore {\n \n     private final int maxSections;\n     private final Path basePath;\n     private final Map<Long, PointCloudSection> cache;\n     private final Set<Long> dirty = new java.util.HashSet<>();\n     private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();\n \n     public CachedSpatialStore(Path basePath, int maxSections) {\n         this.basePath = basePath;\n         this.maxSections = maxSections;\n         // LinkedHashMap with access order for LRU\n         this.cache = new LinkedHashMap<Long, PointCloudSection>(16, 0.75f, true) {\n             @Override\n             protected boolean removeEldestEntry(Map.Entry<Long, PointCloudSection> eldest) {\n                 if (size() > maxSections) {\n                     if (dirty.contains(eldest.getKey())) {\n                         saveSection(eldest.getKey(), eldest.getValue());\n                         dirty.remove(eldest.getKey());\n                     }\n                     return true;\n                 }\n                 return false;\n             }\n         };\n     }\n \n     // ... rest of class implements PointCloudStore.add ...\n
package dev.minedar.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A spatial cache for point cloud data that loads sections on demand and evicts
 * least-recently-used sections when the cache exceeds a maximum size. Dirty
 * sections are persisted to disk automatically.
 *
 * <p>This provides the bounded memory behavior required for low-RAM usage
 * while retaining persistence across sessions.
 */
public class CachedSpatialStore {

    private final int maxSections;
    private final Path basePath;
    private final Map<Long, PointCloudSection> cache;
    private final Set<Long> dirty = new java.util.HashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CachedSpatialStore(Path basePath, int maxSections) {
        this.basePath = basePath;
        this.maxSections = maxSections;
        // LinkedHashMap with access order for LRU
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

    /** Returns the section containing the given block coordinates, loading it if necessary. */
    public PointCloudSection getSection(int bx, int by, int bz) {
        long key = SpatialChunkStore.sectionKey(SpatialChunkStore.sectionCoord(bx), SpatialChunkStore.sectionCoord(by), SpatialChunkStore.sectionCoord(bz));
        lock.readLock().lock();
        try {
            PointCloudSection section = cache.get(key);
            if (section != null) {
                return section;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            PointCloudSection section = cache.get(key);
            if (section == null) {
                section = loadSection(key);
                cache.put(key, section);
            }
            return section;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Adds a point to the appropriate section, loading it if necessary. */
    public void add(double x, double y, double z, int rgb, int intensity) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        PointCloudSection section = getSection(bx, by, bz);
        int lx = SpatialChunkStore.localCoord(bx);
        int ly = SpatialChunkStore.localCoord(by);
        int lz = SpatialChunkStore.localCoord(bz);
        section.add(PointCloudSection.pack(lx, ly, lz, rgb, intensity));
        markDirty(section.key());
    }

    public void markDirty(long key) {
        lock.writeLock().lock();
        try {
            dirty.add(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            dirty.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int sectionCount() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int pointCount() {
        lock.readLock().lock();
        try {
            int total = 0;
            for (PointCloudSection s : cache.values()) {
                total += s.size();
// Persistence methods
    private Path sectionPath(long key) {
        // Convert key to a filename, e.g., based on existing PersistentScanStore
        return PersistentScanStore.pathForWorld(basePath, key);
    }

    private PointCloudSection loadSection(long key) {
        Path path = sectionPath(key);
        if (Files.exists(path)) {
            try {
                return PersistentScanStore.loadSection(path);
            } catch (IOException e) {
                // Log and return empty section
                System.err.println("Failed to load section " + key + ": " + e.getMessage());
            }
        }
        return new PointCloudSection();
    }

    private void saveSection(long key, PointCloudSection section) {
        Path path = sectionPath(key);
        try {
            PersistentScanStore.saveSection(path, section);
        } catch (IOException e) {
            System.err.println("Failed to save section " + key + ": " + e.getMessage());
        }
    }

    /** Saves all dirty sections and clears the dirty set. */
    public void flush() {
        lock.writeLock().lock();
        try {
            for (long key : new ArrayList<>(dirty)) {
                PointCloudSection section = cache.get(key);
                if (section != null) {
                    saveSection(key, section);
                }
                dirty.remove(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
            }
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<Long, PointCloudSection> view() {
        lock.readLock().lock();
        try {
            return new java.util.HashMap<>(cache);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Bulk loads sections into the cache. */
    public void putAll(Map<Long, PointCloudSection> sections) {
        lock.writeLock().lock();
        try {
            cache.putAll(sections);
        } finally {
            lock.writeLock().unlock();
        }
    }
package dev.minedar.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A spatial cache for point cloud data that loads sections on demand and evicts
 * least-recently-used sections when the cache exceeds a maximum size. Dirty
 * sections are persisted to disk automatically.
 *
 * <p>This provides the bounded memory behavior required for low-RAM usage
 * while retaining persistence across sessions.
 */
public class CachedSpatialStore {

    private final int maxSections;
    private final Path basePath;
    private final Map<Long, PointCloudSection> cache;
    private final Set<Long> dirty = new java.util.HashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CachedSpatialStore(Path basePath, int maxSections) {
        this.basePath = basePath;
        this.maxSections = maxSections;
        // LinkedHashMap with access order for LRU
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

    /** Returns the section containing the given block coordinates, loading it if necessary. */
    public PointCloudSection getSection(int bx, int by, int bz) {
        long key = SpatialChunkStore.sectionKey(SpatialChunkStore.sectionCoord(bx), SpatialChunkStore.sectionCoord(by), SpatialChunkStore.sectionCoord(bz));
        lock.readLock().lock();
        try {
            PointCloudSection section = cache.get(key);
            if (section != null) {
                return section;
            }
        } finally {
            lock.readLock().unlock();

        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            section = cache.get(key);
            if (section == null) {
                section = loadSection(key);
                cache.put(key, section);
            }
            return section;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Adds a point to the appropriate section, loading it if necessary. */
    public void add(double x, double y, double z, int rgb, int intensity) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        PointCloudSection section = getSection(bx, by, bz);
        int lx = SpatialChunkStore.localCoord(bx);
        int ly = SpatialChunkStore.localCoord(by);
        int lz = SpatialChunkStore.localCoord(bz);
        section.add(PointCloudSection.pack(lx, ly, lz, rgb, intensity));
        markDirty(section.key());
    }

    public void markDirty(long key) {
        lock.writeLock().lock();
        try {
            dirty.add(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            dirty.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int sectionCount() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int pointCount() {
        lock.readLock().lock();
        try {
            int total = 0;
            for (PointCloudSection s : cache.values()) {
                total += s.size();
            }
            return total;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<Long, PointCloudSection> view() {
        lock.readLock().lock();
        try {
            return new java.util.HashMap<>(cache);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Persistence methods
    private Path sectionPath(long key) {
        // Convert key to a filename, e.g., based on existing PersistentScanStore
        return PersistentScanStore.pathForWorld(basePath, key);
    }

    private PointCloudSection loadSection(long key) {
        Path path = sectionPath(key);
        if (Files.exists(path)) {
            try {
                return PersistentScanStore.loadSection(path);
            } catch (IOException e) {
                // Log and return empty section
                System.err.println("Failed to load section " + key + ": " + e.getMessage());
            }
        }
        return new PointCloudSection();
    }

    private void saveSection(long key, PointCloudSection section) {
        Path path = sectionPath(key);
        try {
            PersistentScanStore.saveSection(path, section);
        } catch (IOException e) {
            System.err.println("Failed to save section " + key + ": " + e.getMessage());
        }
    }

    /** Saves all dirty sections and clears the dirty set. */
    public void flush() {
        lock.writeLock().lock();
        try {
            for (long key : new ArrayList<>(dirty)) {
                PointCloudSection section = cache.get(key);
                if (section != null) {
                    saveSection(key, section);
                }
                dirty.remove(key);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}