package dev.minedar.forge.v1_21_1;

import dev.minedar.core.ConfigIO;
import dev.minedar.core.MinedarConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads/writes {@code minedar.json} next to the other Forge configs, reusing the
 * loader-independent {@link ConfigIO}. A broken config never stops the mod from
 * loading: it falls back to defaults (section 54).
 */
final class ForgeConfigIO {

    private ForgeConfigIO() {
    }

    static MinedarConfig load(Path path) {
        if (!Files.exists(path)) {
            MinedarConfig defaults = MinedarConfig.defaults();
            save(path, defaults);
            return defaults;
        }
        try {
            return ConfigIO.fromJson(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            MinedarMod.LOGGER.warn("Could not read {}, using defaults", path, e);
            return MinedarConfig.defaults();
        }
    }

    static void save(Path path, MinedarConfig config) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("minedar.json.tmp");
            Files.writeString(tmp, ConfigIO.toJson(config.sanitised()), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MinedarMod.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
