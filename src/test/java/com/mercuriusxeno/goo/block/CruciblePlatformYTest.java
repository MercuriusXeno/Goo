package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.crucible.CrucibleMath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the crucible platform movement utility.
 * Platform target is computed on the block entity; the BER only lerps.
 */
class CruciblePlatformYTest {

    /**
     * moveToward approaches target without overshooting.
     */
    @Test
    void moveTowardApproachesTarget() {
        assertEquals(0.1f, CrucibleMath.moveToward(0f, 0.5f, 0.1f), 0.001f);
        assertEquals(0.5f, CrucibleMath.moveToward(0.4f, 0.5f, 0.2f), 0.001f);
    }

    /**
     * moveToward works in both directions.
     */
    @Test
    void moveTowardDescends() {
        assertEquals(0.4f, CrucibleMath.moveToward(0.5f, 0f, 0.1f), 0.001f);
        assertEquals(0f, CrucibleMath.moveToward(0.05f, 0f, 0.1f), 0.001f);
    }

    /**
     * moveToward snaps exactly to target when within one step.
     */
    @Test
    void moveTowardSnapsAtTarget() {
        assertEquals(0.5f, CrucibleMath.moveToward(0.48f, 0.5f, 0.1f), 0.001f);
    }

    /**
     * moveToward does nothing when already at target.
     */
    @Test
    void moveTowardNoopAtTarget() {
        assertEquals(0.5f, CrucibleMath.moveToward(0.5f, 0.5f, 0.1f), 0.001f);
    }
}
