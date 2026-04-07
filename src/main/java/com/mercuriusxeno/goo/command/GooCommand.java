package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.data.ScaffoldGenerator;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides /goo commands for value lookup, regeneration, and validation.
 */
public final class GooCommand {

    /** Width of section separator lines in the audit report. */
    private static final int SEPARATOR_WIDTH = 50;
    /** Initial capacity for divisibility-line StringBuilder. */
    private static final int DIVISIBILITY_LINE_CAPACITY = 48;

    // --- Command and argument names ---

    /** Root command name. */
    private static final String CMD_GOO = "goo";
    /** Subcommand name for item lookup. */
    private static final String CMD_LOOKUP = "lookup";
    /** Subcommand name for reload. */
    private static final String CMD_RELOAD = "reload";
    /** Subcommand name for regen. */
    private static final String CMD_REGEN = "regen";
    /** Subcommand name for audit. */
    private static final String CMD_AUDIT = "audit";
    /** Subcommand name for scaffold. */
    private static final String CMD_SCAFFOLD = "scaffold";
    /** Subcommand name for fresh scaffold mode. */
    private static final String CMD_FRESH = "fresh";
    /** Subcommand name for missing scaffold mode. */
    private static final String CMD_MISSING = "missing";
    /** Subcommand name for bare scaffold variant. */
    private static final String CMD_BARE = "bare";
    /** Subcommand name for init. */
    private static final String CMD_INIT = "init";
    /** Command argument name for item. */
    private static final String ARG_ITEM = "item";

    // --- User-facing messages ---

    /** Message prefix for unknown item lookup. */
    private static final String MSG_UNKNOWN_ITEM = "Unknown item: ";
    /** Message suffix when an item has no goo value. */
    private static final String MSG_NO_GOO_VALUE = " has no goo value";
    /** Separator between goo types in value display. */
    private static final String MSG_COMMA = ", ";
    /** Prefix for value multiplier display. */
    private static final String MSG_TIMES = " x";
    /** Suffix showing total blob count. */
    private static final String MSG_TOTAL_PREFIX = " (total: ";
    /** Colon-space separator after item name. */
    private static final String MSG_COLON_SPACE = ": ";
    /** Closing parenthesis. */
    private static final String MSG_CLOSE_PAREN = ")";
    /** Reload success message prefix. */
    private static final String MSG_RELOADED = "Reloaded ";
    /** Reload success message suffix. */
    private static final String MSG_EFFECTIVE_CACHE = " effective values from cache.";
    /** Regen success message prefix. */
    private static final String MSG_REGEN_PREFIX = "Regenerated goo values. Derived ";
    /** Regen success message mid-section. */
    private static final String MSG_REGEN_MID = " new values from recipes. Total: ";
    /** Message when datapack already exists. */
    private static final String MSG_PACK_EXISTS = "Datapack already exists at ";
    /** Instruction to edit and regen. */
    private static final String MSG_EDIT_REGEN = "Edit base_values.json, then /reload and /goo regen";
    /** Message when datapack created. */
    private static final String MSG_PACK_CREATED = "Created goo_overrides datapack at ";
    /** Message when datapack creation fails. */
    private static final String MSG_PACK_FAIL = "Failed to create datapack: ";
    /** Message when no cached recipes available. */
    private static final String MSG_NO_CACHED = "No cached recipes. Run /goo regen first, or use /goo scaffold fresh.";
    /** Scaffold file output path. */
    private static final String FILE_SCAFFOLD = "goo_scaffold.txt";
    /** Scaffold mode label prefix. */
    private static final String MSG_SCAFFOLD_PREFIX = "Scaffold (";
    /** Scaffold success message middle. */
    private static final String MSG_SCAFFOLD_MID = ") written to config/goo_scaffold.txt (";
    /** Scaffold success message suffix. */
    private static final String MSG_SCAFFOLD_SUFFIX = " root(s))";
    /** Scaffold mode label: fresh. */
    private static final String MODE_FRESH = "fresh";
    /** Scaffold mode label: fresh bare. */
    private static final String MODE_FRESH_BARE = "fresh bare";
    /** Scaffold mode label: missing bare. */
    private static final String MODE_MISSING_BARE = "missing bare";
    /** Scaffold mode label: missing. */
    private static final String MODE_MISSING = "missing";

    // --- Audit report strings ---

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

    // --- Report section headers and body text ---

    /** Expression warnings section header prefix. */
    private static final String HDR_EXPR_PREFIX = "EXPRESSION WARNINGS (";
    /** Expression warnings section header suffix. */
    private static final String HDR_EXPR_SUFFIX = " issues in base_values.json expressions)";
    /** All expressions valid message. */
    private static final String MSG_EXPR_VALID = "  All expressions are valid.";
    /** Indent prefix for report lines. */
    private static final String INDENT = "  ";
    /** Expression warnings chat prefix. */
    private static final String MSG_EXPR_CHAT_PREFIX = "  Expression warnings: ";
    /** Expression warnings chat suffix. */
    private static final String MSG_EXPR_CHAT_SUFFIX = " issue(s)";
    /** Phantom IDs section header prefix. */
    private static final String HDR_PHANTOM_PREFIX = "PHANTOM IDS (";
    /** Phantom IDs section header suffix. */
    private static final String HDR_PHANTOM_SUFFIX = " identifiers in base_values.json don't match any registered item)";
    /** All identifiers valid message. */
    private static final String MSG_IDS_VALID = "  All identifiers are valid.";
    /** Denied source label. */
    private static final String LABEL_DENIED = "(denied)";
    /** Valued source label. */
    private static final String LABEL_VALUED = "(valued)";
    /** Phantom IDs chat prefix. */
    private static final String MSG_PHANTOM_CHAT_PREFIX = "  Phantoms: ";
    /** Phantom IDs chat suffix. */
    private static final String MSG_PHANTOM_CHAT_SUFFIX = " ID(s) in base_values.json don't exist in game";
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
    /** Log message for diagnostic file write failure. */
    private static final String LOG_WRITE_FAIL = "Failed to write diagnostic file: {}";
    /** Empty line separator in report output. */
    private static final String EMPTY_LINE = "";
    /** Datapack directory name. */
    private static final String PACK_DIR = "goo_overrides";
    /** Base values file path within datapack. */
    private static final String BASE_VALUES_PATH = "data/goo/goo_values/base_values.json";
    /** Pack mcmeta filename. */
    private static final String PACK_MCMETA_FILE = "pack.mcmeta";
    /** Dash character for section separator lines. */
    private static final String SEPARATOR_CHAR = "-";
    /** Hyphen separator between item and detail. */
    private static final String SEP_DASH = " - ";
    /** Single space separator. */
    private static final String SEP_SPACE = " ";
    private static final String PACK_MCMETA = """
            {
                "pack": {
                    "description": "Goo value overrides",
                    "pack_format": 61
                }
            }
            """;

    private static final String STARTER_BASE_VALUES = """
            {
                "_constants": {
                },

                "_groups": {
                }
            }
            """;

    private GooCommand() {}

    /**
     * Registers all /goo subcommands with the dispatcher.
     *
     * @param dispatcher the server command dispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(CMD_GOO)
            .then(lookupSubcommand())
            .then(opSubcommand(CMD_RELOAD, GooCommand::reload))
            .then(opSubcommand(CMD_REGEN, GooCommand::regen))
            .then(opSubcommand(CMD_AUDIT, GooCommand::audit))
            .then(scaffoldSubcommand())
            .then(opSubcommand(CMD_INIT, GooCommand::init)));
    }

    /** Builds the /goo lookup subcommand with item argument and suggestions.
     *
     * @return the lookup argument builder
     */
    private static ArgumentBuilder<CommandSourceStack, ?> lookupSubcommand() {
        return Commands.literal(CMD_LOOKUP)
            .then(Commands.argument(ARG_ITEM, StringArgumentType.string())
                .suggests(GooCommand::suggestItems)
                .executes(GooCommand::lookup));
    }

    /** Builds an operator-only subcommand with a single execute handler.
     *
     * @param name    the subcommand literal name
     * @param handler the command execution handler
     * @return the argument builder
     */
    private static ArgumentBuilder<CommandSourceStack, ?> opSubcommand(
            String name, Command<CommandSourceStack> handler) {
        return Commands.literal(name).requires(GooCommand::requiresOp).executes(handler);
    }

    /** Builds the /goo scaffold subcommand tree with fresh/missing and bare variants.
     *
     * @return the scaffold argument builder
     */
    private static ArgumentBuilder<CommandSourceStack, ?> scaffoldSubcommand() {
        return Commands.literal(CMD_SCAFFOLD)
            .requires(GooCommand::requiresOp)
            .then(Commands.literal(CMD_FRESH)
                .executes(GooCommand::scaffoldFresh)
                .then(Commands.literal(CMD_BARE).executes(GooCommand::scaffoldFreshBare)))
            .then(Commands.literal(CMD_MISSING)
                .executes(GooCommand::scaffoldMissing)
                .then(Commands.literal(CMD_BARE).executes(GooCommand::scaffoldMissingBare)));
    }

    /**
     * Checks if the command source has operator permissions.
     *
     * @param s the command source stack
     * @return true if the source has operator level
     */
    private static boolean requiresOp(CommandSourceStack s) {
        return s.permissions().hasPermission(
            new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS));
    }

    /**
     * Looks up and displays the goo value of a specific item.
     *
     * @param ctx the command context
     * @return 1 on success, 0 on failure
     */
    private static int lookup(CommandContext<CommandSourceStack> ctx) {
        String itemName = StringArgumentType.getString(ctx, ARG_ITEM);
        Identifier itemId = Identifier.parse(itemName);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal(MSG_UNKNOWN_ITEM + itemName));
            return 0;
        }
        return sendLookupResult(ctx, itemName, itemId);
    }

    /** Resolves and sends the goo value for a validated item ID.
     *
     * @param ctx      the command context
     * @param itemName the item name string
     * @param itemId   the parsed item identifier
     * @return 1 (success)
     */
    private static int sendLookupResult(CommandContext<CommandSourceStack> ctx,
                                         String itemName, Identifier itemId) {
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(itemName + MSG_NO_GOO_VALUE), false);
        } else {
            MutableComponent msg = formatGooValue(itemName, value);
            ctx.getSource().sendSuccess(() -> msg, false);
        }
        return 1;
    }

    /**
     * Builds a chat component showing an item's goo value breakdown.
     *
     * @param itemName the item identifier string
     * @param value    the item's goo value
     * @return the formatted chat component
     */
    private static MutableComponent formatGooValue(String itemName, GooValue value) {
        MutableComponent msg = Component.literal(itemName + MSG_COLON_SPACE);
        boolean first = true;
        for (var entry : value.getAll().entrySet()) {
            if (!first) { msg.append(Component.literal(MSG_COMMA)); }
            appendTypeEntry(msg, entry.getKey(), entry.getValue());
            first = false;
        }
        msg.append(Component.literal(MSG_TOTAL_PREFIX + value.totalBlobs() + MSG_CLOSE_PAREN));
        return msg;
    }

    /** Appends a colored type name and amount to a chat component.
     *
     * @param msg    the component to append to
     * @param type   the goo type
     * @param amount the goo amount
     */
    private static void appendTypeEntry(MutableComponent msg, GooType type, int amount) {
        msg.append(Component.literal(type.getId()).withStyle(Style.EMPTY.withColor(type.getColor())));
        msg.append(Component.literal(MSG_TIMES + amount));
    }

    /**
     * Reloads effective values from cache, then syncs to all clients.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Goo.GOO_VALUES.reload();
        GooValueSync.sendToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_RELOADED + Goo.GOO_VALUES.size() +
                MSG_EFFECTIVE_CACHE), true);
        return 1;
    }

    /**
     * Regenerates all derived values from recipes, then syncs to all clients.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int regen(CommandContext<CommandSourceStack> ctx) {
        Goo.GOO_VALUES.loadBaseValuesFromPacks(ctx.getSource().getServer());
        int derived = Goo.GOO_VALUES.deriveFromRecipes(ctx.getSource().getServer());
        Goo.GOO_VALUES.saveEffectiveValues();
        GooValueSync.sendToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_REGEN_PREFIX + derived +
                MSG_REGEN_MID + Goo.GOO_VALUES.size()), true);
        return 1;
    }

    /**
     * Creates a goo_overrides datapack in the current world for base value customization.
     *
     * @param ctx the command context
     * @return 1 on success, 0 on failure
     */
    private static int init(CommandContext<CommandSourceStack> ctx) {
        Path datapacks = ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        Path packRoot = datapacks.resolve(PACK_DIR);
        Path valuesFile = packRoot.resolve(BASE_VALUES_PATH);
        if (Files.exists(valuesFile)) {
            return reportPackExists(ctx, packRoot);
        }
        return createPack(ctx, packRoot, valuesFile);
    }

    /** Reports that the pack already exists and suggests editing.
     *
     * @param ctx      the command context
     * @param packRoot the pack root directory
     * @return 1
     */
    private static int reportPackExists(CommandContext<CommandSourceStack> ctx, Path packRoot) {
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_PACK_EXISTS + packRoot).withStyle(ChatFormatting.YELLOW), false);
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_EDIT_REGEN).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Creates the datapack directory structure and starter files.
     *
     * @param ctx        the command context
     * @param packRoot   the pack root directory
     * @param valuesFile the base values file path
     * @return 1 on success, 0 on failure
     */
    private static int createPack(CommandContext<CommandSourceStack> ctx,
                                   Path packRoot, Path valuesFile) {
        try {
            Files.createDirectories(valuesFile.getParent());
            Files.writeString(packRoot.resolve(PACK_MCMETA_FILE), PACK_MCMETA, StandardCharsets.UTF_8);
            Files.writeString(valuesFile, STARTER_BASE_VALUES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal(MSG_PACK_FAIL + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_PACK_CREATED + packRoot).withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_EDIT_REGEN).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Collects recipes fresh from the server and generates a full scaffold.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int scaffoldFresh(CommandContext<CommandSourceStack> ctx) {
        ScaffoldGenerator.ScaffoldResult result =
                Goo.GOO_VALUES.generateScaffoldFresh(ctx.getSource().getServer());
        return writeScaffoldAndReport(ctx, result, MODE_FRESH);
    }

    /**
     * Fresh scaffold, bare mode: root keys only, no comments.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int scaffoldFreshBare(CommandContext<CommandSourceStack> ctx) {
        ScaffoldGenerator.ScaffoldResult result =
                Goo.GOO_VALUES.generateScaffoldFresh(ctx.getSource().getServer(), true);
        return writeScaffoldAndReport(ctx, result, MODE_FRESH_BARE);
    }

    /**
     * Missing scaffold, bare mode: root keys only, no comments.
     *
     * @param ctx the command context
     * @return 1 on success, 0 if no cached recipes
     */
    private static int scaffoldMissingBare(CommandContext<CommandSourceStack> ctx) {
        if (Goo.GOO_VALUES.derivedSize() == 0) {
            ctx.getSource().sendFailure(
                Component.literal(MSG_NO_CACHED));
            return 0;
        }
        ScaffoldGenerator.ScaffoldResult result = Goo.GOO_VALUES.generateScaffoldMissing(true);
        return writeScaffoldAndReport(ctx, result, MODE_MISSING_BARE);
    }

    /**
     * Generates scaffold from cached recipes, showing only still-missing roots.
     *
     * @param ctx the command context
     * @return 1 on success, 0 if no cached recipes
     */
    private static int scaffoldMissing(CommandContext<CommandSourceStack> ctx) {
        if (Goo.GOO_VALUES.derivedSize() == 0) {
            ctx.getSource().sendFailure(
                Component.literal(MSG_NO_CACHED));
            return 0;
        }
        ScaffoldGenerator.ScaffoldResult result = Goo.GOO_VALUES.generateScaffoldMissing();
        return writeScaffoldAndReport(ctx, result, MODE_MISSING);
    }

    /**
     * Writes scaffold lines to disk and reports the result to chat.
     *
     * @param ctx    the command context
     * @param result the scaffold generation result
     * @param mode   the scaffold mode label for display
     * @return 1 on success
     */
    private static int writeScaffoldAndReport(CommandContext<CommandSourceStack> ctx,
                                               ScaffoldGenerator.ScaffoldResult result,
                                               String mode) {
        writeDiagnosticFile(FILE_SCAFFOLD, result.lines());
        int rootCount = result.rootCount();
        ctx.getSource().sendSuccess(() ->
            Component.literal(MSG_SCAFFOLD_PREFIX + mode + MSG_SCAFFOLD_MID
                + rootCount + MSG_SCAFFOLD_SUFFIX).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Runs all validators and writes a unified report to config/goo_audit.txt.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int audit(CommandContext<CommandSourceStack> ctx) {
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
        appendAllValuesSection(report);
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
        issues += appendConflictsSection(report);
        issues += appendDivisibilitySection(report);
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
        sendConflictsSummary(ctx);
        sendDivisibilitySummary(ctx);
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
    private static <T> void appendSectionBody(List<String> report, String header,
            String emptyMsg, List<T> items, Function<T, String> formatter) {
        report.add(header);
        report.add(SEPARATOR_CHAR.repeat(SEPARATOR_WIDTH));
        if (items.isEmpty()) { report.add(emptyMsg); }
        else { items.forEach(item -> report.add(formatter.apply(item))); }
        report.add(EMPTY_LINE);
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
        for (Identifier id : Goo.GOO_VALUES.getAllReferencedIds()) {
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
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.getLastCycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        appendSectionBody(report,
                HDR_CYCLES_PREFIX + cycles.size() + HDR_CYCLES_MID + deadCount + HDR_CYCLES_SUFFIX,
                MSG_NO_CYCLES, cycles, GooCommand::formatCycleFileLine);
        return (int) deadCount;
    }

    /**
     * Sends a cycle count summary to chat. Only shown when unanchored cycles exist.
     *
     * @param ctx the command context
     */
    private static void sendCyclesSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.getLastCycles();
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

    // --- Conflicts ---

    /**
     * Appends the conflicts section to the report, returns count of recipe-cheaper conflicts.
     *
     * @param report the report lines list to append to
     * @return the number of recipe-cheaper conflicts
     */
    private static int appendConflictsSection(List<String> report) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.getLastConflicts();
        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        appendSectionBody(report,
                HDR_CONFLICTS_PREFIX + conflicts.size() + HDR_CONFLICTS_MID + exploitCount + HDR_CONFLICTS_SUFFIX,
                MSG_NO_CONFLICTS, conflicts, GooCommand::formatConflictFileLine);
        return (int) exploitCount;
    }

    /**
     * Sends a conflict count summary to chat.
     *
     * @param ctx the command context
     */
    private static void sendConflictsSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.getLastConflicts();
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
        return INDENT + conflict.item() + CONFLICT_BASE + baseStr + CONFLICT_VS + recipeStr + SEP_SPACE + direction;
    }

    // --- Divisibility ---

    /**
     * Appends the divisibility loss section to the report, returns count of lossy recipes.
     *
     * @param report the report lines list to append to
     * @return the number of lossy recipes
     */
    private static int appendDivisibilitySection(List<String> report) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.getLastDivisibilityLosses();
        appendSectionBody(report, HDR_DIV_PREFIX + losses.size() + HDR_DIV_SUFFIX,
                MSG_NO_DIV, losses, GooCommand::formatDivisibilityLine);
        return losses.size();
    }

    /**
     * Sends a divisibility loss count summary to chat.
     *
     * @param ctx the command context
     */
    private static void sendDivisibilitySummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.getLastDivisibilityLosses();
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
        sb.append(INDENT).append(loss.output()).append(SEP_DASH)
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
        return GooValueRegistry.findCheapestAmong(alternatives, Goo.GOO_VALUES::lookup);
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
        sb.append(LABEL_EQ_VALUE).append(val.totalBlobs()).append(LABEL_OPEN_BRACE).append(val).append(LABEL_BLOBS_CLOSE);
        sb.append(Goo.GOO_VALUES.hasBaseValue(itemId) ? LABEL_BASE : LABEL_DERIVED);
    }

    // --- All Values ---

    /**
     * Appends a section listing every item's effective value and its source.
     *
     * @param report the report lines list to append to
     */
    private static void appendAllValuesSection(List<String> report) {
        Map<Identifier, GooValue> effective = Goo.GOO_VALUES.getEffectiveValues();
        List<Identifier> sorted = new ArrayList<>(effective.keySet());
        Collections.sort(sorted);
        appendSectionBody(report, HDR_ALL_PREFIX + sorted.size() + HDR_ALL_SUFFIX,
                EMPTY_LINE, sorted, id -> formatValueLine(id, effective.get(id)));
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
        sb.append(INDENT).append(itemId).append(LABEL_EQ_VALUE)
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
        RecipeInput source = Goo.GOO_VALUES.getDerivationSource(itemId);
        if (hasBase && source != null) {
            sb.append(PROV_BASE_DERIVED).append(formatSourceRecipe(source)).append(MSG_CLOSE_PAREN);
        } else if (hasBase) {
            sb.append(LABEL_BASE);
        } else if (source != null) {
            sb.append(PROV_DERIVED).append(formatSourceRecipe(source)).append(MSG_CLOSE_PAREN);
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

    // --- Utilities ---

    /**
     * Writes diagnostic lines to a file in the config directory.
     *
     * @param filename the output filename
     * @param lines    the lines to write
     */
    private static void writeDiagnosticFile(String filename, List<String> lines) {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(filename);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error(LOG_WRITE_FAIL, filename, e);
        }
    }

    /**
     * Provides item ID suggestions for tab completion.
     *
     * @param ctx     the command context
     * @param builder the suggestions builder
     * @return the suggestions future
     */
    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
            BuiltInRegistries.ITEM.keySet(), builder);
    }

}
