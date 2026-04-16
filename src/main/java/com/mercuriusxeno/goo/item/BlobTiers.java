package com.mercuriusxeno.goo.item;

/**
 * Pure tier naming and volume constants for goo blobs, extracted
 * from GooBlobItem so unit tests can run without Minecraft class init.
 */
public final class BlobTiers {

    /** Volume cost to throw a blob (1 blob = 1000 mB). */
    public static final int THROW_COST = 1000;

    /** Volume threshold: microblob tier (<1,000 mB). */
    private static final int MICROBLOB_THRESHOLD = 1_000;
    /** Volume threshold: blob tier (<10 million mB). */
    private static final int BLOB_THRESHOLD = 10_000_000;
    /** Volume threshold: kiloblob tier (<1 billion mB). */
    private static final int KILOBLOB_THRESHOLD = 1_000_000_000;

    /** Display name for the microblob tier. */
    private static final String TIER_MICROBLOB = "Microblob";
    /** Display name for the blob tier. */
    private static final String TIER_BLOB = "Blob";
    /** Display name for the kiloblob tier. */
    private static final String TIER_KILOBLOB = "Kiloblob";
    /** Display name for the megablob tier (ceiling). */
    private static final String TIER_MEGABLOB = "Megablob";

    /** Sorted thresholds for tier lookup. */
    private static final int[] TIER_THRESHOLDS = {
        MICROBLOB_THRESHOLD, BLOB_THRESHOLD, KILOBLOB_THRESHOLD
    };
    /** Tier names indexed by threshold (plus one for the fallback tier). */
    private static final String[] TIER_NAMES = {
        TIER_MICROBLOB, TIER_BLOB, TIER_KILOBLOB, TIER_MEGABLOB
    };

    private BlobTiers() {}

    /**
     * Returns the display tier name based on volume thresholds.
     *
     * @param volume the volume in microblobs
     * @return the tier display name
     */
    public static String computeTierName(int volume) {
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (volume < TIER_THRESHOLDS[i]) { return TIER_NAMES[i]; }
        }
        return TIER_NAMES[TIER_NAMES.length - 1];
    }
}
