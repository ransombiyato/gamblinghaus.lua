package dev.minedar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A decoded 2D image (sign text, a painting, a map, an item icon) turned into
 * LiDAR dots (sections 38, 46-47).
 *
 * <p>This is the third point source, alongside voxel and entity-model geometry:
 * content that only exists as a rendered surface is captured as pixels —
 * rendered to a texture and read back — then colour-sampled here. Alpha below
 * the threshold is treated as empty space so text gaps and transparent regions
 * stay gaps rather than becoming solid slabs.
 *
 * <p>Pixels are {@code 0xAARRGGBB}. Sampling is deliberately downsampleable so
 * a large painting does not flood the cloud with more points than the geometry
 * around it.
 */
public final class PixelSurface {

    /** Receives one retained pixel sample in surface pixel coordinates. */
    public interface Visitor {
        void visit(int x, int y, int rgb);
    }

    private final int width;
    private final int height;
    private final int[] argb;

    public PixelSurface(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("surface must be at least 1x1");
        }
        if (argb.length < width * height) {
            throw new IllegalArgumentException(
                    "pixel buffer too small: " + argb.length + " < " + (width * height));
        }
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int argbAt(int x, int y) {
        return argb[y * width + x];
    }

    /** Alpha of a pixel, 0-255. */
    public static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** Opaque RGB, dropping alpha. */
    public static int rgbOf(int argb) {
        return argb & 0xFFFFFF;
    }

    /**
     * Visits every pixel whose alpha meets the threshold, in row-major order,
     * emitting its visible colour.
     */
    public void forEachOpaque(int alphaThreshold, Visitor visitor) {
        forEachOpaque(alphaThreshold, 1, visitor);
    }

    /**
     * As {@link #forEachOpaque(int, Visitor)} but samples every {@code step}-th
     * pixel, which controls point density for large surfaces.
     */
    public void forEachOpaque(int alphaThreshold, int step, Visitor visitor) {
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1");
        }
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int p = argb[y * width + x];
                if (alphaOf(p) >= alphaThreshold) {
                    visitor.visit(x, y, rgbOf(p));
                }
            }
        }
    }

    /** Convenience: collect samples as {@code {x, y, rgb}} triples. */
    public List<int[]> samples(int alphaThreshold) {
        List<int[]> out = new ArrayList<>();
        forEachOpaque(alphaThreshold, (x, y, rgb) -> out.add(new int[] {x, y, rgb}));
        return out;
    }
}
