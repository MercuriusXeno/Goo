package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.OmniblobAbsorb.Candidate;
import com.mercuriusxeno.goo.item.OmniblobAbsorb.Result;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure omniblob ground-merge computation. Exercises ID tiebreak
 * (only strictly greater IDs are absorbed) and min-age propagation without
 * spinning up a Minecraft level.
 */
class OmniblobAbsorbTest {

    /** No candidates: volume, age, and discard list are unchanged. */
    @Test
    void empty_noCandidates_unchanged() {
        Result r = OmniblobAbsorb.compute(5, 1000L, 50, List.of());
        assertEquals(1000L, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /** A single candidate with a strictly greater ID is absorbed. */
    @Test
    void greaterId_absorbed() {
        Result r = OmniblobAbsorb.compute(5, 1000L, 50,
            List.of(new Candidate(7, 2000L, 30)));
        assertEquals(3000L, r.volume());
        assertEquals(30, r.age());
        assertEquals(List.of(7), r.discardIds());
    }

    /** A candidate with a smaller ID is skipped (it would be the absorber itself). */
    @Test
    void lesserId_skipped() {
        Result r = OmniblobAbsorb.compute(5, 1000L, 50,
            List.of(new Candidate(3, 2000L, 30)));
        assertEquals(1000L, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /** An equal ID (impossible in practice, but guarded) is skipped. */
    @Test
    void sameId_skipped() {
        Result r = OmniblobAbsorb.compute(5, 1000L, 50,
            List.of(new Candidate(5, 2000L, 30)));
        assertEquals(1000L, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /** Mixed IDs: only strictly greater candidates contribute. */
    @Test
    void mixedIds_onlyGreaterAbsorbed() {
        Result r = OmniblobAbsorb.compute(5, 1000L, 50,
            List.of(
                new Candidate(3, 2000L, 10),
                new Candidate(7, 3000L, 20),
                new Candidate(9, 4000L, 40)
            ));
        assertEquals(8000L, r.volume());
        assertEquals(20, r.age());
        assertEquals(List.of(7, 9), r.discardIds());
    }

    /** Self is older than an absorbed candidate: candidate age wins. */
    @Test
    void minAge_selfOlder_candidateWins() {
        Result r = OmniblobAbsorb.compute(1, 1000L, 100,
            List.of(new Candidate(2, 500L, 5)));
        assertEquals(5, r.age());
    }

    /** Self is younger than an absorbed candidate: self age is retained. */
    @Test
    void minAge_selfYounger_selfRetained() {
        Result r = OmniblobAbsorb.compute(1, 1000L, 5,
            List.of(new Candidate(2, 500L, 100)));
        assertEquals(5, r.age());
    }

    /** Multiple absorbed candidates: age is the overall minimum. */
    @Test
    void minAge_acrossMany() {
        Result r = OmniblobAbsorb.compute(1, 1000L, 80,
            List.of(
                new Candidate(2, 500L, 60),
                new Candidate(3, 500L, 15),
                new Candidate(4, 500L, 45)
            ));
        assertEquals(15, r.age());
    }

    /** Only ineligible candidates present: nothing absorbed, nothing discarded. */
    @Test
    void onlyLesserIds_nothingAbsorbed() {
        Result r = OmniblobAbsorb.compute(10, 1000L, 50,
            List.of(
                new Candidate(3, 2000L, 10),
                new Candidate(7, 3000L, 20)
            ));
        assertEquals(1000L, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }
}
