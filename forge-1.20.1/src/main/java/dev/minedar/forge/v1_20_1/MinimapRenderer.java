package dev.minedar.forge.v1_20_1;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minedar.core.MinimapDensityStore;
import dev.minedar.core.MinimapLayout;
import dev.minedar.core.PeerScannerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the minimap, the fullscreen map, and the small LiDAR crosshair
 * (sections 4, 55-71). Density comes from the {@link MinimapDensityStore}, which
 * is deliberately NOT the persistent scan database (section 76). The map is a
 * rounded square that rotates with the player, uses a hard radius boundary, a
 * pink chunk grid that stays visible over hot cells, a blue frame, cardinal
 * labels, a player marker and compact coordinate/dimension text.
 */
public final class MinimapRenderer {

    private static final double CELL_PX = 2.0;
    private static final double MINIMAP_HALF_PX = 60.0;

    public void renderCrosshair(GuiGraphics g, int width, int height) {
        var c = MinedarClient.get().controller();
        if (!c.isLidarOn() || !c.isScannerEquipped()) {
            return;
        }
        int cx = width / 2;
        int cy = height / 2;
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF); // small white dot
    }

    /** The corner minimap. Hidden while the fullscreen map is open. */
    public void renderMinimap(GuiGraphics g, int width, int height, float partialTick) {
        if (!MinedarMod.config().minimapVisible || FullscreenMapState.isOpen()) {
            return;
        }
        drawMap(g, width, height, MINIMAP_HALF_PX, false, partialTick);
    }

    /** The fullscreen map: same design, fixed larger radius, fast animation. */
    public void renderFullscreen(GuiGraphics g, int width, int height, float partialTick) {
        if (!FullscreenMapState.isOpen()) {
            return;
        }
        double progress = FullscreenMapState.progress();
        double full = Math.min(width, height) * 0.46;
        double half = MinimapLayout.interpolatedRadius(MINIMAP_HALF_PX, full, progress);
        drawMap(g, width, height, half, true, partialTick);

        // Darken everything else so only the map interface remains (section 72).
        int shade = (int) (0xB0 * progress) << 24;
        // (drawn first would be ideal; Gui mixin already blanks vanilla HUD)
    }

    private void drawMap(GuiGraphics g, int width, int height, double halfPx,
                         boolean fullscreen, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        MinimapDensityStore density = MinedarClient.get().minimap();
        double mapRadiusBlocks = fullscreen
                ? MinedarMod.config().minimapRadiusBlocks * MinedarMod.config().fullscreenRadiusMultiplier
                : MinedarMod.config().minimapRadiusBlocks;
        double pxPerBlock = halfPx / mapRadiusBlocks;

        double centerX = width / 2.0;
        double centerY = height / 2.0;
        if (!fullscreen) {
            centerX = 12 + halfPx;
            centerY = 12 + halfPx;
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        // Rotate with the player's facing (section 61). Minecraft yaw increases
        // clockwise, screen rotation is counter-clockwise, so negate.
        pose.mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot()));

        // Heat cells. Density cells are 4 blocks; map each to a filled square.
        for (int[] cell : density.nonZeroCells()) {
            int cx = cell[0];
            int cz = cell[1];
            int colour = density.heatColour(cx, cz);
            double bx = cx * MinimapDensityStore.CELL * pxPerBlock;
            double bz = cz * MinimapDensityStore.CELL * pxPerBlock;
            double size = MinimapDensityStore.CELL * pxPerBlock + 0.5;
            // Hard radius boundary, no fading at the edge (section 57).
            if (Math.abs(bx) > halfPx || Math.abs(bz) > halfPx) {
                continue;
            }
            drawCell(g, bx, bz, size, colour);
        }

        // Chunk grid: thin pink lines that remain visible over hot cells (section 59).
        int[] lines = MinimapLayout.chunkLines((int) mapRadiusBlocks);
        for (int line : lines) {
            double p = line * pxPerBlock;
            if (Math.abs(p) > halfPx) {
                continue;
            }
            g.fill((int) p, (int) -halfPx, (int) p + 1, (int) halfPx,
                    withAlpha(MinimapLayout.CHUNK_LINE_COLOUR, 0x90));
            g.fill((int) -halfPx, (int) p, (int) halfPx, (int) p + 1,
                    withAlpha(MinimapLayout.CHUNK_LINE_COLOUR, 0x90));
        }

        // Other MiNEDAR scanning players as pulsing radar blips (section 66).
        double time = (mc.level != null ? mc.level.getGameTime() : 0) / 20.0 + partialTick / 20.0;
        double pulse = PeerScannerRegistry.pulse(time);
        for (PeerScannerRegistry.Peer peer : MinedarClient.get().peers().activePeers()) {
            double[] off = PeerScannerRegistry.screenOffset(
                    peer.x - mc.player.getX(), peer.z - mc.player.getZ(),
                    mapRadiusBlocks, halfPx);
            if (off == null) {
                continue;
            }
            int r = (int) (PeerScannerRegistry.INDICATOR_RADIUS_PX * (0.6 + pulse));
            g.fill((int) off[0] - r, (int) off[1] - r, (int) off[0] + r, (int) off[1] + r,
                    withAlpha(PeerScannerRegistry.INDICATOR_COLOUR, (int) (0x80 + 0x70 * pulse)));
        }

        pose.popPose();

        // Player marker: dot plus facing triangle, drawn unrotated at centre but
        // rotated to show heading (section 62).
        drawPlayerMarker(g, centerX, centerY, mc.player.getYRot());
        drawFrame(g, centerX, centerY, halfPx);
        drawLabels(g, width, height, centerX, centerY, halfPx, mc, fullscreen);
    }

    private void drawCell(GuiGraphics g, double bx, double bz, double size, int colour) {
        g.fill((int) bx, (int) bz, (int) (bx + size), (int) (bz + size), colour | 0xFF000000);
    }

    private void drawPlayerMarker(GuiGraphics g, double cx, double cy, float yaw) {
        int x = (int) cx;
        int y = (int) cy;
        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFFFFFFFF);
        // Triangle pointing along facing: rotate a small offset by yaw.
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        int tx = (int) (cx + fx * 6);
        int ty = (int) (cy + fz * 6);
        g.fill(tx - 1, ty - 1, tx + 1, ty + 1, 0xFFFFFFFF);
    }

    private void drawFrame(GuiGraphics g, double cx, double cy, double half) {
        int colour = MinimapLayout.FRAME_COLOUR | 0xFF000000;
        // Squircle outline approximated with short segments along a superellipse.
        int steps = 48;
        double exponent = 2.0 / 4.0; // superellipse n=4 -> |x|^4+|y|^4=1
        int prevX = 0;
        int prevY = 0;
        for (int i = 0; i <= steps; i++) {
            double t = (2 * Math.PI * i) / steps;
            double c = Math.cos(t);
            double s = Math.sin(t);
            double nx = Math.signum(c) * Math.pow(Math.abs(c), exponent);
            double nz = Math.signum(s) * Math.pow(Math.abs(s), exponent);
            int x = (int) (cx + nx * half);
            int y = (int) (cy + nz * half);
            if (i > 0) {
                drawLine(g, prevX, prevY, x, y, colour);
            }
            prevX = x;
            prevY = y;
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int colour) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) + 1;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private void drawLabels(GuiGraphics g, int width, int height, double cx, double cy,
                            double half, Minecraft mc, boolean fullscreen) {
        var font = mc.font;
        // Cardinal labels rotate with the map (section 61); only N/S/E/W.
        float yaw = mc.player.getYRot();
        for (String cardinal : MinimapLayout.CARDINALS) {
            double angle = Math.toRadians(MinimapLayout.cardinalScreenAngle(cardinal, yaw));
            int x = (int) (cx + Math.sin(angle) * (half - 8));
            int y = (int) (cy - Math.cos(angle) * (half - 8));
            g.drawString(font, cardinal, x - font.width(cardinal) / 2, y - 4, 0xFFFFFFFF, true);
        }

        int left = (int) (cx - half);
        int top = (int) (cy - half);
        int right = (int) (cx + half);
        // Coordinates top-left, dimension top-right (sections 63-64).
        String coords = "X: " + (int) Math.floor(mc.player.getX())
                + " Z: " + (int) Math.floor(mc.player.getZ());
        g.drawString(font, coords, left, top - 10, 0xFFFFFFFF, true);
        String dim = "dim: " + dimensionLabel(mc);
        g.drawString(font, dim, right - font.width(dim), top - 10, 0xFFFFFFFF, true);
    }

    private static String dimensionLabel(Minecraft mc) {
        if (mc.level == null) {
            return "OVERWORLD";
        }
        String path = mc.level.dimension().location().getPath();
        return path.toUpperCase();
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
