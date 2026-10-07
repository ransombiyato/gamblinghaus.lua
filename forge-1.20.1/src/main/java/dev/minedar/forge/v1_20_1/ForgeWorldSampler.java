package dev.minedar.forge.v1_20_1;

import dev.minedar.core.EntityGeometry;
import dev.minedar.core.Ray;
import dev.minedar.core.SpecialBlockRules;
import dev.minedar.core.WorldSampler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Adapts the live 1.20.1 client world to the loader-independent
 * {@link WorldSampler} the scan engine casts against. Ordinary scene lighting is
 * ignored so LiDAR reveals geometry in darkness (section 49).
 */
public final class ForgeWorldSampler implements WorldSampler {

    @Override
    public SampledBlock blockAt(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        // Unloaded areas are simply not scannable; never crash on them (section 79).
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }

        MaterialRulesRegistry materials = MinedarClient.get().materials();
        String key = materials.keyFor(state);
        int rgb = BlockColours.of(state);

        boolean fluid = materials.isFluid(state);
        boolean solid = !fluid && state.canOcclude();

        SpecialBlockRules.BlockState bs = blockStateOf(state, rgb);
        SpecialBlockRules.Tint tint = SpecialBlockRules.tintFor(bs);
        int tintColour = tint == null ? -1 : tint.colour();
        double tintStrength = tint == null ? 0.0 : tint.strength();

        return new SampledBlock(key, rgb, solid, tintColour, tintStrength);
    }

    private static SpecialBlockRules.BlockState blockStateOf(BlockState state, int rgb) {
        String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        SpecialBlockRules.BlockState bs = new SpecialBlockRules.BlockState(id).baseRgb(rgb);
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            bs.powered(state.getValue(BlockStateProperties.POWERED));
        }
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            bs.open(state.getValue(BlockStateProperties.OPEN));
        }
        if (state.hasProperty(BlockStateProperties.EXTENDED)) {
            bs.extended(state.getValue(BlockStateProperties.EXTENDED));
        }
        return bs;
    }

    @Override
    public List<SampledEntity> entitiesAlong(Ray ray, double maxDistance) {
        List<SampledEntity> out = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return out;
        }
        Vec3 from = new Vec3(ray.ox, ray.oy, ray.oz);
        Vec3 to = new Vec3(ray.pointX(maxDistance), ray.pointY(maxDistance), ray.pointZ(maxDistance));
        AABB box = new AABB(from, to).inflate(1.5);
        float partial = Minecraft.getInstance().getPartialTick();
        for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
            // Real model geometry (section 40): project each posed model cube.
            List<EntityGeometry.Box> boxes = EntityModelCapture.boxes(e, partial);
            if (boxes.isEmpty()) {
                continue; // ScanEngine falls back to entityBoxesAlong for this entity
            }
            for (SampledEntity p : EntityGeometry.sample(boxes, EntityColours.colourFor(e))) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public List<EntityGeometry.Box> entityBoxesAlong(Ray ray, double maxDistance) {
        List<EntityGeometry.Box> out = new ArrayList<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return out;
        }
        Vec3 from = new Vec3(ray.ox, ray.oy, ray.oz);
        Vec3 to = new Vec3(ray.pointX(maxDistance), ray.pointY(maxDistance), ray.pointZ(maxDistance));
        AABB box = new AABB(from, to).inflate(1.5);
        for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
            AABB bb = e.getBoundingBox();
            out.add(new EntityGeometry.Box(bb.minX, bb.minY, bb.minZ,
                    bb.maxX, bb.maxY, bb.maxZ, EntityColours.colourFor(e)));
        }
        return out;
    }
}
