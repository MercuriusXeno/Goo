package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ThrowFreezeState}. Drives the pure state machine
 * tick-by-tick. The dead-entity auto-clear path in
 * {@code getFrozenTarget()} is not covered here because instantiating a
 * {@code net.minecraft.world.entity.Entity} (even via Mockito) triggers
 * Minecraft static initializers outside the NeoForge runtime. That branch
 * is trivially verified by inspection and by in-world UAT.
 */
class ThrowFreezeStateTest {

    private static final BlockPos POS_A = new BlockPos(10, 64, 20);
    private static final BlockPos POS_B = new BlockPos(11, 64, 20);
    private static final TargetResult BLOCK_A =
            new TargetResult.BlockTarget(POS_A, Direction.UP, false);

    @BeforeEach
    void reset() {
        ThrowFreezeState.clear();
    }

    @Nested
    class ArmAndTick {
        @Test
        void unarmedIsNotFrozen() {
            assertFalse(ThrowFreezeState.isFrozen());
            assertNull(ThrowFreezeState.getFrozenTarget());
        }

        @Test
        void armEnablesFreeze() {
            ThrowFreezeState.arm(BLOCK_A);
            assertTrue(ThrowFreezeState.isFrozen());
            assertEquals(BLOCK_A, ThrowFreezeState.getFrozenTarget());
        }

        @Test
        void freezeExpiresAfterExactlyTenTicks() {
            ThrowFreezeState.arm(BLOCK_A);
            // FREEZE_TICKS-1 ticks: still frozen
            for (int i = 0; i < ThrowFreezeState.FREEZE_TICKS - 1; i++) {
                ThrowFreezeState.tick();
                assertTrue(ThrowFreezeState.isFrozen(),
                        "still frozen after " + (i + 1) + " ticks");
            }
            // Final tick: expires
            ThrowFreezeState.tick();
            assertFalse(ThrowFreezeState.isFrozen());
            assertNull(ThrowFreezeState.getFrozenTarget());
        }

        @Test
        void tickWhenUnarmedIsNoop() {
            // Must not throw, must stay unfrozen.
            ThrowFreezeState.tick();
            ThrowFreezeState.tick();
            assertFalse(ThrowFreezeState.isFrozen());
        }

        @Test
        void reArmResetsTimer() {
            ThrowFreezeState.arm(BLOCK_A);
            for (int i = 0; i < 5; i++) {
                ThrowFreezeState.tick();
            }
            ThrowFreezeState.arm(BLOCK_A);
            // Should still have the full window left.
            for (int i = 0; i < ThrowFreezeState.FREEZE_TICKS - 1; i++) {
                ThrowFreezeState.tick();
                assertTrue(ThrowFreezeState.isFrozen());
            }
        }
    }

    @Nested
    class Clear {
        @Test
        void clearDropsFreeze() {
            ThrowFreezeState.arm(BLOCK_A);
            ThrowFreezeState.clear();
            assertFalse(ThrowFreezeState.isFrozen());
            assertNull(ThrowFreezeState.getFrozenTarget());
        }
    }

    @Nested
    class IsFrozenOnChainMarker {
        @Test
        void returnsFalseWhenUnarmed() {
            assertFalse(ThrowFreezeState.isFrozenOnChainMarker(POS_A));
        }

        @Test
        void matchesBlockTargetPos() {
            ThrowFreezeState.arm(BLOCK_A);
            assertTrue(ThrowFreezeState.isFrozenOnChainMarker(POS_A));
            assertFalse(ThrowFreezeState.isFrozenOnChainMarker(POS_B));
        }

        @Test
        void matchesChainMarkerTargetPos() {
            ThrowFreezeState.arm(new TargetResult.ChainMarkerTarget(POS_A));
            assertTrue(ThrowFreezeState.isFrozenOnChainMarker(POS_A));
            assertFalse(ThrowFreezeState.isFrozenOnChainMarker(POS_B));
        }
    }
}
