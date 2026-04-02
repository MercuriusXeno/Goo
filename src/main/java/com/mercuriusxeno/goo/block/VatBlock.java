package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ChoralGasketItem;
import com.mercuriusxeno.goo.item.ChoralTunerItem;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Stationary bulk goo storage block. Multi-type, large capacity with matrix upgrades.
 * Right-click with blob to insert, empty hand to extract, rune ink to upgrade.
 */
public class VatBlock extends BaseEntityBlock {

    /** Codec for serialization. */
    public static final MapCodec<VatBlock> CODEC = simpleCodec(VatBlock::new);

    /** Whether a gasket is attached to the cap (top face). */
    public static final BooleanProperty GASKET_CAP = BooleanProperty.create("gasket_cap");
    /** Whether a gasket is attached to the base (bottom face). */
    public static final BooleanProperty GASKET_BASE = BooleanProperty.create("gasket_base");
    /** Whether another vat block is directly above. */
    public static final BooleanProperty VAT_ABOVE = BooleanProperty.create("vat_above");
    /** Whether another vat block is directly below. */
    public static final BooleanProperty VAT_BELOW = BooleanProperty.create("vat_below");

    /** Vat collision shape: 14x16x14 cuboid centered in the block. */
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

    /** Y midpoint for determining cap vs base clicks. */
    private static final double FACE_MID_Y = 8.0 / 16.0;

    /** Creates a new vat block with the given properties. */
    public VatBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(GASKET_CAP, false)
            .setValue(GASKET_BASE, false)
            .setValue(VAT_ABOVE, false)
            .setValue(VAT_BELOW, false));
    }

    /** Registers blockstate properties for gaskets and vat stacking. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GASKET_CAP, GASKET_BASE, VAT_ABOVE, VAT_BELOW);
    }

    /** Returns the vat's 14x16x14 collision shape. */
    @Override
    protected @NonNull VoxelShape getShape(
            @NonNull BlockState state, @NonNull BlockGetter level,
            @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    /** Returns the vat codec for serialization. */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Returns MODEL render shape (standard block model). */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /** Creates a new VatBlockEntity for this position. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new VatBlockEntity(pos, state);
    }

    /** Registers the server-side tick dispatcher for gasket push. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.VAT.get(), VatBlockEntity::serverTick);
    }

    // -- Neighbor updates (vat stacking) --

    /** Updates VAT_ABOVE/VAT_BELOW, pops occluded gaskets, and redistributes fluid when a neighbor changes. */
    @Override
    protected void neighborChanged(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Block neighborBlock, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (level.isClientSide()) return;
        BlockState updated = computeStackState(state, level, pos);
        if (updated != state) {
            popOccludedGaskets(state, updated, level, pos);
            level.setBlock(pos, updated, 3);
            // Stack topology changed - redistribute fluid so it settles correctly
            VatStackRedistributor.redistribute(level, pos);
        }
    }

    /** Sets self state, pops occluded gaskets, notifies neighbors, and redistributes fluid when placed. */
    @Override
    protected void onPlace(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide()) return;
        BlockState updated = computeStackState(state, level, pos);
        if (updated != state) {
            popOccludedGaskets(state, updated, level, pos);
            level.setBlock(pos, updated, 3);
        }
        notifyVerticalNeighbors(level, pos);
        // Placed vat may join a stack - redistribute so its fluid settles
        VatStackRedistributor.redistribute(level, pos);
    }

    /**
     * Computes VAT_ABOVE/VAT_BELOW from adjacent blocks.
     * Clears gasket flags for faces that become occluded by stacking.
     */
    private static BlockState computeStackState(BlockState state, Level level, BlockPos pos) {
        boolean above = level.getBlockState(pos.above()).getBlock() instanceof VatBlock;
        boolean below = level.getBlockState(pos.below()).getBlock() instanceof VatBlock;
        BlockState updated = state.setValue(VAT_ABOVE, above).setValue(VAT_BELOW, below);
        if (above && updated.getValue(GASKET_CAP)) {
            updated = updated.setValue(GASKET_CAP, false);
        }
        if (below && updated.getValue(GASKET_BASE)) {
            updated = updated.setValue(GASKET_BASE, false);
        }
        return updated;
    }

    /**
     * Drops a gasket item for each face that had a gasket in the old state
     * but was cleared in the new state due to occlusion.
     */
    private static void popOccludedGaskets(
            BlockState oldState, BlockState newState, Level level, BlockPos pos) {
        if (oldState.getValue(GASKET_CAP) && !newState.getValue(GASKET_CAP)) {
            popResource(level, pos, gasketStack());
        }
        if (oldState.getValue(GASKET_BASE) && !newState.getValue(GASKET_BASE)) {
            popResource(level, pos, gasketStack());
        }
    }

    /** Creates a single choral gasket item stack. */
    private static ItemStack gasketStack() {
        return new ItemStack(GooItems.CHORAL_GASKET.get());
    }

    /** Notifies the vat blocks above and below to re-check their stacking state. */
    private static void notifyVerticalNeighbors(Level level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        if (level.getBlockState(abovePos).getBlock() instanceof VatBlock) {
            level.neighborChanged(abovePos, level.getBlockState(pos).getBlock(), null);
        }
        BlockPos belowPos = pos.below();
        if (level.getBlockState(belowPos).getBlock() instanceof VatBlock) {
            level.neighborChanged(belowPos, level.getBlockState(pos).getBlock(), null);
        }
    }

    // -- Interactions --

    /**
     * Dispatches item-on-vat interactions. Tuner passes through, client returns early,
     * then delegates to the server-side dispatch chain.
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {

        if (stack.getItem() instanceof ChoralTunerItem) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity vat)) return InteractionResult.PASS;

        return dispatchInteraction(vat, stack, state, level, pos, player, hand, hitResult);
    }

    /** Server-side instanceof dispatch chain for item interactions. */
    private InteractionResult dispatchInteraction(
            VatBlockEntity vat, ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof ChoralGasketItem) {
            return handleGasketApply(state, level, pos, stack, player, hitResult);
        }
        if (stack.getItem() instanceof GooBlobItem || stack.getItem() instanceof GooOmniblobItem) {
            return handleBlobInsert(vat, stack, player);
        }
        if (stack.is(Items.BUCKET)) {
            return handleBucketFill(vat, stack, player);
        }
        if (stack.getItem() instanceof BucketOfGooItem) {
            return handleBucketPour(vat, stack, player, hand);
        }
        if (stack.getItem() instanceof CanisterItem) {
            return handleCanisterInteraction(vat, stack);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Applies a gasket to the cap or base face depending on where the player clicked.
     * Click on upper half or UP face -> cap gasket. Lower half or DOWN face -> base gasket.
     * Cannot apply to a face that is occluded by another vat or already has a gasket.
     */
    private InteractionResult handleGasketApply(
            BlockState state, Level level, BlockPos pos, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        BooleanProperty target = resolveGasketFace(hitResult, pos);
        if (state.getValue(target)) return InteractionResult.PASS;
        if (isFaceOccluded(state, target)) return InteractionResult.PASS;
        level.setBlock(pos, state.setValue(target, true), 3);

        // Generate UUID and register in GasketRegistry
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof VatBlockEntity vat) {
            GasketRole role = target == GASKET_CAP ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
            UUID newId = vat.ensureGasketId(role);
            if (newId != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                GasketRegistry registry = GasketRegistry.get(serverLevel);
                registry.updateLocation(newId,
                    new GasketLocation(serverLevel.dimension(), pos,
                        role == GasketRole.RECEIVER, GasketPartner.NO_SLOT));
            }
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** Returns true if the given gasket face is occluded by an adjacent vat. */
    private static boolean isFaceOccluded(BlockState state, BooleanProperty gasketProp) {
        return gasketProp == GASKET_CAP ? state.getValue(VAT_ABOVE) : state.getValue(VAT_BELOW);
    }

    /** Resolves which gasket face the player is targeting based on hit location. */
    private static BooleanProperty resolveGasketFace(BlockHitResult hit, BlockPos pos) {
        if (hit.getDirection() == Direction.UP) return GASKET_CAP;
        if (hit.getDirection() == Direction.DOWN) return GASKET_BASE;
        double localY = hit.getLocation().y - pos.getY();
        return localY >= FACE_MID_Y ? GASKET_CAP : GASKET_BASE;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(
            @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull BlockHitResult hitResult) {

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity vat)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            return handleGasketRemove(state, level, pos, vat, hitResult);
        }

        return handleBlobExtract(vat, player);
    }

    /**
     * Removes the gasket on the targeted face if one is installed.
     * Flips the blockstate, drops the gasket item, and clears the gasket UUID.
     */
    private InteractionResult handleGasketRemove(
            BlockState state, Level level, BlockPos pos, VatBlockEntity vat,
            BlockHitResult hitResult) {
        BooleanProperty target = resolveGasketFace(hitResult, pos);
        if (!state.getValue(target)) return InteractionResult.PASS;

        GasketRole role = target == GASKET_CAP ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        GasketInstallation.popGasket(level, pos, vat.getGasketId(role));
        vat.clearGasket(role);
        level.setBlock(pos, state.setValue(target, false), 3);
        return InteractionResult.SUCCESS;
    }

    /** Inserts a blob or omniblob's volume into the vat. */
    private InteractionResult handleBlobInsert(
            VatBlockEntity vat, ItemStack stack, Player player) {
        GooType blobType = BlobStacks.gooTypeOf(stack);
        if (blobType == null) return InteractionResult.PASS;
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) return InteractionResult.PASS;
        if (!vat.canAccept()) return InteractionResult.PASS;

        long accepted = vat.insertGoo(blobType, volume);
        if (accepted <= 0) return InteractionResult.PASS;

        BlobStacks.deplete(stack, accepted, player);
        return InteractionResult.SUCCESS;
    }

    /** Extracts a full stack (64,000 mB) of the dominant type from the vat into the player's inventory. */
    private InteractionResult handleBlobExtract(VatBlockEntity vat, Player player) {
        if (vat.isEmpty()) return InteractionResult.PASS;

        GooType dominant = vat.getDominantType();
        if (dominant == null) return InteractionResult.PASS;

        long extractAmount = Math.min(vat.getContents().getVolume(dominant), BlobStacks.MAX_BLOB_STACK_VOLUME);
        long extracted = vat.extractGoo(dominant, extractAmount);
        if (extracted <= 0) return InteractionResult.PASS;

        ItemStack output = BlobStacks.createForOutput(dominant, extracted);
        PlayerUtils.addOrDrop(player, output);
        return InteractionResult.SUCCESS;
    }

    /** Fills an empty bucket with up to 8 blobs (8,000 mB) of the dominant goo type from the vat. */
    private static final long BUCKET_FILL_AMOUNT = 8 * BlobStacks.MB_PER_BLOB;

    private InteractionResult handleBucketFill(VatBlockEntity vat, ItemStack stack, Player player) {
        if (vat.isEmpty()) return InteractionResult.PASS;

        GooType dominant = vat.getDominantType();
        if (dominant == null) return InteractionResult.PASS;

        long available = vat.getContents().getVolume(dominant);
        long toExtract = Math.min(available, BUCKET_FILL_AMOUNT);
        long extracted = vat.extractGoo(dominant, toExtract);
        if (extracted <= 0) return InteractionResult.PASS;

        ItemStack filledBucket = BucketOfGooItem.createWithGoo(dominant, extracted);
        stack.shrink(1);
        PlayerUtils.addOrDrop(player, filledBucket);
        return InteractionResult.SUCCESS;
    }

    /** Pours a filled goo bucket into the vat, tracking partial fills. */
    private InteractionResult handleBucketPour(
            VatBlockEntity vat, ItemStack stack, Player player, InteractionHand hand) {
        GooContents bucketGoo = BucketOfGooItem.getContents(stack);
        if (bucketGoo.isEmpty()) return InteractionResult.PASS;
        if (!vat.canAccept()) return InteractionResult.PASS;

        boolean inserted = false;
        for (Map.Entry<GooType, Long> entry : bucketGoo.getAll().entrySet()) {
            long accepted = vat.insertGoo(entry.getKey(), entry.getValue());
            if (accepted > 0) {
                bucketGoo = bucketGoo.withRemoved(entry.getKey(), accepted);
                inserted = true;
            }
        }
        if (!inserted) return InteractionResult.PASS;

        BucketOfGooItem.setOrRevert(stack, bucketGoo, player, hand);
        return InteractionResult.SUCCESS;
    }

    /** Routes canister-vat interaction: dump if canister has fluid, drain if empty. */
    private InteractionResult handleCanisterInteraction(VatBlockEntity vat, ItemStack stack) {
        GooContents canisterContents = CanisterItem.getGooContents(stack);
        if (!canisterContents.isEmpty()) {
            return handleCanisterDump(vat, stack, canisterContents);
        }
        return handleCanisterDrain(vat, stack);
    }

    /** Dumps all canister goo into the vat, removing accepted amounts from the canister. */
    private InteractionResult handleCanisterDump(
            VatBlockEntity vat, ItemStack stack, GooContents canisterContents) {
        if (!vat.canAccept()) return InteractionResult.PASS;

        boolean inserted = false;
        for (Map.Entry<GooType, Long> entry : canisterContents.getAll().entrySet()) {
            long accepted = vat.insertGoo(entry.getKey(), entry.getValue());
            if (accepted > 0) {
                CanisterItem.removeGoo(stack, entry.getKey(), accepted);
                inserted = true;
            }
        }
        return inserted ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Drains the vat's dominant type into an empty canister, up to canister capacity. */
    private InteractionResult handleCanisterDrain(VatBlockEntity vat, ItemStack stack) {
        if (vat.isEmpty()) return InteractionResult.PASS;

        GooType dominant = vat.getDominantType();
        if (dominant == null) return InteractionResult.PASS;

        long capacity = ContainerCapacity.canisterCapacity(
            com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack));
        long space = capacity - CanisterItem.getGooContents(stack).totalVolume();
        if (space <= 0) return InteractionResult.PASS;

        long available = vat.getContents().getVolume(dominant);
        long extracted = vat.extractGoo(dominant, Math.min(available, space));
        if (extracted <= 0) return InteractionResult.PASS;

        CanisterItem.addGoo(stack, dominant, extracted);
        return InteractionResult.SUCCESS;
    }

    // -- Block break drops --

    /**
     * Drops gaskets on break and notifies vertical neighbors.
     * Goo contents are retained in the item via data components.
     */
    @Override
    public @NonNull BlockState playerWillDestroy(
            @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide()) {
            dropGaskets(state, level, pos);
            notifyVerticalNeighbors(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops gasket items for any installed gaskets. */
    private void dropGaskets(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(GASKET_CAP)) {
            popResource(level, pos, gasketStack());
        }
        if (state.getValue(GASKET_BASE)) {
            popResource(level, pos, gasketStack());
        }
    }
}
