package dev.minedar.forge.v1_20_1;

import dev.minedar.core.CachedSpatialStore;
import dev.minedar.core.MinimapDensityStore;
import dev.minedar.core.PeerScannerRegistry;
import dev.minedar.core.ScanEngine;
import dev.minedar.core.ScannerController;
import dev.minedar.core.WorldIdentity;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Client-side glue for Forge 1.20.1. Owns the live scanner/point-cloud state and
 * drives it from client ticks and input. All the actual logic is delegated to the
 * loader-independent core; this class only handles Minecraft events, the client
 * world, and disk IO for the current world+dimension.
 */
@Mod.EventBusSubscriber(modid = MinedarMod.MOD_ID, value = Dist.CLIENT)
public final class MinedarClient {

    private static final Logger LOG = MinedarMod.LOGGER;
    private static final MinedarClient INSTANCE = new MinedarClient();

    private final ScannerController controller = new ScannerController();
    private final MaterialRulesRegistry materials = new MaterialRulesRegistry();
    private final ScanEngine engine = new ScanEngine(materials.rules());
    private final MinimapDensityStore minimap = new MinimapDensityStore(32, 1.0);
    private final PeerScannerRegistry peers = new PeerScannerRegistry();
    private final ForgeWorldSampler sampler = new ForgeWorldSampler();

    private CachedSpatialStore pointCloud;
    private WorldIdentity currentWorld;
    private int saveCooldown;
    private int tickCounter;

    private MinedarClient() {
        // pointCloud is created when a world is loaded.
    }

    public static MinedarClient get() {
        return INSTANCE;
    }

    public ScannerController controller() {
        return controller;
    }

    public CachedSpatialStore pointCloud() {
        return pointCloud;
    }

    public MinimapDensityStore minimap() {
        return minimap;
    }

    public PeerScannerRegistry peers() {
        return peers;
    }

    public MaterialRulesRegistry materials() {
        return materials;
    }

    /** Called when the client joins a world/server or changes dimension. */
    public void onWorldChanged(ClientLevel level) {
        if (level == null) {
            flushIfNeeded();
            currentWorld = null;
            if (pointCloud != null) {
                pointCloud.clear();
                pointCloud = null;
            }
            minimap.clear();
            return;
        }
        WorldIdentity identity = identityFor(level);
        if (identity.equals(currentWorld)) {
            return;
        }
        flushIfNeeded();
        currentWorld = identity;
        Path worldDir = ScanStorage.pathFor(identity);
        pointCloud = new CachedSpatialStore(worldDir, 500);
        minimap.clear();
        LOG.info("MiNEDAR tracking world {}", identity.id());
    }

    private WorldIdentity identityFor(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        String worldKey;
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            worldKey = mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            worldKey = mc.getCurrentServer().ip;
        } else {
            worldKey = "unknown";
        }
        String dimension = level.dimension().location().toString();
        return new WorldIdentity(worldKey, dimension);
    }

    /** Fires a scan for each ray direction, writing results into the cloud. */
    public void performScan(List<double[]> dirs) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || dirs.isEmpty() || pointCloud == null) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double ex = player.getX();
        double ey = player.getEyeY();
        double ez = player.getZ();
        double maxDist = MinedarMod.config().scanDistance;

        float yaw = player.getYRot();
        float pitch = player.getXRot();
        for (double[] d : dirs) {
            double[] world = rotateToWorld(d, yaw, pitch);
            var ray = new dev.minedar.core.Ray(ex, ey, ez, world[0], world[1], world[2]);
            engine.cast(ray, sampler, maxDist, pointCloud, minimap, ex, ez);
        }
        markDirty();
    }

    static double[] rotateToWorld(double[] d, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double x1 = d[0];
        double y1 = d[1] * Math.cos(pitch) - d[2] * Math.sin(pitch);
        double z1 = d[1] * Math.sin(pitch) + d[2] * Math.cos(pitch);
        double x2 = x1 * Math.cos(yaw) + z1 * Math.sin(yaw);
        double z2 = -x1 * Math.sin(yaw) + z1 * Math.cos(yaw);
        return new double[] {x2, y1, z2};
    }

    private void markDirty() {
        saveCooldown = 40; // debounce: write at most ~2s after the last scan
    }

    public void flushIfNeeded() {
        if (saveCooldown > 0 && currentWorld != null && pointCloud != null) {
            saveCooldown = 0;
            pointCloud.flush();
        }
    }

    private void saveToDisk() {
        if (currentWorld != null && pointCloud != null) {
            pointCloud.flush();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        INSTANCE.tickClient();
    }

    private void tickClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        onWorldChanged(mc.level);
        tickCounter++;

        MinedarKeybinds.applyHeldState(controller);
        controller.tick();

        int batchSize;
        ScannerController.ScanMode mode = controller.mode();
        if (mode == ScannerController.ScanMode.CONTINUOUS) {
            batchSize = 15;
        } else if (mode == ScannerController.ScanMode.BURST) {
            batchSize = 50;
        } else {
            batchSize = 0;
        }

        if (batchSize > 0 && controller.isScanning()) {
            List<double[]> batch = controller.getBatch(batchSize);
            if (batch != null && !batch.isEmpty()) {
                performScan(batch);
            }
        }

        if (MinedarMod.config().minimapVisible) {
            minimap.decayStep();
        }
        if (saveCooldown > 0) {
            saveCooldown--;
            if (saveCooldown == 0) {
                saveToDisk();
            }
        }
    }

    public int tickCounter() {
        return tickCounter;
    }
}
