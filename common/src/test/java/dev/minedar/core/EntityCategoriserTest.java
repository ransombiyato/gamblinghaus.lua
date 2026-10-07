package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityCategoriserTest {

    @Test
    void hostileMobsAreRed() {
        assertEquals(EntityCategoriser.HOSTILE_RED,
                EntityCategoriser.colourFor(EntityCategoriser.Category.HOSTILE, 0x00FF00));
    }

    @Test
    void neutralMobsAreYellow() {
        assertEquals(EntityCategoriser.NEUTRAL_YELLOW,
                EntityCategoriser.colourFor(EntityCategoriser.Category.NEUTRAL, 0x00FF00));
    }

    @Test
    void passiveMobsKeepVisibleColours() {
        int visible = 0x886644;
        assertEquals(visible,
                EntityCategoriser.colourFor(EntityCategoriser.Category.PASSIVE, visible));
    }

    @Test
    void bossesKeepTheirOwnColours() {
        int witherColour = 0x333333;
        assertEquals(witherColour,
                EntityCategoriser.colourFor(EntityCategoriser.Category.BOSS, witherColour));
    }
}
