package com.mercuriusxeno.goo.item;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure merge computation for omniblob ground absorption. Lives outside
 * {@link GooOmniblobItem} so tests can exercise the rule without pulling
 * {@link net.minecraft.world.item.Item}'s static init (which requires the
 * Minecraft {@code Bootstrap} to have run).
 *
 * <p>Only candidates with strictly greater entity IDs than the absorber are
 * merged. Because entity IDs are monotonic and unique per world, this "lowest
 * ID wins" tiebreak guarantees that every spatial cluster of same-type
 * omniblobs has exactly one absorber - no locks, no double-absorb races.
 */
public final class OmniblobAbsorb {

    /** Sentinel returned by {@link #findAttractorId} when self is the cluster's lowest-ID member. */
    public static final int NO_ATTRACTOR = -1;

    private OmniblobAbsorb() {}

    /**
     * A nearby omniblob candidate considered for absorption.
     *
     * @param id     entity id of the candidate
     * @param volume stored volume in microblobs
     * @param age    current age of the candidate in ticks
     */
    public record Candidate(int id, long volume, int age) {}

    /**
     * Result of an absorb pass.
     *
     * @param volume     summed volume in microblobs
     * @param age        new age for the absorber (min across participants)
     * @param discardIds ids of absorbed candidates in iteration order
     */
    public record Result(long volume, int age, List<Integer> discardIds) {}

    /**
     * Computes the post-merge volume, new age, and discard list.
     * Candidates with ID less than or equal to {@code selfId} are skipped.
     * The new age is the minimum across the absorber and every absorbed
     * candidate (vanilla's own merge rule at {@code ItemEntity.merge}).
     *
     * @param selfId     entity id of the would-be absorber
     * @param selfVolume absorber's current volume in microblobs
     * @param selfAge    absorber's current age in ticks
     * @param candidates nearby same-type omniblob entities
     * @return the combined volume, new age, and list of candidate ids to discard
     */
    public static Result compute(int selfId, long selfVolume, int selfAge,
            List<Candidate> candidates) {
        long total = selfVolume;
        int minAge = selfAge;
        List<Integer> discard = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.id() <= selfId) { continue; }
            total += c.volume();
            if (c.age() < minAge) { minAge = c.age(); }
            discard.add(c.id());
        }
        return new Result(total, minAge, discard);
    }

    /**
     * Returns the smallest candidate ID that is strictly less than {@code selfId},
     * or {@code -1} if no candidate is lower. The caller uses this to decide
     * whether to act as attractor (no lower-ID neighbor → stay put, absorb on
     * near-collision) or attractee (pull self toward the returned id).
     *
     * <p>This keeps the cluster's anchor deterministic: the lowest-ID omniblob
     * in any visible group is the only one that does not move, and every other
     * member drifts toward it.
     *
     * @param selfId     entity id of the querying omniblob
     * @param candidates nearby same-type omniblob entities
     * @return the smallest lower candidate id, or -1 if none are lower
     */
    public static int findAttractorId(int selfId, List<Candidate> candidates) {
        int best = NO_ATTRACTOR;
        for (Candidate c : candidates) {
            if (c.id() >= selfId) { continue; }
            if (best == NO_ATTRACTOR || c.id() < best) { best = c.id(); }
        }
        return best;
    }
}
