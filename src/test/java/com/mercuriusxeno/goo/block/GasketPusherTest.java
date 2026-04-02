package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GasketPusher}: tick guard logic, timer cycle, and dispose safety.
 */
class GasketPusherTest {

    /** Simple in-memory IGooSource for testing without NeoForge. */
    static class TestGooSource implements IGooSource {
        private GooContents contents;

        TestGooSource(GooContents contents) {
            this.contents = contents;
        }

        @Override
        public boolean isEmpty() { return contents.isEmpty(); }

        @Override
        public GooContents toGooContents() { return contents; }

        @Override
        public void loadFrom(GooContents contents) { this.contents = contents; }
    }

    private static TestGooSource emptySource() {
        return new TestGooSource(GooContents.EMPTY);
    }

    private static TestGooSource filledSource() {
        return new TestGooSource(new GooContents(Map.of(GooType.ROCK, 500L)));
    }

    private static GasketPartner blockPartner() {
        return new GasketPartner(new BlockPos(10, 64, 10), 0);
    }

    /** Builds a pusher with the given source, partner, and sync counter. No level (null). */
    private static GasketPusher pusher(IGooSource source, GasketPartner partner, AtomicInteger sync) {
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

        /** Tick does nothing when reservoir is empty. */
        @Test
        void emptyReservoirSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = pusher(emptySource(), blockPartner(), sync);

            for (int i = 0; i < 25; i++) p.tick();
            assertEquals(0, sync.get());
        }

        /** Tick does nothing when partner is null. */
        @Test
        void nullPartnerSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = new GasketPusher(
                filledSource(), () -> UUID.randomUUID(), () -> null,
                () -> null, () -> BlockPos.ZERO, sync::incrementAndGet,
                () -> null);

            for (int i = 0; i < 25; i++) p.tick();
            assertEquals(0, sync.get());
        }

        /** Tick does nothing when both reservoir empty and partner null. */
        @Test
        void emptyAndNullPartnerSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = new GasketPusher(
                emptySource(), () -> null, () -> null,
                () -> null, () -> BlockPos.ZERO, sync::incrementAndGet,
                () -> null);

            for (int i = 0; i < 25; i++) p.tick();
            assertEquals(0, sync.get());
        }

        /**
         * With a filled reservoir and a block partner but no endpoint cache,
         * hasPushableTarget is false (block targets need a cache). Tick is a no-op.
         */
        @Test
        void filledReservoirBlockPartnerNoCacheSkipsTick() {
            AtomicInteger sync = new AtomicInteger();
            GasketPusher p = pusher(filledSource(), blockPartner(), sync);

            for (int i = 0; i < 25; i++) p.tick();
            assertEquals(0, sync.get());
        }
    }

    @Nested
    class Dispose {

        /** Dispose on a fresh pusher does not throw. */
        @Test
        void disposeOnFreshPusherIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            assertDoesNotThrow(p::dispose);
        }

        /** Tick after dispose does not throw. */
        @Test
        void tickAfterDisposeIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            p.dispose();
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 25; i++) p.tick();
            });
        }

        /** Dispose is idempotent. */
        @Test
        void doubleDisposeIsSafe() {
            GasketPusher p = pusher(filledSource(), blockPartner(), new AtomicInteger());
            p.dispose();
            assertDoesNotThrow(p::dispose);
        }
    }

    @Nested
    class SourceInterface {

        /** TestGooSource correctly reports empty. */
        @Test
        void emptySourceIsEmpty() {
            assertTrue(emptySource().isEmpty());
        }

        /** TestGooSource correctly reports non-empty. */
        @Test
        void filledSourceIsNotEmpty() {
            assertFalse(filledSource().isEmpty());
        }

        /** loadFrom replaces contents. */
        @Test
        void loadFromReplacesContents() {
            TestGooSource source = filledSource();
            source.loadFrom(GooContents.EMPTY);
            assertTrue(source.isEmpty());
        }

        /** toGooContents returns current state. */
        @Test
        void toGooContentsReturnsCurrentState() {
            TestGooSource source = filledSource();
            assertEquals(500L, source.toGooContents().getVolume(GooType.ROCK));
        }
    }
}
