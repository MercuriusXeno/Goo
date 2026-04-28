package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooConversion.Assignment;
import com.mercuriusxeno.goo.data.GooConversion.Formula;
import com.mercuriusxeno.goo.data.GooConversion.ParsedConversions;
import com.mercuriusxeno.goo.data.GooConversion.Stack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing logic for conversion formulas, stacks, additives, and assignment blocks.
 * Extracted from GooConversion to keep that class under the method-count threshold.
 */
final class ConversionFormulaParser {

    private static final Logger LOGGER = LogUtils.getLogger();

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
    private static final String LOG_ADDITIVE_EMPTY = "Additive modifier {} resolved to empty";
    private static final String LOG_ADDITIVE_SCALAR = "Additive +${} is a scalar, not a tree constant";
    private static final String LOG_ADDITIVE_UNKNOWN = "Unknown additive constant: +${}";
    private static final String LOG_ADDITIVE_UNSUPPORTED = "Unsupported additive expression: +{}";
    private static final String LOG_UNEXPECTED_TOKEN = "Unexpected token in conversion assignment: {}";
    private static final String LOG_UNKNOWN_REF = "Unknown conversion reference: @{}";

    private static final int GROUP_SOURCE_TYPE = 1;
    private static final int GROUP_SOURCE_DIVISOR = 2;
    private static final int GROUP_TARGET_TYPE = 3;
    private static final int GROUP_TARGET_OP = 4;
    private static final int GROUP_TARGET_VALUE = 5;
    private static final int GROUP_STACK_MULTIPLIER = 1;
    private static final int GROUP_STACK_NAME = 2;
    private static final int GROUP_SCALE_NUM = 1;
    private static final int GROUP_SCALE_DEN = 2;

    // ── Formula pattern: "type / N -> type / M", "type / N -> type * M", or "type / N -> 0"
    private static final Pattern FORMULA_PATTERN = Pattern.compile(
            "\\s*(\\w+)\\s*/\\s*(\\d+)\\s*->\\s*(?:(\\w+)\\s*([*/])\\s*(\\d+)|0)\\s*");

    private static final Pattern STACK_PATTERN = Pattern.compile(
            "\\s*(?:(\\d+)\\s+)?@(\\w+)\\s*");

    /**
     * Pattern for scalar: "* N" or "* N / M" after a parallel source.
     */
    private static final Pattern SCALE_PATTERN = Pattern.compile(
            "\\*\\s*(\\d+)(?:\\s*/\\s*(\\d+))?");

    private ConversionFormulaParser() {
    }

    /**
     * Parses a formula string like "metal / 4 -> aeon / 2".
     *
     * @param expr the raw formula expression
     * @return parsed Formula record
     */
    static Formula parseFormula(String expr) {
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
        return buildTargetFormula(m, source, srcDiv);
    }

    /**
     * Parses the target side of a formula from a successful regex match.
     *
     * @param m      the regex matcher with captured target-side groups
     * @param source the source goo type parsed from the formula
     * @param srcDiv the source divisor parsed from the formula
     * @return a Formula with fully resolved source and target parameters
     */
    private static Formula buildTargetFormula(Matcher m, GooType source, int srcDiv) {
        GooType target = GooType.valueOf(m.group(GROUP_TARGET_TYPE).toUpperCase(Locale.ROOT));
        int tgtVal = Integer.parseInt(m.group(GROUP_TARGET_VALUE));
        boolean isMultiplier = ASTERISK.equals(m.group(GROUP_TARGET_OP));
        int tgtDiv = isMultiplier ? 1 : tgtVal;
        int tgtMul = isMultiplier ? tgtVal : 1;
        return new Formula(source, srcDiv, target, tgtDiv, tgtMul);
    }

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


    /**
     * Parses a _conversions block with constant resolution for additive modifiers.
     *
     * @param entries       ordered map of key-value pairs from the block
     * @param constants     scalar constants for expression resolution
     * @param treeConstants tree constants (GooValue) for additive resolution
     * @return parsed conversion structures
     */
    static ParsedConversions parseBlock(Map<String, String> entries,
                                        Map<String, Integer> constants,
                                        Map<String, GooValue> treeConstants) {
        Map<String, Formula> formulas = new LinkedHashMap<>();
        Map<String, GooValue> additives = new LinkedHashMap<>();
        Map<String, Stack> stacks = new LinkedHashMap<>();
        List<Assignment> assignments = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            classifyEntry(entry.getKey(), entry.getValue(), formulas, additives, stacks,
                    assignments, constants, treeConstants);
        }
        return new ParsedConversions(formulas, additives, stacks, assignments);
    }

    /**
     * Classifies and processes a single entry from the conversions block.
     *
     * @param key           the entry key (item id, tag, or definition name)
     * @param value         the entry value expression to parse
     * @param formulas      the named formula map
     * @param additives     the named additive map
     * @param stacks        the resolved conversion stack map
     * @param assignments   the item-to-assignment map being built
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
     */
    private static void classifyEntry(String key, String value,
                                      Map<String, Formula> formulas,
                                      Map<String, GooValue> additives,
                                      Map<String, Stack> stacks,
                                      List<Assignment> assignments,
                                      Map<String, Integer> constants,
                                      Map<String, GooValue> treeConstants) {
        if (key.startsWith(TAG_PREFIX) || key.contains(COLON)) {
            classifyItemEntry(key, value, stacks, assignments);
        } else {
            classifyDefinition(key, value, formulas, additives, stacks, constants, treeConstants);
        }
    }

    /**
     * Classifies a non-item entry as denial, additive, formula, or stack alias.
     *
     * @param key           the entry key (item id, tag, or definition name)
     * @param value         the entry value expression to parse
     * @param formulas      the named formula map
     * @param additives     the named additive map
     * @param stacks        the resolved conversion stack map
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
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
     * @param key      the entry key (item id, tag, or definition name)
     * @param value    the entry value expression to parse
     * @param formulas the named formula map
     * @param stacks   the resolved conversion stack map
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
     * @param key         the entry key (item id, tag, or definition name)
     * @param value       the entry value expression to parse
     * @param stacks      the resolved conversion stack map
     * @param assignments the item-to-assignment map being built
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
     * @param key       the entry key (item id, tag, or definition name)
     * @param stacks    the resolved conversion stack map
     * @param formulas  the named formula map
     * @param additives the named additive map
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
     * @param key           the entry key (item id, tag, or definition name)
     * @param value         the entry value expression to parse
     * @param additives     the named additive map
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
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
     * @param expr          the expression string to resolve
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
     * @return the resolved result, or null on failure
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
     * @param name          the additive name to look up
     * @param constants     the named integer constants
     * @param treeConstants the named goo value constants from the expression tree
     * @return the resolved result, or null on failure
     */
    private static GooValue resolveNamedAdditive(String name,
                                                 Map<String, Integer> constants,
                                                 Map<String, GooValue> treeConstants) {
        GooValue tree = treeConstants.get(name);
        if (tree != null) {
            return tree;
        }
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(constants.containsKey(name) ? LOG_ADDITIVE_SCALAR : LOG_ADDITIVE_UNKNOWN, name);
        }
        return null;
    }


    /**
     * Parses an assignment value: optional #source for parallel copy,
     * optional * N / M scalar, then space-delimited @conversion chain.
     *
     * @param target the assignment target (item or tag)
     * @param value  the entry value expression to parse
     * @param stacks the resolved conversion stack map
     * @return the resolved result, or null on failure
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
     * @param target    the assignment target (item or tag)
     * @param remaining the unparsed portion of the assignment expression
     * @param m         the regex matcher with captured groups
     * @param stacks    the resolved conversion stack map
     * @return the resolved result, or null on failure
     */
    private static Assignment buildScaledAssignment(String target, String remaining,
                                                    Matcher m, Map<String, Stack> stacks) {
        int mul = Integer.parseInt(m.group(GROUP_SCALE_NUM));
        int div = m.group(GROUP_SCALE_DEN) != null ? Integer.parseInt(m.group(GROUP_SCALE_DEN)) : 1;
        String stripped = remaining.substring(0, m.start()).trim()
                + SPACE + remaining.substring(m.end()).trim();
        return buildAssignment(target, stripped, mul, div, stacks);
    }

    /**
     * Builds an Assignment from parsed tokens.
     *
     * @param target          the assignment target (item or tag)
     * @param remaining       the unparsed portion of the assignment expression
     * @param scaleMultiplier the numerator of the scale fraction
     * @param scaleDivisor    the denominator of the scale fraction
     * @param stacks          the resolved conversion stack map
     * @return the resolved result, or null on failure
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
     * @param tokens the space-delimited assignment tokens
     * @return the resolved result, or null on failure
     */
    private static String findParallelSource(String... tokens) {
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
     * @param tokens the space-delimited assignment tokens
     * @param stacks the resolved conversion stack map
     * @return the resolved result, or null on failure
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
     * @param part   the individual token to classify
     * @param chain  the accumulating list of resolved stacks
     * @param stacks the resolved conversion stack map
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
     * @param expr   the expression string to resolve
     * @param stacks the resolved conversion stack map
     * @return the resolved result, or null on failure
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
     * @param raw      the unresolved stack with potential alias references
     * @param stacks   the resolved conversion stack map
     * @param formulas the named formula map
     * @return the resolved result, or null on failure
     */
    private static Stack resolveChain(Stack raw, Map<String, Stack> stacks,
                                      Map<String, Formula> formulas) {
        Stack known = stacks.get(raw.formulaName());
        if (known != null) {
            return new Stack(known.formulaName(), raw.multiplier() * known.multiplier());
        }
        if (!formulas.containsKey(raw.formulaName()) && LOGGER.isWarnEnabled()) {
            LOGGER.warn(LOG_UNKNOWN_REF, raw.formulaName());
        }
        return raw;
    }
}
