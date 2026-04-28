package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates _conversions and _post_conversions blocks in base_values.json for
 * unknown @refs, malformed formulas, and bad constant references. Extracted from
 * {@link GooValueValidator} to keep per-class method counts manageable.
 */
final class GooConversionValidator {

    /**
     * Dot separator for validation context paths.
     */
    private static final String DOT = ".";
    /**
     * Denied value string in base_values.json.
     */
    private static final String VALUE_DENIED = "denied";
    /**
     * JSON block key for pre-derivation conversions.
     */
    private static final String KEY_CONVERSIONS = "_conversions";
    /**
     * JSON block key for post-derivation conversions.
     */
    private static final String KEY_POST_CONVERSIONS = "_post_conversions";
    /**
     * Formula arrow operator in conversion values.
     */
    private static final String FORMULA_ARROW = "->";
    /**
     * Additive prefix in conversion values.
     */
    private static final String ADDITIVE_PREFIX = "+";
    /**
     * At-sign prefix for conversion references.
     */
    private static final String AT_PREFIX = "@";
    /**
     * Dollar-sign prefix for constant references.
     */
    private static final String DOLLAR_PREFIX = "$";
    /**
     * Colon character used in namespaced identifiers.
     */
    private static final String COLON = ":";
    /**
     * Prefix for tag/pseudo-tag references.
     */
    private static final String PREFIX_TAG = "#";
    /**
     * Whitespace regex for splitting conversion reference strings.
     */
    private static final String WHITESPACE_REGEX = "\\s+";
    /**
     * Regex for stripping leading digits from ref names.
     */
    private static final String LEADING_DIGITS_REGEX = "^\\d+\\s*";
    /**
     * Empty string replacement for regex stripping.
     */
    private static final String EMPTY = "";

    /**
     * Warning suffix: expected a string value.
     */
    private static final String WARN_EXPECTED_STRING = ": expected a string value";
    /**
     * Warning suffix: invalid formula prefix.
     */
    private static final String WARN_INVALID_FORMULA = ": invalid formula: ";
    /**
     * Warning suffix: unknown constant prefix.
     */
    private static final String WARN_UNKNOWN_CONST = ": unknown constant ";
    /**
     * Warning suffix: unknown conversion reference prefix.
     */
    private static final String WARN_UNKNOWN_CONV_REF = ": unknown conversion reference @";

    private GooConversionValidator() {
    }

    /**
     * Validates both _conversions and _post_conversions blocks.
     *
     * @param json           the root JSON object
     * @param knownConstants known constant names for reference validation
     * @param warnings       accumulator for validation warnings
     */
    static void validateConversionsSection(JsonObject json, Set<String> knownConstants,
                                           List<String> warnings) {
        validateConversionBlock(json, KEY_CONVERSIONS, knownConstants, warnings);
        validateConversionBlock(json, KEY_POST_CONVERSIONS, knownConstants, warnings);
    }

    /**
     * Validates a conversion block for unknown @refs and malformed formulas.
     *
     * @param json           the root JSON object containing the block
     * @param blockKey       the key of the conversion block
     * @param knownConstants known constant names for reference validation
     * @param warnings       accumulator for validation warnings
     */
    static void validateConversionBlock(JsonObject json, String blockKey,
                                        Set<String> knownConstants, List<String> warnings) {
        if (!json.has(blockKey)) {
            return;
        }
        JsonObject block = json.getAsJsonObject(blockKey);
        Set<String> knownRefs = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            validateConversionEntry(blockKey, entry, knownConstants, knownRefs, warnings);
        }
    }

    /**
     * Validates a single conversion entry, dispatching to formula, additive, or ref validation.
     *
     * @param blockKey       the parent block key for context
     * @param entry          the JSON entry to validate
     * @param knownConstants known constant names
     * @param knownRefs      mutable set of known conversion refs
     * @param warnings       accumulator for validation warnings
     */
    static void validateConversionEntry(String blockKey, Map.Entry<String, JsonElement> entry,
                                        Set<String> knownConstants, Set<String> knownRefs,
                                        List<String> warnings) {
        String key = entry.getKey();
        if (!entry.getValue().isJsonPrimitive()) {
            warnings.add(blockKey + DOT + key + WARN_EXPECTED_STRING);
            return;
        }
        String value = entry.getValue().getAsString();
        if (!VALUE_DENIED.equals(value)) {
            classifyAndValidateConversion(blockKey + DOT + key, key, value, knownConstants, knownRefs, warnings);
        }
    }

    /**
     * Classifies a conversion value by type and validates it.
     *
     * @param ctx            context path for error messages
     * @param key            the entry key
     * @param value          the entry value string
     * @param knownConstants known constant names
     * @param knownRefs      mutable set of known conversion refs
     * @param warnings       accumulator for validation warnings
     */
    static void classifyAndValidateConversion(String ctx, String key, String value,
                                              Set<String> knownConstants, Set<String> knownRefs,
                                              List<String> warnings) {
        if (value.contains(FORMULA_ARROW)) {
            validateFormulaAndRegister(ctx, key, value, knownRefs, warnings);
        } else if (value.startsWith(ADDITIVE_PREFIX)) {
            validateAdditiveAndRegister(ctx, key, value, knownConstants, knownRefs, warnings);
        } else if (value.contains(AT_PREFIX)) {
            validateRefEntry(ctx, key, value, knownRefs, warnings);
        }
    }

    /**
     * Validates a formula entry and registers its key as a known ref.
     *
     * @param ctx       context path for error messages
     * @param key       the entry key to register
     * @param value     the formula string
     * @param knownRefs mutable set of known conversion refs
     * @param warnings  accumulator for validation warnings
     */
    static void validateFormulaAndRegister(String ctx, String key, String value,
                                           Set<String> knownRefs, List<String> warnings) {
        validateFormulaEntry(ctx, value, warnings);
        knownRefs.add(key);
    }

    /**
     * Validates an additive entry and registers its key as a known ref.
     *
     * @param ctx            context path for error messages
     * @param key            the entry key to register
     * @param value          the additive string
     * @param knownConstants known constant names
     * @param knownRefs      mutable set of known conversion refs
     * @param warnings       accumulator for validation warnings
     */
    static void validateAdditiveAndRegister(String ctx, String key, String value,
                                            Set<String> knownConstants, Set<String> knownRefs,
                                            List<String> warnings) {
        validateAdditiveEntry(ctx, value, knownConstants, warnings);
        knownRefs.add(key);
    }

    /**
     * Validates a formula conversion entry (contains "->") by attempting to parse it.
     *
     * @param ctx      the context path for error messages
     * @param value    the formula string
     * @param warnings accumulator for validation warnings
     */
    static void validateFormulaEntry(String ctx, String value, List<String> warnings) {
        try {
            GooConversion.parseFormula(value);
        } catch (IllegalArgumentException e) {
            warnings.add(ctx + WARN_INVALID_FORMULA + e.getMessage());
        }
    }

    /**
     * Validates an additive conversion entry (starts with "+") for constant reference correctness.
     *
     * @param ctx            the context path for error messages
     * @param value          the additive string (e.g. "+$iron")
     * @param knownConstants known constant names
     * @param warnings       accumulator for validation warnings
     */
    static void validateAdditiveEntry(String ctx, String value, Set<String> knownConstants,
                                      List<String> warnings) {
        String ref = value.substring(1).trim();
        if (ref.startsWith(DOLLAR_PREFIX) && !knownConstants.contains(ref.substring(1))) {
            warnings.add(ctx + WARN_UNKNOWN_CONST + ref);
        }
    }

    /**
     * Validates a ref-based conversion entry (contains "@") for unknown @references.
     * Registers the key as a known ref if it is not a tag or namespaced identifier.
     *
     * @param ctx       the context path for error messages
     * @param key       the conversion entry key
     * @param value     the conversion value string
     * @param knownRefs mutable set of known conversion reference names
     * @param warnings  accumulator for validation warnings
     */
    static void validateRefEntry(String ctx, String key, String value,
                                 Set<String> knownRefs, List<String> warnings) {
        checkAtReferences(ctx, value, knownRefs, warnings);
        if (!key.startsWith(PREFIX_TAG) && !key.contains(COLON)) {
            knownRefs.add(key);
        }
    }

    /**
     * Checks each @-prefixed part of a ref value for unknown references.
     *
     * @param ctx       context path for error messages
     * @param value     the conversion value string to scan
     * @param knownRefs known conversion reference names
     * @param warnings  accumulator for validation warnings
     */
    static void checkAtReferences(String ctx, String value,
                                  Set<String> knownRefs, List<String> warnings) {
        for (String part : value.trim().split(WHITESPACE_REGEX)) {
            if (part.startsWith(AT_PREFIX)) {
                warnUnknownAtRef(ctx, part, knownRefs, warnings);
            }
        }
    }

    /**
     * Emits a warning if the @-ref name is not in the known set.
     *
     * @param ctx       context path for error messages
     * @param part      the @-prefixed token to check
     * @param knownRefs known conversion reference names
     * @param warnings  accumulator for validation warnings
     */
    static void warnUnknownAtRef(String ctx, String part,
                                 Set<String> knownRefs, List<String> warnings) {
        String refName = part.substring(1).replaceAll(LEADING_DIGITS_REGEX, EMPTY);
        if (!knownRefs.contains(refName)) {
            warnings.add(ctx + WARN_UNKNOWN_CONV_REF + refName);
        }
    }
}
