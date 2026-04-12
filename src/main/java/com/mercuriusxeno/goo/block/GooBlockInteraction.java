package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooInteractionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Shared interaction infrastructure for slot-based goo blocks.
 * Handles validation, dispatch, and slot search for blocks that
 * accept GooInteractionType-classified item interactions.
 */
public final class GooBlockInteraction {

    /** SuppressWarnings value for unchecked generic casts. */
    private static final String UNCHECKED = "unchecked";

    /** Sentinel value: no matching slot found. */

    private GooBlockInteraction() {}

    /**
     * Dispatches a validated interaction to a block-specific handler.
     *
     * @param <T> the block entity type
     */
    @FunctionalInterface
    public interface Dispatcher<T extends BlockEntity> {
        InteractionResult dispatch(
                GooInteractionType interaction, T entity, ItemStack stack,
                Player player, InteractionHand hand, BlockHitResult hitResult,
                BlockPos pos, Level level);
    }

    /**
     * Full item interaction flow: classify -> validate -> dispatch.
     * Call from useItemOn().
     *
     * @param stack            the held item stack
     * @param level            the world
     * @param pos              the block position
     * @param player           the interacting player
     * @param hand             the hand used
     * @param hitResult        the block hit result
     * @param entityType       expected block entity class
     * @param rejectAsEmptyHand predicate returning true for interaction types that should
     *                          fall through to useWithoutItem (e.g. null)
     * @param dispatcher       block-specific dispatch function
     * @param <T>              the block entity type
     * @return the interaction result
     */
    public static <T extends BlockEntity> InteractionResult handleItemInteraction(
            ItemStack stack, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            Class<T> entityType,
            Predicate<@Nullable GooInteractionType> rejectAsEmptyHand,
            Dispatcher<T> dispatcher) {

        GooInteractionType interaction = GooInteractionType.classify(stack);
        InteractionResult earlyOut = validate(
                interaction, level, pos, player, entityType, rejectAsEmptyHand);
        if (earlyOut != null) { return earlyOut; }

        @SuppressWarnings(UNCHECKED)
        T entity = (T) level.getBlockEntity(pos);
        return dispatcher.dispatch(interaction, entity, stack, player, hand, hitResult, pos, level);
    }

    /**
     * Shared validation for item interactions.
     * Returns an early-out result or null to continue.
     *
     * @param <T>                the block entity type
     * @param interaction        the classified interaction type, or null
     * @param level              the world
     * @param pos                the block position
     * @param player             the interacting player
     * @param entityType         expected block entity class
     * @param rejectAsEmptyHand  predicate returning true for types that fall through
     * @return an early-out result, or null to continue dispatch
     */
    static <T extends BlockEntity> @Nullable InteractionResult validate(
            @Nullable GooInteractionType interaction, Level level,
            BlockPos pos, Player player, Class<T> entityType,
            Predicate<@Nullable GooInteractionType> rejectAsEmptyHand) {
        if (rejectAsEmptyHand.test(interaction)) { return InteractionResult.TRY_WITH_EMPTY_HAND; }
        if (interaction == GooInteractionType.TUNER_PASS) { return InteractionResult.PASS; }
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (!entityType.isInstance(level.getBlockEntity(pos))) { return InteractionResult.PASS; }
        return checkCooldown(interaction, level, player);
    }

    /**
     * Returns SUCCESS if the interaction is on cooldown, null otherwise.
     * @param interaction the classified interaction type
     * @param level the current level (for game time)
     * @param player the interacting player (for UUID-based cooldown)
     * @return SUCCESS if on cooldown, null to continue dispatch
     */
    private static @Nullable InteractionResult checkCooldown(
            GooInteractionType interaction, Level level, Player player) {
        if (interaction.requiresCooldown()
                && InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Shared validation for empty-hand interactions.
     * Returns an early-out result or null to continue.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @return the interaction result
     */
    public static @Nullable InteractionResult validateEmptyHand(
            Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Finds the first slot matching the predicate, preferring the hit slot.
     * Returns -1 if no slot matches.
     *
     * @param hitSlot  the slot the player targeted, or -1
     * @param maxSlots the total number of slots to scan
     * @param matches  predicate testing whether a slot index qualifies
     * @return matching slot index, or -1
     */
    public static int findSlot(int hitSlot, int maxSlots, IntPredicate matches) {
        if (hitSlot >= 0 && matches.test(hitSlot)) { return hitSlot; }
        for (int i = 0; i < maxSlots; i++) {
            if (matches.test(i)) { return i; }
        }
        return NO_SLOT;
    }
}
