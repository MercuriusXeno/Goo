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

    /** Creates a new canister block item. */
    public CanisterItem(Block block, Properties properties) {
        super(block, properties);
    }

    // --- Interaction overrides ---

    /**
     * Intercepts use-on to handle clicking existing canister blocks.
     * Two paths: direct hit on a canister shape, or clicking the block
     * beneath the canister (ray through empty space hits the surface below).
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        if (level.getBlockState(clickedPos).getBlock() instanceof CanisterBlock) {
            return insertIntoExisting(context, level, clickedPos);
        }

        BlockPos placePos = clickedPos.relative(context.getClickedFace());
        if (level.getBlockState(placePos).getBlock() instanceof CanisterBlock) {
            return insertIntoExistingFromBehind(context, level, placePos);
        }

        // Validate support below before placing a new canister block
        BlockPos placeTarget = clickedPos.relative(context.getClickedFace());
        if (!isSupportedBelow(level, placeTarget.below())) {
            return InteractionResult.PASS;
        }

        InteractionResult result = super.useOn(context);
        if (result.consumesAction() && !level.isClientSide()
                && context.getPlayer() != null && context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return result;
    }

    // --- Insertion logic ---

    /** Inserts this canister into an existing canister block's grid (direct hit). */
    private InteractionResult insertIntoExisting(
            UseOnContext context, Level level, BlockPos pos) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CanisterBlockEntity be)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }

        int slot = resolveConstrainedSlot(
                context.getClickLocation(), pos, context.getClickedFace(), be, level);
        if (slot < 0) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (!be.insertCanister(slot, stack, player.isCreative())) return InteractionResult.PASS;
        stack.shrink(1);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        return InteractionResult.SUCCESS;
    }

    /**
     * Inserts into a canister block reached via click-through (the player
     * clicked the block behind it). The entry face is the opposite of the
     * clicked face so the slot resolves against the near side - matching
     * the ghost preview position.
     */
    private InteractionResult insertIntoExistingFromBehind(
            UseOnContext context, Level level, BlockPos pos) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CanisterBlockEntity be)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }

        Direction entryFace = context.getClickedFace().getOpposite();
        int slot = resolveConstrainedSlot(
                context.getClickLocation(), pos, entryFace, be, level);
        if (slot < 0) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (!be.insertCanister(slot, stack, player.isCreative())) return InteractionResult.PASS;
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
     */
    @Override
    protected boolean placeBlock(@NonNull BlockPlaceContext context, @NonNull BlockState state) {
        if (!super.placeBlock(context, state)) return false;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof CanisterBlockEntity be) {
            int slot = computePlacementSlot(context);
            // Constrain to allowed slots if block below is an attachment target
            if (!isSlotAllowed(level, pos, slot)) {
                java.util.Set<Integer> allowed = getAllowedSlots(level, pos);
                slot = (allowed != null && !allowed.isEmpty())
                    ? allowed.iterator().next() : slot;
            }
            boolean creative = context.getPlayer() != null && context.getPlayer().isCreative();
            be.assignFromItemStack(slot, context.getItemInHand(), creative);
            stampOwner(be, context.getPlayer());
            popConflictingGaskets(level, pos);
        }
        return true;
    }

    /**
     * Computes the target slot for new block placement using the click location
     * on the adjacent solid block's face.
     */
    private int computePlacementSlot(BlockPlaceContext context) {
        BlockPos placePos = context.getClickedPos();
        Vec3 clickLoc = context.getClickLocation();
        float px = (float) ((clickLoc.x - placePos.getX()) * 16.0);
        float pz = (float) ((clickLoc.z - placePos.getZ()) * 16.0);
        Direction entryFace = context.getClickedFace().getOpposite();
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /** Stamps owner UUID on the canister block entity. */
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
        float px = (float) ((hitLocation.x - pos.getX()) * 16.0);
        float pz = (float) ((hitLocation.z - pos.getZ()) * 16.0);
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
        float px = (float) ((hitLocation.x - pos.getX()) * 16.0);
        float pz = (float) ((hitLocation.z - pos.getZ()) * 16.0);
        int slot = CanisterSlotLayout.placementSlot(face, px, pz);

        if (slot >= 0 && be.getCanister(slot).isEmpty()) return slot;

        if (slot >= 0) {
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(slot, px, pz);
            if (be.getCanister(adjacent).isEmpty()) return adjacent;
        }

        return findFirstEmpty(be);
    }

    /**
     * Resolves the best empty slot for insertion, constrained by the block
     * below (if it implements ICanisterAttachable). Falls back to the first
     * allowed empty slot if the targeted and adjacent slots are disallowed.
     */
    private static int resolveConstrainedSlot(
            Vec3 hitLocation, BlockPos pos, Direction face,
            CanisterBlockEntity be, Level level) {
        int slot = resolveInsertionSlot(hitLocation, pos, face, be);
        if (slot >= 0 && isSlotAllowed(level, pos, slot)) return slot;

        // Targeted slot is disallowed - find first allowed empty slot
        java.util.Set<Integer> allowed = getAllowedSlots(level, pos);
        if (allowed == null) return slot; // no constraint, use original result
        return findFirstAllowedEmpty(be, allowed);
    }

    /** Finds the first empty slot in the block entity. */
    private static int findFirstEmpty(CanisterBlockEntity be) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (be.getCanister(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Finds the first empty slot that is in the allowed set. */
    private static int findFirstAllowedEmpty(CanisterBlockEntity be, java.util.Set<Integer> allowed) {
        for (int slot : allowed) {
            if (be.getCanister(slot).isEmpty()) return slot;
        }
        return -1;
    }

    // --- Gasket mutual exclusivity ---

    /**
     * Pops any gaskets on the attachment target below when a canister is placed
     * above it. E.g. hub intake gasket pops when a canister is copper-fitted on top.
     */
    private static void popConflictingGaskets(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity belowBe = level.getBlockEntity(belowPos);
        if (belowBe instanceof IGasketHolder holder) {
            // Pop the RECEIVER gasket on the top face of the block below
            java.util.UUID receiverGasketId = holder.getGasketId(GasketRole.RECEIVER);
            if (receiverGasketId != null) {
                GasketInstallation.popGasket(level, belowPos, receiverGasketId);
                holder.clearGasket(GasketRole.RECEIVER);
            }
        }
    }

    // --- Placement validation ---

    /**
     * Returns true if the block below can support a canister. A canister needs
     * either a solid rendering surface or an ICanisterAttachable with capacity.
     */
    private static boolean isSupportedBelow(Level level, BlockPos belowPos) {
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.isSolidRender()) return true;
        BlockEntity be = level.getBlockEntity(belowPos);
        return be instanceof ICanisterAttachable att && att.canAttachOnTop();
    }

    /**
     * Returns the set of allowed slot indices for a canister block at the given
     * position, or null if no constraint applies. Queries the ICanisterAttachable
     * block below (if any) for its allowed slot set.
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
     */
    static boolean isSlotAllowed(Level level, BlockPos canisterPos, int slot) {
        java.util.Set<Integer> allowed = getAllowedSlots(level, canisterPos);
        return allowed == null || allowed.contains(slot);
    }

    // --- Static contents helpers ---

    /** Returns the goo contents from the stack, or EMPTY if none. */
    public static GooContents getGooContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /** Returns the canister metadata from the stack, or EMPTY if none. Gasket UUIDs are not auto-generated; they are only created when a choral gasket is physically installed. */
    public static CanisterMetadata getMetadata(ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        return meta != null ? meta : CanisterMetadata.EMPTY;
    }

    /** Sets the goo contents on the stack. Removes component if empty. */
    public static void setGooContents(ItemStack stack, GooContents contents) {
        if (!contents.isEmpty()) {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        } else {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        }
    }

    /** Sets the canister metadata on the stack. Removes component if no data. */
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
     */
    public static long addGoo(ItemStack stack, GooType type, long amount) {
        if (amount <= 0) return 0;

        GooContents contents = getGooContents(stack);
        long capacity = ContainerCapacity.canisterCapacity(
            com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack));
        long accepted = contents.cappedAddAmount(amount, capacity);
        if (accepted <= 0) return 0;

        setGooContents(stack, contents.withAdded(type, accepted));
        return accepted;
    }

    /**
     * Try to remove goo of a specific type from the canister.
     * Returns the amount actually removed.
     */
    public static long removeGoo(ItemStack stack, GooType type, long amount) {
        GooContents contents = getGooContents(stack);
        if (contents.isEmpty()) return 0;

        long available = contents.getVolume(type);
        long toRemove = Math.min(amount, available);
        if (toRemove <= 0) return 0;

        setGooContents(stack, contents.withRemoved(type, toRemove));
        return toRemove;
    }

    // --- Inventory click interactions ---

    /**
     * Handles cursor-on-canister inventory clicks: blob/omniblob insert,
     * empty-cursor drain, and bucket drain/fill.
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack canister, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return handleEmptyCursorDrain(canister, cursorAccess);
        }
        if (action == ClickAction.PRIMARY) {
            if (cursor.getItem() instanceof GooBlobItem) {
                return handleBlobInsert(canister, cursor, cursorAccess);
            }
            if (cursor.getItem() instanceof GooOmniblobItem) {
                return handleOmniblobInsert(canister, cursor, cursorAccess);
            }
            if (cursor.is(Items.BUCKET)) {
                return handleBucketDrain(canister, cursor, cursorAccess);
            }
            if (cursor.getItem() instanceof BucketOfGooItem) {
                return handlePartialBucketDrain(canister, cursor, cursorAccess);
            }
        }
        return false;
    }

    /** Transfers blob goo into the canister, shrinking the blob stack by accepted blobs. */
    private static boolean handleBlobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooBlobItem) cursor.getItem()).getGooType();
        long volume = BlobStacks.volumeOf(cursor);
        long accepted = addGoo(canister, type, volume);
        if (accepted <= 0) return false;

        int blobsUsed = (int) (accepted / BlobStacks.MB_PER_BLOB);
        cursor.shrink(blobsUsed);
        if (cursor.isEmpty()) cursorAccess.set(ItemStack.EMPTY);
        return true;
    }

    /** Transfers omniblob goo into the canister, reducing or clearing the cursor. */
    private static boolean handleOmniblobInsert(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooType type = ((GooOmniblobItem) cursor.getItem()).getGooType();
        long volume = GooOmniblobItem.getVolume(cursor);
        long accepted = addGoo(canister, type, volume);
        if (accepted <= 0) return false;

        long remaining = volume - accepted;
        if (remaining <= 0) {
            cursorAccess.set(ItemStack.EMPTY);
        } else {
            GooOmniblobItem.setVolume(cursor, remaining);
        }
        return true;
    }

    /** Drains up to 64,000 mB of the dominant goo type onto the cursor as a blob output. */
    private static boolean handleEmptyCursorDrain(ItemStack canister, SlotAccess cursorAccess) {
        GooContents contents = getGooContents(canister);
        if (contents.isEmpty()) return false;

        GooType dominant = contents.largestType();
        if (dominant == null) return false;

        long toExtract = Math.min(contents.getVolume(dominant), ContainerCapacity.BLOB_CAP);
        long extracted = removeGoo(canister, dominant, toExtract);
        if (extracted <= 0) return false;

        cursorAccess.set(BlobStacks.createForOutput(dominant, extracted));
        return true;
    }

    /** Drains up to BUCKET_CAP of the dominant goo type into an empty bucket on the cursor. */
    private static boolean handleBucketDrain(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooContents contents = getGooContents(canister);
        if (contents.isEmpty()) return false;

        GooType dominant = contents.largestType();
        if (dominant == null) return false;

        long toExtract = Math.min(contents.getVolume(dominant), ContainerCapacity.BUCKET_CAP);
        long extracted = removeGoo(canister, dominant, toExtract);
        if (extracted <= 0) return false;

        cursorAccess.set(BucketOfGooItem.createWithGoo(dominant, extracted));
        return true;
    }

    /** Drains goo into a partially-filled bucket, up to remaining bucket capacity. */
    private static boolean handlePartialBucketDrain(ItemStack canister, ItemStack cursor, SlotAccess cursorAccess) {
        GooContents bucketContents = BucketOfGooItem.getContents(cursor);
        long remainingCap = ContainerCapacity.BUCKET_CAP - bucketContents.totalVolume();
        if (remainingCap <= 0) return false;

        GooContents canisterContents = getGooContents(canister);
        if (canisterContents.isEmpty()) return false;

        GooType dominant = canisterContents.largestType();
        if (dominant == null) return false;

        long toExtract = Math.min(canisterContents.getVolume(dominant), remainingCap);
        long extracted = removeGoo(canister, dominant, toExtract);
        if (extracted <= 0) return false;

        BucketOfGooItem.setContents(cursor, bucketContents.withAdded(dominant, extracted));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.CANISTER_INSERT;
    }
}
