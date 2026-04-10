package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds and writes the /goo audit report. Orchestrates structural checks
 * (expressions, phantom IDs, cycles, no-value items) and delegates value
 * comparison sections to {@link GooAuditValues}.
 */
final class GooAuditReport {

    // --- Shared/template constants (package-private for GooAuditValues access) ---

    /** Width of section separator lines in the audit report. */
    static final int SEPARATOR_WIDTH = 50;
    /** Dash character for section separator lines. */
    static final String SEPARATOR_CHAR = "-";
    /** Indent prefix for report lines. */
    static final String INDENT = "  ";
    /** Empty line separator in report output. */
    static final String EMPTY_LINE = "";
    /** Single space separator. */
    static final String SEP_SPACE = " ";
    /** Closing parenthesis. */
    static final String MSG_CLOSE_PAREN = ")";
    /** Denied source label. */
    static final String LABEL_DENIED = "(denied)";
    /** Valued source label. */
    static final String LABEL_VALUED = "(valued)";

    // --- Audit orchestration constants (private) ---

    /** Audit report title. */
    private static final String AUDIT_TITLE = "Goo Audit Report";
    /** Audit report title underline. */
    private static final String AUDIT_UNDERLINE = "================";
    /** Audit file output path. */
    private static final String FILE_AUDIT = "goo_audit.txt";
    /** All checks passed message. */
    private static final String MSG_ALL_PASSED = "All checks passed. No issues found.";
    /** Audit issues found suffix. */
    private static final String MSG_ISSUES_SUFFIX = " issue(s) found. See config/goo_audit.txt";
    /** Link to full report. */
    private static final String MSG_FULL_REPORT = "Full report: config/goo_audit.txt";
    /** Log message for diagnostic file write failure. */
    private static final String LOG_WRITE_FAIL = "Failed to write diagnostic file: {}";

    // --- Expression warning constants (private) ---

    /** Expression warnings section header prefix. */
    private static final String HDR_EXPR_PREFIX = "EXPRESSION WARNINGS (";
    /** Expression warnings section header suffix. */
    private static final String HDR_EXPR_SUFFIX = " issues in base_values.json expressions)";
    /** All expressions valid message. */
    private static final String MSG_EXPR_VALID = "  All expressions are valid.";
    /** Expression warnings chat prefix. */
    private static final String MSG_EXPR_CHAT_PREFIX = "  Expression warnings: ";
    /** Expression warnings chat suffix. */
    private static final String MSG_EXPR_CHAT_SUFFIX = " issue(s)";

    // --- Phantom ID constants (private) ---

    /** Phantom IDs section header prefix. */
    private static final String HDR_PHANTOM_PREFIX = "PHANTOM IDS (";
    /** Phantom IDs section header suffix. */
    private static final String HDR_PHANTOM_SUFFIX = " identifiers in base_values.json don't match any registered item)";
    /** All identifiers valid message. */
    private static final String MSG_IDS_VALID = "  All identifiers are valid.";
    /** Phantom IDs chat prefix. */
    private static final String MSG_PHANTOM_CHAT_PREFIX = "  Phantoms: ";
    /** Phantom IDs chat suffix. */
    private static final String MSG_PHANTOM_CHAT_SUFFIX = " ID(s) in base_values.json don't exist in game";

    // --- Cycle constants (private) ---

    /** Cycles section header prefix. */
    private static final String HDR_CYCLES_PREFIX = "CYCLES (";
    /** Cycles section header middle. */
    private static final String HDR_CYCLES_MID = " total, ";
    /** Cycles section header suffix. */
    private static final String HDR_CYCLES_SUFFIX = " dead)";
    /** No cycles detected message. */
    private static final String MSG_NO_CYCLES = "  No recipe cycles detected.";
    /** Cycles chat prefix. */
    private static final String MSG_CYCLES_CHAT_PREFIX = "  Cycles: ";
    /** Cycles chat suffix. */
    private static final String MSG_CYCLES_CHAT_SUFFIX = " unanchored (need base values to resolve)";
    /** Cycle arrow separator. */
    private static final String CYCLE_ARROW = " -> ";
    /** Anchored cycle prefix. */
    private static final String CYCLE_OK_PREFIX = "  [OK]   ";
    /** Anchored cycle suffix. */
    private static final String CYCLE_ANCHORED_BY = " (anchored by ";
    /** Dead cycle prefix. */
    private static final String CYCLE_DEAD_PREFIX = "  [DEAD] ";
    /** Dead cycle suffix. */
    private static final String CYCLE_DEAD_SUFFIX = " (no base values - unresolvable)";

    // --- No-value constants (private) ---

    /** No value section header prefix. */
    private static final String HDR_NO_VALUE_PREFIX = "NO VALUE (";
    /** No value section header suffix. */
    private static final String HDR_NO_VALUE_SUFFIX = " registered items have no goo value)";
    /** All items have values message. */
    private static final String MSG_ALL_VALUED = "  All registered items have goo values.";
    /** No value quote-comma format prefix. */
    private static final String NO_VALUE_QUOTE = "\"";
    /** No value quote-comma format suffix. */
    private static final String NO_VALUE_SUFFIX = "\",";
    /** No value chat prefix. */
    private static final String MSG_NO_VALUE_CHAT_PREFIX = "  No value: ";
    /** No value chat suffix. */
    private static final String MSG_NO_VALUE_CHAT_SUFFIX = " item(s) have no goo value";

    private GooAuditReport() {}

    /** Runs the full audit: builds report, writes file, sends results to chat.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    static int run(CommandContext<CommandSourceStack> ctx) {
        int issues = buildAndWriteReport();
        sendAuditResultToChat(ctx, issues);
        sendSectionSummaries(ctx);
        return 1;
    }

    /**
     * Builds the full audit report, writes it to disk, and returns the issue count.
     *
     * @return the total number of issues found
     */
    private static int buildAndWriteReport() {
        List<String> report = new ArrayList<>();
        report.add(AUDIT_TITLE);
        report.add(AUDIT_UNDERLINE);
        report.add(EMPTY_LINE);
        int issues = appendIssueSections(report);
        appendNoValueSection(report);
        GooAuditValues.appendAllValuesSection(report);
        writeDiagnosticFile(FILE_AUDIT, report);
        return issues;
    }

    /** Appends all issue-counting sections and returns the total issue count.
     *
     * @param report the report lines list
     * @return the combined issue count
     */
    private static int appendIssueSections(List<String> report) {
        int issues = appendExpressionWarningsSection(report);
        issues += appendPhantomIdsSection(report);
        issues += appendCyclesSection(report);
        issues += GooAuditValues.appendConflictsSection(report);
        issues += GooAuditValues.appendDivisibilitySection(report);
        return issues;
    }

    /**
     * Sends the pass/fail headline and file location to chat.
     *
     * @param ctx    the command context
     * @param issues the total issue count
     */
    private static void sendAuditResultToChat(CommandContext<CommandSourceStack> ctx, int issues) {
        String text = issues == 0 ? MSG_ALL_PASSED : issues + MSG_ISSUES_SUFFIX;
        ChatFormatting color = issues == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    /**
     * Sends per-section summaries and the file location to chat.
     *
     * @param ctx the command context
     */
    private static void sendSectionSummaries(CommandContext<CommandSourceStack> ctx) {
        sendExpressionWarningsSummary(ctx);
        sendPhantomIdsSummary(ctx);
        sendCyclesSummary(ctx);
        GooAuditValues.sendConflictsSummary(ctx);
        GooAuditValues.sendDivisibilitySummary(ctx);
        sendNoValueSummary(ctx);
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_FULL_REPORT).withStyle(ChatFormatting.GRAY), false);
    }

    // --- Section template ---

    /** Appends a standard audit section: header, separator, items (or empty message), blank line.
     *
     * @param report   the report lines list
     * @param header   the section header line
     * @param emptyMsg the message to show when the list is empty
     * @param items    the items to format
     * @param formatter formats each item into a report line
     * @param <T>      the item type
     */
    static <T> void appendSectionBody(List<String> report, String header,
            String emptyMsg, List<T> items, Function<T, String> formatter) {
        report.add(header);
        report.add(SEPARATOR_CHAR.repeat(SEPARATOR_WIDTH));
        if (items.isEmpty()) { report.add(emptyMsg); }
        else { items.forEach(item -> report.add(formatter.apply(item))); }
        report.add(EMPTY_LINE);
    }

    // --- Utilities ---

    /**
     * Writes diagnostic lines to a file in the config directory.
     *
     * @param filename the output filename
     * @param lines    the lines to write
     */
    static void writeDiagnosticFile(String filename, List<String> lines) {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(filename);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_WRITE_FAIL, filename, e);
        }
    }

    // --- Expression Warnings ---

    /**
     * Appends expression validation warnings (bad constants, out-of-order refs).
     *
     * @param report the report lines list to append to
     * @return the number of warnings found
     */
    private static int appendExpressionWarningsSection(List<String> report) {
        List<String> warnings = Goo.GOO_VALUES.validateBaseValues();
        appendSectionBody(report, HDR_EXPR_PREFIX + warnings.size() + HDR_EXPR_SUFFIX,
                MSG_EXPR_VALID, warnings, w -> INDENT + w);
        return warnings.size();
    }

    /**
     * Sends expression warnings summary to chat.
     *
     * @param ctx the command context
     */
    private static void sendExpressionWarningsSummary(CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = Goo.GOO_VALUES.validateBaseValues();
        if (warnings.isEmpty()) { return; }
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_EXPR_CHAT_PREFIX + warnings.size() + MSG_EXPR_CHAT_SUFFIX)
                .withStyle(ChatFormatting.YELLOW), false);
    }

    // --- Phantom IDs ---

    /**
     * Finds identifiers in base_values.json that don't match any registered item.
     *
     * @return sorted list of phantom identifiers
     */
    private static List<Identifier> findPhantomIds() {
        List<Identifier> phantoms = new ArrayList<>();
        for (Identifier id : Goo.GOO_VALUES.diagnostics().allReferencedIds()) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                phantoms.add(id);
            }
        }
        Collections.sort(phantoms);
        return phantoms;
    }

    /**
     * Appends the phantom IDs section to the report, returns count of phantoms.
     *
     * @param report the report lines list to append to
     * @return the number of phantom IDs found
     */
    private static int appendPhantomIdsSection(List<String> report) {
        List<Identifier> phantoms = findPhantomIds();
        appendSectionBody(report, HDR_PHANTOM_PREFIX + phantoms.size() + HDR_PHANTOM_SUFFIX,
                MSG_IDS_VALID, phantoms, id -> INDENT + id + SEP_SPACE
                        + (Goo.GOO_VALUES.isDenied(id) ? LABEL_DENIED : LABEL_VALUED));
        return phantoms.size();
    }

    /**
     * Sends a phantom ID count summary to chat.
     *
     * @param ctx the command context
     */
    private static void sendPhantomIdsSummary(CommandContext<CommandSourceStack> ctx) {
        List<Identifier> phantoms = findPhantomIds();
        if (phantoms.isEmpty()) { return; }

        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_PHANTOM_CHAT_PREFIX + phantoms.size() + MSG_PHANTOM_CHAT_SUFFIX)
                .withStyle(ChatFormatting.RED), false);
    }

    // --- Cycles ---

    /**
     * Appends the cycles section to the report, returns count of dead cliques.
     *
     * @param report the report lines list to append to
     * @return the number of dead (unanchored) cycles
     */
    private static int appendCyclesSection(List<String> report) {
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.diagnostics().cycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        appendSectionBody(report,
                HDR_CYCLES_PREFIX + cycles.size() + HDR_CYCLES_MID + deadCount + HDR_CYCLES_SUFFIX,
                MSG_NO_CYCLES, cycles, GooAuditReport::formatCycleFileLine);
        return (int) deadCount;
    }

    /**
     * Sends a cycle count summary to chat. Only shown when unanchored cycles exist.
     *
     * @param ctx the command context
     */
    private static void sendCyclesSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.diagnostics().cycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        if (deadCount == 0) { return; }

        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_CYCLES_CHAT_PREFIX + deadCount + MSG_CYCLES_CHAT_SUFFIX)
                .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Formats a single cycle as a plain-text line.
     *
     * @param cycle the recipe cycle to format
     * @return the formatted line
     */
    private static String formatCycleFileLine(GooValueRegistry.RecipeCycle cycle) {
        String items = cycle.items().stream()
            .map(Identifier::toString)
            .collect(Collectors.joining(CYCLE_ARROW));

        if (cycle.hasAnchor()) {
            return CYCLE_OK_PREFIX + items + CYCLE_ANCHORED_BY + cycle.anchor() + MSG_CLOSE_PAREN;
        }
        return CYCLE_DEAD_PREFIX + items + CYCLE_DEAD_SUFFIX;
    }

    // --- No Value ---

    /**
     * Appends a section listing all registered items with no effective goo value.
     *
     * @param report the report lines list to append to
     */
    private static void appendNoValueSection(List<String> report) {
        List<Identifier> noValue = findItemsWithNoValue();
        appendSectionBody(report, HDR_NO_VALUE_PREFIX + noValue.size() + HDR_NO_VALUE_SUFFIX,
                MSG_ALL_VALUED, noValue, id -> NO_VALUE_QUOTE + id + NO_VALUE_SUFFIX);
    }

    /**
     * Finds all registered items that have no effective goo value, excluding denied items.
     *
     * @return sorted list of unvalued item identifiers
     */
    private static List<Identifier> findItemsWithNoValue() {
        List<Identifier> noValue = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (Goo.GOO_VALUES.lookup(id) == null && !Goo.GOO_VALUES.isDenied(id)) {
                noValue.add(id);
            }
        }
        Collections.sort(noValue);
        return noValue;
    }

    /**
     * Sends a no-value count summary to chat.
     *
     * @param ctx the command context
     */
    private static void sendNoValueSummary(CommandContext<CommandSourceStack> ctx) {
        List<Identifier> noValue = findItemsWithNoValue();
        if (noValue.isEmpty()) { return; }

        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_NO_VALUE_CHAT_PREFIX + noValue.size() + MSG_NO_VALUE_CHAT_SUFFIX)
                .withStyle(ChatFormatting.GRAY), false);
    }

}
