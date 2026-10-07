package dev.minedar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, dependency-free JSON serialisation for MiNEDAR's flat config object
 * (section 54). A hand-rolled writer/reader keeps {@code common} free of
 * Minecraft/Gson so it can be unit-tested standalone, while still emitting a
 * clean JSON file. Parsing is deliberately tolerant: unknown keys are ignored
 * and malformed values fall back to defaults rather than throwing.
 */
public final class ConfigIO {

    private ConfigIO() {
    }

    public static String toJson(MinedarConfig c) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"minimapVisible\": ").append(c.minimapVisible).append(",\n");
        sb.append("  \"minimapRadiusBlocks\": ").append(c.minimapRadiusBlocks).append(",\n");
        sb.append("  \"defaultScanRadius\": ").append(c.defaultScanRadius).append(",\n");
        sb.append("  \"scanDistance\": ").append(c.scanDistance).append(",\n");
        sb.append("  \"autosave\": ").append(c.autosave).append(",\n");
        sb.append("  \"fullscreenRadiusMultiplier\": ").append(c.fullscreenRadiusMultiplier).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static MinedarConfig fromJson(String json) {
        MinedarConfig c = new MinedarConfig();
        Map<String, String> values = parseFlat(json);
        c.minimapVisible = bool(values.get("minimapVisible"), c.minimapVisible);
        c.minimapRadiusBlocks = integer(values.get("minimapRadiusBlocks"), c.minimapRadiusBlocks);
        c.defaultScanRadius = number(values.get("defaultScanRadius"), c.defaultScanRadius);
        c.scanDistance = number(values.get("scanDistance"), c.scanDistance);
        c.autosave = bool(values.get("autosave"), c.autosave);
        c.fullscreenRadiusMultiplier = number(
                values.get("fullscreenRadiusMultiplier"), c.fullscreenRadiusMultiplier);
        return c.sanitised();
    }

    /** Extracts top-level "key": value pairs from a flat object. */
    static Map<String, String> parseFlat(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        int i = 0;
        int n = json.length();
        while (i < n) {
            int keyStart = json.indexOf('"', i);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = json.indexOf('"', keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = json.substring(keyStart + 1, keyEnd);
            int colon = json.indexOf(':', keyEnd);
            if (colon < 0) {
                break;
            }
            int valueStart = colon + 1;
            int valueEnd = valueStart;
            boolean inString = false;
            while (valueEnd < n) {
                char ch = json.charAt(valueEnd);
                if (ch == '"') {
                    inString = !inString;
                } else if (!inString && (ch == ',' || ch == '}' || ch == '\n')) {
                    break;
                }
                valueEnd++;
            }
            String value = json.substring(valueStart, valueEnd).trim();
            out.put(key, value);
            i = valueEnd;
        }
        return out;
    }

    private static boolean bool(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String v = raw.trim().toLowerCase();
        if (v.equals("true")) {
            return true;
        }
        if (v.equals("false")) {
            return false;
        }
        return fallback;
    }

    private static int integer(String raw, int fallback) {
        try {
            return raw == null ? fallback : (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double number(String raw, double fallback) {
        try {
            return raw == null ? fallback : Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
