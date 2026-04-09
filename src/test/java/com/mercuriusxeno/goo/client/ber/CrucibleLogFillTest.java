package com.mercuriusxeno.goo.client.ber;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the crucible BER's logarithmic liquid fill curve.
 */
class CrucibleLogFillTest {

    private static final long CAP = CrucibleBlockEntityRenderer.LIQUID_LOG_CAP;

    /** Zero volume gives zero fill. */
    @Test
    void zeroVolumeGivesZeroFill() {
        assertEquals(0f, CrucibleBlockEntityRenderer.computeLogFill(0, CAP), 0.001f);
    }

    /** Volume at cap gives full fill. */
    @Test
    void volumeAtCapGivesFullFill() {
        assertEquals(1f, CrucibleBlockEntityRenderer.computeLogFill(CAP, CAP), 0.001f);
    }

    /** Volume above cap clamps to 1.0. */
    @Test
    void volumeAboveCapClampsToOne() {
        assertEquals(1f, CrucibleBlockEntityRenderer.computeLogFill(CAP * 3, CAP), 0.001f);
    }

    /** Small volume produces a front-loaded fill (more than linear). */
    @Test
    void smallVolumeFrontLoaded() {
        float fill = CrucibleBlockEntityRenderer.computeLogFill(1_000L, CAP);
        float linearFill = 1_000f / (float) CAP;
        assertTrue(fill > linearFill,
            "Logarithmic fill should exceed linear for small volumes");
        assertTrue(fill < 1f, "Should not be full");
    }

    /** Negative volume gives zero fill. */
    @Test
    void negativeVolumeGivesZeroFill() {
        assertEquals(0f, CrucibleBlockEntityRenderer.computeLogFill(-100, CAP), 0.001f);
    }

    /** Fill is monotonically increasing with volume. */
    @Test
    void fillIncreasesMonotonically() {
        float prev = 0f;
        for (long v = 100; v <= CAP; v += 1000) {
            float fill = CrucibleBlockEntityRenderer.computeLogFill(v, CAP);
            assertTrue(fill > prev, "Fill should increase with volume at v=" + v);
            prev = fill;
        }
    }
}
