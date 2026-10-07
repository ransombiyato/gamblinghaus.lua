package dev.minedar.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Records a bounded, dimension-independent stream of recently observed dots and
 * turns it into a chunk/cell density grid for the heatmap minimap (sections
 * 55-58, 76). This is deliberately NOT the persistent historical store: it is a
 * compact rolling density summary around the player, so the minimap never has to
 * load the full world database.
 *
 * <p>Each cell is a packed counter. Old observations decay so the heatmap stays
 * live without unbounded growth.
 */
public final class MinimapDensityStore {

    /** Cell edge in blocks. One heatmap cell per 4x4 blocks. */
    public static final int CELL = 4;

    private final int radiusCells;
    private final int[] cells;
    private final int side;
    private final double decay;

    /**
     * @param radiusCells number of cells from centre to edge (the display radius)
     * @param decay       per-frame multiplicative decay applied to all cells
     */
    public MinimapDensityStore(int radiusCells, double decay) {
        this.radiusCells = radiusCells;
        this.decay = Math.max(0.0, Math.min(1.0, decay));
        this.side = radiusCells * 2 + 1;
        this.cells = new int[side * side];
    }

    public int radiusCells() {
        return radiusCells;
    }

    public int side() {
        return side;
    }

    private int index(int cx, int cz) {
        int ix = cx + radiusCells;
        int iz = cz + radiusCells;
        if (ix < 0 || iz < 0 || ix >= side || iz >= side) {
            return -1;
        }
        return iz * side + ix;
    }

    /** Adds one observation at world coordinates relative to the player. */
    public void add(double relX, double relZ, int weight) {
        int cx = (int) Math.floor(relX / CELL);
        int cz = (int) Math.floor(relZ / CELL);
        int idx = index(cx, cz);
        if (idx < 0) {
            return; // outside the minimap radius: hard boundary, not faded
        }
        cells[idx] = Math.min(Caps.MAX_DENSITY, cells[idx] + weight);
    }

    /** Applies one global decay step; call once per tick while the world ticks. */
    public void decayStep() {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = (int) (cells[i] * decay);
        }
    }

    public int densityAt(int cx, int cz) {
        int idx = index(cx, cz);
        return idx < 0 ? 0 : cells[idx];
    }

    /** Normalised [0,1] density for a cell, capped at {@link Caps#MAX_DENSITY}. */
    public double normalised(int cx, int cz) {
        return Math.min(1.0, densityAt(cx, cz) / (double) Caps.MAX_DENSITY);
    }

    /** Heat colour for a cell using the smooth blue-cyan-green-yellow-red ramp. */
    public int heatColour(int cx, int cz) {
        return Colour.heat(normalised(cx, cz));
    }

    public void clear() {
        java.util.Arrays.fill(cells, 0);
    }

    /** Dense snapshot of non-zero cells for rendering/tests. */
    public List<int[]> nonZeroCells() {
        List<int[]> out = new ArrayList<>();
        for (int cz = -radiusCells; cz <= radiusCells; cz++) {
            for (int cx = -radiusCells; cx <= radiusCells; cx++) {
                int d = densityAt(cx, cz);
                if (d > 0) {
                    out.add(new int[] {cx, cz, d});
                }
            }
        }
        return out;
    }

    /** Small helper for bounded queues used by callers that batch observations. */
    public static <T> Deque<T> boundedQueue(int max) {
        return new ArrayDeque<>(max);
    }

    /** Tunable caps shared across the mod. */
    public static final class Caps {
        /** More dots beyond this do not brighten the cell further (section 58). */
        public static final int MAX_DENSITY = 64;

        private Caps() {
        }
    }
}
