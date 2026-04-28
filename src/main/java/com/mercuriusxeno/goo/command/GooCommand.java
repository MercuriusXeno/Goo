package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Provides /goo commands for value lookup, regeneration, and validation.
 */
public final class GooCommand {

    // --- Command and argument names ---

    /**
     * Root command name.
     */
    private static final String CMD_GOO = "goo";
    /**
     * Subcommand name for item lookup.
     */
    private static final String CMD_LOOKUP = "lookup";
    /**
     * Subcommand name for reload.
     */
    private static final String CMD_RELOAD = "reload";
    /**
     * Subcommand name for regen.
     */
    private static final String CMD_REGEN = "regen";
    /**
     * Subcommand name for audit.
     */
    private static final String CMD_AUDIT = "audit";
    /**
     * Subcommand name for scaffold.
     */
    private static final String CMD_SCAFFOLD = "scaffold";
    /**
     * Subcommand name for fresh scaffold mode.
     */
    private static final String CMD_FRESH = "fresh";
    /**
     * Subcommand name for missing scaffold mode.
     */
    private static final String CMD_MISSING = "missing";
    /**
     * Subcommand name for bare scaffold variant.
     */
    private static final String CMD_BARE = "bare";
    /**
     * Subcommand name for init.
     */
    private static final String CMD_INIT = "init";
    /**
     * Command argument name for item.
     */
    private static final String ARG_ITEM = "item";

    // --- User-facing messages ---

    /**
     * Message prefix for unknown item lookup.
     */
    private static final String MSG_UNKNOWN_ITEM = "Unknown item: ";
    /**
     * Message suffix when an item has no goo value.
     */
    private static final String MSG_NO_GOO_VALUE = " has no goo value";
    /**
     * Separator between goo types in value display.
     */
    private static final String MSG_COMMA = ", ";
    /**
     * Prefix for value multiplier display.
     */
    private static final String MSG_TIMES = " x";
    /**
     * Suffix showing total blob count.
     */
    private static final String MSG_TOTAL_PREFIX = " (total: ";
    /**
     * Colon-space separator after item name.
     */
    private static final String MSG_COLON_SPACE = ": ";
    /**
     * Closing parenthesis.
     */
    private static final String MSG_CLOSE_PAREN = ")";
    /**
     * Reload success message prefix.
     */
    private static final String MSG_RELOADED = "Reloaded ";
    /**
     * Reload success message suffix.
     */
    private static final String MSG_EFFECTIVE_CACHE = " effective values from cache.";
    /**
     * Regen success message prefix.
     */
    private static final String MSG_REGEN_PREFIX = "Regenerated goo values. Derived ";
    /**
     * Regen success message mid-section.
     */
    private static final String MSG_REGEN_MID = " new values from recipes. Total: ";
    /**
     * Message when datapack already exists.
     */
    private static final String MSG_PACK_EXISTS = "Datapack already exists at ";
    /**
     * Instruction to edit and regen.
     */
    private static final String MSG_EDIT_REGEN = "Edit base_values.json, then /reload and /goo regen";
    /**
     * Message when datapack created.
     */
    private static final String MSG_PACK_CREATED = "Created goo_overrides datapack at ";
    /**
     * Message when datapack creation fails.
     */
    private static final String MSG_PACK_FAIL = "Failed to create datapack: ";
    /**
     * Message when no cached recipes available.
     */
    private static final String MSG_NO_CACHED = "No cached recipes. Run /goo regen first, or use /goo scaffold fresh.";
    /**
     * Scaffold file output path.
     */
    private static final String FILE_SCAFFOLD = "goo_scaffold.txt";
    /**
     * Scaffold mode label prefix.
     */
    private static final String MSG_SCAFFOLD_PREFIX = "Scaffold (";
    /**
     * Scaffold success message middle.
     */
    private static final String MSG_SCAFFOLD_MID = ") written to config/goo_scaffold.txt (";
    /**
     * Scaffold success message suffix.
     */
    private static final String MSG_SCAFFOLD_SUFFIX = " root(s))";
    /**
     * Scaffold mode label: fresh.
     */
    private static final String MODE_FRESH = "fresh";
    /**
     * Scaffold mode label: fresh bare.
     */
    private static final String MODE_FRESH_BARE = "fresh bare";
    /**
     * Scaffold mode label: missing bare.
     */
    private static final String MODE_MISSING_BARE = "missing bare";
    /**
     * Scaffold mode label: missing.
     */
    private static final String MODE_MISSING = "missing";

    /**
     * Orphan scan: prefix for per-orphan report line.
     */
    private static final String MSG_ORPHAN_PREFIX = "Orphan: ";
    /**
     * Orphan scan: detail fragment separating type from position.
     */
    private static final String MSG_ORPHAN_AT = " at ";
    /**
     * Orphan scan: label for the block type at the position.
     */
    private static final String MSG_ORPHAN_BLOCK = " (block: ";
    /**
     * Orphan scan: message when no orphans are found.
     */
    private static final String MSG_NO_ORPHANS = "No orphaned block entities in chunk ";
    /**
     * Orphan scan: verb when the fix flag is set and orphans are removed.
     */
    private static final String MSG_ORPHAN_REMOVED = "Removed";
    /**
     * Orphan scan: verb when the fix flag is not set and orphans are reported.
     */
    private static final String MSG_ORPHAN_FOUND = "Found";
    /**
     * Orphan scan: middle fragment in the summary message.
     */
    private static final String MSG_ORPHAN_IN_CHUNK = " orphan(s) in chunk ";
    /**
     * Orphan scan: suffix appended when fix is false.
     */
    private static final String MSG_ORPHAN_FIX_HINT = " (use /goo orphans fix to remove)";
    /**
     * Orphan scan: space separator in summary.
     */
    private static final String MSG_SPACE = " ";
    /**
     * Orphan scan: empty string for no-suffix case.
     */
    private static final String MSG_EMPTY = "";
    /**
     * Subcommand name for orphan scanning.
     */
    private static final String CMD_ORPHANS = "orphans";
    /**
     * Subcommand name for orphan fix mode.
     */
    private static final String CMD_FIX = "fix";

    /**
     * Bit-shift used to convert a world coordinate to chunk coordinate.
     */
    private static final int CHUNK_COORD_SHIFT = 4;

    /**
     * Block-update flags passed to sendBlockUpdated: notify clients and neighbors.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    /**
     * Datapack directory name.
     */
    private static final String PACK_DIR = "goo_overrides";
    /**
     * Base values file path within datapack.
     */
    private static final String BASE_VALUES_PATH = "data/goo/goo_values/base_values.json";
    /**
     * Pack mcmeta filename.
     */
    private static final String PACK_MCMETA_FILE = "pack.mcmeta";
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

    private GooCommand() {
    }

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
                .then(opSubcommand(CMD_AUDIT, GooAuditReport::run))
                .then(scaffoldSubcommand())
                .then(opSubcommand(CMD_INIT, GooCommand::init))
                .then(Commands.literal(CMD_ORPHANS).requires(GooCommand::requiresOp)
                        .executes(ctx -> scanOrphans(ctx, false))
                        .then(Commands.literal(CMD_FIX).executes(ctx -> scanOrphans(ctx, true)))));
    }

    /**
     * Builds the /goo lookup subcommand with item argument and suggestions.
     *
     * @return the lookup argument builder
     */
    private static ArgumentBuilder<CommandSourceStack, ?> lookupSubcommand() {
        return Commands.literal(CMD_LOOKUP)
                .then(Commands.argument(ARG_ITEM, StringArgumentType.string())
                        .suggests(GooCommand::suggestItems)
                        .executes(GooCommand::lookup));
    }

    /**
     * Builds an operator-only subcommand with a single execute handler.
     *
     * @param name    the subcommand literal name
     * @param handler the command execution handler
     * @return the argument builder
     */
    private static ArgumentBuilder<CommandSourceStack, ?> opSubcommand(
            String name, Command<CommandSourceStack> handler) {
        return Commands.literal(name).requires(GooCommand::requiresOp).executes(handler);
    }

    /**
     * Builds the /goo scaffold subcommand tree with fresh/missing and bare variants.
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

    /**
     * Resolves and sends the goo value for a validated item ID.
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
            if (!first) {
                msg.append(Component.literal(MSG_COMMA));
            }
            appendTypeEntry(msg, entry.getKey(), entry.getValue());
            first = false;
        }
        msg.append(Component.literal(MSG_TOTAL_PREFIX + value.totalBlobs() + MSG_CLOSE_PAREN));
        return msg;
    }

    /**
     * Appends a colored type name and amount to a chat component.
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
        Goo.GOO_VALUES.loadEffectiveCache();
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

    /**
     * Reports that the pack already exists and suggests editing.
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

    /**
     * Creates the datapack directory structure and starter files.
     *
     * @param ctx        the command context
     * @param packRoot   the pack root directory
     * @param valuesFile the base values file path
     * @return 1 on success, 0 on failure
     */
    private static int createPack(CommandContext<CommandSourceStack> ctx,
                                  Path packRoot, Path valuesFile) {
        try {
            writePackFiles(packRoot, valuesFile);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal(MSG_PACK_FAIL + e.getMessage()));
            return 0;
        }
        sendPackCreatedFeedback(ctx, packRoot);
        return 1;
    }

    /**
     * Writes the pack.mcmeta and starter base-values files to disk.
     *
     * @param packRoot   the datapack root directory to create files in
     * @param valuesFile the destination path for the starter base_values.json
     */
    private static void writePackFiles(Path packRoot, Path valuesFile) throws IOException {
        Files.createDirectories(valuesFile.getParent());
        Files.writeString(packRoot.resolve(PACK_MCMETA_FILE), PACK_MCMETA, StandardCharsets.UTF_8);
        Files.writeString(valuesFile, STARTER_BASE_VALUES, StandardCharsets.UTF_8);
    }

    /**
     * Sends success messages after pack creation.
     *
     * @param ctx      the command context for sending chat feedback
     * @param packRoot the datapack root directory to report in the message
     */
    private static void sendPackCreatedFeedback(CommandContext<CommandSourceStack> ctx, Path packRoot) {
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_PACK_CREATED + packRoot).withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_EDIT_REGEN).withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * Collects recipes fresh from the server and generates a full scaffold.
     *
     * @param ctx the command context
     * @return 1 on success
     */
    private static int scaffoldFresh(CommandContext<CommandSourceStack> ctx) {
        ScaffoldGenerator.ScaffoldResult result =
                Goo.GOO_VALUES.generateScaffoldFresh(ctx.getSource().getServer(), false);
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
        if (Goo.GOO_VALUES.diagnostics().derivedSize() == 0) {
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
        if (Goo.GOO_VALUES.diagnostics().derivedSize() == 0) {
            ctx.getSource().sendFailure(
                    Component.literal(MSG_NO_CACHED));
            return 0;
        }
        ScaffoldGenerator.ScaffoldResult result = Goo.GOO_VALUES.generateScaffoldMissing(false);
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
        GooAuditReport.writeDiagnosticFile(FILE_SCAFFOLD, result.lines());
        int rootCount = result.rootCount();
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_SCAFFOLD_PREFIX + mode + MSG_SCAFFOLD_MID
                        + rootCount + MSG_SCAFFOLD_SUFFIX).withStyle(ChatFormatting.GREEN), false);
        return 1;
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

    /**
     * Scans the chunk the player is standing in for orphaned block entities
     * (block entities at positions where the block doesn't expect one).
     * Reports and removes any found.
     *
     * @param ctx the command context
     * @param fix true to remove orphans, false to only report them
     * @return 1 on success
     */
    private static int scanOrphans(CommandContext<CommandSourceStack> ctx, boolean fix) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos playerPos = BlockPos.containing(ctx.getSource().getPosition());
        ChunkPos cp = new ChunkPos(
                playerPos.getX() >> CHUNK_COORD_SHIFT, playerPos.getZ() >> CHUNK_COORD_SHIFT);
        LevelChunk chunk = level.getChunk(cp.x(), cp.z());
        int total = removeOrReportOrphans(ctx, level, chunk, fix);
        sendOrphanSummary(ctx, cp, total, fix);
        return 1;
    }

    /**
     * Iterates block entities in the chunk, reporting each orphan and optionally removing it.
     *
     * @param ctx   the command context for sending per-orphan messages
     * @param level the server level
     * @param chunk the chunk to scan
     * @param fix   true to remove orphans
     * @return the number of orphans found
     */
    private static int removeOrReportOrphans(CommandContext<CommandSourceStack> ctx,
                                             ServerLevel level, LevelChunk chunk, boolean fix) {
        int count = 0;
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            BlockPos bePos = be.getBlockPos();
            BlockState state = level.getBlockState(bePos);
            if (!state.hasBlockEntity()) {
                reportOrphan(ctx, be, bePos, state);
                if (fix) {
                    chunk.removeBlockEntity(bePos);
                    level.sendBlockUpdated(bePos, state, state, BLOCK_UPDATE_FLAGS);
                }
                count++;
            }
        }
        return count;
    }

    /**
     * Sends a red chat message describing one orphaned block entity.
     *
     * @param ctx   the command context
     * @param be    the orphaned block entity
     * @param bePos the block entity position
     * @param state the block state at that position
     */
    private static void reportOrphan(CommandContext<CommandSourceStack> ctx,
                                     BlockEntity be, BlockPos bePos, BlockState state) {
        String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
        ctx.getSource().sendSuccess(() -> Component.literal(
                        MSG_ORPHAN_PREFIX + type + MSG_ORPHAN_AT + bePos
                                + MSG_ORPHAN_BLOCK + state.getBlock().getName().getString() + MSG_CLOSE_PAREN)
                .withStyle(ChatFormatting.RED), false);
    }

    /**
     * Sends the summary line after scanning: green if none found, yellow if any found.
     *
     * @param ctx   the command context
     * @param cp    the chunk position
     * @param total the number of orphans found
     * @param fix   true if the scan was run in fix mode
     */
    private static void sendOrphanSummary(CommandContext<CommandSourceStack> ctx,
                                          ChunkPos cp, int total, boolean fix) {
        if (total == 0) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal(MSG_NO_ORPHANS + cp)
                            .withStyle(ChatFormatting.GREEN), false);
        } else {
            String verb = fix ? MSG_ORPHAN_REMOVED : MSG_ORPHAN_FOUND;
            ctx.getSource().sendSuccess(() ->
                    Component.literal(verb + MSG_SPACE + total + MSG_ORPHAN_IN_CHUNK + cp
                                    + (fix ? MSG_EMPTY : MSG_ORPHAN_FIX_HINT))
                            .withStyle(ChatFormatting.YELLOW), true);
        }
    }

}
