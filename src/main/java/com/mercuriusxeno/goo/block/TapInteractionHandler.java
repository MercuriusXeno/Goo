package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;

/**
 * Stateless dispatch and handler methods for tap block interactions.
 * Keeps TapBlock under the TooManyMethods threshold by extracting
 * item-use, empty-hand, and sub-region hit logic.
 */
final class TapInteractionHandler {

    /** Error message for TUNER_PASS reaching dispatch. */
    private static final String ERR_TUNER_PASS = "TUNER_PASS handled in validate";
    /** Error message prefix for unexpected interaction types reaching dispatch. */
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";
    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private TapInteractionHandler() { }

    // --- Dispatch ---

    /** Routes canister-region interactions - only blob ops, no canister insert.
     *
     * @param interaction the classified interaction type
     * @param tap         the tap block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    static InteractionResult dispatchCanisterRegion(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        if (interaction == GooInteractionType.TUNER_PASS) {
            throw new IllegalStateException(ERR_TUNER_PASS);
        }
        return dispatchCanisterRegionNonTuner(interaction, tap, stack, player, hand);
    }

    /**
     * Dispatches a non-tuner canister-region interaction to the matching handler.
     *
     * @param interaction the classified interaction type (must not be TUNER_PASS)
     * @param tap         the tap block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @return the interaction result
     */
    private static InteractionResult dispatchCanisterRegionNonTuner(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand) {
        return switch (interaction) {
            case CANISTER_INSERT -> InteractionResult.TRY_WITH_EMPTY_HAND;
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /** Routes a classified interaction to the appropriate tap handler.
     *
     * @param interaction the classified interaction type
     * @param tap         the tap block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    static InteractionResult dispatchTap(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        if (interaction == GooInteractionType.TUNER_PASS) {
            throw new IllegalStateException(ERR_TUNER_PASS);
        }
        return dispatchTapNonTuner(interaction, tap, stack, player, hand);
    }

    /**
     * Dispatches a non-tuner tap interaction to the matching handler.
     *
     * @param interaction the classified interaction type (must not be TUNER_PASS)
     * @param tap         the tap block entity
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @return the interaction result
     */
    private static InteractionResult dispatchTapNonTuner(
            GooInteractionType interaction, TapBlockEntity tap, ItemStack stack,
            Player player, InteractionHand hand) {
        return switch (interaction) {
            case CANISTER_INSERT -> handleCanisterInsert(tap, stack, player);
            case BLOB_INSERT     -> handleBlobInsert(tap, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /** Dispatches empty-hand interactions by sub-region: canister, valve, body, gasket.
     *
     * @param state       the block state
     * @param level       the current level
     * @param pos         the block position
     * @param player      the interacting player
     * @param hitResult   the ray trace hit result
     * @param tap         the tap block entity
     * @param facing      the tap facing direction
     * @param valveShapes per-facing valve shapes for hit detection
     * @param canisterSlotShapes per-facing canister slot shapes for hit detection
     * @return the interaction result
     */
    static InteractionResult dispatchEmptyHand(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult, TapBlockEntity tap, Direction facing,
            Map<Direction, VoxelShape> valveShapes, Map<Direction, VoxelShape> canisterSlotShapes) {
        if (hitCanister(hitResult, pos, facing, canisterSlotShapes) && !tap.getCanister().isEmpty()) {
            return removeCanister(tap, level, pos, player);
        }
        if (hitValve(hitResult, pos, facing, valveShapes)) {
            return toggleValve(state, level, pos);
        }
        return tryCanisterOrGasket(state, level, pos, player, tap);
    }

    // --- Sub-region hit detection ---

    /** Returns true if the hit point is within the valve sub-region.
     *
     * @param hit         the ray trace hit result
     * @param pos         the block position
     * @param facing      the facing direction
     * @param valveShapes per-facing valve shapes
     * @return true if the condition is met
     */
    static boolean hitValve(BlockHitResult hit, BlockPos pos, Direction facing,
            Map<Direction, VoxelShape> valveShapes) {
        VoxelShape valve = valveShapes.getOrDefault(facing, valveShapes.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, valve);
    }

    /** Returns true if the hit point is within the canister slot sub-region.
     *
     * @param hit                the ray trace hit result
     * @param pos                the block position
     * @param facing             the facing direction
     * @param canisterSlotShapes per-facing canister slot shapes
     * @return true if the condition is met
     */
    static boolean hitCanister(BlockHitResult hit, BlockPos pos, Direction facing,
            Map<Direction, VoxelShape> canisterSlotShapes) {
        VoxelShape slot = canisterSlotShapes.getOrDefault(facing, canisterSlotShapes.get(Direction.SOUTH));
        return ShapeHitCheck.hitInsideShape(hit, pos, slot);
    }

    // --- Handlers ---

    /** Inserts a canister into the tap's slot if empty.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleCanisterInsert(
            TapBlockEntity tap, ItemStack stack, Player player) {
        if (!tap.insertCanister(stack.copyWithCount(1))) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        stack.consume(1, player);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.DECORATED_POT_INSERT,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Pours goo from a blob or omniblob into the tap's canister.
     *
     * @param tap    the tap block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleBlobInsert(
            TapBlockEntity tap, ItemStack stack, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null || !tap.canAcceptGoo()) { return InteractionResult.PASS; }

        int volume = BlobStacks.volumeOf(stack);
        int accepted = tap.insertGoo(type, volume);
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        tap.getLevel().playSound(null, tap.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    // --- Empty-hand helpers ---

    /** Removes the canister if present, otherwise tries to remove the gasket.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param tap    the tap block entity
     * @return the interaction result
     */
    private static InteractionResult tryCanisterOrGasket(
            BlockState state, Level level, BlockPos pos, Player player, TapBlockEntity tap) {
        if (!tap.getCanister().isEmpty()) {
            return removeCanister(tap, level, pos, player);
        }
        return tryRemoveGasket(state, level, pos, player, tap);
    }

    /** Removes the canister from the tap and gives it to the player.
     *
     * @param tap    the tap block entity
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @return SUCCESS
     */
    private static InteractionResult removeCanister(
            TapBlockEntity tap, Level level, BlockPos pos, Player player) {
        ItemStack removed = tap.removeCanister();
        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Toggles the valve open/closed and plays the appropriate sound.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     * @return SUCCESS
     */
    private static InteractionResult toggleValve(BlockState state, Level level, BlockPos pos) {
        boolean nowOpen = !state.getValue(TapBlock.OPEN);
        level.setBlock(pos, state.setValue(TapBlock.OPEN, nowOpen), BLOCK_UPDATE_FLAGS);
        level.playSound(null, pos,
            nowOpen ? SoundEvents.COPPER_TRAPDOOR_OPEN : SoundEvents.COPPER_TRAPDOOR_CLOSE,
            SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Removes the gasket if the player is sneaking and one is installed.
     *
     * @param state  the block state
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param tap    the tap block entity
     * @return SUCCESS if removed, PASS otherwise
     */
    private static InteractionResult tryRemoveGasket(
            BlockState state, Level level, BlockPos pos, Player player, TapBlockEntity tap) {
        if (player.isShiftKeyDown() && state.getValue(TapBlock.HAS_GASKET)) {
            GasketInstallation.popGasket(level, pos, tap.getGasketId(GasketRole.RECEIVER));
            tap.clearGasket(GasketRole.RECEIVER);
            level.setBlock(pos, state.setValue(TapBlock.HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // --- Block break drops ---

    /** Drops the gasket item if one is installed.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     */
    static void dropGasketOnBreak(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(TapBlock.HAS_GASKET)) {
            Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
    }

    /** Drops the canister item if one is inserted.
     *
     * @param level the current level
     * @param pos   the block position
     */
    static void dropCanisterOnBreak(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TapBlockEntity tap) {
            ItemStack canister = tap.removeCanister();
            if (!canister.isEmpty()) {
                Block.popResource(level, pos, canister);
            }
        }
    }
}
