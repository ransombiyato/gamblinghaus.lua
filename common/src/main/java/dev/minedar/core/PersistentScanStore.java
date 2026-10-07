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
