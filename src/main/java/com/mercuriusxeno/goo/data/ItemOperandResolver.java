package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves item reference operands in integer expressions.
 * Handles dot-notation (e.g. minecraft:coal.blaze) to extract a single
 * goo type value from an item's GooValue.
 */
final class ItemOperandResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Dot separator for type extraction. */
    private static final String DOT = ".";
    /** Colon separator for namespaced IDs. */
    private static final String COLON = ":";
    /** Sentinel indicating no valid dot position was found. */
    private static final int NO_DOT = -1;
    /** Log warning for unknown item in dot-notation. */
    private static final String WARN_UNKNOWN_ITEM = "Unknown item in dot-notation: {}";
    /** Log warning for item reference without .type. */
    private static final String WARN_ITEM_NO_TYPE = "Item reference without .type in int expression: {}";

    private ItemOperandResolver() {}

    /**
     * Resolves an item reference operand: dot-notation (e.g. minecraft:coal.blaze) extracts
     * a single type, bare namespaced IDs without .type produce a warning.
     *
     * @param token the item reference token
     * @param baseValues item values for lookups
     * @return the resolved integer value
     */
    static int resolveItemOperand(String token, Map<Identifier, GooValue> baseValues) {
        if (token.contains(DOT)) {
            int extracted = tryItemDotExtraction(token, baseValues);
            if (extracted != Integer.MIN_VALUE) {
                return extracted;
            }
        }
        return resolveBareName(token);
    }

    /**
     * Handles a bare (non-dot-notation) item token: namespaced IDs without a .type
     * suffix get a warning and return 0, plain numbers are parsed as integers.
     *
     * @param token the bare item reference or numeric literal
     * @return 0 for namespaced IDs missing a type, or the parsed integer
     */
    private static int resolveBareName(String token) {
        if (token.contains(COLON)) {
            LOGGER.warn(WARN_ITEM_NO_TYPE, token);
            return 0;
        }
        return Integer.parseInt(token);
    }

    /**
     * Attempts to extract a single goo type value from an item via dot notation
     * (e.g. minecraft:coal.blaze). Returns {@link Integer#MIN_VALUE} if the suffix
     * is not a valid goo type or the item is unknown.
     *
     * @param token the full dot-notation token
     * @param baseValues item values for lookups
     * @return the extracted type value, or Integer.MIN_VALUE if not resolvable
     */
    private static int tryItemDotExtraction(String token,
                                            Map<Identifier, GooValue> baseValues) {
        int dotIdx = findTypeDot(token);
        if (dotIdx < 0) {
            return Integer.MIN_VALUE;
        }
        return resolveItemType(token, dotIdx, baseValues);
    }

    /**
     * Finds the position of the dot separating the item ID from its type suffix.
     * Returns {@link #NO_DOT} if no valid type-dot exists (dot at edges, or dot before a colon).
     *
     * @param token the full dot-notation token
     * @return the dot index, or NO_DOT if no valid type-dot is found
     */
    private static int findTypeDot(String token) {
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx >= token.length() - 1) {
            return NO_DOT;
        }
        int colonIdx = token.indexOf(COLON.charAt(0));
        return (colonIdx >= 0 && dotIdx <= colonIdx) ? NO_DOT : dotIdx;
    }

    /**
     * Resolves the goo type and item lookup for a dot-notation token once the dot
     * position is known. Returns {@link Integer#MIN_VALUE} for unrecognized types,
     * warns and returns 0 for unknown items.
     *
     * @param token      the full dot-notation token
     * @param dotIdx     position of the type-separating dot
     * @param baseValues item values for lookups
     * @return the extracted type value, or Integer.MIN_VALUE if the type is invalid
     */
    private static int resolveItemType(String token, int dotIdx,
                                       Map<Identifier, GooValue> baseValues) {
        String typeSuffix = token.substring(dotIdx + 1);
        try {
            GooType type = GooType.valueOf(typeSuffix.toUpperCase(Locale.ROOT));
            return lookupItemValue(token.substring(0, dotIdx), type, baseValues);
        } catch (IllegalArgumentException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Looks up an item's value for a specific goo type. Warns and returns 0 if
     * the item ID is not found in the base values map.
     *
     * @param itemId     the namespaced item identifier string
     * @param type       the goo type to extract
     * @param baseValues item values for lookups
     * @return the item's amount for the given type, or 0 if the item is unknown
     */
    private static int lookupItemValue(String itemId, GooType type,
                                       Map<Identifier, GooValue> baseValues) {
        GooValue value = baseValues.get(Identifier.parse(itemId));
        if (value == null) {
            LOGGER.warn(WARN_UNKNOWN_ITEM, itemId);
            return 0;
        }
        return value.get(type);
    }
}
