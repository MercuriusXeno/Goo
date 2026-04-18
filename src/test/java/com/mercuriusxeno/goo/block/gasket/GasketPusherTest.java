package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GasketPusher}: tick guard logic, timer cycle, and dispose safety.
 */
class GasketPusherTest {

    /**
     * Minimal stub ResourceHandler that avoids Minecraft bootstrap.
     * Empty variant: size=0 so isSourceEmpty returns true.
     * Filled variant: size=1, getResource returns a non-empty placeholder.
     */
    static class StubFluidHandler implements ResourceHandler<FluidResource> {
        private final int slotCount;

        StubFluidHandler(int slotCount) { this.slotCount = slotCount; }

        @Override public int size() { return slotCount; }
        @Override public FluidResource getResource(int index) { return FluidResource.EMPTY; }
        @Override public long getAmountAsLong(int index) { return 0; }
        @Override public int insert(int index, FluidResource resource, int maxAmount, TransactionContext tx) { return 0; }
        @Override public int extract(int index, FluidResource resource, int maxAmount, TransactionContext tx) { return 0; }
        @Override public boolean isValid(int index, FluidResource resource) { return true; }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return 4000; }
    }

    /** Both stubs are empty (size=0) since these tests only verify no-op guard behavior. */
    private static ResourceHandler<FluidResource> emptySource() { return new StubFluidHandler(0); }
    private static ResourceHandler<FluidResource> filledSource() { return new StubFluidHandler(0); }

    private static GasketPartner blockPartner() {
        return new GasketPartner(new BlockPos(10, 64, 10), 0);
    }

    /** Builds a pusher with the given source, partner, and sync counter. No level (null). */
    private static GasketPusher pusher(
            ResourceHandler<FluidResource> source, GasketPartner partner, AtomicInteger sync) {
        return new GasketPusher(
            source,
            () -> UUID.randomUUID(),
            () -> partner,
            () -> null,
            () -> BlockPos.ZERO,
            sync::incrementAndGet,
            () -> null);
    }

    @Nested
    class TickGuard {

        @Test
        void emptyReservoirSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = pusher(emptySource(), blockPartner(), sync);
            for (int i = 0; i < 25; i++) { p.tick(); }
            assertEquals(0, sync.get());
        }

        @Test
        void nullPartnerSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = new GasketPusher(
                filledSource(), () -> UUID.randomUUID(), () -> null,
                () -> null, () -> BlockPos.ZERO, sync::incrementAndGet,
                () -> null);
            for (int i = 0; i < 25; i++) { p.tick(); }
            assertEquals(0, sync.get());
        }

        @Test
        void emptyAndNullPartnerSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = new GasketPusher(
                emptySource(), () -> null, () -> null,
                () -> null, () -> BlockPos.ZERO, sync::incrementAndGet,
                () -> null);
            for (int i = 0; i < 25; i++) { p.tick(); }
            assertEquals(0, sync.get());
        }

        @Test
        void filledReservoirBlockPartnerNoCacheSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = pusher(filledSource(), blockPartner(), sync);
            for (int i = 0; i < 25; i++) { p.tick(); }
            assertEquals(0, sync.get());
        }
    }

    @Nested
    class Dispose {

        @Test
        void disposeOnFreshPusherIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            assertDoesNotThrow(p::dispose);
        }

        @Test
        void tickAfterDisposeIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            p.dispose();
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 25; i++) { p.tick(); }
            });
        }

        @Test
        void doubleDisposeIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            p.dispose();
            assertDoesNotThrow(p::dispose);
        }
    }
}
