package dev.minedar.neoforge.v1_21_1;

import dev.minedar.core.MinimapDensityStore;
import dev.minedar.core.MinedarConfig;
import dev.minedar.core.PersistentScanStore;
import dev.minedar.core.ScanEngine;
import dev.minedar.core.ScannerController;
import dev.minedar.core.SpatialChunkStore;
import dev.minedar.core.WorldIdentity;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Client-side glue for Forge 1.20.1. Owns the live scanner/point-cloud state and
 * drives it from client ticks and input. All the actual logic is delegated to the
 * loader-independent core; this class only handles Minecraft events, the client
 * world, and disk IO for the current world+dimension.
 */

public final class MinedarClient {

    private static final Logger LOG = MinedarMod.LOGGER;

    private static final MinedarClient INSTANCE = new MinedarClient();

    private final ScannerController controller = new ScannerController();
    private final MaterialRulesRegistry materials = new MaterialRulesRegistry();
private final CachedSpatialStore pointCloud;
    private final MinimapDensityStore minimap =
            new MinimapDensityStore(32, 1.0);
    private final PeerScannerRegistry peers = new PeerScannerRegistry();
    private final NeoForgeWorldSampler sampler = new NeoForgeWorldSampler();

    private WorldIdentity currentWorld;
    private int saveCooldown;
    private int tickCounter;

    private MinedarClient() {
        this.pointCloud = null;
    }
    private final ScanEngine engine = new ScanEngine(materials.rules());
    private final SpatialChunkStore pointCloud = new SpatialChunkStore();
    private final MinimapDensityStore minimap =
            new MinimapDensityStore(32, 1.0);
    private final dev.minedar.core.PeerScannerRegistry peers = new dev.minedar.core.PeerScannerRegistry();
    private final ForgeWorldSampler sampler = new ForgeWorldSampler();

    private WorldIdentity currentWorld;
    private int saveCooldown;
    private int tickCounter;

    private MinedarClient() {
    }

    public static MinedarClient get() {
        return INSTANCE;
    }

    public ScannerController controller() {
        return controller;
    }

    public SpatialChunkStore pointCloud() {
        return pointCloud;
    }

    public MinimapDensityStore minimap() {
        return minimap;
    }

    public dev.minedar.core.PeerScannerRegistry peers() {
        return peers;
    }

    public MaterialRulesRegistry materials() {
public void onWorldChanged(ClientLevel level) {
        if (level == null) {
            flushIfNeeded();
            currentWorld = null;
            if (pointCloud != null) {
                pointCloud.clear();
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
        int maxSections = 500;
        Path worldDir = ScanStorage.pathFor(identity);
        pointCloud = new CachedSpatialStore(worldDir, maxSections);
        minimap.clear();
        LOG.info("MiNEDAR tracking world {}", identity.id());
    }
        return materials;
    }

    /** Called when the client joins a world/server or changes dimension. */
    public void onWorldChanged(ClientLevel level) {
        if (level == null) {
            flushIfNeeded();
            currentWorld = null;
            pointCloud.clear();
            minimap.clear();
            return;
        }
        WorldIdentity identity = identityFor(level);
        if (identity.equals(currentWorld)) {
            return;
        }
        flushIfNeeded();
        currentWorld = identity;
        pointCloud.clear();
        minimap.clear();
        loadFromDisk();
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
    public void performScan(java.util.List<double[]> dirs) {
public void flushIfNeeded() {
        if (saveCooldown > 0 && currentWorld != null) {
            saveCooldown = 0;
            if (pointCloud != null) {
                pointCloud.flush();
            }
        }
    }

    private void saveToDisk() {
        if (currentWorld == null) {
            return;
        }
        if (pointCloud != null) {
            pointCloud.flush();
        }
    }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || dirs.isEmpty()) {
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

        // Rotate the pattern from model space into world space using the player's look.
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        for (double[] d : dirs) {
            double[] world = rotateToWorld(d, yaw, pitch);
            var ray = new dev.minedar.core.Ray(ex, ey, ez, world[0], world[1], world[2]);
            engine.cast(ray, sampler, maxDist, pointCloud, minimap, ex, ez);
        }
        markDirty();
    }

    /** Converts a model-space direction into a world direction (Minecraft yaw/pitch). */
    static double[] rotateToWorld(double[] d, float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        // Minecraft: -Z forward. Apply pitch about X, then yaw about Y.
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
        if (saveCooldown > 0 && currentWorld != null) {
            saveCooldown = 0;
            saveToDisk();
        }
    }

    private Path storePath() {
        return ScanStorage.pathFor(currentWorld);
    }

    private void loadFromDisk() {
        if (currentWorld == null) {
            return;
        }
        try {
            Map<Long, dev.minedar.core.PointCloudSection> sections =
                    PersistentScanStore.load(storePath());
            for (var e : sections.entrySet()) {
                pointCloud.view().put(e.getKey(), e.getValue());
            }
            LOG.info("Loaded {} sections for {}", pointCloud.sectionCount(), currentWorld.id());
        } catch (IOException e) {
            LOG.warn("Could not load scan data for {}, starting fresh", currentWorld.id(), e);
        }
    }

    private void saveToDisk() {
        if (currentWorld == null) {
            return;
        }
        try {
            PersistentScanStore.save(storePath(), pointCloud.view());
        } catch (IOException e) {
            LOG.warn("Could not save scan data for {}", currentWorld.id(), e);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
        int rays = controller.tick();
        if (rays > 0) {
            performScan(controller.directionsFor(rays));
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
