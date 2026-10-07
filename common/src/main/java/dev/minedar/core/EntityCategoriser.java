package dev.minedar.core;

/**
 * Maps entity categories to their LiDAR dot colour (section 41). Bosses keep
 * their own colours; only generic hostile/neutral buckets are forced.
 */
public final class EntityCategoriser {

    public enum Category {
        /** Forced red. */
        HOSTILE,
        /** Forced yellow. */
        NEUTRAL,
        /** Use the entity's visible colours. */
        PASSIVE,
        /** Use the entity's own colours rather than a category colour. */
        BOSS
    }

    public static final int HOSTILE_RED = 0xFF3030;
    public static final int NEUTRAL_YELLOW = 0xE8D030;

    private EntityCategoriser() {
    }

    /** Applies the category colour; passive/boss keep the base visible colour. */
    public static int colourFor(Category category, int visibleRgb) {
        return switch (category) {
            case HOSTILE -> HOSTILE_RED;
            case NEUTRAL -> NEUTRAL_YELLOW;
            case PASSIVE, BOSS -> visibleRgb;
        };
    }
}
