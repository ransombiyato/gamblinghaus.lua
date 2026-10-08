package dev.minedar.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent, versioned, crash-safe scan storage.
 */
public final class PersistentScanStore {

    public static final int MAGIC = 0x4D4E4442; // "MNDR"
    public static final int CURRENT_VERSION = 2;
    public static final int MIN_SUPPORTED_VERSION = 1;

    public static final class CorruptStoreException extends IOException {
        public CorruptStoreException(String message) {
            super(message);
        }
    }

    public static final class UnsupportedVersionException extends IOException {
        public UnsupportedVersionException(String message) {
            super(message);
        }
    }

    /** Returns the file path for a specific section within a world directory. */
    public static Path sectionPath(Path worldDir, long sectionKey) {
        int sx = (int) ((sectionKey >> 42) & 0x3FFFFF);
        int sy = (int) ((sectionKey >> 22) & 0xFFFFF);
        int sz = (int) (sectionKey & 0x3FFFFF);
        return worldDir.resolve(Integer.toString(sx))
                      .resolve(Integer.toString(sy))
                      .resolve(Integer.toString(sz) + ".dat");
    }
package dev.minedar.core;

import java.io.ByteArrayInputStream;
/** Saves a single section to its file under the world directory. */
    public static void saveSection(Path worldDir, long sectionKey, PointCloudSection section) throws IOException {
        Path path = sectionPath(worldDir, sectionKey);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] payload = encodeSingleSection(sectionKey, section);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, payload);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] encodeSingleSection(long key, PointCloudSection section) throws IOException {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bodyBytes))) {
            out.writeLong(key);
            out.writeInt(section.size());
            for (int i = 0; i < section.size(); i++) {
                out.writeLong(section.get(i));
            }
        }
        return bodyBytes.toByteArray();
    }

    /** Loads a single section from its file under the world directory. */
    public static PointCloudSection loadSection(Path worldDir, long sectionKey) throws IOException {
        Path path = sectionPath(worldDir, sectionKey);
        if (!Files.exists(path)) {
            return new PointCloudSection();
        }
        return decodeSingleSection(Files.readAllBytes(path));
    }

    private static PointCloudSection decodeSingleSection(byte[] file) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file))) {
            long key = in.readLong(); // we can ignore or verify
            int points = in.readInt();
            PointCloudSection section = new PointCloudSection(points);
            for (int i = 0; i < points; i++) {
                section.add(in.readLong());
            }
            return section;
        }
    }
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
/** Loads all sections from the world directory by scanning for section files. */
    public static Map<Long, PointCloudSection> load(Path worldPath) throws IOException {
        Map<Long, PointCloudSection> sections = new HashMap<>();
        if (!Files.exists(worldPath)) {
            return sections;
        }
        // Walk the directory tree to find .dat files
        java.nio.file.Files.walk(worldPath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".dat"))
                .forEach(sectionFile -> {
                    try {
                        long sectionKey = extractKeyFromPath(worldPath, sectionFile);
                        if (sectionKey != -1) {
                            PointCloudSection section = loadSection(worldPath, sectionKey);
                            sections.put(sectionKey, section);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to load section from " + sectionFile + ": " + e.getMessage());
                    }
                });
        return sections;
    }

    private static long extractKeyFromPath(Path worldPath, Path sectionFile) {
        try {
            Path relative = worldPath.relativize(sectionFile);
            if (relative.getNameCount() != 3) {
                return -1;
            }
            int sx = Integer.parseInt(relative.getName(0).toString());
            int sy = Integer.parseInt(relative.getName(1).toString());
            String fname = relative.getName(2).toString();
            if (!fname.endsWith(".dat")) {
                return -1;
            }
            fname = fname.substring(0, fname.length() - 4);
            int sz = Integer.parseInt(fname);
            return SpatialChunkStore.sectionKey(sx, sy, sz);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Saves all given sections to the world directory, one file per section. */
    public static void save(Path worldPath, Map<Long, PointCloudSection> sections) throws IOException {
        for (Map.Entry<Long, PointCloudSection> e : sections.entrySet()) {
            saveSection(worldPath, e.getKey(), e.getValue());
        }
    }
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistent, versioned, crash-safe scan storage (sections 10-11, 79).
 *
 * <p>Layout: a 4-byte magic, a schema version, a body length, a CRC32 of the
 * body, then gzip-compressed body of (sectionKey, count, packed longs...).
 * Writes go to a temp file and are atomically moved into place, so a crash
 * mid-save can never leave a half-written file. Loading detects corruption via
 * the CRC and rejects unsupported future versions rather than guessing.
 */
public final class PersistentScanStore {

    public static final int MAGIC = 0x4D4E4452; // "MNDR"
    /** Current on-disk schema. Bump when the body layout changes. */
    public static final int CURRENT_VERSION = 2;
    /** Oldest version we can still load via migration. */
    public static final int MIN_SUPPORTED_VERSION = 1;

    public static final class CorruptStoreException extends IOException {
        public CorruptStoreException(String message) {
            super(message);
        }
    }

    public static final class UnsupportedVersionException extends IOException {
        public UnsupportedVersionException(String message) {
            super(message);
        }
    }

    /** Serialises the given sections to a compressed byte payload. */
    public static byte[] encode(Map<Long, PointCloudSection> sections) throws IOException {
        ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bodyBytes))) {
            out.writeInt(sections.size());
            for (Map.Entry<Long, PointCloudSection> e : sections.entrySet()) {
                PointCloudSection section = e.getValue();
                out.writeLong(e.getKey());
                out.writeInt(section.size());
                for (int i = 0; i < section.size(); i++) {
                    out.writeLong(section.get(i));
                }
            }
        }
        byte[] body = bodyBytes.toByteArray();

        CRC32 crc = new CRC32();
        crc.update(body);

        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(fileBytes)) {
            out.writeInt(MAGIC);
            out.writeInt(CURRENT_VERSION);
            out.writeInt(body.length);
            out.writeLong(crc.getValue());
            out.write(body);
        }
        return fileBytes.toByteArray();
    }

    /** Parses a payload produced by {@link #encode}. */
    public static Map<Long, PointCloudSection> decode(byte[] file) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new CorruptStoreException("bad magic 0x" + Integer.toHexString(magic));
            }
            int version = in.readInt();
            if (version > CURRENT_VERSION) {
                throw new UnsupportedVersionException(
                        "store version " + version + " is newer than supported " + CURRENT_VERSION);
            }
            if (version < MIN_SUPPORTED_VERSION) {
                throw new UnsupportedVersionException(
                        "store version " + version + " is older than supported " + MIN_SUPPORTED_VERSION);
            }
            int bodyLength = in.readInt();
            long expectedCrc = in.readLong();
            if (bodyLength < 0 || bodyLength > 512 * 1024 * 1024) {
                throw new CorruptStoreException("implausible body length " + bodyLength);
            }
            byte[] body = new byte[bodyLength];
            in.readFully(body);

            CRC32 crc = new CRC32();
            crc.update(body);
            if (crc.getValue() != expectedCrc) {
                throw new CorruptStoreException("crc mismatch");
            }

            Map<Long, PointCloudSection> sections = new HashMap<>();
            try (DataInputStream bodyIn = new DataInputStream(
                    new GZIPInputStream(new ByteArrayInputStream(body)))) {
                int count = bodyIn.readInt();
                for (int s = 0; s < count; s++) {
                    long key = bodyIn.readLong();
                    int points = bodyIn.readInt();
                    if (points < 0 || points > 16 * 1024 * 1024) {
                        throw new CorruptStoreException("implausible section point count " + points);
                    }
                    PointCloudSection section = new PointCloudSection(points);
                    for (int p = 0; p < points; p++) {
                        section.add(bodyIn.readLong());
                    }
                    sections.put(key, section);
                }
            }
            return sections;
        }
    }

    /** Atomically writes sections to {@code path}. */
    public static void save(Path path, Map<Long, PointCloudSection> sections) throws IOException {
        byte[] payload = encode(sections);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, payload);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Loads sections, or returns an empty map if the file is absent. Corrupt or
     * unsupported files are surfaced as typed exceptions so the caller can fall
     * back safely instead of crashing Minecraft (section 79).
     */
    public static Map<Long, PointCloudSection> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        return decode(Files.readAllBytes(path));
    }

    /** Copies bytes from {@code in} to {@code out}; small IO helper for adapters. */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
    }
}
