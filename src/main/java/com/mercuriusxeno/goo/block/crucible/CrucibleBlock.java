package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.IGooLightSource;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The crucible block (goocible): melts items into goo. Items for melting are
 * received by detecting item entities landing in the block, not by right-click.
 * Right-click handles fuel rod insertion, blob insertion, canister collection,
 * and empty-hand goo extraction.
 * Drops internal state (PMI, fuel rod, reservoir blobs) when broken.
 *
 * Blockstate properties: POWERED (redstone gating), LIT (active/melting visual),
 * HAS_GASKET (bottom gasket).
 * The goocible model switches between on (LIT=true) and off (LIT=false) states.
 */
public class CrucibleBlock extends BaseEntityBlock {

    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);

    /** Horizontal facing direction - orients the crucible's front (fire-glow) face toward the player. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Redstone signal present: crucible is disabled when true. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** Whether the crucible is actively melting (drives on/off model state). */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    /** Light level emitted by the firebox while LIT (matches the prior
     * Properties.lightLevel(13) value). */
    private static final int CRUCIBLE_LIT_LIGHT = 13;
    /** Whether a gasket is attached to this crucible. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    /** Goocible body: full-width solid base, 13px tall. */
    private static final VoxelShape BODY = box(0, 0, 0, 16, 13, 16);
    /** Goocible rim: 4 walls forming a hollow collar so items can fall inside. */
    private static final VoxelShape RIM = Shapes.or(
        box(2, 13, 2, 14, 16, 4),
        box(2, 13, 12, 14, 16, 14),
        box(2, 13, 4, 4, 16, 12),
        box(12, 13, 4, 14, 16, 12));
    /** Combined collision/outline shape. */
    private static final VoxelShape SHAPE = Shapes.or(BODY, RIM);

    /** Creates a crucible block and registers default blockstate values.
     *
     * @param properties the block properties
     */
    public CrucibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, false)
            .setValue(LIT, false)
            .setValue(HAS_GASKET, false));
    }

    /** Places the crucible so its front face points toward the player.
     *
     * @param context the block placement context
     * @return the state for placement
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Returns the codec for serialization.
     *
     * @return the codec
     */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Registers all crucible blockstate properties.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LIT, HAS_GASKET);
    }

    /** Returns MODEL render shape since the crucible uses a block model.
     *
     * @param state the block state
     * @return the render shape
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Block-light emission combines the LIT firebox glow (the existing
     * 13-light burn) with the BE's emissive-goo contribution; max wins.
     * The LIT path stays state-driven (cheap, no BE lookup); goo emission
     * needs the BE so it routes through {@link IGooLightSource}.
     *
     * @param state the block state
     * @param level the block-getter
     * @param pos   the block position
     * @return combined light level in [0, 15]
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        int burn = state.getValue(LIT) ? CRUCIBLE_LIT_LIGHT : 0;
        int goo = IGooLightSource.blockEmissionFor(level, pos);
        return Math.max(burn, goo);
    }

    /** BE-driven emission: emission depends on the reservoir contents, so
     * NeoForge needs to query with a real BlockGetter+BlockPos rather than
     * probing once with EmptyBlockGetter. */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /** Returns the goocible pot collision/outline shape.
     *
     * @param state   the block state
     * @param level   the current level
     * @param pos     the block position
     * @param context the collision context
     * @return the shape
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /** Enables shape-based light occlusion for the non-full-block crucible.
     *
     * @param state the block state
     * @return true if the condition is met
     */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Returns full block shape so levers can attach to any face via isFaceSturdy.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     * @return the block support shape
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Creates the crucible block entity for this position.
     *
     * @param pos   the block position
     * @param state the block state
     * @return the new block entity
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    /** Registers the server-side melt tick dispatcher.
     *
     * @param level the current level
     * @param state the block state
     * @param type  the goo type
     * @return the ticker
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) { return null; }
        return createTickerHelper(type, GooBlockEntities.CRUCIBLE.get(), CrucibleBlockEntity::serverTick);
    }

    /** Dispatches held-item interactions: fuel, canister, or blob insertion.
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
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return InteractionResult.PASS; }

        if (level.isClientSide()) {
            return clientItemResult(stack);
        }
        return serverItemInteraction(stack, crucible, player, hand);
    }

    /** Returns the client-side result based on whether the item is handled.
     *
     * @param stack the item stack
     * @return SUCCESS if handled, TRY_WITH_EMPTY_HAND otherwise
     */
    private static InteractionResult clientItemResult(ItemStack stack) {
        return CrucibleInteraction.wouldHandleItem(stack)
            ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Attempts each server-side item interaction in priority order.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand used
     * @return SUCCESS if any interaction matched, TRY_WITH_EMPTY_HAND otherwise
     */
    private static InteractionResult serverItemInteraction(
            ItemStack stack, CrucibleBlockEntity crucible, Player player, InteractionHand hand) {
        if (tryAnyFluidInteraction(stack, crucible, player, hand)) { return InteractionResult.SUCCESS; }
        if (CrucibleInteraction.tryCollectWithCanister(stack, crucible)) { return InteractionResult.SUCCESS; }
        if (CrucibleInteraction.tryInsertBlob(stack, crucible, player)) { return InteractionResult.SUCCESS; }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Tries fuel insertion in priority order.
     *
     * @param stack    the held item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand used
     * @return true if any fluid interaction succeeded
     */
    private static boolean tryAnyFluidInteraction(
            ItemStack stack, CrucibleBlockEntity crucible, Player player, InteractionHand hand) {
        return CrucibleInteraction.tryInsertFuel(stack, crucible, player);
    }

    /** Handles empty-hand interactions: shift = gasket/fuel removal, bare = goo extraction.
     *
     * @param state     the block state
     * @param level     the current level
     * @param pos       the block position
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the result
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return InteractionResult.PASS; }

        if (player.isShiftKeyDown()) {
            return shiftClickEmptyHand(state, level, pos, crucible, player);
        }
        return CrucibleInteraction.tryExtractGoo(crucible, player);
    }

    /** Handles shift-click with empty hand: gasket pop or fuel rod removal.
     *
     * @param state    the block state
     * @param level    the current level
     * @param pos      the block position
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the interaction result
     */
    private static InteractionResult shiftClickEmptyHand(
            BlockState state, Level level, BlockPos pos,
            CrucibleBlockEntity crucible, Player player) {
        if (state.getValue(HAS_GASKET)) {
            GasketInstallation.popGasket(level, pos, crucible.getGasketId(GasketRole.TRANSMITTER));
            crucible.clearGasket(GasketRole.TRANSMITTER);
            level.setBlock(pos, state.setValue(HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            return InteractionResult.SUCCESS;
        }
        return CrucibleInteraction.tryRemoveFuelRod(crucible, player);
    }

    // -- Item entity absorption --

    /**
     * Absorbs item entities that land in the basin, feeding them into the melting pipeline.
     * Only absorbs when the crucible is enabled (no redstone) and has fuel.
     *
     * @param state         the block state
     * @param level         the current level
     * @param pos           the block position
     * @param entity        the item entity
     * @param effectApplier the block effect applier
     * @param moving        true if the entity is moving
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean moving) {
        if (level.isClientSide()) { return; }
        if (!(entity instanceof ItemEntity itemEntity)) { return; }
        if (itemEntity.isRemoved()) { return; }
        tryAbsorbItemEntity(level, pos, itemEntity);
    }

    /** Attempts absorption if the crucible is enabled and fueled.
     *
     * @param level      the current level
     * @param pos        the block position
     * @param itemEntity the item entity inside the block
     */
    private static void tryAbsorbItemEntity(Level level, BlockPos pos, ItemEntity itemEntity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return; }
        if (!crucible.isEnabled()) { return; }
        if (!crucible.hasFuel()) { return; }
        CrucibleAbsorption.tryAbsorbItem(itemEntity, crucible);
    }

    // -- Neighbor updates (redstone) --

    /** Updates powered state when neighbors change.
     *
     * @param state         the block state
     * @param level         the current level
     * @param pos           the block position
     * @param neighborBlock the neighbor block that changed
     * @param orientation   the redstone orientation, or null
     * @param movedByPiston true if moved by a piston
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) { return; }

        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), UPDATE_NEIGHBORS | UPDATE_CLIENTS);
        }
    }

    // -- Block break drops --

    /** Drops all crucible internals (PMI, fuel rod, reservoir, gasket) before the block breaks.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param state  the block state
     * @param player the interacting player
     * @return the result
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
            BlockState state, Player player) {
        CrucibleDrops.dropCrucibleContents(level, pos);
        CrucibleDrops.dropGasket(state, level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

}
