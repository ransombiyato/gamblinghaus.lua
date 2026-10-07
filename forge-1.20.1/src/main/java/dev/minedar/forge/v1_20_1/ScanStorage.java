package dev.minedar.forge.v1_20_1;

import dev.minedar.core.WorldIdentity;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/** Resolves the on-disk scan file for a world+dimension (section 77). */
final class ScanStorage {

    private ScanStorage() {
    }

    static Path pathFor(WorldIdentity identity) {
        return FMLPaths.GAMEDIR.get()
                .resolve("minedar")
                .resolve(identity.id() + ".mndr");
    }
}
