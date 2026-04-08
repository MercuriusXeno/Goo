package com.mercuriusxeno.goo.item;

/**
 * Pure formatting utilities for goo volumes. Shared between server-side
 * data components and client-side renderers.
 */
public final class GooFormat {

    /** Volume threshold: sub-blob microblobs (<1,000). */
    private static final long THRESHOLD_MICROBLOB = 1_000L;
    /** Volume threshold: blob tier (<1 million mB). */
    private static final long THRESHOLD_BLOB = 1_000_000L;
    /** Volume threshold: kiloblob tier (<1 billion mB). */
    private static final long THRESHOLD_KILO = 1_000_000_000L;
    /** Volume threshold: megablob tier (<1 trillion mB). */
    private static final long THRESHOLD_MEGA = 1_000_000_000_000L;
    /** Volume threshold: gigablob tier (<1 quadrillion mB). */
    private static final long THRESHOLD_GIGA = 1_000_000_000_000_000L;
    /** Divisor for blob tier. */
    private static final long DIVISOR_BLOB = 1_000L;
    /** Divisor for kiloblob tier. */
    private static final long DIVISOR_KILO = 1_000_000L;
    /** Divisor for megablob tier. */
    private static final long DIVISOR_MEGA = 1_000_000_000L;
    /** Divisor for gigablob tier. */
    private static final long DIVISOR_GIGA = 1_000_000_000_000L;
    /** Divisor for terrablob tier. */
    private static final long DIVISOR_TERRA = 1_000_000_000_000_000L;
    /** Number of significant digits in standard display format. */
    private static final int DISPLAY_SIG_DIGITS = 4;
    /** Number of significant digits in compact display format. */
    private static final int COMPACT_SIG_DIGITS = 3;
    /** Microblob format width (zero-padded to 3 digits). */
    private static final String MICROBLOB_FORMAT = "%03d";
    /** Decimal scale multiplier for formatFraction. */
    private static final int DECIMAL_BASE = 10;
    /** Suffix for blob tier (no prefix). */
    private static final String SUFFIX_BLOB = "";
    /** Suffix for kiloblob tier. */
    private static final String SUFFIX_KILO = "K";
    /** Suffix for megablob tier. */
    private static final String SUFFIX_MEGA = "M";
    /** Suffix for gigablob tier. */
    private static final String SUFFIX_GIGA = "G";
    /** Suffix for terrablob tier. */
    private static final String SUFFIX_TERRA = "T";
    /** Decimal point separator. */
    private static final String DOT = ".";
    /** Space separator between value and suffix. */
    private static final String SEP_SPACE = " ";

    /** Volume tier: threshold, divisor, and suffix for a formatting bracket. */
    private record FormatTier(long threshold, long divisor, String suffix) {}

    /** Tiers in ascending order; last tier uses MAX_VALUE as a sentinel. */
    private static final FormatTier[] TIERS = {
        new FormatTier(THRESHOLD_BLOB, DIVISOR_BLOB, SUFFIX_BLOB),
        new FormatTier(THRESHOLD_KILO, DIVISOR_KILO, SUFFIX_KILO),
        new FormatTier(THRESHOLD_MEGA, DIVISOR_MEGA, SUFFIX_MEGA),
        new FormatTier(THRESHOLD_GIGA, DIVISOR_GIGA, SUFFIX_GIGA),
        new FormatTier(Long.MAX_VALUE, DIVISOR_TERRA, SUFFIX_TERRA),
    };

    private GooFormat() {}

    /**
     * Formats a microblob amount into a human-readable string with unit suffix.
     *
     * @param microblobs the volume in microblobs
     * @return the formatted display string
     */
    public static String formatFluidDisplay(long microblobs) {
        if (microblobs < THRESHOLD_MICROBLOB) { return formatMicroblobs(microblobs); }
        FormatTier tier = findTier(microblobs);
        return formatWithDecimal(microblobs, tier.divisor, tier.suffix);
    }

    /** Returns the format tier for the given volume.
     *
     * @param microblobs the volume in microblobs
     * @return the matching tier
     */
    private static FormatTier findTier(long microblobs) {
        for (FormatTier tier : TIERS) {
            if (microblobs < tier.threshold) { return tier; }
        }
        return TIERS[TIERS.length - 1];
    }

    /**
     * Compact format for item slot overlay: no "B" suffix, no spaces, 3 sig digits.
     *
     * @param microblobs the volume in microblobs
     * @return the compact formatted string
     */
    public static String formatFluidDisplayCompact(long microblobs) {
        if (microblobs < THRESHOLD_MICROBLOB) {
            return formatMicroblobs(microblobs);
        }
        return formatCompactWithSigDigits(microblobs);
    }

    /**
     * Formats sub-blob amounts as a decimal fraction (e.g. 100 mB -> ".100", 1 mB -> ".001").
     *
     * @param microblobs the sub-blob volume (0-999)
     * @return the decimal fraction string
     */
    static String formatMicroblobs(long microblobs) {
        return DOT + String.format(MICROBLOB_FORMAT, microblobs);
    }

    /**
     * Formats a value with 4 significant digits and the given suffix.
     *
     * @param value   the volume in microblobs
     * @param divisor the tier divisor
     * @param suffix  the unit suffix (e.g. "K", "M")
     * @return the formatted string
     */
    static String formatWithDecimal(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = value % divisor;
        int decimalDigits = DISPLAY_SIG_DIGITS - Long.toString(whole).length();
        String sep = suffix.isEmpty() ? SUFFIX_BLOB : SEP_SPACE;
        if (decimalDigits <= 0 || remainder == 0) { return whole + sep + suffix; }
        return whole + DOT + formatFraction(remainder, divisor, decimalDigits) + sep + suffix;
    }

    /**
     * Extracts the first n decimal digits of remainder/divisor, trimming trailing zeros.
     *
     * @param remainder the remainder after whole division
     * @param divisor   the tier divisor
     * @param digits    the number of decimal digits
     * @return the formatted fraction string
     */
    static String formatFraction(long remainder, long divisor, int digits) {
        String raw = Long.toString(scaleRemainder(remainder, divisor, digits));
        return padAndTrimZeros(raw, digits);
    }

    /** Scales the remainder by 10^digits and divides by the tier divisor.
     *
     * @param remainder the remainder after whole division
     * @param divisor   the tier divisor
     * @param digits    the number of decimal digits
     * @return the scaled integer representing the decimal fraction
     */
    private static long scaleRemainder(long remainder, long divisor, int digits) {
        long scaled = remainder;
        for (int i = 0; i < digits; i++) { scaled *= DECIMAL_BASE; }
        return scaled / divisor;
    }

    /** Left-pads the raw digit string to the target width, then trims trailing zeros.
     *
     * @param raw    the raw digit string
     * @param digits the target width
     * @return the padded and trimmed string
     */
    private static String padAndTrimZeros(String raw, int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = raw.length(); i < digits; i++) { sb.append('0'); }
        sb.append(raw);
        int end = sb.length();
        while (end > 1 && sb.charAt(end - 1) == '0') { end--; }
        return sb.substring(0, end);
    }

    /**
     * Routes to the correct tier and formats compactly with 3 significant digits.
     *
     * @param microblobs the volume in microblobs
     * @return the compact formatted string
     */
    private static String formatCompactWithSigDigits(long microblobs) {
        FormatTier tier = findTier(microblobs);
        return compactSigDigits(microblobs, tier.divisor, tier.suffix);
    }

    /**
     * Formats with 3 significant digits, no spaces, trailing zeros trimmed.
     *
     * @param value   the volume in microblobs
     * @param divisor the tier divisor
     * @param suffix  the unit suffix
     * @return the compact formatted string
     */
    private static String compactSigDigits(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = value % divisor;
        int wholeDigits = Long.toString(whole).length();
        int decimalDigits = COMPACT_SIG_DIGITS - wholeDigits;

        if (decimalDigits <= 0 || remainder == 0) {
            return whole + suffix;
        }
        return whole + DOT + formatFraction(remainder, divisor, decimalDigits) + suffix;
    }
}
