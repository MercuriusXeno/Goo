package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooConversion.Assignment;
import com.mercuriusxeno.goo.data.GooConversion.Formula;
import com.mercuriusxeno.goo.data.GooConversion.Stack;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Application logic for conversion formulas and assignments against effective goo values.
 * Extracted from GooConversion to keep that class under the method-count threshold.
 */
final class ConversionApplicator {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ── String constants ─────────────────────────────────────────────────
    private static final String SLASH_SEP = " / ";
    private static final String LOSSY_PREFIX = "Lossy conversion division: ";
    private static final String IN_CTX = " in ";
    private static final String REMAINDER_PREFIX = " (remainder ";
    private static final String REMAINDER_SUFFIX = ")";
    private static final String LOG_PARALLEL_MISMATCH =
            "Parallel copy size mismatch: {} has {} items, source has {} items";
    private static final String LOG_SCALE_FAILED = "Scale failed for {} (* {} / {}): {}";
    private static final String LOG_NEGATIVE = "Conversion produced negative value for {}: {} -- skipped";
    private static final String LOG_CONV_FAILED = "Conversion failed for {}: {}";
    private static final String LOG_UNKNOWN_FORMULA = "Unknown formula or additive @{} in conversion assignment";

    private ConversionApplicator() { }

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
    static GooValue apply(GooValue original, Formula formula, int times) {
        int sourceAmount = original.get(formula.sourceType());
        if (sourceAmount == 0) {
            return original;
        }
        return computeConversion(original, formula, times, sourceAmount);
    }

    /**
     * Performs the actual conversion arithmetic.
     * @param original the item's current goo value
     * @param formula  the conversion formula to apply
     * @param times          the conversion multiplier
     * @param sourceAmount   the source goo amount being consumed
     * @return the converted goo value
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

    /**
     * Creates a new GooValue with source removed and target added.
     * @param original the item's current goo value
     * @param formula  the conversion formula to apply
     * @param removed        the amount of source goo removed
     * @param added          the amount of target goo added
     * @return the converted goo value
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
     * @param value   the dividend
     * @param divisor the divisor
     * @param context description for the error message if lossy
     * @return the converted goo value
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
    static void applyAssignment(Map<Identifier, GooValue> effectiveValues,
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param targetItems    the list of target item identifiers
     * @param sourceItems    the list of source item identifiers
     * @param assignment the assignment whose fraction caused the error
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param targetItems    the list of target item identifiers
     * @param sourceItems    the list of source item identifiers
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param targetItems    the list of target item identifiers
     * @param assignment the assignment whose fraction caused the error
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param itemId     the item identifier
     * @param assignment the assignment whose fraction caused the error
     */
    private static void scaleItem(Map<Identifier, GooValue> effectiveValues,
                                   Identifier itemId, Assignment assignment) {
        GooValue current = effectiveValues.get(itemId);
        if (current == null || current.isEmpty()) { return; }
        tryScaleValue(effectiveValues, itemId, current, assignment);
    }

    /**
     * Attempts to scale a value by the assignment fraction, logging on failure.
     * @param effectiveValues the mutable item-to-value map being built
     * @param itemId     the item identifier
     * @param current        the item's current goo value
     * @param assignment the assignment whose fraction caused the error
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

    /**
     * Logs a scale failure at error level.
     * @param itemId     the item that failed scaling
     * @param assignment the assignment whose fraction caused the error
     * @param e          the arithmetic exception that occurred
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param items           the list of item identifiers to apply to
     * @param stack           the conversion stack to apply
     * @param formulas       the named formula map
     * @param additives      the named additive map
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param items           the list of item identifiers to apply to
     * @param stack           the conversion stack to apply
     * @param formulas       the named formula map
     */
    private static void applyResolvedFormula(Map<Identifier, GooValue> effectiveValues,
                                              List<Identifier> items, Stack stack,
                                              Map<String, Formula> formulas) {
        Formula formula = formulas.get(stack.formulaName());
        if (formula == null) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(LOG_UNKNOWN_FORMULA, stack.formulaName());
            }
            return;
        }
        applyFormulaStack(effectiveValues, items, formula, stack.multiplier());
    }

    /**
     * Adds an additive value (times multiplier) to each item.
     * @param effectiveValues the mutable item-to-value map being built
     * @param items           the list of item identifiers to apply to
     * @param additive       the additive goo value to add
     * @param multiplier     the times multiplier for the stack
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param items           the list of item identifiers to apply to
     * @param formula         the conversion formula to apply
     * @param multiplier     the times multiplier for the stack
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
     * @param effectiveValues the mutable item-to-value map being built
     * @param itemId     the item identifier
     * @param formula         the conversion formula to apply
     * @param multiplier     the times multiplier for the stack
     */
    private static void applyFormulaToItem(Map<Identifier, GooValue> effectiveValues,
                                            Identifier itemId, Formula formula,
                                            int multiplier) {
        GooValue current = effectiveValues.get(itemId);
        if (current == null || current.isEmpty()) { return; }
        tryApplyFormula(effectiveValues, itemId, current, formula, multiplier);
    }

    /**
     * Attempts formula conversion with error handling for arithmetic and negative results.
     * @param effectiveValues the mutable item-to-value map being built
     * @param itemId     the item identifier
     * @param current        the item's current goo value
     * @param formula         the conversion formula to apply
     * @param multiplier     the times multiplier for the stack
     */
    private static void tryApplyFormula(Map<Identifier, GooValue> effectiveValues,
            Identifier itemId, GooValue current, Formula formula, int multiplier) {
        try {
            GooValue converted = apply(current, formula, multiplier);
            storeIfValid(effectiveValues, itemId, converted);
        } catch (ArithmeticException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(LOG_CONV_FAILED, itemId, e.getMessage());
            }
        }
    }

    /**
     * Stores a converted value if it contains no negative entries.
     * @param effectiveValues the mutable item-to-value map being built
     * @param itemId     the item identifier
     * @param converted      the computed result to store
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
