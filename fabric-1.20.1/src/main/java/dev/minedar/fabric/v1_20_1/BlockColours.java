package dev.minedar.fabric.v1_20_1;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Derives an approximate dot colour for a block from its map colour. This is a
 * cheap, generic way to tint retained dots toward the real block colour without
 * per-block hard-coding (section 80). Terracotta's texture variation is handled
 * by the renderer reading the block texture (section 38, the explicit exception).
 */
final class BlockColours {

    private BlockColours() {
    }

    static int of(BlockState state) {
        try {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return 0xFFFFFF;
            }
            return state.getMapColor(level, BlockPos.ZERO).col;
        } catch (RuntimeException e) {
            // Unknown/modded block: fall back to white rather than failing.
            return 0xFFFFFF;
        }
    }
}
