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

    /**
     * No candidates: volume, age, and discard list are unchanged.
     */
    @Test
    void empty_noCandidates_unchanged() {
        Result r = OmniblobAbsorb.compute(5, 1000, 50, List.of());
        assertEquals(1000, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /**
     * A single candidate with a strictly greater ID is absorbed.
     */
    @Test
    void greaterId_absorbed() {
        Result r = OmniblobAbsorb.compute(5, 1000, 50,
                List.of(new Candidate(7, 2000, 30)));
        assertEquals(3000, r.volume());
        assertEquals(30, r.age());
        assertEquals(List.of(7), r.discardIds());
    }

    /**
     * A candidate with a smaller ID is skipped (it would be the absorber itself).
     */
    @Test
    void lesserId_skipped() {
        Result r = OmniblobAbsorb.compute(5, 1000, 50,
                List.of(new Candidate(3, 2000, 30)));
        assertEquals(1000, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /**
     * An equal ID (impossible in practice, but guarded) is skipped.
     */
    @Test
    void sameId_skipped() {
        Result r = OmniblobAbsorb.compute(5, 1000, 50,
                List.of(new Candidate(5, 2000, 30)));
        assertEquals(1000, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    /**
     * Mixed IDs: only strictly greater candidates contribute.
     */
    @Test
    void mixedIds_onlyGreaterAbsorbed() {
        Result r = OmniblobAbsorb.compute(5, 1000, 50,
                List.of(
                        new Candidate(3, 2000, 10),
                        new Candidate(7, 3000, 20),
                        new Candidate(9, 4000, 40)
                ));
        assertEquals(8000, r.volume());
        assertEquals(20, r.age());
        assertEquals(List.of(7, 9), r.discardIds());
    }

    /**
     * Self is older than an absorbed candidate: candidate age wins.
     */
    @Test
    void minAge_selfOlder_candidateWins() {
        Result r = OmniblobAbsorb.compute(1, 1000, 100,
                List.of(new Candidate(2, 500, 5)));
        assertEquals(5, r.age());
    }

    /**
     * Self is younger than an absorbed candidate: self age is retained.
     */
    @Test
    void minAge_selfYounger_selfRetained() {
        Result r = OmniblobAbsorb.compute(1, 1000, 5,
                List.of(new Candidate(2, 500, 100)));
        assertEquals(5, r.age());
    }

    /**
     * Multiple absorbed candidates: age is the overall minimum.
     */
    @Test
    void minAge_acrossMany() {
        Result r = OmniblobAbsorb.compute(1, 1000, 80,
                List.of(
                        new Candidate(2, 500, 60),
                        new Candidate(3, 500, 15),
                        new Candidate(4, 500, 45)
                ));
        assertEquals(15, r.age());
    }

    /**
     * Only ineligible candidates present: nothing absorbed, nothing discarded.
     */
    @Test
    void onlyLesserIds_nothingAbsorbed() {
        Result r = OmniblobAbsorb.compute(10, 1000, 50,
                List.of(
                        new Candidate(3, 2000, 10),
                        new Candidate(7, 3000, 20)
                ));
        assertEquals(1000, r.volume());
        assertEquals(50, r.age());
        assertTrue(r.discardIds().isEmpty());
    }

    // -- findAttractorId --

    /**
     * No candidates: no attractor.
     */
    @Test
    void attractor_empty_returnsNone() {
        assertEquals(-1, OmniblobAbsorb.findAttractorId(5, List.of()));
    }

    /**
     * Every candidate is higher-id: self is the attractor, returns -1.
     */
    @Test
    void attractor_allHigherIds_returnsNone() {
        int r = OmniblobAbsorb.findAttractorId(5, List.of(
                new Candidate(7, 1000, 0),
                new Candidate(9, 1000, 0)
        ));
        assertEquals(-1, r);
    }

    /**
     * A candidate equal to self is not an attractor (strict less-than).
     */
    @Test
    void attractor_equalId_returnsNone() {
        int r = OmniblobAbsorb.findAttractorId(5, List.of(new Candidate(5, 1000, 0)));
        assertEquals(-1, r);
    }

    /**
     * Single lower-id candidate: it is the attractor.
     */
    @Test
    void attractor_singleLowerId_returnsThat() {
        int r = OmniblobAbsorb.findAttractorId(5, List.of(new Candidate(3, 1000, 0)));
        assertEquals(3, r);
    }

    /**
     * Multiple lower-id candidates: returns the smallest.
     */
    @Test
    void attractor_multipleLowerIds_returnsSmallest() {
        int r = OmniblobAbsorb.findAttractorId(10, List.of(
                new Candidate(8, 1000, 0),
                new Candidate(3, 1000, 0),
                new Candidate(6, 1000, 0)
        ));
        assertEquals(3, r);
    }

    /**
     * Mix of lower and higher ids: still returns the smallest lower.
     */
    @Test
    void attractor_mixedIds_returnsSmallestLower() {
        int r = OmniblobAbsorb.findAttractorId(5, List.of(
                new Candidate(9, 1000, 0),
                new Candidate(2, 1000, 0),
                new Candidate(7, 1000, 0),
                new Candidate(4, 1000, 0)
        ));
        assertEquals(2, r);
    }
}
