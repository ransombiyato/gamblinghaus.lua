package dev.minedar.fabric.v1_20_1;

import dev.minedar.core.WorldIdentity;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Resolves the on-disk scan directory for a world+dimension (section 77). */
final class ScanStorage {

    private ScanStorage() {
    }

    static Path pathFor(WorldIdentity identity) {
        return FabricLoader.getInstance().getGameDir()
                .resolve("minedar")
                .resolve(identity.id());
    }
}
