package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;

/**
 * Post-derivation conversion layer: transfers goo between types using named formulas.
 * Conversions apply after all base + derived values are finalized.
 *
 * Parsing is delegated to {@link ConversionFormulaParser};
 * application is delegated to {@link ConversionApplicator}.
 */
public final class GooConversion {

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


    /**
     * Parses a formula string like "metal / 4 -> aeon / 2".
     *
     * @param expr the raw formula expression
     * @return parsed Formula record
     */
    public static Formula parseFormula(String expr) {
        return ConversionFormulaParser.parseFormula(expr);
    }

    /**
     * Parses a stack reference like "2 @oxidation" or "@oxidation".
     *
     * @param expr the raw stack expression
     * @return parsed Stack record
     */
    static Stack parseStack(String expr) {
        return ConversionFormulaParser.parseStack(expr);
    }

    /**
     * Parses a _conversions block (order-dependent). No constant resolution.
     *
     * @param entries ordered map of key-value pairs from the block
     * @return parsed conversion structures
     */
    public static ParsedConversions parseBlock(Map<String, String> entries) {
        return ConversionFormulaParser.parseBlock(entries, Map.of(), Map.of());
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
        return ConversionFormulaParser.parseBlock(entries, constants, treeConstants);
    }


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
        return ConversionApplicator.apply(original, formula, times);
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
        ConversionApplicator.applyAssignment(effectiveValues, targetItems, sourceItems,
                assignment, formulas, additives);
    }
}
