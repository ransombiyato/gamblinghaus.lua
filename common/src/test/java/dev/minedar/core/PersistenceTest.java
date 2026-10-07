package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {

    private Map<Long, PointCloudSection> sample() {
        Map<Long, PointCloudSection> map = new HashMap<>();
        PointCloudSection a = new PointCloudSection();
        a.add(PointCloudSection.pack(1, 2, 3, 0xAABBCC, 200));
        a.add(PointCloudSection.pack(4, 5, 6, 0x112233, 100));
        map.put(SpatialChunkStore.sectionKey(0, 0, 0), a);

        PointCloudSection b = new PointCloudSection();
        b.add(PointCloudSection.pack(15, 15, 15, 0xFFFFFF, 255));
        map.put(SpatialChunkStore.sectionKey(-3, 5, 9), b);
        return map;
    }

    @Test
    void roundTripsThroughEncodeDecode() throws IOException {
        Map<Long, PointCloudSection> original = sample();
        byte[] encoded = PersistentScanStore.encode(original);
        Map<Long, PointCloudSection> decoded = PersistentScanStore.decode(encoded);

        assertEquals(2, decoded.size());
        PointCloudSection a = decoded.get(SpatialChunkStore.sectionKey(0, 0, 0));
        assertEquals(2, a.size());
        assertEquals(0xAABBCC, PointCloudSection.rgb(a.get(0)));
        assertEquals(4, PointCloudSection.localX(a.get(1)));
    }

    @Test
    void survivesSaveLoadCycle(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dim").resolve("scan.mndr");
        PersistentScanStore.save(file, sample());
        assertTrue(Files.exists(file));

        Map<Long, PointCloudSection> loaded = PersistentScanStore.load(file);
        assertEquals(2, loaded.size());
    }

    @Test
    void missingFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        assertEquals(0, PersistentScanStore.load(dir.resolve("nope.mndr")).size());
    }

    @Test
    void detectsCorruptionViaCrc() throws IOException {
        byte[] encoded = PersistentScanStore.encode(sample());
        encoded[encoded.length - 1] ^= 0xFF; // flip a body byte
        assertThrows(PersistentScanStore.CorruptStoreException.class,
                () -> PersistentScanStore.decode(encoded));
    }

    @Test
    void detectsBadMagic() {
        byte[] junk = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};
        assertThrows(PersistentScanStore.CorruptStoreException.class,
                () -> PersistentScanStore.decode(junk));
    }

    @Test
    void rejectsFutureSchemaVersion() throws IOException {
        byte[] encoded = PersistentScanStore.encode(sample());
        // Version int lives at offset 4..7.
        int future = PersistentScanStore.CURRENT_VERSION + 1;
        encoded[4] = (byte) (future >>> 24);
        encoded[5] = (byte) (future >>> 16);
        encoded[6] = (byte) (future >>> 8);
        encoded[7] = (byte) future;
        assertThrows(PersistentScanStore.UnsupportedVersionException.class,
                () -> PersistentScanStore.decode(encoded));
    }

    @Test
    void saveIsAtomicAndLeavesNoTempFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scan.mndr");
        PersistentScanStore.save(file, sample());
        try (var stream = Files.list(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void worldsAndDimensionsAreSeparated() {
        WorldIdentity overworld = new WorldIdentity("SinglePlayerWorld", "minecraft:overworld");
        WorldIdentity nether = new WorldIdentity("SinglePlayerWorld", "minecraft:the_nether");
        WorldIdentity serverWorld = new WorldIdentity("mc.example.com:25565", "minecraft:overworld");

        assertNotEquals(overworld.id(), nether.id());
        assertNotEquals(overworld.id(), serverWorld.id());
    }

    @Test
    void worldIdentityNormalisesCaseAndRejectsBlanks() {
        assertEquals(
                new WorldIdentity("MyWorld", "minecraft:overworld").id(),
                new WorldIdentity("myworld", "MINECRAFT:OVERWORLD").id());
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity(" ", "dim"));
        assertThrows(IllegalArgumentException.class, () -> new WorldIdentity("world", ""));
    }

    @Test
    void moddedDimensionsGetTheirOwnStore() {
        WorldIdentity modded = new WorldIdentity("world", "mymod:twilight");
        assertTrue(modded.id().contains("mymod_twilight"));
    }
}
