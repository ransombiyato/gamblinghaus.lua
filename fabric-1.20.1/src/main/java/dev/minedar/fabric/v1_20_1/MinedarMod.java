package dev.minedar.fabric.v1_20_1;

import dev.minedar.core.MinedarConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Fabric 1.20.1 build of MiNEDAR. This class only wires the
 * mod up; all LiDAR logic lives in the loader-independent {@code common} module
 * and the client-side handler in this module.
 */
public final class MinedarMod implements ClientModInitializer {

    public static final String MOD_ID = "minedar";
    public static final Logger LOGGER = LoggerFactory.getLogger("MiNEDAR");

    private static java.nio.file.Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("minedar.json");
    }

    /** Shared, loader-independent configuration. */
    private static MinedarConfig config = MinedarConfig.defaults();

    @Override
    public void onInitializeClient() {
        config = FabricConfigIO.load(configPath());
        MinedarKeybinds.register();
        MinedarClient.register();
        LOGGER.info("MiNEDAR loaded (Fabric 1.20.1), minimap={} radius={}",
                config.minimapVisible, config.minimapRadiusBlocks);
    }

    public static MinedarConfig config() {
        return config;
    }

    public static void saveConfig() {
        FabricConfigIO.save(configPath(), config);
    }
}
