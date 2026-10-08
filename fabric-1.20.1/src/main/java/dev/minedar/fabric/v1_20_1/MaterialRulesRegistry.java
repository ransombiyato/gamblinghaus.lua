package dev.minedar.fabric.v1_20_1;

import dev.minedar.core.MaterialProfile;
import dev.minedar.core.MaterialRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Maps real 1.20.1 blocks/fluids onto the loader-independent material keys the
 * core understands (sections 12-23, 80). Unknown/modded content falls through to
 * generic rules rather than being special-cased, matching the compatibility
 * philosophy.
 */
public final class MaterialRulesRegistry {

    private final MaterialRules rules = new MaterialRules();

    public MaterialRules rules() {
        return rules;
    }

    /** Material key for a block state, or "solid" for ordinary opaque geometry. */
    public String keyFor(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
            return "glass";
        }
        if (block == Blocks.TINTED_GLASS) {
            return "tinted_glass";
        }
        if (isStainedGlass(block)) {
            return "stained_glass";
        }
        if (block == Blocks.WATER) {
            return "water";
        }
        if (block == Blocks.LAVA) {
            return "lava";
        }
        if (block == Blocks.ICE || block == Blocks.BLUE_ICE || block == Blocks.PACKED_ICE
                || block == Blocks.FROSTED_ICE) {
            return "ice";
        }
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) {
            return "snow";
        }
        if (block == Blocks.POWDER_SNOW) {
            return "powder_snow";
        }
        if (block == Blocks.COBWEB) {
            return "cobweb";
        }
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
            return "fire";
        }
        if (block == Blocks.NETHER_PORTAL) {
            return "nether_portal";
        }
        if (block == Blocks.END_PORTAL) {
            return "end_portal";
        }
        if (isLeaves(block)) {
            return "leaves";
        }
        return "solid";
    }

    /** Applies fluid transmission; a fluid block is not "solid" for the ray. */
    public boolean isFluid(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty();
    }

    private static boolean isStainedGlass(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.contains("stained_glass");
    }

    private static boolean isLeaves(Block block) {
        // Vanilla leaves, plus any block whose id ends with "_leaves" (modded too).
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.endsWith("_leaves") || path.equals("leaves");
    }

    /** Registers any additional custom material mappings. */
    public void registerCustom(String key, MaterialProfile profile) {
        rules.register(key, profile);
    }
}
