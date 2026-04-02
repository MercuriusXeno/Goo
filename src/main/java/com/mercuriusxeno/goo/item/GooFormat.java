package com.mercuriusxeno.goo.item;

/**
 * Pure formatting utilities for goo volumes. Shared between server-side
 * data components and client-side renderers.
 */
public final class GooFormat {

    private GooFormat() {}

    /**
     * Formats a microblob amount into a human-readable string with unit suffix.
     * Tiers: mB (< 1000), B (< 1e6), KB (< 1e9), MB (< 1e12), GB (< 1e15), TB (>= 1e15).
     */
    public static String formatFluidDisplay(long microblobs) {
        if (microblobs < 1_000L) {
            return formatMicroblobs(microblobs);
        }
        if (microblobs < 1_000_000L) {
            return formatWithDecimal(microblobs, 1_000L, "");
        }
        if (microblobs < 1_000_000_000L) {
            return formatWithDecimal(microblobs, 1_000_000L, "K");
        }
        if (microblobs < 1_000_000_000_000L) {
            return formatWithDecimal(microblobs, 1_000_000_000L, "M");
        }
        if (microblobs < 1_000_000_000_000_000L) {
            return formatWithDecimal(microblobs, 1_000_000_000_000L, "G");
        }
        return formatWithDecimal(microblobs, 1_000_000_000_000_000L, "T");
    }

    /** Compact format for item slot overlay: no "B" suffix, no spaces, 3 sig digits. */
    public static String formatFluidDisplayCompact(long microblobs) {
        if (microblobs < 1_000L) {
            return formatMicroblobs(microblobs);
        }
        return formatCompactWithSigDigits(microblobs);
    }

    /** Formats sub-blob amounts as a decimal fraction (e.g. 100 mB -> ".100", 1 mB -> ".001"). */
    static String formatMicroblobs(long microblobs) {
        return "." + String.format("%03d", microblobs);
    }

    /** Formats a value with 4 significant digits and the given suffix. */
    static String formatWithDecimal(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = value % divisor;
        int wholeDigits = Long.toString(whole).length();
        int decimalDigits = 4 - wholeDigits;
        String sep = suffix.isEmpty() ? "" : " ";

        if (decimalDigits <= 0 || remainder == 0) {
            return whole + sep + suffix;
        }
        return whole + "." + formatFraction(remainder, divisor, decimalDigits) + sep + suffix;
    }

    /** Extracts the first n decimal digits of remainder/divisor, trimming trailing zeros. */
    static String formatFraction(long remainder, long divisor, int digits) {
        long scaled = remainder;
        for (int i = 0; i < digits; i++) {
            scaled *= 10;
        }
        String raw = Long.toString(scaled / divisor);
        while (raw.length() < digits) {
            raw = "0" + raw;
        }
        int end = raw.length();
        while (end > 1 && raw.charAt(end - 1) == '0') {
            end--;
        }
        return raw.substring(0, end);
    }

    /** Routes to the correct tier and formats compactly with 3 significant digits. */
    private static String formatCompactWithSigDigits(long microblobs) {
        if (microblobs < 1_000_000L) {
            return compactSigDigits(microblobs, 1_000L, "");
        }
        if (microblobs < 1_000_000_000L) {
            return compactSigDigits(microblobs, 1_000_000L, "K");
        }
        if (microblobs < 1_000_000_000_000L) {
            return compactSigDigits(microblobs, 1_000_000_000L, "M");
        }
        if (microblobs < 1_000_000_000_000_000L) {
            return compactSigDigits(microblobs, 1_000_000_000_000L, "G");
        }
        return compactSigDigits(microblobs, 1_000_000_000_000_000L, "T");
    }

    /** Formats with 3 significant digits, no spaces, trailing zeros trimmed. */
    private static String compactSigDigits(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = value % divisor;
        int wholeDigits = Long.toString(whole).length();
        int decimalDigits = 3 - wholeDigits;

        if (decimalDigits <= 0 || remainder == 0) {
            return whole + suffix;
        }
        return whole + "." + formatFraction(remainder, divisor, decimalDigits) + suffix;
    }
}
