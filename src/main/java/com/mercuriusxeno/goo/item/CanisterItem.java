package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.GasketInstallation;
import com.mercuriusxeno.goo.block.ICanisterAttachable;
import com.mercuriusxeno.goo.block.IGasketHolder;
import com.mercuriusxeno.goo.block.InteractionCooldown;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Canister item: multi-type goo storage with rune ink upgrades.
 * Capacity scales with matrices via ContainerCapacity.
 *
 * <p>Overrides placement to support multi-canister blocks: clicking an
 * existing canister block inserts into the targeted slot rather than
 * placing a new block. Slot targeting uses the vanilla BlockHitResult
 * location directly.</p>
 */
public class CanisterItem extends BlockItem implements IGooItemInteraction {

    /** Pixels per block: converts block-space [0..1] to pixel-space [0..16]. */
    private static final double PIXELS_PER_BLOCK = 16.0;
    /** Sentinel value: no empty slot found. */
    private static final int NO_SLOT = -1;

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
        if (!isSupportedBelow(level, placePos.below())) {
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

        int slot = resolveAndConstrain(context.getClickLocation(), pos, entryFace, be);
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
        if (player == null) { return false; }
        return !InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime());
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
        int slot = constrainSlot(computePlacementSlot(context), level, pos);
        boolean creative = context.getPlayer() != null && context.getPlayer().isCreative();
        be.assignFromItemStack(slot, context.getItemInHand(), creative);
        stampOwner(be, context.getPlayer());
        popConflictingGaskets(level, pos);
    }

    /**
     * Falls back to the first allowed slot if the target slot is disallowed.
     *
     * @param slot  the preferred slot index
     * @param level the current level
     * @param pos   the canister block position
     * @return the constrained slot index
     */
    private static int constrainSlot(int slot, Level level, BlockPos pos) {
        if (isSlotAllowed(level, pos, slot)) { return slot; }
        java.util.Set<Integer> allowed = getAllowedSlots(level, pos);
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.iterator().next();
        }
        return slot;
    }

    /**
     * Computes the target slot for new block placement using the click location
     * on the adjacent solid block's face.
     *
     * @param context the block placement context
     * @return the target slot index (0-8)
     */
    private int computePlacementSlot(BlockPlaceContext context) {
        BlockPos placePos = context.getClickedPos();
        Vec3 clickLoc = context.getClickLocation();
        float px = (float) ((clickLoc.x - placePos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((clickLoc.z - placePos.getZ()) * PIXELS_PER_BLOCK);
        Direction entryFace = context.getClickedFace().getOpposite();
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /**
     * Stamps owner UUID on the canister block entity.
     *
     * @param be     the canister block entity
     * @param player the player who placed the canister
     */
    private void stampOwner(CanisterBlockEntity be, Player player) {
        if (player != null) {
            be.setOwner(player.getUUID());
        }
    }

    // --- Slot projection ---

    /**
     * Determines which canister grid cell a hit point targets for placement.
     *
     * @param hitLocation the contact point from BlockHitResult.getLocation()
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @return slot index 0-8
     */
    public static int projectToCanisterSlot(Vec3 hitLocation, BlockPos pos, Direction face) {
        float px = (float) ((hitLocation.x - pos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((hitLocation.z - pos.getZ()) * PIXELS_PER_BLOCK);
        return CanisterSlotLayout.placementSlot(face, px, pz);
    }

    /**
     * Resolves the best empty slot for canister insertion.
     *
     * @param hitLocation the contact point from BlockHitResult.getLocation()
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @param be          the canister block entity to check occupancy
     * @return empty slot index 0-8, or -1 if all full
     */
    public static int resolveInsertionSlot(
            Vec3 hitLocation, BlockPos pos, Direction face, CanisterBlockEntity be) {
        float px = (float) ((hitLocation.x - pos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((hitLocation.z - pos.getZ()) * PIXELS_PER_BLOCK);
        int slot = CanisterSlotLayout.placementSlot(face, px, pz);

        if (slot >= 0 && be.getCanister(slot).isEmpty()) { return slot; }

        return fallbackSlot(slot, px, pz, be);
    }

    /**
     * Tries the adjacent slot by cursor lean, then falls back to the first empty.
     *
     * @param slot the targeted slot index
     * @param px   the x pixel coordinate within the block
     * @param pz   the z pixel coordinate within the block
     * @param be   the canister block entity
     * @return the fallback slot index, or -1 if all full
     */
    private static int fallbackSlot(int slot, float px, float pz, CanisterBlockEntity be) {
        if (slot >= 0) {
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(slot, px, pz);
            if (be.getCanister(adjacent).isEmpty()) { return adjacent; }
        }
        return findFirstEmpty(be);
    }

    /**
     * Resolves the best empty slot, then constrains to allowed slots for
     * the attachment target below.
     *
     * @param hitLocation the contact point from the hit result
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @param be          the canister block entity
     * @return constrained slot index, or -1 if none available
     */
    private static int resolveAndConstrain(
            Vec3 hitLocation, BlockPos pos, Direction face, CanisterBlockEntity be) {
        int slot = resolveInsertionSlot(hitLocation, pos, face, be);
        Level level = be.getLevel();
        if (slot >= 0 && isSlotAllowed(level, pos, slot)) { return slot; }

        java.util.Set<Integer> allowed = getAllowedSlots(level, pos);
        if (allowed == null) { return slot; }
        return findFirstAllowedEmpty(be, allowed);
    }

    /**
     * Finds the first empty slot in the block entity.
     *
     * @param be the canister block entity
     * @return the first empty slot index, or -1 if all full
     */
    private static int findFirstEmpty(CanisterBlockEntity be) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (be.getCanister(i).isEmpty()) { return i; }
        }
        return NO_SLOT;
    }

    /**
     * Finds the first empty slot that is in the allowed set.
     *
     * @param be      the canister block entity
     * @param allowed the set of allowed slot indices
     * @return the first allowed empty slot, or -1 if none
     */
    private static int findFirstAllowedEmpty(CanisterBlockEntity be, java.util.Set<Integer> allowed) {
        for (int slot : allowed) {
            if (be.getCanister(slot).isEmpty()) { return slot; }
        }
        return NO_SLOT;
    }

    // --- Gasket mutual exclusivity ---

    /**
     * Pops any gaskets on the attachment target below when a canister is placed
     * above it. E.g. hub intake gasket pops when a canister is copper-fitted on top.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     */
    private static void popConflictingGaskets(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity belowBe = level.getBlockEntity(belowPos);
        if (belowBe instanceof IGasketHolder holder) {
            popReceiverGasket(level, belowPos, holder);
        }
    }

    /**
     * Pops the RECEIVER gasket on the top face of the block below, if present.
     *
     * @param level  the current level
     * @param pos    the gasket holder block position
     * @param holder the gasket holder
     */
    private static void popReceiverGasket(Level level, BlockPos pos, IGasketHolder holder) {
        java.util.UUID gasketId = holder.getGasketId(GasketRole.RECEIVER);
        if (gasketId != null) {
            GasketInstallation.popGasket(level, pos, gasketId);
            holder.clearGasket(GasketRole.RECEIVER);
        }
    }

    // --- Placement validation ---

    /**
     * Returns true if the block below can support a canister. A canister needs
     * either a solid rendering surface or an ICanisterAttachable with capacity.
     *
     * @param level    the current level
     * @param belowPos the block position below the canister
     * @return true if the position can support a canister
     */
    private static boolean isSupportedBelow(Level level, BlockPos belowPos) {
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.isSolidRender()) { return true; }
        BlockEntity be = level.getBlockEntity(belowPos);
        return be instanceof ICanisterAttachable att && att.canAttachOnTop();
    }

    /**
     * Returns the set of allowed slot indices for a canister block at the given
     * position, or null if no constraint applies. Queries the ICanisterAttachable
     * block below (if any) for its allowed slot set.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     * @return the allowed slots, or null if unconstrained
     */
    static java.util.@Nullable Set<Integer> getAllowedSlots(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity be = level.getBlockEntity(belowPos);
        if (be instanceof ICanisterAttachable att) {
            return att.allowedSlots();
        }
        return null;
    }

    /**
     * Returns true if the given slot is allowed for a canister block at the given
     * position. If there is no ICanisterAttachable below, all slots are allowed.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     * @param slot         the slot index to check
     * @return true if the slot is allowed
     */
    static boolean isSlotAllowed(Level level, BlockPos canisterPos, int slot) {
        java.util.Set<Integer> allowed = getAllowedSlots(level, canisterPos);
        return allowed == null || allowed.contains(slot);
    }

    // --- Static contents helpers ---

    /**
     * Returns the goo contents from the stack, or EMPTY if none.
     *
     * @param stack the item stack
     * @return the goo contents, never null
     */
    public static GooContents getGooContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
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
     * Sets the goo contents on the stack. Removes component if empty.
     *
     * @param stack    the item stack
     * @param contents the goo contents to set
     */
    public static void setGooContents(ItemStack stack, GooContents contents) {
        if (contents.isEmpty()) {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        } else {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
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
     * Try to add goo to the canister. Returns the amount actually added.
     * Multi-type: accepts any goo type, enforces capacity via ContainerCapacity.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static long addGoo(ItemStack stack, GooType type, long amount) {
        if (amount <= 0) { return 0; }

        GooContents contents = getGooContents(stack);
        long capacity = ContainerCapacity.canisterCapacity(
            com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack));
        long accepted = contents.cappedAddAmount(amount, capacity);
        if (accepted <= 0) { return 0; }

        setGooContents(stack, contents.withAdded(type, accepted));
        return accepted;
    }

    /**
     * Try to remove goo of a specific type from the canister.
     * Returns the amount actually removed.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static long removeGoo(ItemStack stack, GooType type, long amount) {
        GooContents contents = getGooContents(stack);
        if (contents.isEmpty()) { return 0; }

        long available = contents.getVolume(type);
        long toRemove = Math.min(amount, available);
        if (toRemove <= 0) { return 0; }

        setGooContents(stack, contents.withRemoved(type, toRemove));
        return toRemove;
    }

    // --- Inventory click interactions ---

    /**
     * Handles cursor-on-canister inventory clicks: blob/omniblob insert,
     * empty-cursor drain, and bucket drain/fill.
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
            return handleEmptyCursorDrain(canister, cursorAccess);
        }
        if (action == ClickAction.PRIMARY) {
            return handlePrimaryClick(canister, cursor, cursorAccess);
        }
        return false;
    }

    /**
     * Dispatches primary-click interactions based on cursor item type.
     *
     * @param canister     the canister item stack
     * @param cursor       the cursor item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    private static boolean handlePrimaryClick(
            ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        if (cursor.getItem() instanceof GooBlobItem) {
            return handleBlobInsert(canister, cursor, cursorAccess);
        }
        if (cursor.getItem() instanceof GooOmniblobItem) {
            return handleOmniblobInsert(canister, cursor, cursorAccess);
        }
        return handlePrimaryBucketClick(canister, cursor, cursorAccess);
    }

    /**
     * Dispatches primary-click bucket interactions (empty or partial).
     *
     * @param canister     the canister item stack
     * @param cursor       the cursor item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    private static boolean handlePrimaryBucketClick(
            ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        if (cursor.is(Items.BUCKET)) {
            return handleBucketDrain(canister, cursor, cursorAccess);
        }
        if (cursor.getItem() instanceof BucketOfGooItem) {
            return handlePartialBucketDrain(canister, cursor, cursorAccess);
        }
        return false;
    }

    /**
     * Transfers blob goo into the canister, shrinking the blob stack by accepted blobs.
     *
     * @param canister    the canister item stack
     * @param cursor      the blob stack on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleBlobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooBlobItem) cursor.getItem()).getGooType();
        long volume = BlobStacks.volumeOf(cursor);
        long accepted = addGoo(canister, type, volume);
        if (accepted <= 0) { return false; }

        int blobsUsed = (int) (accepted / BlobStacks.MB_PER_BLOB);
        cursor.shrink(blobsUsed);
        if (cursor.isEmpty()) { cursorAccess.set(ItemStack.EMPTY); }
        return true;
    }

    /**
     * Transfers omniblob goo into the canister, reducing or clearing the cursor.
     *
     * @param canister    the canister item stack
     * @param cursor      the omniblob on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handleOmniblobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooOmniblobItem) cursor.getItem()).getGooType();
        long volume = GooOmniblobItem.getVolume(cursor);
        long accepted = addGoo(canister, type, volume);
        if (accepted <= 0) { return false; }

        applyOmniblobRemainder(cursor, cursorAccess, volume - accepted);
        return true;
    }

    /**
     * Clears the omniblob cursor or updates its remaining volume.
     *
     * @param cursor       the omniblob item stack
     * @param cursorAccess access to set the cursor contents
     * @param remaining    the remaining volume after transfer
     */
    private static void applyOmniblobRemainder(ItemStack cursor, SlotAccess cursorAccess, long remaining) {
        if (remaining <= 0) {
            cursorAccess.set(ItemStack.EMPTY);
        } else {
            GooOmniblobItem.setVolume(cursor, remaining);
        }
    }

    /**
     * Drains up to 64,000 mB of the dominant goo type onto the cursor as a blob output.
     *
     * @param canister    the canister item stack
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was extracted
     */
    private static boolean handleEmptyCursorDrain(ItemStack canister, SlotAccess cursorAccess) {
        GooType dominant = dominantType(canister);
        if (dominant == null) { return false; }

        long extracted = extractCapped(canister, dominant, ContainerCapacity.BLOB_CAP);
        if (extracted <= 0) { return false; }

        cursorAccess.set(BlobStacks.createForOutput(dominant, extracted));
        return true;
    }

    /**
     * Drains up to BUCKET_CAP of the dominant goo type into an empty bucket on the cursor.
     *
     * @param canister    the canister item stack
     * @param cursor      the empty bucket on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was extracted
     */
    private static boolean handleBucketDrain(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType dominant = dominantType(canister);
        if (dominant == null) { return false; }

        long extracted = extractCapped(canister, dominant, ContainerCapacity.BUCKET_CAP);
        if (extracted <= 0) { return false; }

        cursorAccess.set(BucketOfGooItem.createWithGoo(dominant, extracted));
        return true;
    }

    /**
     * Drains goo into a partially-filled bucket, up to remaining bucket capacity.
     *
     * @param canister    the canister item stack
     * @param cursor      the partially-filled bucket on the cursor
     * @param cursorAccess access to set the cursor contents
     * @return true if any goo was transferred
     */
    private static boolean handlePartialBucketDrain(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        long remainingCap = bucketRemainingCapacity(cursor);
        if (remainingCap <= 0) { return false; }

        GooType dominant = dominantType(canister);
        if (dominant == null) { return false; }

        long extracted = extractCapped(canister, dominant, remainingCap);
        if (extracted <= 0) { return false; }

        addToBucket(cursor, dominant, extracted);
        return true;
    }

    /**
     * Adds extracted goo to a partially-filled bucket item stack.
     *
     * @param cursor the bucket item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     */
    private static void addToBucket(ItemStack cursor, GooType type, long amount) {
        GooContents bucketContents = BucketOfGooItem.getContents(cursor);
        BucketOfGooItem.setContents(cursor, bucketContents.withAdded(type, amount));
    }

    /**
     * Returns the dominant goo type in the canister, or null if empty.
     *
     * @param canister the canister item stack
     * @return the dominant goo type, or null
     */
    private static @Nullable GooType dominantType(ItemStack canister) {
        GooContents contents = getGooContents(canister);
        if (contents.isEmpty()) { return null; }
        return contents.largestType();
    }

    /**
     * Extracts up to cap mB of the given type, capped by available volume.
     *
     * @param canister the canister item stack
     * @param type     the goo type to extract
     * @param cap      the maximum volume to extract
     * @return the amount actually extracted
     */
    private static long extractCapped(ItemStack canister, GooType type, long cap) {
        long available = getGooContents(canister).getVolume(type);
        return removeGoo(canister, type, Math.min(available, cap));
    }

    /**
     * Returns the remaining bucket capacity for a partially-filled goo bucket.
     *
     * @param cursor the bucket item stack
     * @return the remaining capacity in microblobs
     */
    private static long bucketRemainingCapacity(ItemStack cursor) {
        GooContents bucketContents = BucketOfGooItem.getContents(cursor);
        return ContainerCapacity.BUCKET_CAP - bucketContents.totalVolume();
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
