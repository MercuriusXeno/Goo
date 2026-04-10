package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Value comparison audit sections: conflicts, divisibility losses, and
 * the all-values listing. Called by {@link GooAuditReport} during the
 * audit report build.
 */
final class GooAuditValues {

    // --- Conflict constants ---

    /** Conflicts section header prefix. */
    private static final String HDR_CONFLICTS_PREFIX = "CONFLICTS (";
    /** Conflicts section header middle. */
    private static final String HDR_CONFLICTS_MID = " total, ";
    /** Conflicts section header suffix. */
    private static final String HDR_CONFLICTS_SUFFIX = " recipe-cheaper)";
    /** No conflicts message. */
    private static final String MSG_NO_CONFLICTS = "  No base/recipe value conflicts.";
    /** Conflicts chat prefix. */
    private static final String MSG_CONFLICTS_CHAT_PREFIX = "  Conflicts: ";
    /** Conflicts chat open paren. */
    private static final String MSG_CONFLICTS_CHAT_MID = " (";
    /** Conflicts chat suffix. */
    private static final String MSG_CONFLICTS_CHAT_SUFFIX = " recipe-cheaper)";
    /** Blobs label in conflict lines. */
    private static final String LABEL_BLOBS_OPEN = " blobs {";
    /** Close brace in conflict lines. */
    private static final String LABEL_BLOBS_CLOSE = "}";
    /** Recipe cheaper direction label. */
    private static final String DIR_RECIPE_CHEAPER = "<- RECIPE CHEAPER";
    /** Base cheaper direction label. */
    private static final String DIR_BASE_CHEAPER = "<- BASE CHEAPER";
    /** Conflict base prefix. */
    private static final String CONFLICT_BASE = " - base: ";
    /** Conflict vs separator. */
    private static final String CONFLICT_VS = " vs recipe: ";

    // --- Divisibility constants ---

    /** Initial capacity for divisibility-line StringBuilder. */
    private static final int DIVISIBILITY_LINE_CAPACITY = 48;
    /** Divisibility section header prefix. */
    private static final String HDR_DIV_PREFIX = "DIVISIBILITY LOSS (";
    /** Divisibility section header suffix. */
    private static final String HDR_DIV_SUFFIX = " recipes lose value to integer division)";
    /** No divisibility losses message. */
    private static final String MSG_NO_DIV = "  No divisibility losses detected.";
    /** Divisibility chat prefix. */
    private static final String MSG_DIV_CHAT_PREFIX = "  Divisibility: ";
    /** Divisibility chat suffix. */
    private static final String MSG_DIV_CHAT_SUFFIX = " recipe(s) lose blobs to integer division";
    /** Divisibility total label. */
    private static final String DIV_TOTAL = " total / ";
    /** Divisibility items label. */
    private static final String DIV_ITEMS = " items = ";
    /** Divisibility each label. */
    private static final String DIV_EACH = " each (loses ";
    /** Divisibility blob(s) suffix. */
    private static final String DIV_BLOBS = " blob(s))";
    /** Divisibility ingredient provenance newline indent. */
    private static final String DIV_INGREDIENT_INDENT = "\n      ";
    /** No value label for ingredient. */
    private static final String LABEL_NO_VALUE = " = no value";
    /** Equals-space-value format prefix. */
    private static final String LABEL_EQ_VALUE = " = ";
    /** Open brace for value display. */
    private static final String LABEL_OPEN_BRACE = " {";
    /** Base provenance tag. */
    private static final String LABEL_BASE = " (base)";
    /** Derived provenance tag. */
    private static final String LABEL_DERIVED = " (derived)";

    // --- All Values constants ---

    /** All values section header prefix. */
    private static final String HDR_ALL_PREFIX = "ALL VALUES (";
    /** All values section header suffix. */
    private static final String HDR_ALL_SUFFIX = " items)";
    /** Provenance: base+derived prefix. */
    private static final String PROV_BASE_DERIVED = " (base+derived from: ";
    /** Provenance: derived prefix. */
    private static final String PROV_DERIVED = " (derived from: ";
    /** Plus separator in recipe format. */
    private static final String RECIPE_PLUS = " + ";
    /** Multiplier prefix in recipe format. */
    private static final String RECIPE_TIMES_PREFIX = "x ";
    /** Arrow to result count in recipe format. */
    private static final String RECIPE_ARROW = " -> ";
    /** Hyphen separator between item and detail. */
    private static final String SEP_DASH = " - ";

    private GooAuditValues() {}

    // --- Conflicts ---

    /**
     * Appends the conflicts section to the report, returns count of recipe-cheaper conflicts.
     *
     * @param report the report lines list to append to
     * @return the number of recipe-cheaper conflicts
     */
    static int appendConflictsSection(List<String> report) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.diagnostics().conflicts();
        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        GooAuditReport.appendSectionBody(report,
                HDR_CONFLICTS_PREFIX + conflicts.size() + HDR_CONFLICTS_MID + exploitCount + HDR_CONFLICTS_SUFFIX,
                MSG_NO_CONFLICTS, conflicts, GooAuditValues::formatConflictFileLine);
        return (int) exploitCount;
    }

    /**
     * Sends a conflict count summary to chat.
     *
     * @param ctx the command context
     */
    static void sendConflictsSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.diagnostics().conflicts();
        if (conflicts.isEmpty()) { return; }

        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_CONFLICTS_CHAT_PREFIX + conflicts.size() + MSG_CONFLICTS_CHAT_MID + exploitCount + MSG_CONFLICTS_CHAT_SUFFIX)
                .withStyle(exploitCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
    }

    /**
     * Formats a single conflict as a plain-text line.
     *
     * @param conflict the value conflict to format
     * @return the formatted line
     */
    private static String formatConflictFileLine(GooValueRegistry.ValueConflict conflict) {
        String baseStr = conflict.baseValue().totalBlobs() + LABEL_BLOBS_OPEN + conflict.baseValue() + LABEL_BLOBS_CLOSE;
        String recipeStr = conflict.recipeValue().totalBlobs() + LABEL_BLOBS_OPEN + conflict.recipeValue() + LABEL_BLOBS_CLOSE;
        String direction = conflict.isRecipeCheaper() ? DIR_RECIPE_CHEAPER : DIR_BASE_CHEAPER;
        return GooAuditReport.INDENT + conflict.item() + CONFLICT_BASE + baseStr + CONFLICT_VS + recipeStr + GooAuditReport.SEP_SPACE + direction;
    }

    // --- Divisibility ---

    /**
     * Appends the divisibility loss section to the report, returns count of lossy recipes.
     *
     * @param report the report lines list to append to
     * @return the number of lossy recipes
     */
    static int appendDivisibilitySection(List<String> report) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.diagnostics().divisibilityLosses();
        GooAuditReport.appendSectionBody(report, HDR_DIV_PREFIX + losses.size() + HDR_DIV_SUFFIX,
                MSG_NO_DIV, losses, GooAuditValues::formatDivisibilityLine);
        return losses.size();
    }

    /**
     * Sends a divisibility loss count summary to chat.
     *
     * @param ctx the command context
     */
    static void sendDivisibilitySummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.diagnostics().divisibilityLosses();
        if (losses.isEmpty()) { return; }

        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_DIV_CHAT_PREFIX + losses.size() + MSG_DIV_CHAT_SUFFIX)
                .withStyle(ChatFormatting.YELLOW), false);
    }

    /**
     * Formats a single divisibility loss with ingredient provenance.
     *
     * @param loss the divisibility loss to format
     * @return the formatted line
     */
    private static String formatDivisibilityLine(GooValueRegistry.DivisibilityLoss loss) {
        StringBuilder sb = new StringBuilder(DIVISIBILITY_LINE_CAPACITY);
        sb.append(GooAuditReport.INDENT).append(loss.output()).append(SEP_DASH)
            .append(loss.inputTotal()).append(DIV_TOTAL)
            .append(loss.outputCount()).append(DIV_ITEMS).append(loss.perItemValue())
            .append(DIV_EACH).append(loss.lostBlobs()).append(DIV_BLOBS);
        appendIngredientProvenance(sb, loss);
        return sb.toString();
    }

    /**
     * Appends ingredient value sources to the divisibility loss line.
     *
     * @param sb   the string builder to append to
     * @param loss the divisibility loss record
     */
    private static void appendIngredientProvenance(StringBuilder sb,
            GooValueRegistry.DivisibilityLoss loss) {
        for (var alternatives : loss.recipe().ingredientAlternatives()) {
            Identifier cheapest = findCheapestIngredientId(alternatives);
            if (cheapest == null) { continue; }
            sb.append(DIV_INGREDIENT_INDENT).append(cheapest);
            appendValueSource(sb, cheapest);
        }
    }

    /**
     * Finds the cheapest valued item ID among alternatives.
     *
     * @param alternatives the set of alternative item IDs
     * @return the cheapest item ID, or null if none have values
     */
    private static Identifier findCheapestIngredientId(Set<Identifier> alternatives) {
        return IGooValueLookup.findCheapestAmong(alternatives, Goo.GOO_VALUES::lookup);
    }

    /**
     * Appends " = {value} (base|derived)" for a single ingredient.
     *
     * @param sb     the string builder to append to
     * @param itemId the ingredient item ID
     */
    private static void appendValueSource(StringBuilder sb, Identifier itemId) {
        GooValue val = Goo.GOO_VALUES.lookup(itemId);
        if (val == null) {
            sb.append(LABEL_NO_VALUE);
            return;
        }
        sb.append(LABEL_EQ_VALUE).append(val.totalBlobs()).append(LABEL_OPEN_BRACE).append(val).append(LABEL_BLOBS_CLOSE)
          .append(Goo.GOO_VALUES.hasBaseValue(itemId) ? LABEL_BASE : LABEL_DERIVED);
    }

    // --- All Values ---

    /**
     * Appends a section listing every item's effective value and its source.
     *
     * @param report the report lines list to append to
     */
    static void appendAllValuesSection(List<String> report) {
        Map<Identifier, GooValue> effective = Goo.GOO_VALUES.getEffectiveValues();
        List<Identifier> sorted = new ArrayList<>(effective.keySet());
        Collections.sort(sorted);
        GooAuditReport.appendSectionBody(report, HDR_ALL_PREFIX + sorted.size() + HDR_ALL_SUFFIX,
                GooAuditReport.EMPTY_LINE, sorted, id -> formatValueLine(id, effective.get(id)));
    }

    /**
     * Formats a single item's value with its provenance (base/derived + source recipe).
     *
     * @param itemId the item identifier
     * @param value  the item's goo value
     * @return the formatted line
     */
    private static String formatValueLine(Identifier itemId, GooValue value) {
        StringBuilder sb = new StringBuilder();
        sb.append(GooAuditReport.INDENT).append(itemId).append(LABEL_EQ_VALUE)
            .append(value.totalBlobs()).append(LABEL_OPEN_BRACE).append(value).append(LABEL_BLOBS_CLOSE);
        appendProvenance(sb, itemId);
        return sb.toString();
    }

    /**
     * Appends the provenance tag: (base), (derived), or (base+derived).
     *
     * @param sb     the string builder to append to
     * @param itemId the item identifier
     */
    private static void appendProvenance(StringBuilder sb, Identifier itemId) {
        boolean hasBase = Goo.GOO_VALUES.hasBaseValue(itemId);
        RecipeInput source = Goo.GOO_VALUES.diagnostics().derivationSources().get(itemId);
        if (hasBase && source != null) {
            sb.append(PROV_BASE_DERIVED).append(formatSourceRecipe(source)).append(GooAuditReport.MSG_CLOSE_PAREN);
        } else if (hasBase) {
            sb.append(LABEL_BASE);
        } else if (source != null) {
            sb.append(PROV_DERIVED).append(formatSourceRecipe(source)).append(GooAuditReport.MSG_CLOSE_PAREN);
        }
    }

    /**
     * Formats a source recipe as a compact ingredient list.
     *
     * @param recipe the source recipe input
     * @return the formatted recipe string
     */
    private static String formatSourceRecipe(RecipeInput recipe) {
        Map<Identifier, Integer> counts = countIngredients(recipe);
        String ingredientStr = counts.entrySet().stream()
            .map(e -> e.getValue() > 1 ? e.getValue() + RECIPE_TIMES_PREFIX + e.getKey() : e.getKey().toString())
            .collect(Collectors.joining(RECIPE_PLUS));
        return recipe.resultCount() > 1
            ? ingredientStr + RECIPE_ARROW + recipe.resultCount() : ingredientStr;
    }

    /**
     * Counts how many times each cheapest ingredient appears in a recipe.
     *
     * @param recipe the source recipe input
     * @return ingredient counts keyed by cheapest item ID
     */
    private static Map<Identifier, Integer> countIngredients(RecipeInput recipe) {
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            Identifier cheapest = findCheapestIngredientId(alts);
            if (cheapest != null) {
                counts.merge(cheapest, 1, Integer::sum);
            }
        }
        return counts;
    }

}
