package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-derivation conversion layer: transfers goo between types using named formulas.
 * Conversions apply after all base + derived values are finalized.
 */
public final class GooConversion {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── String constants ─────────────────────────────────────────────────
    private static final String INVALID_FORMULA = "Invalid conversion formula: ";
    private static final String INVALID_REF = "Invalid conversion reference: ";
    private static final String DENIED = "denied";
    private static final String ADDITIVE_PREFIX = "+";
    private static final String ARROW = "->";
    private static final String ASTERISK = "*";
    private static final String DOLLAR = "$";
    private static final String TAG_PREFIX = "#";
    private static final String COLON = ":";
    private static final String AT_SIGN = "@";
    private static final String SPACE = " ";
    private static final String WHITESPACE_SPLIT = "\\s+";
    private static final String SLASH_SEP = " / ";
    private static final String LOSSY_PREFIX = "Lossy conversion division: ";
    private static final String IN_CTX = " in ";
    private static final String REMAINDER_PREFIX = " (remainder ";
    private static final String REMAINDER_SUFFIX = ")";
    private static final String LOG_ADDITIVE_EMPTY = "Additive modifier {} resolved to empty";
    private static final String LOG_ADDITIVE_SCALAR = "Additive +${} is a scalar, not a tree constant";
    private static final String LOG_ADDITIVE_UNKNOWN = "Unknown additive constant: +${}";
    private static final String LOG_ADDITIVE_UNSUPPORTED = "Unsupported additive expression: +{}";
    private static final String LOG_UNEXPECTED_TOKEN = "Unexpected token in conversion assignment: {}";
    private static final String LOG_UNKNOWN_REF = "Unknown conversion reference: @{}";
    private static final String LOG_UNKNOWN_FORMULA = "Unknown formula or additive @{} in conversion assignment";
    private static final String LOG_PARALLEL_MISMATCH =
            "Parallel copy size mismatch: {} has {} items, source has {} items";
    private static final String LOG_SCALE_FAILED = "Scale failed for {} (* {} / {}): {}";
    private static final String LOG_NEGATIVE = "Conversion produced negative value for {}: {} -- skipped";
    private static final String LOG_CONV_FAILED = "Conversion failed for {}: {}";

    // ── Regex capture group indices ──────────────────────────────────────
    private static final int GROUP_SOURCE_TYPE = 1;
    private static final int GROUP_SOURCE_DIVISOR = 2;
    private static final int GROUP_TARGET_TYPE = 3;
    private static final int GROUP_TARGET_OP = 4;
    private static final int GROUP_TARGET_VALUE = 5;
    private static final int GROUP_STACK_MULTIPLIER = 1;
    private static final int GROUP_STACK_NAME = 2;
    private static final int GROUP_SCALE_NUM = 1;
    private static final int GROUP_SCALE_DEN = 2;

    private GooConversion() { }

    /**
     * A conversion formula: "source / N -> target / M".
     *
     * @param sourceType       goo type consumed
     * @param sourceDivisor    divisor applied to the source amount
     * @param targetType       goo type produced
     * @param targetDivisor    divisor applied to the per-application amount
     * @param targetMultiplier multiplier applied to the target amount
     */
    public record Formula(GooType sourceType, int sourceDivisor,
                          GooType targetType, int targetDivisor,
                          int targetMultiplier) { }

    /**
     * A named stack: N applications of a formula.
     *
     * @param formulaName name of the formula or alias this stack references
     * @param multiplier  number of simultaneous applications
     */
    public record Stack(String formulaName, int multiplier) { }

    /**
     * An item or tag assignment: optionally copy values from a parallel source tag,
     * scale by a fraction, then apply a chain of conversion stacks in order.
     *
     * @param target          the target item ID or #tag
     * @param parallelSource  if non-null, a #tag to copy values from (parallel arrays)
     * @param scaleMultiplier numerator for post-copy scaling (1 = no scaling)
     * @param scaleDivisor    denominator for post-copy scaling (1 = no scaling, exact division)
     * @param chain           conversion stacks to apply in order after copy + scale
     */
    public record Assignment(String target, String parallelSource,
                             int scaleMultiplier, int scaleDivisor,
                             List<Stack> chain) { }

    /**
     * Result of parsing a _conversions block.
     *
     * @param formulas    named conversion formulas
     * @param additives   named additive goo values
     * @param stacks      named conversion stacks (aliases)
     * @param assignments ordered list of item/tag assignments
     */
    public record ParsedConversions(
            Map<String, Formula> formulas,
            Map<String, GooValue> additives,
            Map<String, Stack> stacks,
            List<Assignment> assignments
    ) { }

    // ── Formula pattern: "type / N -> type / M", "type / N -> type * M", or "type / N -> 0"

    private static final Pattern FORMULA_PATTERN = Pattern.compile(
            "\\s*(\\w+)\\s*/\\s*(\\d+)\\s*->\\s*(?:(\\w+)\\s*([*/])\\s*(\\d+)|0)\\s*");

    /**
     * Parses a formula string like "metal / 4 -> aeon / 2".
     *
     * @param expr the raw formula expression
     * @return parsed Formula record
     */
    public static Formula parseFormula(String expr) {
        Matcher m = FORMULA_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException(INVALID_FORMULA + expr);
        }
        return buildFormula(m);
    }

    /**
     * Extracts a Formula from a successful regex match.
     *
     * @param m matched regex groups
     * @return the parsed Formula
     */
    private static Formula buildFormula(Matcher m) {
        GooType source = GooType.valueOf(m.group(GROUP_SOURCE_TYPE).toUpperCase(Locale.ROOT));
        int srcDiv = Integer.parseInt(m.group(GROUP_SOURCE_DIVISOR));
        if (m.group(GROUP_TARGET_TYPE) == null) {
            return new Formula(source, srcDiv, source, 1, 0);
        }
        GooType target = GooType.valueOf(m.group(GROUP_TARGET_TYPE).toUpperCase(Locale.ROOT));
        int tgtVal = Integer.parseInt(m.group(GROUP_TARGET_VALUE));
        boolean isMultiplier = ASTERISK.equals(m.group(GROUP_TARGET_OP));
        int tgtDiv = isMultiplier ? 1 : tgtVal;
        int tgtMul = isMultiplier ? tgtVal : 1;
        return new Formula(source, srcDiv, target, tgtDiv, tgtMul);
    }

    // ── Stack reference pattern: "N @name" or "@name" ───────────────────

    private static final Pattern STACK_PATTERN = Pattern.compile(
            "\\s*(?:(\\d+)\\s+)?@(\\w+)\\s*");

    /**
     * Parses a stack reference like "2 @oxidation" or "@oxidation".
     *
     * @param expr the raw stack expression
     * @return parsed Stack record
     */
    static Stack parseStack(String expr) {
        Matcher m = STACK_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException(INVALID_REF + expr);
        }
        int multiplier = m.group(GROUP_STACK_MULTIPLIER) != null
                ? Integer.parseInt(m.group(GROUP_STACK_MULTIPLIER)) : 1;
        return new Stack(m.group(GROUP_STACK_NAME), multiplier);
    }

    // ── Block parsing ────────────────────────────────────────────────────

    /**
     * Parses a _conversions block (order-dependent). No constant resolution.
     *
     * @param entries ordered map of key-value pairs from the block
     * @return parsed conversion structures
     */
    public static ParsedConversions parseBlock(Map<String, String> entries) {
        return parseBlock(entries, Map.of(), Map.of());
    }

    /**
     * Parses a _conversions block with constant resolution for additive modifiers.
     *
     * @param entries       ordered map of key-value pairs from the block
     * @param constants     scalar constants for expression resolution
     * @param treeConstants tree constants (GooValue) for additive resolution
     * @return parsed conversion structures
     */
    public static ParsedConversions parseBlock(Map<String, String> entries,
                                                Map<String, Integer> constants,
                                                Map<String, GooValue> treeConstants) {
        Map<String, Formula> formulas = new LinkedHashMap<>();
        Map<String, GooValue> additives = new LinkedHashMap<>();
        Map<String, Stack> stacks = new LinkedHashMap<>();
        List<Assignment> assignments = new ArrayList<>();
        classifyAllEntries(entries, formulas, additives, stacks, assignments, constants, treeConstants);
        return new ParsedConversions(formulas, additives, stacks, assignments);
    }

    /**
     * Iterates all entries and dispatches each to classification.
     *
     * @param entries        raw key-value pairs
     * @param formulas       mutable formula map
     * @param additives      mutable additive map
     * @param stacks         mutable stack map
     * @param assignments    mutable assignment list
     * @param constants      scalar constants
     * @param treeConstants  tree constants
     */
    private static void classifyAllEntries(Map<String, String> entries,
                                            Map<String, Formula> formulas,
                                            Map<String, GooValue> additives,
                                            Map<String, Stack> stacks,
                                            List<Assignment> assignments,
                                            Map<String, Integer> constants,
                                            Map<String, GooValue> treeConstants) {
        for (var entry : entries.entrySet()) {
            classifyEntry(entry.getKey(), entry.getValue(), formulas, additives, stacks,
                    assignments, constants, treeConstants);
        }
    }

    /**
     * Classifies and processes a single entry from the conversions block.
     *
     * @param key            the entry key (item, tag, formula name, or stack name)
     * @param value          the entry value expression
     * @param formulas       mutable formula map to populate
     * @param additives      mutable additive map to populate
     * @param stacks         mutable stack map to populate
     * @param assignments    mutable assignment list to populate
     * @param constants      scalar constants for additive resolution
     * @param treeConstants  tree constants for additive resolution
     */
    private static void classifyEntry(String key, String value,
                                       Map<String, Formula> formulas,
                                       Map<String, GooValue> additives,
                                       Map<String, Stack> stacks,
                                       List<Assignment> assignments,
                                       Map<String, Integer> constants,
                                       Map<String, GooValue> treeConstants) {
        if (isItemOrTag(key)) {
            classifyItemEntry(key, value, stacks, assignments);
        } else {
            classifyDefinition(key, value, formulas, additives, stacks, constants, treeConstants);
        }
    }

    /**
     * Classifies a non-item entry as denial, additive, formula, or stack alias.
     *
     * @param key            the definition name
     * @param value          the entry value expression
     * @param formulas       mutable formula map
     * @param additives      mutable additive map
     * @param stacks         mutable stack map
     * @param constants      scalar constants
     * @param treeConstants  tree constants
     */
    private static void classifyDefinition(String key, String value,
                                            Map<String, Formula> formulas,
                                            Map<String, GooValue> additives,
                                            Map<String, Stack> stacks,
                                            Map<String, Integer> constants,
                                            Map<String, GooValue> treeConstants) {
        if (DENIED.equals(value)) {
            denyEntry(key, stacks, formulas, additives);
        } else if (value.startsWith(ADDITIVE_PREFIX)) {
            parseAdditiveEntry(key, value, additives, constants, treeConstants);
        } else {
            classifyFormulaOrStack(key, value, formulas, stacks);
        }
    }

    /**
     * Classifies a value as either a formula definition or a stack alias.
     *
     * @param key      the definition name
     * @param value    the expression (contains "->" for formulas, else stack ref)
     * @param formulas mutable formula map
     * @param stacks   mutable stack map
     */
    private static void classifyFormulaOrStack(String key, String value,
                                                Map<String, Formula> formulas,
                                                Map<String, Stack> stacks) {
        if (value.contains(ARROW)) {
            formulas.put(key, parseFormula(value));
        } else {
            stacks.put(key, resolveChain(parseStack(value), stacks, formulas));
        }
    }

    /**
     * Handles an item or tag key: either denied or parsed as an assignment.
     *
     * @param key         the item/tag key
     * @param value       the entry value expression
     * @param stacks      known stacks for resolution
     * @param assignments mutable assignment list
     */
    private static void classifyItemEntry(String key, String value,
                                           Map<String, Stack> stacks,
                                           List<Assignment> assignments) {
        if (!DENIED.equals(value)) {
            assignments.add(parseAssignment(key, value, stacks));
        }
    }

    /**
     * Removes a denied entry from all definition maps.
     *
     * @param key       the denied name
     * @param stacks    mutable stack map
     * @param formulas  mutable formula map
     * @param additives mutable additive map
     */
    private static void denyEntry(String key, Map<String, Stack> stacks,
                                   Map<String, Formula> formulas,
                                   Map<String, GooValue> additives) {
        stacks.remove(key);
        formulas.remove(key);
        additives.remove(key);
    }

    /**
     * Parses and stores an additive modifier entry like "+$waxed".
     *
     * @param key           the additive name
     * @param value         the raw value starting with "+"
     * @param additives     mutable additive map to populate
     * @param constants     scalar constants
     * @param treeConstants tree constants
     */
    private static void parseAdditiveEntry(String key, String value,
                                            Map<String, GooValue> additives,
                                            Map<String, Integer> constants,
                                            Map<String, GooValue> treeConstants) {
        GooValue additive = resolveAdditive(value.substring(1).trim(), constants, treeConstants);
        if (additive != null && !additive.isEmpty()) {
            additives.put(key, additive);
        } else {
            LOGGER.warn(LOG_ADDITIVE_EMPTY, value);
        }
    }

    /**
     * Resolves an additive expression like "$waxed" against tree constants.
     *
     * @param expr          the expression (without leading "+")
     * @param constants     scalar constants for fallback lookup
     * @param treeConstants tree constants (GooValue) for primary lookup
     * @return the resolved GooValue, or null if unresolvable
     */
    private static GooValue resolveAdditive(String expr, Map<String, Integer> constants,
                                             Map<String, GooValue> treeConstants) {
        if (!expr.startsWith(DOLLAR)) {
            LOGGER.warn(LOG_ADDITIVE_UNSUPPORTED, expr);
            return null;
        }
        return resolveNamedAdditive(expr.substring(1), constants, treeConstants);
    }

    /**
     * Looks up a named additive in tree constants, falling back to scalar warning.
     *
     * @param name          constant name (without "$" prefix)
     * @param constants     scalar constants
     * @param treeConstants tree constants
     * @return the resolved GooValue or null
     */
    private static GooValue resolveNamedAdditive(String name,
                                                  Map<String, Integer> constants,
                                                  Map<String, GooValue> treeConstants) {
        GooValue tree = treeConstants.get(name);
        if (tree != null) { return tree; }
        LOGGER.warn(constants.containsKey(name) ? LOG_ADDITIVE_SCALAR : LOG_ADDITIVE_UNKNOWN, name);
        return null;
    }

    /**
     * True if the key looks like an item ID or tag reference.
     *
     * @param key the entry key to check
     * @return true if it starts with "#" or contains ":"
     */
    private static boolean isItemOrTag(String key) {
        return key.startsWith(TAG_PREFIX) || key.contains(COLON);
    }

    /** Pattern for scalar: "* N" or "* N / M" after a parallel source. */
    private static final Pattern SCALE_PATTERN = Pattern.compile(
            "\\*\\s*(\\d+)(?:\\s*/\\s*(\\d+))?");

    /**
     * Parses an assignment value: optional #source for parallel copy,
     * optional * N / M scalar, then space-delimited @conversion chain.
     *
     * @param target the target item ID or #tag
     * @param value  the raw assignment expression
     * @param stacks known stacks for resolution
     * @return parsed Assignment record
     */
    private static Assignment parseAssignment(String target, String value,
                                               Map<String, Stack> stacks) {
        String remaining = value.trim();
        Matcher m = SCALE_PATTERN.matcher(remaining);
        if (!m.find()) {
            return buildAssignment(target, remaining, 1, 1, stacks);
        }
        return buildScaledAssignment(target, remaining, m, stacks);
    }

    /**
     * Builds an assignment after extracting scale factors from a matched expression.
     *
     * @param target    target item/tag
     * @param remaining raw token string containing the scale match
     * @param m         successful scale pattern matcher
     * @param stacks    known stacks for resolution
     * @return parsed Assignment with scale applied
     */
    private static Assignment buildScaledAssignment(String target, String remaining,
                                                     Matcher m, Map<String, Stack> stacks) {
        int mul = Integer.parseInt(m.group(GROUP_SCALE_NUM));
        int div = m.group(GROUP_SCALE_DEN) != null ? Integer.parseInt(m.group(GROUP_SCALE_DEN)) : 1;
        String stripped = stripScaleMatch(remaining, m);
        return buildAssignment(target, stripped, mul, div, stacks);
    }

    /**
     * Removes the scale match from the remaining string.
     *
     * @param remaining    the full remaining string
     * @param scaleMatcher the matcher with start/end positions
     * @return remaining string with the scale portion removed
     */
    private static String stripScaleMatch(String remaining, Matcher scaleMatcher) {
        return remaining.substring(0, scaleMatcher.start()).trim()
                + SPACE + remaining.substring(scaleMatcher.end()).trim();
    }

    /**
     * Builds an Assignment from parsed tokens.
     *
     * @param target          target item/tag
     * @param remaining       whitespace-delimited tokens after scale extraction
     * @param scaleMultiplier numerator for scaling
     * @param scaleDivisor    denominator for scaling
     * @param stacks          known stacks for resolution
     * @return the assembled Assignment
     */
    private static Assignment buildAssignment(String target, String remaining,
                                               int scaleMultiplier, int scaleDivisor,
                                               Map<String, Stack> stacks) {
        String[] tokens = remaining.trim().split(WHITESPACE_SPLIT);
        String parallelSource = findParallelSource(tokens);
        List<Stack> chain = collectChain(tokens, stacks);
        return new Assignment(target, parallelSource, scaleMultiplier, scaleDivisor, chain);
    }

    /**
     * Finds the first tag-prefixed token to use as a parallel copy source.
     *
     * @param tokens whitespace-split assignment tokens
     * @return the tag token (e.g. "#minecraft:logs"), or null if none
     */
    private static String findParallelSource(String[] tokens) {
        for (String t : tokens) {
            if (t.startsWith(TAG_PREFIX)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Collects resolved conversion stacks from @-prefixed tokens.
     *
     * @param tokens whitespace-split assignment tokens
     * @param stacks known stack definitions for resolution
     * @return ordered list of resolved stacks
     */
    private static List<Stack> collectChain(String[] tokens, Map<String, Stack> stacks) {
        List<Stack> chain = new ArrayList<>();
        for (String part : tokens) {
            if (part.isEmpty() || part.startsWith(TAG_PREFIX)) {
                continue;
            }
            classifyToken(part, chain, stacks);
        }
        return chain;
    }

    /**
     * Classifies a single non-tag token as a stack reference or warns on unknown.
     *
     * @param part   the token to classify
     * @param chain  mutable chain list to append to
     * @param stacks known stack definitions
     */
    private static void classifyToken(String part, List<Stack> chain,
                                       Map<String, Stack> stacks) {
        if (part.contains(AT_SIGN)) {
            chain.add(resolveStackRef(part, stacks));
        } else {
            LOGGER.warn(LOG_UNEXPECTED_TOKEN, part);
        }
    }

    /**
     * Resolves a @ref expression against known stacks.
     *
     * @param expr   the raw "@name" or "N @name" expression
     * @param stacks known stack definitions
     * @return resolved Stack with multipliers collapsed
     */
    private static Stack resolveStackRef(String expr, Map<String, Stack> stacks) {
        Stack raw = parseStack(expr);
        Stack known = stacks.get(raw.formulaName());
        if (known != null) {
            return new Stack(known.formulaName(), raw.multiplier() * known.multiplier());
        }
        return raw;
    }

    /**
     * Resolves stack chain: "2 @exposed" where exposed is "1 @oxidation" -> "2 @oxidation".
     *
     * @param raw      the unresolved stack
     * @param stacks   known stack definitions
     * @param formulas known formula definitions
     * @return resolved Stack or the raw stack if unresolvable
     */
    private static Stack resolveChain(Stack raw, Map<String, Stack> stacks,
                                       Map<String, Formula> formulas) {
        Stack known = stacks.get(raw.formulaName());
        if (known != null) { return new Stack(known.formulaName(), raw.multiplier() * known.multiplier()); }
        if (!formulas.containsKey(raw.formulaName()) && LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_UNKNOWN_REF, raw.formulaName());
        }
        return raw;
    }

    // ── Application ──────────────────────────────────────────────────────

    /**
     * Applies a conversion formula N times to a GooValue. All applications use the
     * original value (simultaneous stacking). Division must be exact or throws.
     *
     * @param original the item's current value
     * @param formula  the conversion formula
     * @param times    number of simultaneous applications
     * @return the modified GooValue
     * @throws ArithmeticException if division is lossy
     */
    public static GooValue apply(GooValue original, Formula formula, int times) {
        int sourceAmount = original.get(formula.sourceType());
        if (sourceAmount == 0) {
            return original;
        }
        return computeConversion(original, formula, times, sourceAmount);
    }

    /**
     * Performs the actual conversion arithmetic.
     *
     * @param original     base value
     * @param formula      conversion formula
     * @param times        number of applications
     * @param sourceAmount amount of source type present
     * @return modified GooValue with source removed and target added
     */
    private static GooValue computeConversion(GooValue original, Formula formula,
                                               int times, int sourceAmount) {
        int perApp = exactDivide(sourceAmount, formula.sourceDivisor(),
                formula.sourceType() + SLASH_SEP + formula.sourceDivisor());
        int removed = perApp * times;
        int added = exactDivide(perApp, formula.targetDivisor(),
                formula.targetType() + SLASH_SEP + formula.targetDivisor())
                * formula.targetMultiplier() * times;
        return applyDelta(original, formula, removed, added);
    }

    /** Creates a new GooValue with source removed and target added.
     *
     * @param original the base value
     * @param formula  the conversion formula (source and target types)
     * @param removed  amount to subtract from source type
     * @param added    amount to add to target type
     * @return the converted GooValue
     */
    private static GooValue applyDelta(GooValue original, Formula formula,
                                        int removed, int added) {
        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        original.getAll().forEach(result::put);
        result.merge(formula.sourceType(), -removed, Integer::sum);
        result.merge(formula.targetType(), added, Integer::sum);
        return new GooValue(result);
    }

    /**
     * Integer division that throws if lossy.
     *
     * @param value   the dividend
     * @param divisor the divisor
     * @param context description for error messages
     * @return exact quotient
     * @throws ArithmeticException if value is not evenly divisible
     */
    private static int exactDivide(int value, int divisor, String context) {
        if (value % divisor != 0) {
            throw new ArithmeticException(
                    LOSSY_PREFIX + value + IN_CTX + context
                    + REMAINDER_PREFIX + value % divisor + REMAINDER_SUFFIX);
        }
        return value / divisor;
    }

    /**
     * Applies a full assignment (optional parallel copy + conversion chain) to items.
     *
     * @param effectiveValues the mutable effective values map
     * @param targetItems     ordered list of target items
     * @param sourceItems     ordered list of source items for parallel copy (null if no copy)
     * @param assignment      the assignment with parallel source and conversion chain
     * @param formulas        formula lookup table
     * @param additives       additive modifier lookup table
     */
    public static void applyAssignment(Map<Identifier, GooValue> effectiveValues,
                                        List<Identifier> targetItems,
                                        List<Identifier> sourceItems,
                                        Assignment assignment,
                                        Map<String, Formula> formulas,
                                        Map<String, GooValue> additives) {
        applyParallelCopy(effectiveValues, targetItems, sourceItems, assignment);
        applyScale(effectiveValues, targetItems, assignment);
        for (Stack stack : assignment.chain()) {
            applyStack(effectiveValues, targetItems, stack, formulas, additives);
        }
    }

    /**
     * Copies values from source items to target items in parallel (1:1 index mapping).
     *
     * @param effectiveValues mutable values map
     * @param targetItems     target item list
     * @param sourceItems     source item list (null if no parallel copy)
     * @param assignment      assignment for error reporting
     */
    private static void applyParallelCopy(Map<Identifier, GooValue> effectiveValues,
                                           List<Identifier> targetItems,
                                           List<Identifier> sourceItems,
                                           Assignment assignment) {
        if (sourceItems == null) { return; }
        if (sourceItems.size() != targetItems.size()) {
            if (LOGGER.isErrorEnabled()) { LOGGER.error(LOG_PARALLEL_MISMATCH, assignment.target(), targetItems.size(), sourceItems.size()); }
            return;
        }
        copyParallelValues(effectiveValues, targetItems, sourceItems);
    }

    /**
     * Performs the per-index value copy.
     *
     * @param effectiveValues mutable values map
     * @param targetItems     target items
     * @param sourceItems     source items
     */
    private static void copyParallelValues(Map<Identifier, GooValue> effectiveValues,
                                            List<Identifier> targetItems,
                                            List<Identifier> sourceItems) {
        for (int i = 0; i < targetItems.size(); i++) {
            GooValue sourceVal = effectiveValues.get(sourceItems.get(i));
            if (sourceVal != null && !sourceVal.isEmpty()) {
                effectiveValues.put(targetItems.get(i), sourceVal);
            }
        }
    }

    /**
     * Scales all target items by the assignment's multiplier/divisor fraction.
     *
     * @param effectiveValues mutable values map
     * @param targetItems     items to scale
     * @param assignment      assignment containing scale factors
     */
    private static void applyScale(Map<Identifier, GooValue> effectiveValues,
                                    List<Identifier> targetItems, Assignment assignment) {
        if (assignment.scaleMultiplier() == 1 && assignment.scaleDivisor() == 1) {
            return;
        }
        for (Identifier itemId : targetItems) {
            scaleItem(effectiveValues, itemId, assignment);
        }
    }

    /**
     * Scales a single item's value by the assignment fraction.
     *
     * @param effectiveValues mutable values map
     * @param itemId          the item to scale
     * @param assignment      assignment containing scale factors
     */
    private static void scaleItem(Map<Identifier, GooValue> effectiveValues,
                                   Identifier itemId, Assignment assignment) {
        GooValue current = effectiveValues.get(itemId);
        if (current == null || current.isEmpty()) { return; }
        tryScaleValue(effectiveValues, itemId, current, assignment);
    }

    /**
     * Attempts to scale a value by the assignment fraction, logging on failure.
     *
     * @param effectiveValues mutable values map
     * @param itemId          the item being scaled
     * @param current         the item's current value
     * @param assignment      assignment containing scale factors
     */
    private static void tryScaleValue(Map<Identifier, GooValue> effectiveValues,
                                       Identifier itemId, GooValue current,
                                       Assignment assignment) {
        try {
            GooValue scaled = current.multiply(assignment.scaleMultiplier())
                    .divideExact(assignment.scaleDivisor());
            effectiveValues.put(itemId, scaled);
        } catch (ArithmeticException e) {
            logScaleError(itemId, assignment, e);
        }
    }

    /** Logs a scale failure at error level.
     *
     * @param itemId     the item that failed to scale
     * @param assignment the assignment containing scale factors
     * @param e          the arithmetic exception
     */
    private static void logScaleError(Identifier itemId, Assignment assignment,
                                       ArithmeticException e) {
        if (LOGGER.isErrorEnabled()) {
            LOGGER.error(LOG_SCALE_FAILED, itemId, assignment.scaleMultiplier(),
                    assignment.scaleDivisor(), e.getMessage());
        }
    }

    /**
     * Applies a single conversion stack (formula or additive) to a list of items.
     *
     * @param effectiveValues mutable values map
     * @param items           items to apply the stack to
     * @param stack           the stack to apply
     * @param formulas        formula lookup
     * @param additives       additive lookup
     */
    private static void applyStack(Map<Identifier, GooValue> effectiveValues,
                                    List<Identifier> items, Stack stack,
                                    Map<String, Formula> formulas,
                                    Map<String, GooValue> additives) {
        GooValue additive = additives.get(stack.formulaName());
        if (additive != null) {
            applyAdditiveStack(effectiveValues, items, additive, stack.multiplier());
            return;
        }
        applyResolvedFormula(effectiveValues, items, stack, formulas);
    }

    /**
     * Resolves and applies a formula stack, logging if the formula name is unknown.
     *
     * @param effectiveValues mutable values map
     * @param items           items to convert
     * @param stack           the stack referencing a formula name
     * @param formulas        formula lookup
     */
    private static void applyResolvedFormula(Map<Identifier, GooValue> effectiveValues,
                                              List<Identifier> items, Stack stack,
                                              Map<String, Formula> formulas) {
        Formula formula = formulas.get(stack.formulaName());
        if (formula == null) {
            LOGGER.error(LOG_UNKNOWN_FORMULA, stack.formulaName());
            return;
        }
        applyFormulaStack(effectiveValues, items, formula, stack.multiplier());
    }

    /**
     * Adds an additive value (times multiplier) to each item.
     *
     * @param effectiveValues mutable values map
     * @param items           items to modify
     * @param additive        the additive GooValue
     * @param multiplier      number of applications
     */
    private static void applyAdditiveStack(Map<Identifier, GooValue> effectiveValues,
                                            List<Identifier> items, GooValue additive,
                                            int multiplier) {
        for (Identifier itemId : items) {
            GooValue current = effectiveValues.get(itemId);
            if (current == null) {
                current = GooValue.EMPTY;
            }
            effectiveValues.put(itemId, current.add(additive, multiplier));
        }
    }

    /**
     * Applies a formula conversion (times multiplier) to each item.
     *
     * @param effectiveValues mutable values map
     * @param items           items to convert
     * @param formula         the conversion formula
     * @param multiplier      number of applications
     */
    private static void applyFormulaStack(Map<Identifier, GooValue> effectiveValues,
                                           List<Identifier> items, Formula formula,
                                           int multiplier) {
        for (Identifier itemId : items) {
            applyFormulaToItem(effectiveValues, itemId, formula, multiplier);
        }
    }

    /**
     * Applies a formula to a single item, logging errors on failure.
     *
     * @param effectiveValues mutable values map
     * @param itemId          the item to convert
     * @param formula         the conversion formula
     * @param multiplier      number of applications
     */
    private static void applyFormulaToItem(Map<Identifier, GooValue> effectiveValues,
                                            Identifier itemId, Formula formula,
                                            int multiplier) {
        GooValue current = effectiveValues.get(itemId);
        if (current == null || current.isEmpty()) { return; }
        tryApplyFormula(effectiveValues, itemId, current, formula, multiplier);
    }

    /** Attempts formula conversion with error handling for arithmetic and negative results.
     *
     * @param effectiveValues mutable values map
     * @param itemId          the item being converted
     * @param current         the item's current value
     * @param formula         the conversion formula
     * @param multiplier      number of applications
     */
    private static void tryApplyFormula(Map<Identifier, GooValue> effectiveValues,
            Identifier itemId, GooValue current, Formula formula, int multiplier) {
        try {
            GooValue converted = apply(current, formula, multiplier);
            storeIfValid(effectiveValues, itemId, converted);
        } catch (ArithmeticException e) {
            LOGGER.error(LOG_CONV_FAILED, itemId, e.getMessage());
        }
    }

    /**
     * Stores a converted value if it contains no negative entries.
     *
     * @param effectiveValues mutable values map
     * @param itemId          the item being converted
     * @param converted       the candidate converted value
     */
    private static void storeIfValid(Map<Identifier, GooValue> effectiveValues,
                                      Identifier itemId, GooValue converted) {
        if (converted.hasNegative()) {
            LOGGER.error(LOG_NEGATIVE, itemId, converted);
            return;
        }
        effectiveValues.put(itemId, converted);
    }
}
