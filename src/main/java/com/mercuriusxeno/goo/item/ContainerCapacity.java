package com.mercuriusxeno.goo.item;

/**
 * Pure constants and capacity formulas for all goo container types.
 * Centralizes capacity logic that was previously scattered across
 * CanisterItem, VatBlockEntity, and implicit constants.
 */
public final class ContainerCapacity {

    /** Base canister capacity in microblobs (2^20 = 1,048,576 mB). */
    public static final int CANISTER_BASE = 1 << 20;

    /** Base vat capacity in microblobs (2^25 = 33,554,432 mB). */
    public static final int VAT_BASE = 1 << 25;

    /** Hard cap for blob item volume in microblobs (64,000 mB = 64 blobs). */
    public static final int BLOB_CAP = 64_000;

    /** Maximum Compression enchantment level (shared by canister and vat). */
    public static final int MAX_COMPRESSION = 5;

    private ContainerCapacity() {}

    /**
     * Computes canister capacity at the given compression level.
     * Formula: CANISTER_BASE << min(level, MAX_COMPRESSION).
     * Range: 2^20 mB (level 0) to 2^25 mB (level 5).
     *
     * @param compressionLevel the Compression enchantment level (0-5)
     * @return capacity in microblobs
     */
    public static int canisterCapacity(int compressionLevel) {
        return CANISTER_BASE << Math.max(0, Math.min(compressionLevel, MAX_COMPRESSION));
    }

    /**
     * Computes vat capacity at the given compression level.
     * Formula: VAT_BASE << min(level, MAX_COMPRESSION).
     * Range: 2^25 mB (level 0) to 2^30 mB (level 5).
     *
     * @param compressionLevel the Compression enchantment level (0-5)
     * @return capacity in microblobs
     */
    public static int vatCapacity(int compressionLevel) {
        return VAT_BASE << Math.max(0, Math.min(compressionLevel, MAX_COMPRESSION));
    }
}
