package com.mercuriusxeno.goo.item;

/**
 * Pure tier naming and volume constants for goo blobs, extracted
 * from GooBlobItem so unit tests can run without Minecraft class init.
 */
public final class BlobTiers {

    /** Volume cost to throw a blob (1 blob = 1000 mB). */
    public static final long THROW_COST = 1000L;

    /** Volume threshold: microblob tier (<1,000 mB). */
    private static final long MICROBLOB_THRESHOLD = 1_000L;
    /** Volume threshold: blob tier (<10 million mB). */
    private static final long BLOB_THRESHOLD = 10_000_000L;
    /** Volume threshold: kiloblob tier (<1 billion mB). */
    private static final long KILOBLOB_THRESHOLD = 1_000_000_000L;
    /** Volume threshold: megablob tier (<1 trillion mB). */
    private static final long MEGABLOB_THRESHOLD = 1_000_000_000_000L;
    /** Volume threshold: gigablob tier (<1 quadrillion mB). */
    private static final long GIGABLOB_THRESHOLD = 1_000_000_000_000_000L;

    /** Display name for the microblob tier. */
    private static final String TIER_MICROBLOB = "Microblob";
    /** Display name for the blob tier. */
    private static final String TIER_BLOB = "Blob";
    /** Display name for the kiloblob tier. */
    private static final String TIER_KILOBLOB = "Kiloblob";
    /** Display name for the megablob tier. */
    private static final String TIER_MEGABLOB = "Megablob";
    /** Display name for the gigablob tier. */
    private static final String TIER_GIGABLOB = "Gigablob";
    /** Display name for the terrablob tier. */
    private static final String TIER_TERRABLOB = "Terrablob";

    /** Sorted thresholds for tier lookup. */
    private static final long[] TIER_THRESHOLDS = {
        MICROBLOB_THRESHOLD, BLOB_THRESHOLD, KILOBLOB_THRESHOLD,
        MEGABLOB_THRESHOLD, GIGABLOB_THRESHOLD
    };
    /** Tier names indexed by threshold (plus one for the fallback tier). */
    private static final String[] TIER_NAMES = {
        TIER_MICROBLOB, TIER_BLOB, TIER_KILOBLOB,
        TIER_MEGABLOB, TIER_GIGABLOB, TIER_TERRABLOB
    };

    private BlobTiers() {}

    /**
     * Returns the display tier name based on volume thresholds.
     *
     * @param volume the volume in microblobs
     * @return the tier display name
     */
    public static String computeTierName(long volume) {
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (volume < TIER_THRESHOLDS[i]) { return TIER_NAMES[i]; }
        }
        return TIER_NAMES[TIER_NAMES.length - 1];
    }
}
