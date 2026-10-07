package dev.minedar.core;

/**
 * State-aware overrides for blocks whose LiDAR appearance depends on more than
 * their material (sections 23-26, 37-39). State is captured at the moment of
 * scanning; nothing here tracks changes afterwards, matching the historical
 * model.
 *
 * <p>The loader adapter supplies a {@link BlockState} filled from the real
 * block; this class is pure so every rule is unit-testable.
 */
public final class SpecialBlockRules {

    /** Everything a rule might need to know about a block at scan time. */
    public static final class BlockState {
        public final String blockId;
        /** Redstone/piston/etc. powered or active. */
        public boolean powered;
        /** Door / trapdoor / fence gate open. */
        public boolean open;
        /** Piston extended. */
        public boolean extended;
        /** Sticky piston or slime section. */
        public boolean sticky;
        /** Fluid level 0..8 (source = 8). */
        public int fluidLevel = 8;
        /** Base colour derived from the block for tinting. */
        public int baseRgb = Colour.WHITE;

        public BlockState(String blockId) {
            this.blockId = blockId;
        }

        public BlockState powered(boolean v) {
            powered = v;
            return this;
        }

        public BlockState open(boolean v) {
            open = v;
            return this;
        }

        public BlockState extended(boolean v) {
            extended = v;
            return this;
        }

        public BlockState sticky(boolean v) {
            sticky = v;
            return this;
        }

        public BlockState fluidLevel(int v) {
            fluidLevel = v;
            return this;
        }

        public BlockState baseRgb(int v) {
            baseRgb = v;
            return this;
        }
    }

    /** Dark red for unpowered redstone, brighter red when powered (section 23). */
    public static final int REDSTONE_DARK = 0x7A1010;
    public static final int REDSTONE_BRIGHT = 0xE03030;

    private SpecialBlockRules() {
    }

    /** Tint multiplier (0..1 strength) and colour for a block, or null if none. */
    public record Tint(int colour, double strength) {
    }

    /**
     * Returns the tint to apply to retained dots, or {@code null} for ordinary
     * geometry. Applies only where the spec states behaviour actually differs.
     */
    public static Tint tintFor(BlockState state) {
        String id = state.blockId == null ? "" : state.blockId.toLowerCase();

        if (id.contains("redstone") && (id.contains("wire") || id.contains("dust")
                || id.contains("block") || id.contains("torch") || id.contains("repeater")
                || id.contains("comparator") || id.contains("lamp"))) {
            return new Tint(state.powered ? REDSTONE_BRIGHT : REDSTONE_DARK, 0.7);
        }
        if (id.contains("piston") && state.sticky) {
            return new Tint(0x90D060, 0.35); // sticky/slime section
        }
        if (id.contains("nether_portal")) {
            return new Tint(0x8B30C8, 0.75);
        }
        if (id.contains("end_portal") && !id.contains("frame")) {
            return new Tint(0x103040, 0.6);
        }
        if (id.contains("end_portal_frame")) {
            // Uses the frame's actual block colour rather than a portal tint.
            return new Tint(state.baseRgb, 0.2);
        }
        if (id.contains("obsidian")) {
            return new Tint(state.baseRgb, 0.25);
        }
        if (id.endsWith("_bed") || id.equals("bed")) {
            // Subtle tint from the bed's own colour.
            return new Tint(state.baseRgb, 0.25);
        }
        return null;
    }

    /**
     * True when a block's retained dots should use the tint even though the tint
     * strength is low (terracotta keeps texture-derived colour variation, an
     * explicit exception in section 38).
     */
    public static boolean keepsTextureColour(String blockId) {
        return blockId != null && blockId.toLowerCase().contains("terracotta");
    }

    /** Thin geometry blocks: gaps must stay gaps, no tint (section 36, 39). */
    public static boolean isThinGeometry(String blockId) {
        if (blockId == null) {
            return false;
        }
        String id = blockId.toLowerCase();
        return id.contains("ladder") || id.contains("vine") || id.contains("scaffolding")
                || id.contains("rail") || id.contains("chain") || id.contains("bars");
    }

    /**
     * Interaction preview for doors/trapdoors/fence gates (section 24). These are
     * dark-grey outlines of the next physical position and are never persisted.
     */
    public static int interactionPreviewColour() {
        return 0x404040;
    }

    public static boolean isInteractableTransformable(String blockId) {
        if (blockId == null) {
            return false;
        }
        String id = blockId.toLowerCase();
        return id.endsWith("_door") || id.contains("trapdoor") || id.contains("fence_gate");
    }
}
