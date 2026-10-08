package dev.minedar.forge.v1_21_1;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.minedar.core.Colour;
import dev.minedar.core.PointCloudRenderer;
import dev.minedar.core.CachedSpatialStore;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Renders the LiDAR point cloud (sections 8, 74-75). The geometry maths lives in
 * {@link PointCloudRenderer}; this class only sets up the graphics state and
 * feeds the emitted camera-relative quads into a single vertex buffer.
 *
 * <p>1.21 replaced the pooled {@code Tesselator.getBuilder()} with an immediate
 * builder ({@code begin(...)}) that yields {@code MeshData} via {@code build()},
 * and the model-view {@code PoseStack} with a {@code Matrix4fStack}. The camera
 * view is applied directly onto that stack and world-relative positions are
 * uploaded, exactly as the level pass does for terrain.
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
        CachedSpatialStore cloud = MinedarClient.get().pointCloud();
        if (cloud.sectionCount() == 0) {
            return;
        }

        Vec3 cam = camera.getPosition();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.rotateX((float) Math.toRadians(camera.getXRot()));
        modelView.rotateY((float) Math.toRadians(camera.getYRot() + 180.0f));
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);

        int emitted = PointCloudRenderer.forEachVisibleDot(cloud.view(), cam.x, cam.y, cam.z,
                maxDistance, camRightX, camRightZ,
                (x, y, z, rx, rz, h, intensity, rgb) -> {
                    float r = Colour.red(rgb) / 255.0f;
                    float g = Colour.green(rgb) / 255.0f;
                    float b = Colour.blue(rgb) / 255.0f;
                    float a = 1.0f;
                    float ox = rx * h;
                    float oz = rz * h;
                    bb.addVertex(x - ox, y - h, z - oz).setColor(r, g, b, a);
                    bb.addVertex(x + ox, y - h, z + oz).setColor(r, g, b, a);
                    bb.addVertex(x + ox, y + h, z + oz).setColor(r, g, b, a);
                    bb.addVertex(x - ox, y + h, z - oz).setColor(r, g, b, a);
                });

        MeshData mesh = bb.build();
        if (emitted > 0 && mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        modelView.popMatrix();
    }
}
