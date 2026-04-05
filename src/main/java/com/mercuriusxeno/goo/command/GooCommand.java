package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.data.ScaffoldGenerator;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Provides /goo commands for value lookup, regeneration, and validation.
 */
public class GooCommand {

    /** Registers all /goo subcommands with the dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("goo")
            .then(Commands.literal("lookup")
                .then(Commands.argument("item", StringArgumentType.string())
                    .suggests(GooCommand::suggestItems)
                    .executes(GooCommand::lookup)))
            .then(Commands.literal("reload")
                .requires(GooCommand::requiresOp)
                .executes(GooCommand::reload))
            .then(Commands.literal("regen")
                .requires(GooCommand::requiresOp)
                .executes(GooCommand::regen))
            .then(Commands.literal("audit")
                .requires(GooCommand::requiresOp)
                .executes(GooCommand::audit))
            .then(Commands.literal("scaffold")
                .requires(GooCommand::requiresOp)
                .then(Commands.literal("fresh")
                    .executes(GooCommand::scaffoldFresh)
                    .then(Commands.literal("bare")
                        .executes(GooCommand::scaffoldFreshBare)))
                .then(Commands.literal("missing")
                    .executes(GooCommand::scaffoldMissing)
                    .then(Commands.literal("bare")
                        .executes(GooCommand::scaffoldMissingBare))))
            .then(Commands.literal("init")
                .requires(GooCommand::requiresOp)
                .executes(GooCommand::init))
        );
    }

    /** Checks if the command source has operator permissions. */
    private static boolean requiresOp(CommandSourceStack s) {
        return s.permissions().hasPermission(
            new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS));
    }

    /** Looks up and displays the goo value of a specific item. */
    private static int lookup(CommandContext<CommandSourceStack> ctx) {
        String itemName = StringArgumentType.getString(ctx, "item");
        Identifier itemId = Identifier.parse(itemName);

        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal("Unknown item: " + itemName));
            return 0;
        }

        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                Component.literal(itemName + " has no goo value"), false);
            return 1;
        }

        MutableComponent msg = formatGooValue(itemName, value);
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /** Builds a chat component showing an item's goo value breakdown. */
    private static MutableComponent formatGooValue(String itemName, GooValue value) {
        MutableComponent msg = Component.literal(itemName + ": ");
        boolean first = true;
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            if (!first) msg.append(Component.literal(", "));
            msg.append(Component.literal(entry.getKey().getId())
                .withStyle(Style.EMPTY.withColor(entry.getKey().getColor())));
            msg.append(Component.literal(" x" + entry.getValue()));
            first = false;
        }
        msg.append(Component.literal(" (total: " + value.totalBlobs() + ")"));
        return msg;
    }

    /** Reloads effective values from cache, then syncs to all clients. */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Goo.GOO_VALUES.reload();
        GooValueSync.sendToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
            Component.literal("Reloaded " + Goo.GOO_VALUES.size() +
                " effective values from cache."), true);
        return 1;
    }

    /** Regenerates all derived values from recipes, then syncs to all clients. */
    private static int regen(CommandContext<CommandSourceStack> ctx) {
        Goo.GOO_VALUES.loadBaseValuesFromPacks(ctx.getSource().getServer());
        int derived = Goo.GOO_VALUES.deriveFromRecipes(ctx.getSource().getServer());
        Goo.GOO_VALUES.saveEffectiveValues();
        GooValueSync.sendToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
            Component.literal("Regenerated goo values. Derived " + derived +
                " new values from recipes. Total: " + Goo.GOO_VALUES.size()), true);
        return 1;
    }

    /** Creates a goo_overrides datapack in the current world for base value customization. */
    private static int init(CommandContext<CommandSourceStack> ctx) {
        Path datapacks = ctx.getSource().getServer().getWorldPath(LevelResource.DATAPACK_DIR);
        Path packRoot = datapacks.resolve("goo_overrides");
        Path valuesFile = packRoot.resolve("data/goo/goo_values/base_values.json");

        if (Files.exists(valuesFile)) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("Datapack already exists at " + packRoot)
                    .withStyle(ChatFormatting.YELLOW), false);
            ctx.getSource().sendSuccess(() ->
                Component.literal("Edit base_values.json, then /reload and /goo regen")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        try {
            Files.createDirectories(valuesFile.getParent());
            Files.writeString(packRoot.resolve("pack.mcmeta"), PACK_MCMETA, StandardCharsets.UTF_8);
            Files.writeString(valuesFile, STARTER_BASE_VALUES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Failed to create datapack: " + e.getMessage()));
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
            Component.literal("Created goo_overrides datapack at " + packRoot)
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() ->
            Component.literal("Edit base_values.json, then /reload and /goo regen")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

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

    /** Collects recipes fresh from the server and generates a full scaffold. */
    private static int scaffoldFresh(CommandContext<CommandSourceStack> ctx) {
        ScaffoldGenerator.ScaffoldResult result =
                Goo.GOO_VALUES.generateScaffoldFresh(ctx.getSource().getServer());
        return writeScaffoldAndReport(ctx, result, "fresh");
    }

    /** Fresh scaffold, bare mode: root keys only, no comments. */
    private static int scaffoldFreshBare(CommandContext<CommandSourceStack> ctx) {
        ScaffoldGenerator.ScaffoldResult result =
                Goo.GOO_VALUES.generateScaffoldFresh(ctx.getSource().getServer(), true);
        return writeScaffoldAndReport(ctx, result, "fresh bare");
    }

    /** Missing scaffold, bare mode: root keys only, no comments. */
    private static int scaffoldMissingBare(CommandContext<CommandSourceStack> ctx) {
        if (Goo.GOO_VALUES.derivedSize() == 0) {
            ctx.getSource().sendFailure(
                Component.literal("No cached recipes. Run /goo regen first, or use /goo scaffold fresh."));
            return 0;
        }
        ScaffoldGenerator.ScaffoldResult result = Goo.GOO_VALUES.generateScaffoldMissing(true);
        return writeScaffoldAndReport(ctx, result, "missing bare");
    }

    /** Generates scaffold from cached recipes, showing only still-missing roots. */
    private static int scaffoldMissing(CommandContext<CommandSourceStack> ctx) {
        if (Goo.GOO_VALUES.derivedSize() == 0) {
            ctx.getSource().sendFailure(
                Component.literal("No cached recipes. Run /goo regen first, or use /goo scaffold fresh."));
            return 0;
        }
        ScaffoldGenerator.ScaffoldResult result = Goo.GOO_VALUES.generateScaffoldMissing();
        return writeScaffoldAndReport(ctx, result, "missing");
    }

    /** Writes scaffold lines to disk and reports the result to chat. */
    private static int writeScaffoldAndReport(CommandContext<CommandSourceStack> ctx,
                                               ScaffoldGenerator.ScaffoldResult result,
                                               String mode) {
        writeDiagnosticFile("goo_scaffold.txt", result.lines());
        int rootCount = result.rootCount();
        ctx.getSource().sendSuccess(() ->
            Component.literal("Scaffold (" + mode + ") written to config/goo_scaffold.txt ("
                + rootCount + " root(s))").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** Runs all validators and writes a unified report to config/goo_audit.txt. */
    private static int audit(CommandContext<CommandSourceStack> ctx) {
        int issues = buildAndWriteReport();
        sendAuditResultToChat(ctx, issues);
        sendSectionSummaries(ctx);
        return 1;
    }

    /** Builds the full audit report, writes it to disk, and returns the issue count. */
    private static int buildAndWriteReport() {
        List<String> report = new ArrayList<>();
        report.add("Goo Audit Report");
        report.add("================");
        report.add("");

        int issues = 0;
        issues += appendExpressionWarningsSection(report);
        issues += appendPhantomIdsSection(report);
        issues += appendCyclesSection(report);
        issues += appendConflictsSection(report);
        issues += appendDivisibilitySection(report);
        appendNoValueSection(report);
        appendAllValuesSection(report);

        writeDiagnosticFile("goo_audit.txt", report);
        return issues;
    }

    /** Sends the pass/fail headline and file location to chat. */
    private static void sendAuditResultToChat(CommandContext<CommandSourceStack> ctx, int issues) {
        if (issues == 0) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("All checks passed. No issues found.")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            ctx.getSource().sendSuccess(() ->
                Component.literal(issues + " issue(s) found. See config/goo_audit.txt")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** Sends per-section summaries and the file location to chat. */
    private static void sendSectionSummaries(CommandContext<CommandSourceStack> ctx) {
        sendExpressionWarningsSummary(ctx);
        sendPhantomIdsSummary(ctx);
        sendCyclesSummary(ctx);
        sendConflictsSummary(ctx);
        sendDivisibilitySummary(ctx);
        sendNoValueSummary(ctx);

        ctx.getSource().sendSuccess(() ->
            Component.literal("Full report: config/goo_audit.txt")
                .withStyle(ChatFormatting.GRAY), false);
    }

    // --- Expression Warnings ---

    /** Appends expression validation warnings (bad constants, out-of-order refs). */
    private static int appendExpressionWarningsSection(List<String> report) {
        List<String> warnings = Goo.GOO_VALUES.validateBaseValues();

        report.add("EXPRESSION WARNINGS (" + warnings.size() + " issues in base_values.json expressions)");
        report.add("-".repeat(50));

        if (warnings.isEmpty()) {
            report.add("  All expressions are valid.");
        } else {
            for (String warning : warnings) {
                report.add("  " + warning);
            }
        }
        report.add("");
        return warnings.size();
    }

    /** Sends expression warnings summary to chat. */
    private static void sendExpressionWarningsSummary(CommandContext<CommandSourceStack> ctx) {
        List<String> warnings = Goo.GOO_VALUES.validateBaseValues();
        if (warnings.isEmpty()) return;
        ctx.getSource().sendSuccess(() ->
            Component.literal("  Expression warnings: " + warnings.size() + " issue(s)")
                .withStyle(ChatFormatting.YELLOW), false);
    }

    // --- Phantom IDs ---

    /** Finds identifiers in base_values.json that don't match any registered item. */
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

    /** Appends the phantom IDs section to the report, returns count of phantoms. */
    private static int appendPhantomIdsSection(List<String> report) {
        List<Identifier> phantoms = findPhantomIds();

        report.add("PHANTOM IDS (" + phantoms.size() + " identifiers in base_values.json don't match any registered item)");
        report.add("-".repeat(50));

        if (phantoms.isEmpty()) {
            report.add("  All identifiers are valid.");
        } else {
            for (Identifier id : phantoms) {
                String source = Goo.GOO_VALUES.isDenied(id) ? "(denied)" : "(valued)";
                report.add("  " + id + " " + source);
            }
        }
        report.add("");
        return phantoms.size();
    }

    /** Sends a phantom ID count summary to chat. */
    private static void sendPhantomIdsSummary(CommandContext<CommandSourceStack> ctx) {
        List<Identifier> phantoms = findPhantomIds();
        if (phantoms.isEmpty()) return;

        ctx.getSource().sendSuccess(() ->
            Component.literal("  Phantoms: " + phantoms.size() + " ID(s) in base_values.json don't exist in game")
                .withStyle(ChatFormatting.RED), false);
    }

    // --- Cycles ---

    /** Appends the cycles section to the report, returns count of dead cliques. */
    private static int appendCyclesSection(List<String> report) {
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.getLastCycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();

        report.add("CYCLES (" + cycles.size() + " total, " + deadCount + " dead)");
        report.add("-".repeat(50));

        if (cycles.isEmpty()) {
            report.add("  No recipe cycles detected.");
        } else {
            for (GooValueRegistry.RecipeCycle cycle : cycles) {
                report.add(formatCycleFileLine(cycle));
            }
        }
        report.add("");
        return (int) deadCount;
    }

    /** Sends a cycle count summary to chat. Only shown when unanchored cycles exist. */
    private static void sendCyclesSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.RecipeCycle> cycles = Goo.GOO_VALUES.getLastCycles();
        long deadCount = cycles.stream().filter(c -> !c.hasAnchor()).count();
        if (deadCount == 0) return;

        ctx.getSource().sendSuccess(() ->
            Component.literal("  Cycles: " + deadCount + " unanchored (need base values to resolve)")
                .withStyle(ChatFormatting.RED), false);
    }

    /** Formats a single cycle as a plain-text line. */
    private static String formatCycleFileLine(GooValueRegistry.RecipeCycle cycle) {
        String items = cycle.items().stream()
            .map(Identifier::toString)
            .collect(Collectors.joining(" -> "));

        if (cycle.hasAnchor()) {
            return "  [OK]   " + items + " (anchored by " + cycle.anchor() + ")";
        }
        return "  [DEAD] " + items + " (no base values - unresolvable)";
    }

    // --- Conflicts ---

    /** Appends the conflicts section to the report, returns count of recipe-cheaper conflicts. */
    private static int appendConflictsSection(List<String> report) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.getLastConflicts();
        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();

        report.add("CONFLICTS (" + conflicts.size() + " total, " + exploitCount + " recipe-cheaper)");
        report.add("-".repeat(50));

        if (conflicts.isEmpty()) {
            report.add("  No base/recipe value conflicts.");
        } else {
            for (GooValueRegistry.ValueConflict conflict : conflicts) {
                report.add(formatConflictFileLine(conflict));
            }
        }
        report.add("");
        return (int) exploitCount;
    }

    /** Sends a conflict count summary to chat. */
    private static void sendConflictsSummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.ValueConflict> conflicts = Goo.GOO_VALUES.getLastConflicts();
        if (conflicts.isEmpty()) return;

        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        ctx.getSource().sendSuccess(() ->
            Component.literal("  Conflicts: " + conflicts.size() + " (" + exploitCount + " recipe-cheaper)")
                .withStyle(exploitCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
    }

    /** Formats a single conflict as a plain-text line. */
    private static String formatConflictFileLine(GooValueRegistry.ValueConflict conflict) {
        String baseStr = conflict.baseValue().totalBlobs() + " blobs {" + conflict.baseValue() + "}";
        String recipeStr = conflict.recipeValue().totalBlobs() + " blobs {" + conflict.recipeValue() + "}";
        String direction = conflict.isRecipeCheaper() ? "<- RECIPE CHEAPER" : "<- BASE CHEAPER";
        return "  " + conflict.item() + " - base: " + baseStr + " vs recipe: " + recipeStr + " " + direction;
    }

    // --- Divisibility ---

    /** Appends the divisibility loss section to the report, returns count of lossy recipes. */
    private static int appendDivisibilitySection(List<String> report) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.getLastDivisibilityLosses();

        report.add("DIVISIBILITY LOSS (" + losses.size() + " recipes lose value to integer division)");
        report.add("-".repeat(50));

        if (losses.isEmpty()) {
            report.add("  No divisibility losses detected.");
        } else {
            for (GooValueRegistry.DivisibilityLoss loss : losses) {
                report.add(formatDivisibilityLine(loss));
            }
        }
        report.add("");
        return losses.size();
    }

    /** Sends a divisibility loss count summary to chat. */
    private static void sendDivisibilitySummary(CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.DivisibilityLoss> losses = Goo.GOO_VALUES.getLastDivisibilityLosses();
        if (losses.isEmpty()) return;

        ctx.getSource().sendSuccess(() ->
            Component.literal("  Divisibility: " + losses.size() + " recipe(s) lose blobs to integer division")
                .withStyle(ChatFormatting.YELLOW), false);
    }

    /** Formats a single divisibility loss with ingredient provenance. */
    private static String formatDivisibilityLine(GooValueRegistry.DivisibilityLoss loss) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(loss.output()).append(" - ")
            .append(loss.inputTotal()).append(" total / ")
            .append(loss.outputCount()).append(" items = ").append(loss.perItemValue())
            .append(" each (loses ").append(loss.lostBlobs()).append(" blob(s))");
        appendIngredientProvenance(sb, loss);
        return sb.toString();
    }

    /** Appends ingredient value sources to the divisibility loss line. */
    private static void appendIngredientProvenance(StringBuilder sb,
            GooValueRegistry.DivisibilityLoss loss) {
        for (var alternatives : loss.recipe().ingredientAlternatives()) {
            Identifier cheapest = findCheapestIngredientId(alternatives);
            if (cheapest == null) continue;
            sb.append("\n      ").append(cheapest);
            appendValueSource(sb, cheapest);
        }
    }

    /** Finds the cheapest valued item ID among alternatives. */
    private static Identifier findCheapestIngredientId(Set<Identifier> alternatives) {
        return GooValueRegistry.findCheapestAmong(alternatives, Goo.GOO_VALUES::lookup);
    }

    /** Appends " = {value} (base|derived)" for a single ingredient. */
    private static void appendValueSource(StringBuilder sb, Identifier itemId) {
        GooValue val = Goo.GOO_VALUES.lookup(itemId);
        if (val == null) {
            sb.append(" = no value");
            return;
        }
        sb.append(" = ").append(val.totalBlobs()).append(" {").append(val).append("}");
        sb.append(Goo.GOO_VALUES.hasBaseValue(itemId) ? " (base)" : " (derived)");
    }

    // --- All Values ---

    /** Appends a section listing every item's effective value and its source. */
    private static void appendAllValuesSection(List<String> report) {
        Map<Identifier, GooValue> effective = Goo.GOO_VALUES.getEffectiveValues();
        List<Identifier> sorted = new ArrayList<>(effective.keySet());
        Collections.sort(sorted);

        report.add("ALL VALUES (" + sorted.size() + " items)");
        report.add("-".repeat(50));

        for (Identifier id : sorted) {
            report.add(formatValueLine(id, effective.get(id)));
        }
        report.add("");
    }

    /** Formats a single item's value with its provenance (base/derived + source recipe). */
    private static String formatValueLine(Identifier itemId, GooValue value) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(itemId).append(" = ")
            .append(value.totalBlobs()).append(" {").append(value).append("}");
        appendProvenance(sb, itemId);
        return sb.toString();
    }

    /** Appends the provenance tag: (base), (derived), or (base+derived). */
    private static void appendProvenance(StringBuilder sb, Identifier itemId) {
        boolean hasBase = Goo.GOO_VALUES.hasBaseValue(itemId);
        RecipeInput source = Goo.GOO_VALUES.getDerivationSource(itemId);

        if (hasBase && source != null) {
            sb.append(" (base+derived from: ").append(formatSourceRecipe(source)).append(")");
        } else if (hasBase) {
            sb.append(" (base)");
        } else if (source != null) {
            sb.append(" (derived from: ").append(formatSourceRecipe(source)).append(")");
        }
    }

    /** Formats a source recipe as a compact ingredient list. */
    private static String formatSourceRecipe(RecipeInput recipe) {
        List<String> parts = new ArrayList<>();
        Map<Identifier, Integer> counts = countIngredients(recipe);
        for (var entry : counts.entrySet()) {
            String item = entry.getKey().toString();
            parts.add(entry.getValue() > 1 ? entry.getValue() + "x " + item : item);
        }
        String result = String.join(" + ", parts);
        if (recipe.resultCount() > 1) {
            result += " -> " + recipe.resultCount();
        }
        return result;
    }

    /** Counts how many times each cheapest ingredient appears in a recipe. */
    private static Map<Identifier, Integer> countIngredients(RecipeInput recipe) {
        Map<Identifier, Integer> counts = new java.util.LinkedHashMap<>();
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            Identifier cheapest = findCheapestIngredientId(alts);
            if (cheapest != null) {
                counts.merge(cheapest, 1, Integer::sum);
            }
        }
        return counts;
    }

    // --- No Value ---

    /** Appends a section listing all registered items with no effective goo value. */
    private static void appendNoValueSection(List<String> report) {
        List<Identifier> noValue = findItemsWithNoValue();

        report.add("NO VALUE (" + noValue.size() + " registered items have no goo value)");
        report.add("-".repeat(50));

        if (noValue.isEmpty()) {
            report.add("  All registered items have goo values.");
        } else {
            for (Identifier id : noValue) {
                report.add("\"" + id + "\",");
            }
        }
        report.add("");
    }

    /** Finds all registered items that have no effective goo value, excluding denied items. */
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

    /** Sends a no-value count summary to chat. */
    private static void sendNoValueSummary(CommandContext<CommandSourceStack> ctx) {
        List<Identifier> noValue = findItemsWithNoValue();
        if (noValue.isEmpty()) return;

        ctx.getSource().sendSuccess(() ->
            Component.literal("  No value: " + noValue.size() + " item(s) have no goo value")
                .withStyle(ChatFormatting.GRAY), false);
    }

    // --- Utilities ---

    /** Writes diagnostic lines to a file in the config directory. */
    private static void writeDiagnosticFile(String filename, List<String> lines) {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve(filename);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to write diagnostic file: {}", filename, e);
        }
    }

    /** Provides item ID suggestions for tab completion. */
    private static CompletableFuture<Suggestions> suggestItems(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
            BuiltInRegistries.ITEM.keySet(), builder);
    }

}
