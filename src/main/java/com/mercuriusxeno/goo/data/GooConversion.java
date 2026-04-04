package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-derivation conversion layer: transfers goo between types using named formulas.
 * Conversions apply after all base + derived values are finalized.
 */
public final class GooConversion {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GooConversion() {}

    /** A conversion formula: "source / N -> target / M". */
    public record Formula(GooType sourceType, int sourceDivisor,
                          GooType targetType, int targetDivisor) {}

    /** A named stack: N applications of a formula. */
    public record Stack(String formulaName, int multiplier) {}

    /**
     * An item or tag assignment: optionally copy values from a parallel source tag,
     * then apply a chain of conversion stacks in order.
     *
     * @param target        the target item ID or #tag
     * @param parallelSource if non-null, a #tag to copy values from (parallel arrays)
     * @param chain         conversion stacks to apply in order after the copy
     */
    public record Assignment(String target, String parallelSource, java.util.List<Stack> chain) {}

    /** Result of parsing a _conversions block. */
    public record ParsedConversions(
            Map<String, Formula> formulas,
            Map<String, Stack> stacks,
            java.util.List<Assignment> assignments
    ) {}

    // ── Formula pattern: "type / N -> type / M" ─────────────────────────

    private static final Pattern FORMULA_PATTERN = Pattern.compile(
            "\\s*(\\w+)\\s*/\\s*(\\d+)\\s*->\\s*(\\w+)\\s*/\\s*(\\d+)\\s*");

    /** Parses a formula string like "metal / 4 -> aeon / 2". */
    public static Formula parseFormula(String expr) {
        Matcher m = FORMULA_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid conversion formula: " + expr);
        }
        GooType source = GooType.valueOf(m.group(1).toUpperCase());
        int sourceDivisor = Integer.parseInt(m.group(2));
        GooType target = GooType.valueOf(m.group(3).toUpperCase());
        int targetDivisor = Integer.parseInt(m.group(4));
        return new Formula(source, sourceDivisor, target, targetDivisor);
    }

    // ── Stack reference pattern: "N @name" or "@name" ───────────────────

    private static final Pattern STACK_PATTERN = Pattern.compile(
            "\\s*(?:(\\d+)\\s+)?@(\\w+)\\s*");

    /** Parses a stack reference like "2 @oxidation" or "@oxidation". */
    static Stack parseStack(String expr) {
        Matcher m = STACK_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid conversion reference: " + expr);
        }
        int multiplier = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
        String name = m.group(2);
        return new Stack(name, multiplier);
    }

    // ── Block parsing ────────────────────────────────────────────────────

    /** Parses a _conversions block (order-dependent). */
    public static ParsedConversions parseBlock(Map<String, String> entries) {
        Map<String, Formula> formulas = new LinkedHashMap<>();
        Map<String, Stack> stacks = new LinkedHashMap<>();
        java.util.List<Assignment> assignments = new java.util.ArrayList<>();

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isItemOrTag(key)) {
                if ("denied".equals(value)) continue; // datapack noop override
                assignments.add(parseAssignment(key, value, stacks));
            } else if ("denied".equals(value)) {
                // Noop a named stack so downstream @refs become no-ops
                stacks.remove(key);
                formulas.remove(key);
                continue;
            } else if (value.contains("->")) {
                // Formula declaration
                formulas.put(key, parseFormula(value));
            } else {
                // Named stack (references a formula or another stack)
                Stack raw = parseStack(value);
                // Resolve chain: if the ref points to another stack, multiply through
                Stack resolved = resolveChain(raw, stacks, formulas);
                stacks.put(key, resolved);
            }
        }
        return new ParsedConversions(formulas, stacks, assignments);
    }

    /** True if the key looks like an item ID or tag reference. */
    private static boolean isItemOrTag(String key) {
        return key.startsWith("#") || key.contains(":");
    }

    /**
     * Parses an assignment value: optional #source for parallel copy,
     * then space-delimited @conversion chain.
     * Examples: "@exposed", "#copper_originals @exposed", "#originals @exposed @weathered"
     */
    private static Assignment parseAssignment(String target, String value,
                                               Map<String, Stack> stacks) {
        String[] parts = value.trim().split("\\s+");
        String parallelSource = null;
        java.util.List<Stack> chain = new java.util.ArrayList<>();

        for (String part : parts) {
            if (part.startsWith("#")) {
                parallelSource = part;
            } else if (part.contains("@")) {
                chain.add(resolveStackRef(part, stacks));
            } else {
                LOGGER.warn("Unexpected token in conversion assignment: {}", part);
            }
        }
        return new Assignment(target, parallelSource, chain);
    }

    /** Resolves a @ref expression against known stacks. */
    private static Stack resolveStackRef(String expr, Map<String, Stack> stacks) {
        Stack raw = parseStack(expr);
        Stack known = stacks.get(raw.formulaName());
        if (known != null) {
            return new Stack(known.formulaName(), raw.multiplier() * known.multiplier());
        }
        return raw;
    }

    /** Resolves stack chain: "2 @exposed" where exposed is "1 @oxidation" -> "2 @oxidation". */
    private static Stack resolveChain(Stack raw, Map<String, Stack> stacks,
                                       Map<String, Formula> formulas) {
        Stack known = stacks.get(raw.formulaName());
        if (known != null) {
            return new Stack(known.formulaName(), raw.multiplier() * known.multiplier());
        }
        if (formulas.containsKey(raw.formulaName())) {
            return raw;
        }
        LOGGER.warn("Unknown conversion reference: @{}", raw.formulaName());
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
        if (sourceAmount == 0) return original;

        int removed = exactDivide(sourceAmount, formula.sourceDivisor(),
                formula.sourceType() + " / " + formula.sourceDivisor()) * times;
        int perApplication = exactDivide(sourceAmount, formula.sourceDivisor(),
                formula.sourceType() + " / " + formula.sourceDivisor());
        int added = exactDivide(perApplication, formula.targetDivisor(),
                formula.targetType() + " / " + formula.targetDivisor()) * times;

        Map<GooType, Integer> result = new EnumMap<>(GooType.class);
        original.getAll().forEach(result::put);
        result.merge(formula.sourceType(), -removed, Integer::sum);
        result.merge(formula.targetType(), added, Integer::sum);
        return new GooValue(result);
    }

    /** Integer division that throws if lossy. */
    private static int exactDivide(int value, int divisor, String context) {
        if (value % divisor != 0) {
            throw new ArithmeticException(
                    "Lossy conversion division: " + value + " in " + context
                    + " (remainder " + value % divisor + ")");
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
     */
    public static void applyAssignment(Map<Identifier, GooValue> effectiveValues,
                                        java.util.List<Identifier> targetItems,
                                        java.util.List<Identifier> sourceItems,
                                        Assignment assignment,
                                        Map<String, Formula> formulas) {
        // Parallel copy phase
        if (sourceItems != null) {
            if (sourceItems.size() != targetItems.size()) {
                LOGGER.error("Parallel copy size mismatch: {} has {} items, source has {} items",
                        assignment.target(), targetItems.size(), sourceItems.size());
                return;
            }
            for (int i = 0; i < targetItems.size(); i++) {
                GooValue sourceVal = effectiveValues.get(sourceItems.get(i));
                if (sourceVal != null && !sourceVal.isEmpty()) {
                    effectiveValues.put(targetItems.get(i), sourceVal);
                }
            }
        }

        // Conversion chain phase
        for (Stack stack : assignment.chain()) {
            applyStack(effectiveValues, targetItems, stack, formulas);
        }
    }

    /** Applies a single conversion stack to a list of items. */
    private static void applyStack(Map<Identifier, GooValue> effectiveValues,
                                    java.util.List<Identifier> items, Stack stack,
                                    Map<String, Formula> formulas) {
        Formula formula = formulas.get(stack.formulaName());
        if (formula == null) {
            LOGGER.error("Unknown formula @{} in conversion assignment", stack.formulaName());
            return;
        }
        for (Identifier itemId : items) {
            GooValue current = effectiveValues.get(itemId);
            if (current == null || current.isEmpty()) continue;
            try {
                GooValue converted = apply(current, formula, stack.multiplier());
                if (converted.hasNegative()) {
                    LOGGER.error("Conversion produced negative value for {}: {} -- skipped",
                            itemId, converted);
                    continue;
                }
                effectiveValues.put(itemId, converted);
            } catch (ArithmeticException e) {
                LOGGER.error("Conversion failed for {}: {}", itemId, e.getMessage());
            }
        }
    }
}
