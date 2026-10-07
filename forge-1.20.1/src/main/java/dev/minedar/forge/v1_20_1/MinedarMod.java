package dev.minedar.forge.v1_20_1;

import dev.minedar.core.MinedarConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Forge 1.20.1 build of MiNEDAR. This class only wires the
 * mod up; all LiDAR logic lives in the loader-independent {@code common} module
 * and the client-side handler in this module.
 */
@Mod(MinedarMod.MOD_ID)
public final class MinedarMod {

    public static final String MOD_ID = "minedar";
    public static final Logger LOGGER = LoggerFactory.getLogger("MiNEDAR");

    /** Shared, loader-independent configuration. */
    private static MinedarConfig config = MinedarConfig.defaults();

    public MinedarMod() {
        config = ForgeConfigIO.load(FMLPaths.CONFIGDIR.get().resolve("minedar.json"));
        MinedarKeybinds.register();
        LOGGER.info("MiNEDAR loaded (Forge 1.20.1), minimap={} radius={}",
                config.minimapVisible, config.minimapRadiusBlocks);
    }

    public static MinedarConfig config() {
        return config;
    }

    public static void saveConfig() {
        ForgeConfigIO.save(FMLPaths.CONFIGDIR.get().resolve("minedar.json"), config);
    }
}
