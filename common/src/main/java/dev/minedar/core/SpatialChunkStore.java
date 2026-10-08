package dev.minedar.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**\n * The spatial store: sections keyed by world section coordinates. Mirrors the\n * \"chunked/spatially partitioned storage\" requirement (sections 10, 74) and is\n * the in-memory working set the renderer reads from. It never keeps the whole\n * world as one flat array and only holds sections the client currently needs.\n *\n * @see PointCloudStore\n */\npublic final class SpatialChunkStore implements PointCloudStore {\n \n     private static final int SHIFT = 4; // 16 blocks per section\n \n     private final Map<Long, PointCloudSection> sections = new HashMap<>();\n ... (rest of class)
package dev.minedar.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**\n * The spatial store: sections keyed by world section coordinates. Mirrors the\n * \"chunked/spatially partitioned storage\" requirement (sections 10, 74) and is\n * the in-memory working set the renderer reads from. It never keeps the whole\n * world as one flat array and only holds sections the client currently needs.\n */\n public final class SpatialChunkStore implements PointCloudStore {\n \n     private static final int SHIFT = 4; // 16 blocks per section\n \n     private final Map<Long, PointCloudSection> sections = new HashMap<>();\n ... (rest of class)
package dev.minedar.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The spatial store: sections keyed by world section coordinates. Mirrors the
 * "chunked/spatially partitioned storage" requirement (sections 10, 74) and is
 * the in-memory working set the renderer reads from. It never keeps the whole
 * world as one flat array and only holds sections the client currently needs.
 */
public final class SpatialChunkStore {

    private static final int SHIFT = 4; // 16 blocks per section

    private final Map<Long, PointCloudSection> sections = new HashMap<>();

    public static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFF);
    }

    public static int sectionCoord(int block) {
        return block >> SHIFT;
    }

    public static int localCoord(int block) {
        return block & (PointCloudSection.SIZE - 1);
    }

    /** Adds one world-space point, routing it into the correct section. */
    public void add(double x, double y, double z, int rgb, int intensity) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        addBlock(bx, by, bz, rgb, intensity);
    }

    public void addBlock(int bx, int by, int bz, int rgb, int intensity) {
        long key = sectionKey(sectionCoord(bx), sectionCoord(by), sectionCoord(bz));
        PointCloudSection section = sections.computeIfAbsent(key, k -> new PointCloudSection());
        section.add(PointCloudSection.pack(
                localCoord(bx), localCoord(by), localCoord(bz), rgb, intensity));
    }

    /**
     * Replaces a section's contents wholesale. Used when a region is rescanned:
     * the newly observed geometry supersedes the previous observation for that
     * section while leaving other history untouched (section 9).
     */
    public void replaceSection(int sx, int sy, int sz, PointCloudSection replacement) {
        sections.put(sectionKey(sx, sy, sz), replacement);
    }

    public PointCloudSection section(int sx, int sy, int sz) {
        return sections.get(sectionKey(sx, sy, sz));
    }

    public int sectionCount() {
        return sections.size();
    }

    public int pointCount() {
        int total = 0;
        for (PointCloudSection s : sections.values()) {
            total += s.size();
        }
        return total;
    }

    /** Keys of the currently held working set, for persistence flushing. */
    public List<Long> keys() {
        return new ArrayList<>(sections.keySet());
    }

    public Map<Long, PointCloudSection> view() {
        return sections;
    }

    public void clear() {
        sections.clear();
    }
}
