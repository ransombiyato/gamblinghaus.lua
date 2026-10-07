package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpecialBlockRulesTest {

    @Test
    void unpoweredRedstoneIsDarkRed() {
        var state = new SpecialBlockRules.BlockState("redstone_wire").powered(false);
        var tint = SpecialBlockRules.tintFor(state);
        assertNotNull(tint);
        assertEquals(SpecialBlockRules.REDSTONE_DARK, tint.colour());
    }

    @Test
    void poweredRedstoneIsBrighterAndRedder() {
        var powered = SpecialBlockRules.tintFor(
                new SpecialBlockRules.BlockState("redstone_wire").powered(true));
        assertNotNull(powered);
        assertEquals(SpecialBlockRules.REDSTONE_BRIGHT, powered.colour());
        assertTrue(Colour.red(SpecialBlockRules.REDSTONE_BRIGHT)
                        > Colour.red(SpecialBlockRules.REDSTONE_DARK),
                "powered redstone must be brighter/redder");
    }

    @Test
    void stickyPistonGetsGreenTintButPlainPistonDoesNot() {
        var sticky = SpecialBlockRules.tintFor(
                new SpecialBlockRules.BlockState("sticky_piston").sticky(true));
        assertNotNull(sticky);
        assertTrue(Colour.green(sticky.colour()) > Colour.red(sticky.colour()));

        var plain = SpecialBlockRules.tintFor(
                new SpecialBlockRules.BlockState("piston").sticky(false));
        assertNull(plain, "the piston body/rod keep normal LiDAR colour");
    }

    @Test
    void netherPortalGetsPurpleTint() {
        var tint = SpecialBlockRules.tintFor(
                new SpecialBlockRules.BlockState("nether_portal"));
        assertNotNull(tint);
        assertTrue(Colour.red(tint.colour()) > 0 && Colour.blue(tint.colour()) > 0);
        assertTrue(Colour.blue(tint.colour()) > Colour.green(tint.colour()));
    }

    @Test
    void endPortalSurfaceAndFrameAreHandledDifferently() {
        var surface = SpecialBlockRules.tintFor(new SpecialBlockRules.BlockState("end_portal"));
        var frame = SpecialBlockRules.tintFor(
                new SpecialBlockRules.BlockState("end_portal_frame").baseRgb(0x3A6B4A));
        assertNotNull(surface);
        assertNotNull(frame);
        // Frame uses its own block colour (greenish), not the portal tint.
        assertEquals(0x3A6B4A, frame.colour());
    }

    @Test
    void terracottaKeepsTextureColour() {
        assertTrue(SpecialBlockRules.keepsTextureColour("white_terracotta"));
        assertTrue(SpecialBlockRules.keepsTextureColour("terracotta"));
        assertFalse(SpecialBlockRules.keepsTextureColour("stone"));
    }

    @Test
    void thinGeometryBlocksAreRecognised() {
        assertTrue(SpecialBlockRules.isThinGeometry("ladder"));
        assertTrue(SpecialBlockRules.isThinGeometry("oak_fence") == false);
        assertTrue(SpecialBlockRules.isThinGeometry("vine"));
        assertTrue(SpecialBlockRules.isThinGeometry("scaffolding"));
        assertTrue(SpecialBlockRules.isThinGeometry("rail"));
        assertFalse(SpecialBlockRules.isThinGeometry("stone"));
    }

    @Test
    void doorsAndTrapdoorsGetInteractionPreview() {
        assertTrue(SpecialBlockRules.isInteractableTransformable("oak_door"));
        assertTrue(SpecialBlockRules.isInteractableTransformable("oak_trapdoor"));
        assertTrue(SpecialBlockRules.isInteractableTransformable("oak_fence_gate"));
        assertFalse(SpecialBlockRules.isInteractableTransformable("stone"));
        assertEquals(0x404040, SpecialBlockRules.interactionPreviewColour());
    }

    @Test
    void unknownBlocksGetNoTint() {
        assertNull(SpecialBlockRules.tintFor(new SpecialBlockRules.BlockState("some_modded_block")));
        assertNull(SpecialBlockRules.tintFor(new SpecialBlockRules.BlockState(null)));
    }
}
