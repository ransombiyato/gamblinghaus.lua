package dev.minedar.core;

/**
 * Robust world identity (section 77). A world is never identified by dimension
 * name alone: it is the pair (world identity, dimension id). For single-player
 * the world identity is the save folder name; for multiplayer it is the server
 * address. Dimensions include modded ones (e.g. {@code modid:dimension}).
 */
public record WorldIdentity(String worldKey, String dimensionId) {

    public WorldIdentity {
        if (worldKey == null || worldKey.isBlank()) {
            throw new IllegalArgumentException("worldKey required");
        }
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId required");
        }
        worldKey = normalise(worldKey);
        dimensionId = normalise(dimensionId);
    }

    /** Stable, filesystem-safe id that separates worlds and dimensions. */
    public String id() {
        return safe(worldKey) + "__" + safe(dimensionId);
    }

    private static String normalise(String s) {
        return s.trim().toLowerCase();
    }

    private static String safe(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
    }
}
