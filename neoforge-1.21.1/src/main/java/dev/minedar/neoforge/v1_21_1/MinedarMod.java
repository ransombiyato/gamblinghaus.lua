package dev.minedar.neoforge.v1_21_1;

import dev.minedar.core.MinedarConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the NeoForge 1.21.1 build of MiNEDAR. This class only wires the
 * mod up; all LiDAR logic lives in the loader-independent {@code common} module
 * and the client-side handler in this module. The {@code Dist.CLIENT} marker
 * keeps the constructor off a dedicated server, matching the client-only mod.
 */
@Mod(value = MinedarMod.MOD_ID, dist = Dist.CLIENT)
public final class MinedarMod {

    public static final String MOD_ID = "minedar";
    public static final Logger LOGGER = LoggerFactory.getLogger("MiNEDAR");

    /** Shared, loader-independent configuration. */
    private static MinedarConfig config = MinedarConfig.defaults();

    public MinedarMod(IEventBus modBus) {
        config = ForgeConfigIO.load(FMLPaths.CONFIGDIR.get().resolve("minedar.json"));
        MinedarKeybinds.register(modBus);
        ClientEvents.register(modBus);
        NeoForge.EVENT_BUS.register(MinedarClient.class);
        NeoForge.EVENT_BUS.register(MinedarKeybinds.class);
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        LOGGER.info("MiNEDAR loaded (NeoForge 1.21.1), minimap={} radius={}",
                config.minimapVisible, config.minimapRadiusBlocks);
    }

    public static MinedarConfig config() {
        return config;
    }

    public static void saveConfig() {
        ForgeConfigIO.save(FMLPaths.CONFIGDIR.get().resolve("minedar.json"), config);
    }
}
