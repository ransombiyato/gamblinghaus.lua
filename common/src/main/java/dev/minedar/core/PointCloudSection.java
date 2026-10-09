package dev.minedar.core;

/**
 * A single 16x16x16 section of LiDAR points, stored as one packed {@code long}
 * per point. Nothing here is a heavyweight object: a section of N points costs
 * exactly one {@code long[]} of capacity, which is what keeps MiNEDAR from
 * turning world history into a RAM-hungry mess.
 *
 * <p>Packed layout (44 significant bits of a long):
 * <pre>
 *   bits  0..11  local position, 4 bits per axis (x | y&lt;&lt;4 | z&lt;&lt;8)
 *   bits 12..35  RGB colour, 8 bits per channel
 *   bits 36..43  intensity/alpha, 0..255
 * </pre>
 */
public final class PointCloudSection {

    /** Section edge length in blocks. */
    public static final int SIZE = 16;
    private static final int INITIAL_CAPACITY = 64;

    private long[] points;
    private int count;
    /**
     * Membership set of packed points already stored. Continuous scanning
     * re-casts the same cone every pass, so without dedup the same dot would be
     * appended indefinitely and a section would grow without bound while the
     * trigger is held. A section holds at most a few thousand distinct dots, so
     * this stays small; it is created lazily to keep tiny sections cheap.
     */
    private java.util.HashSet<Long> index;

    public PointCloudSection() {
        this(INITIAL_CAPACITY);
    }

    public PointCloudSection(int initialCapacity) {
        this.points = new long[Math.max(1, initialCapacity)];
        this.count = 0;
    }

    public static long pack(int lx, int ly, int lz, int rgb, int intensity) {
        if ((lx | ly | lz) < 0 || lx >= SIZE || ly >= SIZE || lz >= SIZE) {
            throw new IllegalArgumentException("local coords out of range: " + lx + "," + ly + "," + lz);
        }
        long pos = (lx & 0xF) | ((ly & 0xF) << 4) | ((lz & 0xF) << 8);
        long col = (rgb & 0xFFFFFFL) << 12;
        long inten = (intensity & 0xFFL) << 36;
        return pos | col | inten;
    }

    public static int localX(long packed) {
        return (int) (packed & 0xF);
    }

    public static int localY(long packed) {
        return (int) ((packed >>> 4) & 0xF);
    }

    public static int localZ(long packed) {
        return (int) ((packed >>> 8) & 0xF);
    }

    public static int rgb(long packed) {
        return (int) ((packed >>> 12) & 0xFFFFFF);
    }

    public static int intensity(long packed) {
        return (int) ((packed >>> 36) & 0xFF);
    }

    /** Adds a packed point, ignoring an exact duplicate. */
    public boolean add(long packed) {
        if (count > 64) {
            if (index == null) {
                index = new java.util.HashSet<>(count * 2);
                for (int i = 0; i < count; i++) {
                    index.add(points[i]);
                }
            }
            if (!index.add(packed)) {
                return false;
            }
        } else {
            for (int i = 0; i < count; i++) {
                if (points[i] == packed) {
                    return false;
                }
            }
        }
        if (count == points.length) {
            long[] grown = new long[points.length * 2];
            System.arraycopy(points, 0, grown, 0, count);
            points = grown;
        }
        points[count++] = packed;
        return true;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public long get(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(index);
        }
        return points[index];
    }

    /** Writes the packed points into {@code out}; returns the number copied. */
    public int copyInto(long[] out, int offset) {
        System.arraycopy(points, 0, out, offset, count);
        return count;
    }

    public long[] raw() {
        return points;
    }
}
