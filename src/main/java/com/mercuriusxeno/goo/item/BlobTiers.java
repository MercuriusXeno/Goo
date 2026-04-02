package com.mercuriusxeno.goo.item;

/**
 * Pure tier naming and volume constants for goo blobs, extracted
 * from GooBlobItem so unit tests can run without Minecraft class init.
 */
public final class BlobTiers {

    private BlobTiers() {}

    /** Volume cost to throw a blob (1 blob = 1000 mB). */
    public static final long THROW_COST = 1000L;

    /**
     * Returns the display tier name based on volume thresholds.
     * <1000: Microblob, <1e7: Blob, <1e9: Kiloblob, <1e12: Megablob,
     * <1e15: Gigablob, else: Terrablob.
     */
    public static String computeTierName(long volume) {
        if (volume < 1_000L) return "Microblob";
        if (volume < 10_000_000L) return "Blob";
        if (volume < 1_000_000_000L) return "Kiloblob";
        if (volume < 1_000_000_000_000L) return "Megablob";
        if (volume < 1_000_000_000_000_000L) return "Gigablob";
        return "Terrablob";
    }
}
