package dev.minedar.forge.v1_20_1;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import dev.minedar.core.Colour;
import dev.minedar.core.PointCloudRenderer;
import dev.minedar.core.SpatialChunkStore;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the LiDAR point cloud (sections 8, 74-75). The geometry maths lives in
 * {@link PointCloudRenderer}; this class only sets up the graphics state and
 * feeds the emitted camera-relative quads into a single vertex buffer.
 *
 * <p>The point cloud is drawn at {@code AFTER_LEVEL}, once the level render has
 * returned. That is deliberate: every earlier stage ({@code AFTER_SKY} through
 * {@code AFTER_WEATHER}) is dispatched from <em>inside</em>
 * {@code LevelRenderer.renderLevel}, which is cancelled while LiDAR hides the
 * world, so those stages never fire in LiDAR mode. {@code AFTER_LEVEL} is
 * dispatched by {@code GameRenderer} after that call returns, so it is the one
 * stage that always runs.
 *
 * <p>The model-view matrix is rebuilt from the camera rather than reused from the
 * level render: by the time {@code AFTER_LEVEL} runs the level's model-view has
 * been popped, so the point cloud applies the camera view transform itself and
 * uploads world-relative positions, exactly as {@code LevelRenderer} does for
 * terrain. This keeps the dots in world space regardless of what the level pass
 * left on the stack.
 */
public final class LiDARRenderer {

    /**
     * @param camera           the live camera
     * @param projectionMatrix the level projection matrix for this frame
     * @param maxDistance      scan distance, used for the section distance cull
     * @param camRightX        screen-right x derived from the camera yaw
     * @param camRightZ        screen-right z derived from the camera yaw
     */
    public void render(Camera camera, Matrix4f projectionMatrix,
                       double maxDistance, double camRightX, double camRightZ) {
        SpatialChunkStore cloud = MinedarClient.get().pointCloud();
        if (cloud.sectionCount() == 0) {
            return;
        }

        Vec3 cam = camera.getPosition();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        modelView.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0f));
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int emitted = PointCloudRenderer.forEachVisibleDot(cloud, cam.x, cam.y, cam.z,
                maxDistance, camRightX, camRightZ,
                (x, y, z, rx, rz, h, intensity, rgb) -> {
                    float r = Colour.red(rgb) / 255.0f;
                    float g = Colour.green(rgb) / 255.0f;
                    float b = Colour.blue(rgb) / 255.0f;
                    float a = 1.0f;
                    float ox = rx * h;
                    float oz = rz * h;
                    bb.vertex(x - ox, y - h, z - oz).color(r, g, b, a).endVertex();
                    bb.vertex(x + ox, y - h, z + oz).color(r, g, b, a).endVertex();
                    bb.vertex(x + ox, y + h, z + oz).color(r, g, b, a).endVertex();
                    bb.vertex(x - ox, y + h, z - oz).color(r, g, b, a).endVertex();
                });

        BufferBuilder.RenderedBuffer rendered = bb.end();
        if (emitted > 0) {
            BufferUploader.drawWithShader(rendered);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
