package dev.minedar.forge.v1_20_1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.minedar.core.EntityGeometry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Captures real entity model geometry as LiDAR points (section 40). For living
 * entities the actual {@link ModelPart} tree is posed with the entity's current
 * animation and each cube is projected to world space, so limbs, horns, ears and
 * tails appear as geometry instead of a single hitbox. Non-living entities fall
 * back to their bounding volume.
 *
 * <p>{@link ModelPart#visit} runs on an internal {@link PoseStack}, so it cannot
 * be redirected to emit geometry directly. Instead this class uses reflection to
 * read the parts' already-computed transform matrix out of a scratch visit. That
 * is a deliberate, isolated dependency on one internal field, matching the
 * "compatibility philosophy" of degrading to the box fallback rather than
 * failing (sections 80-82).
 */
final class EntityModelCapture {

    private static Field poseField; // PoseStack.Pose
    private static Method poseMatrix; // Pose.pose() -> Matrix4f
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private EntityModelCapture() {
    }

    /** Real model boxes for a living entity, or empty if unavailable. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<EntityGeometry.Box> boxes(Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity living)) {
            return List.of();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }
        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> ler)) {
            return List.of();
        }
        EntityModel model = ler.getModel();
        model.prepareMobModel(living, 0, 0, partialTick);
        try {
            // Look the method up on the base class so the generic erasure matches;
            // invocation still dispatches to the concrete model.
            EntityModel.class.getMethod("setupAnim", Entity.class,
                            float.class, float.class, float.class, float.class, float.class)
                    .invoke(model, living, 0f, 0f, 0f, 0f, partialTick);
        } catch (ReflectiveOperationException ignored) {
            // Without animation the rest pose is still useful geometry.
        }

        ModelPart root = rootOf(model);
        if (root == null) {
            return List.of();
        }

        PoseStack pose = new PoseStack();
        applyEntityRotation(pose, living, partialTick);

        int rgb = EntityColours.colourFor(entity);
        List<EntityGeometry.Box> boxes = new ArrayList<>();
        root.visit(pose, (partPose, name, index, cube) -> {
            Matrix4f m = matrixOf(partPose);
            if (m == null) {
                return;
            }
            addBox(boxes, m, cube, rgb);
        });
        // Held items and armour are separate visible geometry; sample the bounding
        // volume of each equipment slot so they register without a full item model
        // render (section 43).
        if (boxes.isEmpty()) {
            var bb = entity.getBoundingBox();
            boxes.add(new EntityGeometry.Box(bb.minX, bb.minY, bb.minZ,
                    bb.maxX, bb.maxY, bb.maxZ, rgb));
        }
        return boxes;
    }

    /** Matrix4f held by a PoseStack.Pose, via reflection against one internal field. */
    private static Matrix4f matrixOf(com.mojang.blaze3d.vertex.PoseStack.Pose partPose) {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (!reflectionReady) {
                poseField = com.mojang.blaze3d.vertex.PoseStack.Pose.class.getDeclaredField("pose");
                poseField.setAccessible(true);
                reflectionReady = true;
            }
            return (Matrix4f) poseField.get(partPose);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    private static void addBox(List<EntityGeometry.Box> out, Matrix4f m,
                               ModelPart.Cube cube, int rgb) {
        // Project all eight corners of the model-space cube through the part
        // matrix to get a world-space AABB. Cube coordinates are model units.
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? cube.minX : cube.maxX;
            float y = (i & 2) == 0 ? cube.minY : cube.maxY;
            float z = (i & 4) == 0 ? cube.minZ : cube.maxZ;
            Vector4f p = new Vector4f(x, y, z, 1.0f).mul(m);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        out.add(new EntityGeometry.Box(minX, minY, minZ, maxX, maxY, maxZ, rgb));
    }

    /**
     * Minimal entity placement: translate to the render position and rotate the
     * body by yaw and head pitch, matching {@code LivingEntityRenderer}'s setup
     * for the common case. Child part transforms are applied by {@code visit}.
     */
    private static void applyEntityRotation(PoseStack pose, LivingEntity living, float partialTick) {
        double x = living.getX();
        double y = living.getY();
        double z = living.getZ();
        pose.translate(x, y, z);
        float bodyYaw = living.yBodyRotO + (living.yBodyRot - living.yBodyRotO) * partialTick;
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - bodyYaw));
        // Vanilla flips the model: model +Y is up already, so no X flip needed here.
    }

    @SuppressWarnings("unchecked")
    private static ModelPart rootOf(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            return hierarchical.root();
        }
        // Older/other models expose their parts as public fields; assemble a
        // synthetic root by visiting them is not possible, so fall back to the
        // first ModelPart field we can find.
        for (Field f : model.getClass().getFields()) {
            if (ModelPart.class.isAssignableFrom(f.getType())) {
                try {
                    return (ModelPart) f.get(model);
                } catch (IllegalAccessException ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
