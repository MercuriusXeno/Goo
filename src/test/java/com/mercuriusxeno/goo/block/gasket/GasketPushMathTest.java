package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GasketPushMath}: pure push logic without Minecraft dependencies.
 */
class GasketPushMathTest {

    /** Empty reservoir produces empty result. */
    @Test
    void emptyReservoirProducesEmptyResult() {
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            GooContents.EMPTY, (type, vol) -> vol);
        assertTrue(result.accepted().isEmpty());
        assertTrue(result.remaining().isEmpty());
    }

    /** Single type fully accepted: entire volume transfers, nothing remains. */
    @Test
    void singleTypeFullyAccepted() {
        GooContents reservoir = new GooContents(Map.of(GooType.ROCK, 500));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> vol);
        assertEquals(500, result.accepted().getVolume(GooType.ROCK));
        assertTrue(result.remaining().isEmpty());
    }

    /** Single type partially accepted: remainder stays in reservoir. */
    @Test
    void singleTypePartiallyAccepted() {
        GooContents reservoir = new GooContents(Map.of(GooType.METAL, 1000));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> 300);
        assertEquals(300, result.accepted().getVolume(GooType.METAL));
        assertEquals(700, result.remaining().getVolume(GooType.METAL));
    }

    /** Multi-type with mixed acceptance: each type handled independently. */
    @Test
    void multiTypeMixedAcceptance() {
        GooContents reservoir = new GooContents(Map.of(
            GooType.ROCK, 400,
            GooType.VITAL, 600
        ));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> {
                if (type == GooType.ROCK) return vol;       // fully accepted
                if (type == GooType.VITAL) return 200;     // partially accepted
                return 0;
            });
        assertEquals(400, result.accepted().getVolume(GooType.ROCK));
        assertEquals(200, result.accepted().getVolume(GooType.VITAL));
        assertEquals(0, result.remaining().getVolume(GooType.ROCK));
        assertEquals(400, result.remaining().getVolume(GooType.VITAL));
    }

    /** Destination full (accepts 0): all goo remains in reservoir. */
    @Test
    void destinationFullReturnsAllAsRemaining() {
        GooContents reservoir = new GooContents(Map.of(
            GooType.BLAZE, 1000,
            GooType.FROST, 500
        ));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> 0);
        assertTrue(result.accepted().isEmpty());
        assertEquals(1000, result.remaining().getVolume(GooType.BLAZE));
        assertEquals(500, result.remaining().getVolume(GooType.FROST));
    }

    /** Acceptor returning more than offered is clamped to offered volume. */
    @Test
    void acceptorOverclaimClampedToOffered() {
        GooContents reservoir = new GooContents(Map.of(GooType.GLOW, 100));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> 9999);
        assertEquals(100, result.accepted().getVolume(GooType.GLOW));
        assertTrue(result.remaining().isEmpty());
    }

    /** Acceptor returning negative is clamped to zero. */
    @Test
    void acceptorNegativeClampedToZero() {
        GooContents reservoir = new GooContents(Map.of(GooType.HEX, 200));
        GasketPushMath.PushResult result = GasketPushMath.computePush(
            reservoir, (type, vol) -> -50);
        assertTrue(result.accepted().isEmpty());
        assertEquals(200, result.remaining().getVolume(GooType.HEX));
    }

    // --- taperRate tests ---

    /** Zero remaining yields zero rate. */
    @Test
    void taperRateZero() {
        assertEquals(0, GasketPushMath.taperRate(0));
    }

    /** One mB remaining yields 1 mB/tick (floor at 1). */
    @Test
    void taperRateOne() {
        assertEquals(1, GasketPushMath.taperRate(1));
    }

    /** 1000 mB: ceil(1000^0.6) = ceil(63.096) = 64. */
    @Test
    void taperRate1000() {
        assertEquals(64, GasketPushMath.taperRate(1000));
    }

    /** 65536 mB (full canister): ceil(65536^0.6) = ceil(776.05) = 777. */
    @Test
    void taperRate65536() {
        assertEquals(777, GasketPushMath.taperRate(65536));
    }

    /** Negative remaining is treated as zero. */
    @Test
    void taperRateNegative() {
        assertEquals(0, GasketPushMath.taperRate(-5));
    }

    // --- computeTaperedPush tests ---

    /** Tapered push caps offer at taperRate; acceptor takes all offered. */
    @Test
    void taperedPushCapsOffer() {
        GooContents reservoir = new GooContents(Map.of(GooType.ROCK, 1000));
        GasketPushMath.PushResult result = GasketPushMath.computeTaperedPush(
            reservoir, (type, vol) -> vol);
        int expectedRate = GasketPushMath.taperRate(1000); // 64
        assertEquals(expectedRate, result.accepted().getVolume(GooType.ROCK));
        assertEquals(1000 - expectedRate, result.remaining().getVolume(GooType.ROCK));
    }

    /** Tapered push: acceptor rejects partial - accepted is acceptor's limit. */
    @Test
    void taperedPushAcceptorRejects() {
        GooContents reservoir = new GooContents(Map.of(GooType.ROCK, 1000));
        GasketPushMath.PushResult result = GasketPushMath.computeTaperedPush(
            reservoir, (type, vol) -> 10);
        assertEquals(10, result.accepted().getVolume(GooType.ROCK));
        assertEquals(990, result.remaining().getVolume(GooType.ROCK));
    }

    /** Tapered push on empty reservoir is a no-op. */
    @Test
    void taperedPushEmptyReservoir() {
        GasketPushMath.PushResult result = GasketPushMath.computeTaperedPush(
            GooContents.EMPTY, (type, vol) -> vol);
        assertTrue(result.accepted().isEmpty());
        assertTrue(result.remaining().isEmpty());
    }
}
