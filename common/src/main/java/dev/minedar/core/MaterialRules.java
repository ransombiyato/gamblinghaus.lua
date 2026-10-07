package dev.minedar.core;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves how a scan ray interacts with a piece of world content. The core
 * knows nothing about Minecraft; loader modules register profiles against
 * string keys (block ids, entity categories, fluid ids) and this registry
 * applies generic fallbacks when a key is unknown, so modded content never
 * crashes a scan (spec sections 13-43, 79-80).
 */
public final class MaterialRules {

    private final Map<String, MaterialProfile> profiles = new ConcurrentHashMap<>();

    public MaterialRules() {
        registerDefaults();
    }

    private void registerDefaults() {
        profiles.put("glass", MaterialProfile.GLASS);
        profiles.put("stained_glass", MaterialProfile.STAINED_GLASS);
        profiles.put("tinted_glass", MaterialProfile.STAINED_GLASS);
        profiles.put("water", MaterialProfile.WATER);
        profiles.put("lava", MaterialProfile.LAVA);
        profiles.put("leaves", MaterialProfile.LEAVES);
        profiles.put("ice", MaterialProfile.ICE);
        profiles.put("snow", MaterialProfile.SNOW);
        profiles.put("powder_snow", MaterialProfile.POWDER_SNOW);
        profiles.put("cobweb", MaterialProfile.COBWEB);
        profiles.put("fire", MaterialProfile.FIRE);
        profiles.put("soul_fire", MaterialProfile.FIRE);
        profiles.put("explosion", MaterialProfile.EXPLOSION);
        profiles.put("smoke", MaterialProfile.SMOKE);
        profiles.put("nether_portal", MaterialProfile.PORTAL);
        profiles.put("end_portal", MaterialProfile.END_PORTAL);
        profiles.put("cloud", MaterialProfile.CLOUD);
        profiles.put("weather", MaterialProfile.WEATHER);
    }

    public void register(String key, MaterialProfile profile) {
        profiles.put(key.toLowerCase(), profile);
    }

    /** Unknown keys fall back to opaque solid geometry. */
    public MaterialProfile resolve(String key) {
        if (key == null) {
            return MaterialProfile.SOLID;
        }
        return profiles.getOrDefault(key.toLowerCase(), MaterialProfile.SOLID);
    }

    /**
     * Compounds several media in encounter order: the signal that survives one
     * layer feeds the next, and the total retained fraction is accumulated.
     * Returns the transmitted fraction after all layers.
     */
    public double compoundTransmission(Collection<String> layers, double incoming) {
        double signal = incoming;
        for (String layer : layers) {
            signal = resolve(layer).transmit(signal);
        }
        return signal;
    }

    public int size() {
        return profiles.size();
    }
}
