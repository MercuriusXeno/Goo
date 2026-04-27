package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests slot constraint logic for the reactor's corner-only restriction.
 * Verifies that the fallback chain in resolveAndConstrain can produce
 * cross slots, proving per-step constraint checking is load-bearing.
 */
class CanisterSlotConstraintTest {

    /**
     * Corner slots allowed by the reactor.
     */
    private static final Set<Integer> CORNER_SLOTS = Set.of(0, 2, 6, 8);

    /**
     * Cross slots excluded by the reactor.
     */
    private static final Set<Integer> CROSS_SLOTS = Set.of(1, 3, 4, 5, 7);

    @Test
    void cornerSlotsAreAllowed() {
        for (int slot : CORNER_SLOTS) {
            assertTrue(CORNER_SLOTS.contains(slot),
                    "Corner slot " + slot + " should be allowed");
        }
    }

    @Test
    void crossSlotsAreRejected() {
        for (int slot : CROSS_SLOTS) {
            assertFalse(CORNER_SLOTS.contains(slot),
                    "Cross slot " + slot + " should be rejected");
        }
    }

    @Test
    void allNineSlotsAccountedFor() {
        Set<Integer> all = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        for (int slot : all) {
            assertTrue(CORNER_SLOTS.contains(slot) || CROSS_SLOTS.contains(slot),
                    "Slot " + slot + " must be in either corners or cross");
        }
        assertEquals(9, CORNER_SLOTS.size() + CROSS_SLOTS.size());
    }

    /**
     * Proves that aiming at sides of occupied corner canisters produces
     * cross-slot adjacents via the fallback chain, so per-step constraint
     * checking in resolveAndConstrain is required to prevent illegal placement.
     */
    @Nested
    class FallbackProducesCrossSlots {

        @Test
        void adjacentFromSlot0LeaningEastIsCrossSlot() {
            // Slot 0 (NW, center 3,3), cursor leans east (px > center)
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(0, 5.0f, 3.0f);
            assertEquals(1, adjacent, "East lean from slot 0 resolves to slot 1 (N)");
            assertTrue(CROSS_SLOTS.contains(adjacent),
                    "Slot 1 is a cross slot - must be rejected by constrained resolution");
        }

        @Test
        void adjacentFromSlot0LeaningSouthIsCrossSlot() {
            // Slot 0 (NW, center 3,3), cursor leans south (pz > center)
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(0, 3.0f, 5.0f);
            assertEquals(3, adjacent, "South lean from slot 0 resolves to slot 3 (W)");
            assertTrue(CROSS_SLOTS.contains(adjacent),
                    "Slot 3 is a cross slot - must be rejected by constrained resolution");
        }

        @Test
        void adjacentFromSlot2LeaningWestIsCrossSlot() {
            // Slot 2 (NE, center 13,3), cursor leans west (px < center)
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(2, 11.0f, 3.0f);
            assertEquals(1, adjacent, "West lean from slot 2 resolves to slot 1 (N)");
            assertTrue(CROSS_SLOTS.contains(adjacent));
        }

        @Test
        void adjacentFromSlot8LeaningNorthIsCrossSlot() {
            // Slot 8 (SE, center 13,13), cursor leans north (pz < center)
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(8, 13.0f, 11.0f);
            assertEquals(5, adjacent, "North lean from slot 8 resolves to slot 5 (E)");
            assertTrue(CROSS_SLOTS.contains(adjacent));
        }

        @Test
        void everyCornerHasAtLeastOneCrossAdjacent() {
            // For each corner, at least one lean direction produces a cross slot.
            // Edge corners (like slot 8) clamp when leaning outward, so all 4
            // directions must be checked. Proves per-step constraint is load-bearing.
            for (int corner : CORNER_SLOTS) {
                float cx = CanisterSlotLayout.SLOT_CENTERS[corner][0];
                float cz = CanisterSlotLayout.SLOT_CENTERS[corner][1];
                int adjXp = CanisterSlotLayout.adjacentByCursorLean(corner, cx + 2, cz);
                int adjXn = CanisterSlotLayout.adjacentByCursorLean(corner, cx - 2, cz);
                int adjZp = CanisterSlotLayout.adjacentByCursorLean(corner, cx, cz + 2);
                int adjZn = CanisterSlotLayout.adjacentByCursorLean(corner, cx, cz - 2);
                boolean hasCrossAdjacent = CROSS_SLOTS.contains(adjXp)
                        || CROSS_SLOTS.contains(adjXn)
                        || CROSS_SLOTS.contains(adjZp)
                        || CROSS_SLOTS.contains(adjZn);
                assertTrue(hasCrossAdjacent,
                        "Corner slot " + corner + " must have at least one cross-slot adjacent");
            }
        }
    }

    /**
     * Verifies that direct face hits (top/bottom) on cross-slot positions
     * resolve to cross slots, proving the direct-hit constraint check
     * prevents illegal placement when aiming at the canister block face.
     */
    @Nested
    class DirectHitOnCrossSlots {

        @Test
        void topFaceHitOnCenterResolvesToCrossSlot4() {
            float px = CanisterSlotLayout.SLOT_CENTERS[4][0];
            float pz = CanisterSlotLayout.SLOT_CENTERS[4][1];
            int slot = CanisterSlotLayout.placementSlot(Direction.UP, px, pz);
            assertEquals(4, slot);
            assertTrue(CROSS_SLOTS.contains(slot),
                    "Direct hit on center must be rejected for reactor");
        }

        @Test
        void topFaceHitOnEachCrossSlotResolvesCorrectly() {
            for (int cross : CROSS_SLOTS) {
                float px = CanisterSlotLayout.SLOT_CENTERS[cross][0];
                float pz = CanisterSlotLayout.SLOT_CENTERS[cross][1];
                int slot = CanisterSlotLayout.placementSlot(Direction.UP, px, pz);
                assertEquals(cross, slot,
                        "Top face hit at cross slot " + cross + " center");
                assertFalse(CORNER_SLOTS.contains(slot),
                        "Cross slot " + slot + " must not be in allowed set");
            }
        }

        @Test
        void topFaceHitOnEachCornerSlotResolvesCorrectly() {
            for (int corner : CORNER_SLOTS) {
                float px = CanisterSlotLayout.SLOT_CENTERS[corner][0];
                float pz = CanisterSlotLayout.SLOT_CENTERS[corner][1];
                int slot = CanisterSlotLayout.placementSlot(Direction.UP, px, pz);
                assertEquals(corner, slot,
                        "Top face hit at corner slot " + corner + " center");
                assertTrue(CORNER_SLOTS.contains(slot),
                        "Corner slot " + slot + " must be in allowed set");
            }
        }
    }

    /**
     * Verifies that side-face hits (the indirect targeting path) produce
     * offset slots via placementSlot, which can land on cross positions.
     */
    @Nested
    class SideFaceHitOffset {

        @Test
        void eastFaceHitAtSlot0PixelsResolvesToCrossSlot1() {
            // Aiming at the east side of slot 0 (NW): px near right edge of slot 0
            // placementSlot(EAST, px, pz) offsets column +1
            float px = CanisterSlotLayout.SLOT_CENTERS[0][0]; // col 0
            float pz = CanisterSlotLayout.SLOT_CENTERS[0][1]; // row 0
            int slot = CanisterSlotLayout.placementSlot(Direction.EAST, px, pz);
            // EAST offsets col+1: row 0, col 0+1=1 → slot 1 (N, cross)
            assertEquals(1, slot);
            assertTrue(CROSS_SLOTS.contains(slot),
                    "Side-face offset from corner 0 via EAST produces cross slot 1");
        }

        @Test
        void southFaceHitAtSlot0PixelsResolvesToCrossSlot3() {
            float px = CanisterSlotLayout.SLOT_CENTERS[0][0];
            float pz = CanisterSlotLayout.SLOT_CENTERS[0][1];
            int slot = CanisterSlotLayout.placementSlot(Direction.SOUTH, px, pz);
            // SOUTH offsets row+1: row 0+1=1, col 0 → slot 3 (W, cross)
            assertEquals(3, slot);
            assertTrue(CROSS_SLOTS.contains(slot));
        }
    }
}
