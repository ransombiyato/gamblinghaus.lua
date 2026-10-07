package dev.minedar.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShaderStateManagerTest {

    @Test
    void disablesShadersWhenTheyWereOn() {
        ShaderStateManager m = new ShaderStateManager();
        assertEquals(ShaderStateManager.Action.DISABLE, m.engage(true));
        assertTrue(m.isLidarActive());
        assertTrue(m.shadersWereEnabled());
    }

    @Test
    void doesNotTouchShadersThatWereOff() {
        ShaderStateManager m = new ShaderStateManager();
        assertEquals(ShaderStateManager.Action.NONE, m.engage(false));
    }

    @Test
    void restoresOnlyIfTheyWereOnBefore() {
        ShaderStateManager m = new ShaderStateManager();
        m.engage(true);
        assertEquals(ShaderStateManager.Action.ENABLE, m.disengage());
        assertFalse(m.isLidarActive());

        ShaderStateManager m2 = new ShaderStateManager();
        m2.engage(false);
        assertEquals(ShaderStateManager.Action.NONE, m2.disengage());
        // No stale "restore" intent leaks into the next cycle.
        assertFalse(m2.shadersWereEnabled());
    }

    @Test
    void repeatedCyclesStayConsistent() {
        ShaderStateManager m = new ShaderStateManager();
        for (int i = 0; i < 5; i++) {
            assertEquals(ShaderStateManager.Action.DISABLE, m.engage(true));
            assertEquals(ShaderStateManager.Action.ENABLE, m.disengage());
        }
    }

    @Test
    void failedDisableStillRestoresSafely() {
        ShaderStateManager m = new ShaderStateManager();
        m.engage(true);
        m.reportDisableFailed();
        assertEquals(ShaderStateManager.Action.ENABLE, m.disengage());
    }

    @Test
    void disengagingWithoutEngagingIsSaferNoop() {
        ShaderStateManager m = new ShaderStateManager();
        assertEquals(ShaderStateManager.Action.NONE, m.disengage());
    }
}
