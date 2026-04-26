package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.ChainPlacementRules;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.Action;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.CandidateState;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.Decision;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.WaterHandling;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-function tests for the chain/frost placement decision matrix.
 * Validates stacking priority, displacement rules, waterlogging, and the
 * frost-specific freeze-and-rise behavior against non-liquid/water/lava
 * candidate states.
 */
class ChainPlacementRulesTest {

    /** Convenience: builds a CandidateState for an air block. */
    private static CandidateState air() {
        return new CandidateState(false, true, false, false, false, true);
    }

    /** Convenience: builds a CandidateState for a solid unstackable block. */
    private static CandidateState solid() {
        return new CandidateState(false, false, false, false, false, false);
    }

    /** Convenience: builds a CandidateState for a non-fluid replaceable block (fire, tall grass). */
    private static CandidateState replaceableNonFluid() {
        return new CandidateState(false, false, true, false, false, true);
    }

    /** Convenience: builds a CandidateState for water. */
    private static CandidateState water(boolean aboveIsPlaceable) {
        return new CandidateState(false, false, true, true, false, aboveIsPlaceable);
    }

    /** Convenience: builds a CandidateState for lava. */
    private static CandidateState lava() {
        return new CandidateState(false, false, true, false, true, false);
    }

    /** Convenience: builds a CandidateState that already has a same-type marker. */
    private static CandidateState sameMarker() {
        return new CandidateState(true, false, false, false, false, false);
    }

    // ── Stacking priority ──────────────────────────────────────────────

    @Nested
    class Stacking {

        @Test
        void hitHasMarker_stacksAtHit() {
            Decision d = ChainPlacementRules.decide(sameMarker(), air(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 0), d);
        }

        @Test
        void adjacentHasMarker_hitDoesNot_stacksAtAdjacent() {
            Decision d = ChainPlacementRules.decide(solid(), sameMarker(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 1), d);
        }

        @Test
        void bothHaveMarker_hitWinsByPriority() {
            Decision d = ChainPlacementRules.decide(sameMarker(), sameMarker(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 0), d);
        }

        @Test
        void hitMarker_adjacentLava_stillStacksAtHit() {
            Decision d = ChainPlacementRules.decide(sameMarker(), lava(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 0), d);
        }
    }

    // ── Displacement: air and non-fluid replaceable ────────────────────

    @Nested
    class Displacement {

        @Test
        void hitAir_displacesAtHit() {
            Decision d = ChainPlacementRules.decide(air(), air(), WaterHandling.NONE);
            assertEquals(new Decision(Action.DISPLACE, 0), d);
        }

        @Test
        void hitFire_displacesAtHit() {
            Decision d = ChainPlacementRules.decide(replaceableNonFluid(), air(), WaterHandling.NONE);
            assertEquals(new Decision(Action.DISPLACE, 0), d);
        }

        @Test
        void hitSolid_adjacentAir_displacesAtAdjacent() {
            Decision d = ChainPlacementRules.decide(solid(), air(), WaterHandling.NONE);
            assertEquals(new Decision(Action.DISPLACE, 1), d);
        }

        @Test
        void hitSolid_adjacentFire_displacesAtAdjacent() {
            Decision d = ChainPlacementRules.decide(solid(), replaceableNonFluid(), WaterHandling.NONE);
            assertEquals(new Decision(Action.DISPLACE, 1), d);
        }

        @Test
        void bothSolid_noMarker_returnsNone() {
            Decision d = ChainPlacementRules.decide(solid(), solid(), WaterHandling.NONE);
            assertEquals(new Decision(Action.NONE, -1), d);
        }
    }

    // ── Lava: never displaced, never waterlogged ───────────────────────

    @Nested
    class Lava {

        @Test
        void hitLava_adjacentSolid_returnsNone() {
            Decision d = ChainPlacementRules.decide(lava(), solid(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.NONE, -1), d);
        }

        @Test
        void hitLava_adjacentAir_displacesAtAdjacent() {
            Decision d = ChainPlacementRules.decide(lava(), air(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.DISPLACE, 1), d);
        }

        @Test
        void bothLava_returnsNone() {
            Decision d = ChainPlacementRules.decide(lava(), lava(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.NONE, -1), d);
        }

        @Test
        void hitLava_frostHandling_fallsThroughNotFrozen() {
            // Lava never triggers FREEZE_AND_RISE regardless of handling mode.
            Decision d = ChainPlacementRules.decide(lava(), solid(), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.NONE, -1), d);
        }
    }

    // ── Water + WATERLOG handling (chain markers) ──────────────────────

    @Nested
    class WaterWaterlog {

        @Test
        void hitWater_waterloggedAtHit() {
            Decision d = ChainPlacementRules.decide(water(false), solid(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.WATERLOG, 0), d);
        }

        @Test
        void hitSolid_adjacentWater_waterloggedAtAdjacent() {
            Decision d = ChainPlacementRules.decide(solid(), water(false), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.WATERLOG, 1), d);
        }

        @Test
        void hitWater_adjacentWater_waterlogsAtHit() {
            Decision d = ChainPlacementRules.decide(water(false), water(false), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.WATERLOG, 0), d);
        }
    }

    // ── Water + FREEZE_AND_RISE handling (frost field) ─────────────────

    @Nested
    class WaterFreezeAndRise {

        @Test
        void hitWater_aboveClear_freezesAtHit() {
            Decision d = ChainPlacementRules.decide(water(true), solid(), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.FREEZE_AND_RISE, 0), d);
        }

        @Test
        void hitWater_aboveBlocked_fallsThroughToAdjacent() {
            Decision d = ChainPlacementRules.decide(water(false), air(), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.DISPLACE, 1), d);
        }

        @Test
        void hitWater_aboveBlocked_adjacentAlsoWaterAboveClear_freezeAtAdjacent() {
            Decision d = ChainPlacementRules.decide(water(false), water(true), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.FREEZE_AND_RISE, 1), d);
        }

        @Test
        void hitSolid_adjacentWater_aboveClear_freezeAtAdjacent() {
            Decision d = ChainPlacementRules.decide(solid(), water(true), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.FREEZE_AND_RISE, 1), d);
        }

        @Test
        void bothWater_bothAboveBlocked_returnsNone() {
            Decision d = ChainPlacementRules.decide(water(false), water(false), WaterHandling.FREEZE_AND_RISE);
            assertEquals(new Decision(Action.NONE, -1), d);
        }
    }

    // ── Water + NONE handling (effect blocks without water support) ────

    @Nested
    class WaterNoneHandling {

        @Test
        void hitWater_fallsThroughToAdjacent() {
            Decision d = ChainPlacementRules.decide(water(false), air(), WaterHandling.NONE);
            assertEquals(new Decision(Action.DISPLACE, 1), d);
        }

        @Test
        void bothWater_returnsNone() {
            Decision d = ChainPlacementRules.decide(water(false), water(false), WaterHandling.NONE);
            assertEquals(new Decision(Action.NONE, -1), d);
        }
    }

    // ── Stacking trumps placement, even with fresh placements viable ───

    @Nested
    class StackingTrumpsPlacement {

        @Test
        void hitReplaceable_adjacentMarker_stacksAtAdjacent() {
            Decision d = ChainPlacementRules.decide(replaceableNonFluid(), sameMarker(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 1), d);
        }

        @Test
        void hitWater_adjacentMarker_stacksAtAdjacent() {
            Decision d = ChainPlacementRules.decide(water(false), sameMarker(), WaterHandling.WATERLOG);
            assertEquals(new Decision(Action.STACK, 1), d);
        }
    }
}
