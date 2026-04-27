package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Stationary bulk goo storage block. Multi-type, large capacity with matrix upgrades.
 * Right-click with blob to insert, empty hand to extract, rune ink to upgrade.
 */
public class VatBlock extends BaseEntityBlock {

    /**
     * Whether a gasket is attached to the cap (top face).
     */
    public static final BooleanProperty GASKET_CAP = BooleanProperty.create("gasket_cap");
    /**
     * Whether a gasket is attached to the base (bottom face).
     */
    public static final BooleanProperty GASKET_BASE = BooleanProperty.create("gasket_base");
    /**
     * Whether another vat block is directly above.
     */
    public static final BooleanProperty VAT_ABOVE = BooleanProperty.create("vat_above");
    /**
     * Whether another vat block is directly below.
     */
    public static final BooleanProperty VAT_BELOW = BooleanProperty.create("vat_below");
    /**
     * Codec for serialization.
     */
    public static final MapCodec<VatBlock> CODEC = simpleCodec(VatBlock::new);
    /**
     * Vat collision shape: 14x16x14 cuboid centered in the block.
     */
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);
    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    /**
     * Creates a new vat block with the given properties.
     *
     * @param properties the block properties
     */
    public VatBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(GASKET_CAP, false)
                .setValue(GASKET_BASE, false)
                .setValue(VAT_ABOVE, false)
                .setValue(VAT_BELOW, false));
    }

    /**
     * Registers blockstate properties for gaskets and vat stacking.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GASKET_CAP, GASKET_BASE, VAT_ABOVE, VAT_BELOW);
    }

    /**
     * Returns the vat's 14x16x14 collision shape.
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
        return SHAPE;
    }

    /**
     * Returns the vat codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Returns MODEL render shape (standard block model).
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Creates a new VatBlockEntity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new VatBlockEntity(pos, state);
    }

    /**
     * Registers the server-side tick dispatcher for gasket push.
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
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, GooBlockEntities.VAT.get(), VatBlockEntity::serverTick);
    }

    // -- Neighbor updates (vat stacking) --

    /**
     * Updates VAT_ABOVE/VAT_BELOW, pops occluded gaskets, and redistributes fluid when a neighbor changes.
     *
     * @param state         the block state
     * @param level         the current level
     * @param pos           the block position
     * @param neighborBlock the neighbor block that changed
     * @param orientation   the redstone orientation, or null
     * @param movedByPiston true if moved by a piston
     */
    @Override
    protected void neighborChanged(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Block neighborBlock, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        BlockState updated = VatGasketOps.computeStackState(state, level, pos);
        if (updated != state) {
            VatGasketOps.popOccludedGaskets(state, updated, level, pos);
            level.setBlock(pos, updated, BLOCK_UPDATE_FLAGS);
            // Stack topology changed - redistribute fluid so it settles correctly
            VatStackRedistributor.redistribute(level, pos);
        }
    }

    /**
     * Sets self state, pops occluded gaskets, notifies neighbors, and redistributes fluid when placed.
     *
     * @param state         the block state
     * @param level         the current level
     * @param pos           the block position
     * @param oldState      the previous block state
     * @param movedByPiston true if moved by a piston
     */
    @Override
    protected void onPlace(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        BlockState updated = VatGasketOps.computeStackState(state, level, pos);
        if (updated != state) {
            VatGasketOps.popOccludedGaskets(state, updated, level, pos);
            level.setBlock(pos, updated, BLOCK_UPDATE_FLAGS);
        }
        VatGasketOps.notifyVerticalNeighbors(level, pos);
        // Placed vat may join a stack - redistribute so its fluid settles
        VatStackRedistributor.redistribute(level, pos);
    }

    // -- Interactions --

    /**
     * Dispatches item-on-vat interactions. Tuner passes through, client returns early,
     * then delegates to the server-side dispatch chain.
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
            @NonNull ItemStack stack, @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {

        if (stack.getItem() instanceof ChoralTunerItem) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity vat)) {
            return InteractionResult.PASS;
        }

        return VatInteractionHandler.dispatchInteraction(vat, stack, player, hand, hitResult);
    }

    /**
     * Empty-hand: sneak removes gasket, otherwise extracts dominant goo type as blobs.
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
            @NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull BlockHitResult hitResult) {

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VatBlockEntity vat)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            return VatInteractionHandler.handleGasketRemove(vat, hitResult);
        }

        return VatInteractionHandler.handleBlobExtract(vat, player);
    }

    // -- Block break drops --

    /**
     * Drops gaskets on break and notifies vertical neighbors.
     * Goo contents are retained in the item via data components.
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
        if (!level.isClientSide()) {
            VatGasketOps.dropGaskets(state, level, pos);
            VatGasketOps.notifyVerticalNeighbors(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
