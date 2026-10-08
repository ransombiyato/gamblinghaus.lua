package dev.minedar.forge.v1_21_1;

import dev.minedar.core.EntityCategoriser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Category and visible colours for entities (sections 41-44). Hostile mobs are
 * forced red, neutral/conditional-hostile mobs yellow, passive mobs and players
 * use their visible colours, and bosses keep their own colours.
 */
final class EntityColours {

    private EntityColours() {
    }

    static EntityCategoriser.Category categoryOf(Entity e) {
        if (e instanceof Player) {
            return EntityCategoriser.Category.PASSIVE;
        }
        if (isBossLike(e)) {
            return EntityCategoriser.Category.BOSS;
        }
        if (e instanceof Mob mob) {
            if (mob.getType().getCategory() == MobCategory.MONSTER || e instanceof Enemy) {
                return EntityCategoriser.Category.HOSTILE;
            }
            return EntityCategoriser.Category.NEUTRAL;
        }
        return EntityCategoriser.Category.PASSIVE;
    }

    /** Colour for a coarse sample (bounding volume) of the entity. */
    static int colourFor(Entity e) {
        return EntityCategoriser.colourFor(categoryOf(e), visibleColour(e));
    }

    /**
     * Approximate visible colour for passive entities. A true per-texel sample is
     * out of scope; these are the dominant body colours so passive mobs read as
     * themselves rather than a generic green (section 41).
     */
    static int visibleColour(Entity e) {
        if (e instanceof AbstractArrow) {
            return EntityCategoriser.HOSTILE_RED; // arrows red while in flight (section 42)
        }
        if (e instanceof ItemEntity) {
            return 0xFFFFFF; // dropped items are normal white dots (section 42)
        }
        if (e instanceof Player) {
            return 0xE0C0A0; // skin tone; armour/held items scan separately (section 44)
        }
        String id = path(e);
        return switch (id) {
            case "pig" -> 0xF0A0A0;
            case "cow", "mooshroom" -> 0x4A3B2A;
            case "sheep" -> 0xE8E8E8;
            case "chicken" -> 0xE8E8E8;
            case "rabbit" -> 0xC0A080;
            case "horse", "donkey", "mule", "llama", "trader_llama" -> 0x8A5A3A;
            case "wolf" -> 0xB0B0B0;
            case "cat", "ocelot" -> 0xC0A080;
            case "fox" -> 0xC07030;
            case "villager", "wandering_trader" -> 0xB08868;
            case "squid", "glow_squid" -> 0x3050A0;
            case "bat" -> 0x403020;
            case "iron_golem" -> 0xB0A090;
            case "snow_golem" -> 0xF0F0F0;
            case "turtle" -> 0x607040;
            case "axolotl" -> 0xF0A0D0;
            case "bee" -> 0xE0B020;
            case "frog" -> 0x60A040;
            case "allay" -> 0x40A0E0;
            case "goat" -> 0xC0C0B0;
            case "camel" -> 0xD0B070;
            case "sniffer" -> 0x6A8A5A;
            default -> 0xFFFFFF;
        };
    }

    private static String path(Entity e) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        return key == null ? "" : key.getPath();
    }

    static boolean isBossLike(Entity e) {
        String id = path(e);
        return id.contains("ender_dragon") || id.contains("wither")
                || id.contains("warden") || id.contains("elder_guardian");
    }
}
