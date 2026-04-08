package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Hub block: central hub with 8 radial pipes for attaching canisters.
 * Canisters are inserted/removed via right-click on a specific slot.
 * Radially symmetrical: N, NE, E, SE, S, SW, W, NW.
 */
public class HubBlock extends BaseEntityBlock {

    /** Codec for serialization. */
    public static final MapCodec<HubBlock> CODEC = simpleCodec(HubBlock::new);

    /** Whether a choral gasket is installed on the hub's intake. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Error message for TUNER_PASS reaching dispatch. */
    private static final String ERR_TUNER_PASS = "TUNER_PASS handled in validate";

    /** Base slab: full-width, 2px tall. */
    private static final VoxelShape BASE = box(0, 0, 0, 16, 2, 16);

    /** Central spindle: 2x13x2 column. */
    private static final VoxelShape SPINDLE = box(7, 2, 7, 9, 15, 9);

    /** Intake gasket at top of spindle. */
    private static final VoxelShape INTAKE = box(6, 15, 6, 10, 16, 10);

    /** Canister slot 0 (north). */
    private static final VoxelShape SLOT_0 = box(6, 2, 0, 10, 14, 4);

    /** Canister slot 1 (northeast). */
    private static final VoxelShape SLOT_1 = box(11, 2, 1, 15, 14, 5);

    /** Canister slot 2 (east). */
    private static final VoxelShape SLOT_2 = box(12, 2, 6, 16, 14, 10);

    /** Canister slot 3 (southeast). */
    private static final VoxelShape SLOT_3 = box(11, 2, 11, 15, 14, 15);

    /** Canister slot 4 (south). */
    private static final VoxelShape SLOT_4 = box(6, 2, 12, 10, 14, 16);

    /** Canister slot 5 (southwest). */
    private static final VoxelShape SLOT_5 = box(1, 2, 11, 5, 14, 15);

    /** Canister slot 6 (west). */
    private static final VoxelShape SLOT_6 = box(0, 2, 6, 4, 14, 10);

    /** Canister slot 7 (northwest). */
    private static final VoxelShape SLOT_7 = box(1, 2, 1, 5, 14, 5);

    /** Single cuboid enclosing all pipe geometry (y=14-15). Prevents collision jitter. */
    private static final VoxelShape PIPES = box(1, 14, 1, 15, 15, 15);

    /** Canister slot center positions in pixel coordinates (XZ only), indexed by slot. */
    public static final double[][] SLOT_CENTERS = {
        { 8.0,  2.0},  // slot 0 (N)
        {13.0,  3.0},  // slot 1 (NE)
        {14.0,  8.0},  // slot 2 (E)
        {13.0, 13.0},  // slot 3 (SE)
        { 8.0, 14.0},  // slot 4 (S)
        { 3.0, 13.0},  // slot 5 (SW)
        { 2.0,  8.0},  // slot 6 (W)
        { 3.0,  3.0},  // slot 7 (NW)
    };

    /** Max XZ pixel distance from a slot center to count as a hit (4px covers 4x4 canister). */
    private static final double MAX_SLOT_DISTANCE = 4.0;
    /** Sentinel value: no matching slot found. */
    private static final int NO_SLOT = -1;

    /** Per-slot shapes, indexed 0-7. */
    private static final VoxelShape[] SLOT_SHAPES = {
        SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6, SLOT_7
    };

    /** Base structure shape (always visible): base slab + spindle + intake + pipes. */
    private static final VoxelShape FRAME = Shapes.or(BASE, SPINDLE, INTAKE, PIPES);

    /** Number of canister slots on the hub. */
    public static final int SLOT_COUNT = SLOT_SHAPES.length;

    /** Creates a new hub block.
     *
     * @param properties the block properties
     */
    public HubBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HAS_GASKET, false));
    }

    /** Registers the has_gasket blockstate property.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_GASKET);
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    /** Returns MODEL render shape since the hub uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) { return RenderShape.MODEL; }

    /** Returns the VoxelShape for a specific canister slot.
     *
     * @param slot the slot index
     * @return the voxel shape
     */
    public static VoxelShape slotShape(int slot) {
        if (slot < 0 || slot >= SLOT_SHAPES.length) { return Shapes.empty(); }
        return SLOT_SHAPES[slot];
    }

    /** Returns the frame shape (base + spindle + intake + pipes).
     *
     * @return the voxel shape
     */
    public static VoxelShape frameShape() {
        return FRAME;
    }

    /**
     * Selection shape: frame + occupied canister slots only.
     * Custom outline rendering is handled by {@link com.mercuriusxeno.goo.client.SlotOutlineRenderer}.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof HubBlockEntity be) {
            return be.getCachedShape();
        }
        return FRAME;
    }

    /**
     * Pick block: if a canister slot is targeted, returns a copy of that canister.
     * Otherwise returns the hub block item.
     *
     * @param level       the current level
     * @param pos         the block position
     * @param state       the block state
     * @param includeData true to include data components
     * @return the clone item stack
     */
    @Override
    protected @NonNull ItemStack getCloneItemStack(
            @NonNull LevelReader level, @NonNull BlockPos pos,
            @NonNull BlockState state, boolean includeData) {
        if (!(level.getBlockEntity(pos) instanceof HubBlockEntity hub)) {
            return super.getCloneItemStack(level, pos, state, includeData);
        }
        ItemStack targeted = resolveTargetedCanister(hub, pos);
        if (!targeted.isEmpty()) { return targeted; }
        return super.getCloneItemStack(level, pos, state, includeData);
    }

    /**
     * Resolves the canister stack under the player's crosshair, if any.
     *
     * @param hub the hub block entity
     * @param pos the block position
     * @return a copy of the targeted canister, or empty if none targeted
     */
    private static ItemStack resolveTargetedCanister(HubBlockEntity hub, BlockPos pos) {
        var hit = ISidedProxy.get().getCrosshairHit();
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
            return ItemStack.EMPTY;
        }
        int slot = hitSlot(blockHit, pos);
        if (slot < 0) { return ItemStack.EMPTY; }
        ItemStack canister = hub.getCanister(slot);
        return canister.isEmpty() ? ItemStack.EMPTY : canister.copy();
    }

    /** Creates the hub block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new HubBlockEntity(pos, state);
    }

    /** Registers the server-side tick dispatcher for per-slot gasket push.
     *
     * @param level the current level
     * @param state the block state
     * @param type  the goo type
     * @return the ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.HUB.get(), HubBlockEntity::serverTick);
    }

    // --- Interactions ---

    /** Classifies the held item and dispatches to the appropriate hub interaction handler.
     *
     * @param stack     the item stack
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hand      the hand used
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return GooBlockInteraction.handleItemInteraction(
                stack, level, pos, player, hand, hitResult,
                HubBlockEntity.class,
                t -> t == null,
                this::dispatchHub);
    }

    /** Dispatches a validated interaction to the appropriate handler method.
     *
     * @param interaction the classified interaction type
     * @param hub         the hub block entity
     * @param stack       the item stack
     * @param player      the interacting player
     * @param hand        the hand used
     * @param hitResult   the ray trace hit result
     * @param pos         the block position
     * @param level       the current level
     * @return the interaction result
     */
    private InteractionResult dispatchHub(
            GooInteractionType interaction, HubBlockEntity hub, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult, BlockPos pos, Level level) {
        return switch (interaction) {
            case TUNER_PASS       -> throw new IllegalStateException(ERR_TUNER_PASS);
            case CANISTER_INSERT  -> handleCanisterInsert(hub, hitResult, stack, player);
            case BLOB_INSERT      -> handleBlobInsert(hub, hitResult, stack, player);
            case BUCKET_INSERT    -> handleBucketInsert(hub, hitResult, stack, player, hand);
            case BUCKET_EXTRACT   -> handleBucketExtract(hub, hitResult, stack, player);
        };
    }

    /** Empty-hand interaction: sneak removes gasket, otherwise removes targeted canister.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos,
            @NonNull Player player, @NonNull BlockHitResult hitResult) {
        InteractionResult earlyOut = GooBlockInteraction.validateEmptyHand(level, pos, player);
        if (earlyOut != null) { return earlyOut; }
        if (!(level.getBlockEntity(pos) instanceof HubBlockEntity hub)) { return InteractionResult.PASS; }

        if (player.isShiftKeyDown() && state.getValue(HAS_GASKET)) {
            return removeGasket(level, pos, hub);
        }
        return removeCanister(hub, hitResult, pos, player, level);
    }

    /**
     * Removes the intake gasket from the hub and drops it.
     *
     * @param level the current level
     * @param pos   the block position
     * @param hub   the hub block entity
     * @return SUCCESS after removing the gasket
     */
    private static InteractionResult removeGasket(Level level, BlockPos pos, HubBlockEntity hub) {
        GasketInstallation.popGasket(level, pos, hub.getGasketId(GasketRole.RECEIVER));
        hub.clearGasket(GasketRole.RECEIVER);
        return InteractionResult.SUCCESS;
    }

    /**
     * Removes the canister from the targeted hub slot and gives it to the player.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param pos       the block position
     * @param player    the interacting player
     * @param level     the current level
     * @return SUCCESS if a canister was removed, PASS otherwise
     */
    private static InteractionResult removeCanister(
            HubBlockEntity hub, BlockHitResult hitResult, BlockPos pos, Player player, Level level) {
        int slot = hitSlot(hitResult, pos);
        if (slot < 0) { return InteractionResult.PASS; }

        ItemStack removed = hub.removeCanister(slot);
        if (removed.isEmpty()) { return InteractionResult.PASS; }

        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        InteractionCooldown.markInteraction(player.getUUID(), level.getGameTime());
        return InteractionResult.SUCCESS;
    }

    // --- Handlers ---

    /** Inserts a canister item into the best slot resolved from the hit result.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleCanisterInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        if (!tryInsertCanister(hub, hitResult, stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        stack.consume(1, player);
        InteractionCooldown.markInteraction(player.getUUID(), hub.getLevel().getGameTime());
        return InteractionResult.SUCCESS;
    }

    /**
     * Attempts to insert a canister, preferring the hit slot then falling back to first empty.
     *
     * @param hub the hub block entity
     * @param hitResult the block hit result for slot targeting
     * @param stack the canister item stack
     * @return true if the canister was inserted
     */
    private static boolean tryInsertCanister(
            HubBlockEntity hub, BlockHitResult hitResult, ItemStack stack) {
        int slot = hitSlot(hitResult, hub.getBlockPos());
        return (slot >= 0 && hub.insertCanister(slot, stack.copy()))
                || hub.insertCanister(stack.copy());
    }

    /** Inserts goo from a blob or omniblob item into the first accepting hub slot.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBlobInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return InteractionResult.PASS; }
        long volume = BlobStacks.volumeOf(stack);

        long accepted = insertBlobGoo(hub, hitResult, type, volume);
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Finds the best slot and inserts blob goo into it.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @param type      the goo type to insert
     * @param volume    the volume of goo in microblobs
     * @return the volume accepted, or 0 if no slot accepted
     */
    private static long insertBlobGoo(HubBlockEntity hub, BlockHitResult hitResult, GooType type, long volume) {
        var pos = hub.getBlockPos();
        int slot = GooBlockInteraction.findSlot(
                hitSlot(hitResult, pos), HubBlockEntity.MAX_CANISTERS, hub::canAccept);
        if (slot < 0) { return 0; }
        return hub.insertGoo(slot, type, volume);
    }

    /** Pours goo from a bucket of goo into matching hub canister slots.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hand      the hand used
     * @return the interaction result
     */
    private static InteractionResult handleBucketInsert(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player, InteractionHand hand) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty()) { return InteractionResult.PASS; }

        GooContents remainder = pourBucketIntoHub(hub, hitResult, bucketGoo);
        if (remainder == bucketGoo) { return InteractionResult.PASS; }

        BucketOfGooItem.setOrRevert(stack, remainder, player, hand);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Pours each goo type from the bucket into matching hub slots.
     * Returns the updated contents after insertions, or the original if nothing was inserted.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @param bucketGoo the bucket's goo contents
     * @return updated contents after pour, or the original instance if nothing was inserted
     */
    private static GooContents pourBucketIntoHub(HubBlockEntity hub, BlockHitResult hitResult, GooContents bucketGoo) {
        int hitSlotIdx = hitSlot(hitResult, hub.getBlockPos());
        GooContents result = bucketGoo;
        for (var entry : bucketGoo.getAll().entrySet()) {
            result = pourSingleType(hub, hitSlotIdx, entry.getKey(), entry.getValue(), result);
        }
        return result;
    }

    /**
     * Attempts to pour a single goo type into the hub, updating the remaining contents.
     *
     * @param hub        the hub block entity
     * @param hitSlotIdx the preferred slot from the hit result
     * @param type       the goo type to pour
     * @param volume     the volume available
     * @param contents   the current remaining bucket contents
     * @return updated contents with any accepted volume removed
     */
    private static GooContents pourSingleType(
            HubBlockEntity hub, int hitSlotIdx, GooType type, long volume, GooContents contents) {
        int slot = GooBlockInteraction.findSlot(
                hitSlotIdx, HubBlockEntity.MAX_CANISTERS, hub::canAccept);
        if (slot < 0) { return contents; }
        long accepted = hub.insertGoo(slot, type, volume);
        if (accepted <= 0) { return contents; }
        return contents.withRemoved(type, accepted);
    }

    /** Extracts goo from the first non-empty hub slot into an empty bucket.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result
     * @param stack     the item stack
     * @param player    the interacting player
     * @return the interaction result
     */
    private static InteractionResult handleBucketExtract(
            HubBlockEntity hub, BlockHitResult hitResult,
            ItemStack stack, Player player) {
        int slot = findNonEmptySlot(hub, hitResult);
        if (slot < 0) { return InteractionResult.PASS; }

        ItemStack filledBucket = extractLargestGooType(hub, slot);
        if (filledBucket == null) { return InteractionResult.PASS; }

        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        hub.getLevel().playSound(null, hub.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Finds the first non-empty canister slot using hit-slot preference.
     *
     * @param hub       the hub block entity
     * @param hitResult the ray trace hit result for slot targeting
     * @return slot index, or -1 if no non-empty slot found
     */
    private static int findNonEmptySlot(HubBlockEntity hub, BlockHitResult hitResult) {
        var pos = hub.getBlockPos();
        return GooBlockInteraction.findSlot(hitSlot(hitResult, pos),
                HubBlockEntity.MAX_CANISTERS,
                i -> !hub.getSlotGooContents(i).isEmpty());
    }

    /**
     * Extracts the largest goo type from a hub slot into a filled bucket.
     *
     * @param hub  the hub block entity
     * @param slot the slot index to extract from
     * @return a filled bucket item stack, or null if extraction failed
     */
    @Nullable
    private static ItemStack extractLargestGooType(HubBlockEntity hub, int slot) {
        GooContents slotGoo = hub.getSlotGooContents(slot);
        GooType type = slotGoo.largestType();
        if (type == null) { return null; }
        long extracted = hub.extractGoo(slot, type, slotGoo.getVolume(type));
        if (extracted <= 0) { return null; }
        return BucketOfGooItem.createWithGoo(type, extracted);
    }

    /** Drops the intake gasket item on break if one is installed.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state
     * @param player the interacting player
     * @return the block state
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && state.getValue(HAS_GASKET)) {
            popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Determines which canister slot the player clicked by nearest-center detection.
     * Converts the hit position to block-local pixel coords and finds the closest slot.
     *
     * @param hit the block hit result from the interaction
     * @param pos the block position
     * @return slot index 0-7, or -1 if click was too far from any slot
     */
    public static int hitSlot(BlockHitResult hit, BlockPos pos) {
        double pixelX = (hit.getLocation().x - pos.getX()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        double pixelZ = (hit.getLocation().z - pos.getZ()) * ShapeHitCheck.PIXELS_PER_BLOCK;
        return nearestSlot(pixelX, pixelZ);
    }

    /**
     * Finds the nearest canister slot to the given pixel coordinates.
     *
     * @param pixelX x position in block-local pixel space (0-16)
     * @param pixelZ z position in block-local pixel space (0-16)
     * @return slot index 0-7, or -1 if beyond {@link #MAX_SLOT_DISTANCE}
     */
    public static int nearestSlot(double pixelX, double pixelZ) {
        int best = NO_SLOT;
        double bestDist = MAX_SLOT_DISTANCE * MAX_SLOT_DISTANCE;
        for (int i = 0; i < SLOT_CENTERS.length; i++) {
            double distSq = slotDistanceSq(pixelX, pixelZ, i);
            if (distSq < bestDist) {
                bestDist = distSq;
                best = i;
            }
        }
        return best;
    }

    /**
     * Computes the squared distance from a pixel coordinate to a slot center.
     *
     * @param pixelX the x position in block-local pixel space
     * @param pixelZ the z position in block-local pixel space
     * @param slot   the slot index
     * @return the squared Euclidean distance
     */
    private static double slotDistanceSq(double pixelX, double pixelZ, int slot) {
        double dx = pixelX - SLOT_CENTERS[slot][0];
        double dz = pixelZ - SLOT_CENTERS[slot][1];
        return dx * dx + dz * dz;
    }
}
