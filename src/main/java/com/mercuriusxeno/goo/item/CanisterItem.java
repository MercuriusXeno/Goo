package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.InteractionCooldown;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Canister item: single-type fluid storage accepting any registered fluid.
 * Capacity scales with Compression enchantment via ContainerCapacity.
 *
 * <p>Overrides placement to support multi-canister blocks: clicking an
 * existing canister block inserts into the targeted slot rather than
 * placing a new block. Slot targeting uses the vanilla BlockHitResult
 * location directly.</p>
 */
public class CanisterItem extends BlockItem implements IGooItemInteraction {

    /**
     * Creates a new canister block item.
     *
     * @param block      the canister block
     * @param properties the item properties
     */
    public CanisterItem(Block block, Properties properties) {
        super(block, properties);
    }

    // --- Interaction overrides ---

    /**
     * Intercepts use-on to handle clicking existing canister blocks.
     * Two paths: direct hit on a canister shape, or clicking the block
     * beneath the canister (ray through empty space hits the surface below).
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        if (isCanisterBlock(level, clickedPos)) {
            return tryInsertCanister(context, level, clickedPos, context.getClickedFace());
        }
        return useOnNonCanister(context, level, clickedPos);
    }

    /**
     * Handles use-on when the clicked block is not a canister (place-through or new placement).
     *
     * @param context    the use-on context
     * @param level      the current level
     * @param clickedPos the originally clicked block position
     * @return the interaction result
     */
    private InteractionResult useOnNonCanister(UseOnContext context, Level level, BlockPos clickedPos) {
        BlockPos placePos = clickedPos.relative(context.getClickedFace());
        if (isCanisterBlock(level, placePos)) {
            return tryInsertCanister(context, level, placePos, context.getClickedFace().getOpposite());
        }
        return placeNewCanister(context, level, placePos);
    }

    /**
     * Returns true if the block at the given position is a canister block.
     *
     * @param level the current level
     * @param pos   the block position to check
     * @return true if the block is a canister block
     */
    private static boolean isCanisterBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CanisterBlock;
    }

    // --- Insertion logic ---

    /**
     * Validates and places a new canister block, checking support below.
     *
     * @param context  the use-on context
     * @param level    the current level
     * @param placePos the target placement position
     * @return the interaction result
     */
    private InteractionResult placeNewCanister(UseOnContext context, Level level, BlockPos placePos) {
        if (!CanisterPlacementValidator.isSupportedBelow(level, placePos.below())) {
            return InteractionResult.PASS;
        }

        InteractionResult result = super.useOn(context);
        shrinkInCreative(result, context, level);
        return result;
    }

    /**
     * Shrinks the held item in creative mode after a successful placement.
     *
     * @param result  the placement result
     * @param context the use-on context
     * @param level   the current level
     */
    private static void shrinkInCreative(InteractionResult result, UseOnContext context, Level level) {
        if (result.consumesAction() && !level.isClientSide()
                && context.getPlayer() != null && context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
    }

    /**
     * Inserts this canister into an existing canister block's grid.
     * Handles both direct hits and click-through: entryFace determines
     * which face the slot resolves against.
     *
     * @param context   the use-on context
     * @param level     the current level
     * @param pos       the canister block position
     * @param entryFace the face used for slot resolution
     * @return the interaction result
     */
    private InteractionResult tryInsertCanister(
            UseOnContext context, Level level, BlockPos pos, Direction entryFace) {
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        if (!canInteract(context, level)) { return interactionGuardResult(context); }

        var be = canisterEntityAt(level, pos);
        if (be == null) { return InteractionResult.PASS; }

        int slot = CanisterSlotResolver.resolveAndConstrain(context.getClickLocation(), pos, entryFace, be);
        if (slot < 0) { return InteractionResult.PASS; }
        return commitInsertion(context, be, slot, level);
    }

    /**
     * Returns the CanisterBlockEntity at the position, or null.
     *
     * @param level the current level
     * @param pos   the block position
     * @return the canister block entity, or null if absent
     */
    private static @Nullable CanisterBlockEntity canisterEntityAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CanisterBlockEntity be ? be : null;
    }

    /**
     * Returns true if the player exists and is not on interaction cooldown.
     *
     * @param context the use-on context
     * @param level   the current level
     * @return true if the player can interact
     */
    private static boolean canInteract(UseOnContext context, Level level) {
        Player player = context.getPlayer();
        return player != null && !InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime());
    }

    /**
     * Returns the correct guard result when canInteract fails.
     *
     * @param context the use-on context
     * @return PASS if no player, SUCCESS if on cooldown
     */
    private static InteractionResult interactionGuardResult(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) { return InteractionResult.PASS; }
        return InteractionResult.SUCCESS;
    }

    /**
     * Commits the canister insertion into a resolved slot.
     *
     * @param context the use-on context
     * @param be      the canister block entity
     * @param slot    the target slot index
     * @param level   the current level
     * @return the interaction result
     */
    private static InteractionResult commitInsertion(
            UseOnContext context, CanisterBlockEntity be, int slot, Level level) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        boolean creative = player != null && player.isCreative();
        if (!be.insertCanister(slot, stack, creative)) { return InteractionResult.PASS; }
        stack.shrink(1);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        return InteractionResult.SUCCESS;
    }

    // --- Block placement ---

    /**
     * After normal block placement, reads goo data from the held ItemStack and
     * assigns it to the target slot directly. We cannot rely on pendingGooContents
     * because applyImplicitComponents runs later in BlockItem.place(), after
     * placeBlock() has already returned.
     *
     * @param context the block placement context
     * @param state   the block state to place
     * @return true if the block was placed
     */
    @Override
    protected boolean placeBlock(@NonNull BlockPlaceContext context, @NonNull BlockState state) {
        if (!super.placeBlock(context, state)) { return false; }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof CanisterBlockEntity be) {
            initPlacedCanister(context, level, pos, be);
        }
        return true;
    }

    /**
     * Assigns goo data, owner, and gasket cleanup after block placement.
     *
     * @param context the block placement context
     * @param level   the current level
     * @param pos     the canister block position
     * @param be      the canister block entity
     */
    private void initPlacedCanister(
            BlockPlaceContext context, Level level, BlockPos pos, CanisterBlockEntity be) {
        int slot = CanisterPlacementValidator.constrainSlot(
                CanisterPlacementValidator.computePlacementSlot(
                        context.getClickLocation(), context.getClickedPos(),
                        context.getClickedFace().getOpposite()),
                level, pos);
        boolean creative = context.getPlayer() != null && context.getPlayer().isCreative();
        be.assignFromItemStack(slot, context.getItemInHand(), creative);
        CanisterPlacementValidator.stampOwner(be, context.getPlayer());
        CanisterInventoryHandler.popConflictingGaskets(level, pos);
    }

    // --- Static contents helpers ---

    /**
     * Returns the fluid content from the stack, or EMPTY if none.
     *
     * @param stack the item stack
     * @return the fluid content, never null
     */
    public static CanisterFluidContent getFluidContent(ItemStack stack) {
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        return content != null ? content : CanisterFluidContent.EMPTY;
    }

    /**
     * Returns the canister metadata from the stack, or EMPTY if none. Gasket UUIDs
     * are not auto-generated; they are only created when a choral gasket is
     * physically installed.
     *
     * @param stack the item stack
     * @return the canister metadata, never null
     */
    public static CanisterMetadata getMetadata(ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        return meta != null ? meta : CanisterMetadata.EMPTY;
    }

    /**
     * Sets the fluid content on the stack. Removes component if empty.
     *
     * @param stack   the item stack
     * @param content the fluid content to set
     */
    public static void setFluidContent(ItemStack stack, CanisterFluidContent content) {
        if (content.isEmpty()) {
            stack.remove(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        } else {
            stack.set(GooDataComponents.CANISTER_FLUID_CONTENT.get(), content);
        }
    }

    /**
     * Sets the canister metadata on the stack. Removes component if no data.
     *
     * @param stack the item stack
     * @param meta  the canister metadata to set
     */
    public static void setMetadata(ItemStack stack, CanisterMetadata meta) {
        if (meta.hasData()) {
            stack.set(GooDataComponents.CANISTER_METADATA.get(), meta);
        } else {
            stack.remove(GooDataComponents.CANISTER_METADATA.get());
        }
    }

    /**
     * Try to add fluid to the canister. Only accepts if empty or same fluid.
     * Returns the amount actually added.
     *
     * @param stack  the canister item stack
     * @param fluid  the fluid to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static long addFluid(ItemStack stack, Fluid fluid, long amount) {
        long capacity = ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack));
        CanisterFluidContent current = getFluidContent(stack);
        long accepted = current.cappedAddAmount(fluid, amount, capacity);
        if (accepted > 0) {
            setFluidContent(stack, current.withCappedAdd(fluid, amount, capacity));
        }
        return accepted;
    }

    /**
     * Convenience: add goo by type. Resolves GooType to its source fluid.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static long addGoo(ItemStack stack, GooType type, long amount) {
        return addFluid(stack, GooFluids.SOURCES.get(type).get(), amount);
    }

    /**
     * Try to remove fluid from the canister. Only extracts if the canister
     * holds the specified fluid. Returns the amount actually removed.
     *
     * @param stack  the canister item stack
     * @param fluid  the fluid to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static long removeFluid(ItemStack stack, Fluid fluid, long amount) {
        CanisterFluidContent current = getFluidContent(stack);
        if (current.isEmpty() || current.fluid() != fluid) { return 0; }
        long removed = Math.min(amount, current.amount());
        setFluidContent(stack, current.withRemoved(removed));
        return removed;
    }

    /**
     * Convenience: remove goo by type. Resolves GooType to its source fluid.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static long removeGoo(ItemStack stack, GooType type, long amount) {
        return removeFluid(stack, GooFluids.SOURCES.get(type).get(), amount);
    }

    // --- Inventory click interactions ---

    /**
     * Handles cursor-on-canister inventory clicks: blob/omniblob insert,
     * empty-cursor drain.
     *
     * @param canister    the canister item stack in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action (primary or secondary)
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack canister, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return CanisterInventoryHandler.handleEmptyCursorDrain(canister, cursorAccess);
        }
        return action == ClickAction.PRIMARY
                && CanisterInventoryHandler.handlePrimaryClick(canister, cursor, cursorAccess);
    }

    /**
     * Returns CANISTER_INSERT so canister blocks route to slot insertion logic.
     *
     * @return the canister insert interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.CANISTER_INSERT;
    }
}
