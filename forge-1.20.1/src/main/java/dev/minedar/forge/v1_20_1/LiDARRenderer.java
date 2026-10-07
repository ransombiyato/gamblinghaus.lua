package dev.minedar.forge.v1_20_1;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.minedar.core.Colour;
import dev.minedar.core.PointCloudSection;
import dev.minedar.core.SpatialChunkStore;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the LiDAR point cloud (sections 8, 74-75). Each dot is a small
 * camera-facing quad carrying the point's colour and intensity. Distance culling
 * happens before any vertex work, and only sections the client currently holds
 * are walked, so cost scales with what is visible rather than with total history.
 *
 * <p>Cached section meshes are a planned optimisation (section 75); today the
 * geometry is rebuilt per frame from compact packed longs, which keeps work
 * bounded without per-point objects.
 */
public final class LiDARRenderer {

    /** Half-extent of a dot quad in blocks. Kept small and GMod-like. */
    private static final float DOT_SIZE = 0.014f;
    private static final float MIN_INTENSITY = 0.15f;


    public void render(PoseStack pose, Camera camera, double maxDistance,
                       double camRightX, double camRightZ) {
        SpatialChunkStore cloud = MinedarClient.get().pointCloud();
        if (cloud.sectionCount() == 0) {
            return;
        }

        Vec3 cam = camera.getPosition();
        Matrix4f matrix = pose.last().pose();

        // Orthonormal screen basis derived from the camera yaw (upright dots).
        float rx = (float) camRightX;
        float rz = (float) camRightZ;
        float len = (float) Math.sqrt(rx * rx + rz * rz);
        if (len < 1e-6f) {
            rx = 1f;
            rz = 0f;
            len = 1f;
        }
        rx /= len;
        rz /= len;

        float far = (float) (maxDistance * maxDistance);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<Long, PointCloudSection> entry : cloud.view().entrySet()) {
            long key = entry.getKey();
            PointCloudSection section = entry.getValue();

            int sx = signExtend((int) ((key >> 42) & 0x3FFFFF), 22);
            int sy = signExtend((int) ((key >> 22) & 0xFFFFF), 20);
            int sz = signExtend((int) (key & 0x3FFFFF), 22);

            double baseX = sx << 4;
            double baseY = sy << 4;
            double baseZ = sz << 4;

            double dcx = baseX + 8 - cam.x;
            double dcy = baseY + 8 - cam.y;
            double dcz = baseZ + 8 - cam.z;
            if (dcx * dcx + dcy * dcy + dcz * dcz > far) {
                continue;
            }

            long[] raw = section.raw();
            int n = section.size();
            for (int i = 0; i < n; i++) {
                long p = raw[i];
                float wx = (float) (baseX + PointCloudSection.localX(p) + 0.5) - (float) cam.x;
                float wy = (float) (baseY + PointCloudSection.localY(p) + 0.5) - (float) cam.y;
                float wz = (float) (baseZ + PointCloudSection.localZ(p) + 0.5) - (float) cam.z;

                int rgb = PointCloudSection.rgb(p);
                float k = Math.max(MIN_INTENSITY, PointCloudSection.intensity(p) / 255.0f);
                float r = Colour.red(rgb) / 255.0f;
                float g = Colour.green(rgb) / 255.0f;
                float b = Colour.blue(rgb) / 255.0f;

                float h = DOT_SIZE * (0.6f + 0.8f * k);
                float ox = rx * h;
                float oz = rz * h;

                bb.vertex(matrix, wx - ox, wy - h, wz - oz).color(r, g, b, 1f).endVertex();
                bb.vertex(matrix, wx + ox, wy - h, wz + oz).color(r, g, b, 1f).endVertex();
                bb.vertex(matrix, wx + ox, wy + h, wz + oz).color(r, g, b, 1f).endVertex();
                bb.vertex(matrix, wx - ox, wy + h, wz - oz).color(r, g, b, 1f).endVertex();
            }
        }

        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }
}
