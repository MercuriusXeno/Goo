package com.mercuriusxeno.goo.data;

import java.util.List;
import java.util.Set;

/**
 * Validates expression tokens within goo value definitions: checks for forgotten
 * $ prefixes, unknown constants, and out-of-order item references. Extracted from
 * {@link GooValueValidator} to keep per-class method counts manageable.
 */
final class GooValueTokenValidator {

    /** Operator characters skipped during expression validation. */
    private static final String OPERATOR_CHARS = "+-*/()";
    /** Dollar-sign prefix for constant references. */
    private static final String DOLLAR_PREFIX = "$";
    /** Colon character used in namespaced identifiers. */
    private static final String COLON = ":";
    /** Default namespace for unqualified item names. */
    private static final String DEFAULT_NS_PREFIX = "minecraft:";

    /** Warning suffix: unknown constant prefix. */
    private static final String WARN_UNKNOWN_CONST_DOLLAR = ": unknown constant $";
    /** Warning infix: references item not defined above. */
    private static final String WARN_REFERENCES = ": references ";
    /** Warning suffix: not defined above it. */
    private static final String WARN_NOT_DEFINED = " which is not defined above it";
    /** Warning: missing $ prefix pattern start. */
    private static final String WARN_MISSING_DOLLAR_PREFIX = ": '";
    /** Warning: missing $ prefix pattern middle. */
    private static final String WARN_MISSING_DOLLAR_MID = "' looks like a constant missing its $ prefix (should be $";
    /** Warning: missing $ prefix pattern end. */
    private static final String WARN_MISSING_DOLLAR_SUFFIX = ")";
    /** Warning: unrecognized token prefix. */
    private static final String WARN_UNRECOGNIZED_PREFIX = ": unrecognized token '";
    /** Warning: unrecognized token suffix. */
    private static final String WARN_UNRECOGNIZED_SUFFIX = "'";

    private GooValueTokenValidator() {}

    /**
     * Checks expression tokens for forgotten $ prefixes, unknown constants, and out-of-order items.
     *
     * @param expr the expression string to validate
     * @param knownConstants known constant names
     * @param knownItems known item IDs (defined before this entry)
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateExprTokens(String expr, Set<String> knownConstants,
                                   Set<String> knownItems, String context,
                                   List<String> warnings) {
        for (String token : ExpressionTokenizer.tokenize(expr)) {
            if (!isOperatorToken(token)) {
                classifyAndValidateToken(token, knownConstants, knownItems, context, warnings);
            }
        }
    }

    /**
     * Dispatches a non-operator token to the appropriate validator by its prefix.
     *
     * @param token the token to classify and validate
     * @param knownConstants known constant names
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void classifyAndValidateToken(String token, Set<String> knownConstants,
                                         Set<String> knownItems, String context,
                                         List<String> warnings) {
        if (token.startsWith(DOLLAR_PREFIX)) {
            validateConstantToken(token, knownConstants, context, warnings);
        } else if (token.contains(COLON)) {
            validateNamespacedToken(token, knownItems, context, warnings);
        } else if (!Character.isDigit(token.charAt(0))) {
            validateBareWordToken(token, knownConstants, knownItems, context, warnings);
        }
    }

    /**
     * Returns true if the token is a single-character operator or parenthesis.
     *
     * @param token the token to check
     * @return true if operator/paren
     */
    static boolean isOperatorToken(String token) {
        return token.length() == 1 && OPERATOR_CHARS.contains(token);
    }

    /**
     * Validates a $-prefixed constant token, stripping dot-notation suffixes for lookup.
     *
     * @param token the constant token (e.g. "$food.vital")
     * @param knownConstants known constant names
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateConstantToken(String token, Set<String> knownConstants,
                                      String context, List<String> warnings) {
        String name = token.substring(1);
        int dot = name.indexOf('.');
        String baseName = dot >= 0 ? name.substring(0, dot) : name;
        if (!knownConstants.contains(baseName)) {
            warnings.add(context + WARN_UNKNOWN_CONST_DOLLAR + name);
        }
    }

    /**
     * Validates a namespaced item reference token (contains ":"), stripping .type suffixes for lookup.
     *
     * @param token the namespaced token (e.g. "minecraft:iron_ingot.metal")
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateNamespacedToken(String token, Set<String> knownItems,
                                        String context, List<String> warnings) {
        String itemId = stripNamespacedTypeSuffix(token);
        if (!knownItems.contains(itemId)) {
            warnings.add(context + WARN_REFERENCES + itemId + WARN_NOT_DEFINED);
        }
    }

    /**
     * Strips a trailing .type suffix from a namespaced token (e.g. "minecraft:iron_ingot.metal").
     *
     * @param token the namespaced token to strip
     * @return the token without its type suffix, or unchanged if none
     */
    static String stripNamespacedTypeSuffix(String token) {
        int colonIdx = token.indexOf(':');
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx > colonIdx && dotIdx < token.length() - 1) {
            return token.substring(0, dotIdx);
        }
        return token;
    }

    /**
     * Validates a bare word token: checks if it is a known item (with minecraft: prefix),
     * a forgotten $-prefixed constant, or an unrecognized token.
     *
     * @param token the bare word token
     * @param knownConstants known constant names
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateBareWordToken(String token, Set<String> knownConstants,
                                      Set<String> knownItems, String context,
                                      List<String> warnings) {
        String bareItem = stripTypeSuffix(token);
        String qualifiedItem = bareItem.contains(COLON) ? bareItem : DEFAULT_NS_PREFIX + bareItem;
        if (!knownItems.contains(qualifiedItem) && !knownItems.contains(bareItem)) {
            warnUnknownBareWord(token, knownConstants, context, warnings);
        }
    }

    /**
     * Strips a trailing .type suffix (e.g. "iron_ingot.metal" becomes "iron_ingot").
     *
     * @param token the bare word token to strip
     * @return the token without its type suffix, or unchanged if none
     */
    static String stripTypeSuffix(String token) {
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < token.length() - 1) {
            return token.substring(0, dotIdx);
        }
        return token;
    }

    /**
     * Emits the appropriate warning for an unresolved bare word token.
     *
     * @param token the unresolved bare word
     * @param knownConstants known constant names
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void warnUnknownBareWord(String token, Set<String> knownConstants,
                                    String context, List<String> warnings) {
        if (knownConstants.contains(token)) {
            warnings.add(context + WARN_MISSING_DOLLAR_PREFIX + token + WARN_MISSING_DOLLAR_MID + token + WARN_MISSING_DOLLAR_SUFFIX);
        } else {
            warnings.add(context + WARN_UNRECOGNIZED_PREFIX + token + WARN_UNRECOGNIZED_SUFFIX);
        }
    }
}
