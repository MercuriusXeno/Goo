package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.BucketOfGooItem;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

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

    /** Redstone signal present: crucible is disabled when true. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** Whether the crucible is actively melting (drives on/off model state). */
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
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
            .setValue(POWERED, false)
            .setValue(LIT, false)
            .setValue(HAS_GASKET, false));
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
        builder.add(POWERED, LIT, HAS_GASKET);
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

    /** Dispatches held-item interactions: fuel, bucket, canister, or blob insertion.
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
            return wouldHandleItem(stack)
                ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (tryInsertFuel(stack, crucible, player)) { return InteractionResult.SUCCESS; }
        if (tryPourBucket(stack, crucible, player, hand)) { return InteractionResult.SUCCESS; }
        if (tryFillBucket(stack, crucible, player)) { return InteractionResult.SUCCESS; }
        if (tryCollectWithCanister(stack, crucible)) { return InteractionResult.SUCCESS; }
        if (tryInsertBlob(stack, crucible, player)) { return InteractionResult.SUCCESS; }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Returns true if this item type would be handled by the crucible on the server.
     *
     * @param stack the item stack
     * @return true if the condition is met
     */
    private static boolean wouldHandleItem(ItemStack stack) {
        return isFuelItem(stack)
            || stack.is(Items.BUCKET)
            || isGooCarrier(stack);
    }

    /**
     * Returns true if the stack holds a goo carrier item (bucket, canister, blob, or omniblob).
     *
     * @param stack the item stack to test
     * @return true if the item is a goo carrier
     */
    private static boolean isGooCarrier(ItemStack stack) {
        return stack.getItem() instanceof BucketOfGooItem
            || stack.getItem() instanceof CanisterItem
            || stack.getItem() instanceof GooBlobItem
            || stack.getItem() instanceof GooOmniblobItem;
    }

    /** Inserts a fuel rod only if the platform is empty.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if fuel item of insert fuel
     */
    private boolean tryInsertFuel(ItemStack stack, CrucibleBlockEntity crucible,
            Player player) {
        if (!isFuelItem(stack)) { return false; }
        if (crucible.hasFuel()) { return false; }
        crucible.addFuel(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /** Returns true if the item can be inserted as crucible fuel.
     *
     * @param stack the item stack
     * @return the new bucket from reservoir of fill bucket of pour bucket
     */
    private static boolean isFuelItem(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.getItem() instanceof DepletedBlazeRodItem;
    }

    /** Pours a filled goo bucket into the crucible reservoir, reverting to vanilla bucket.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @param hand     the hand used
     * @return the interaction result of insert blob of collect with canister
     */
    private boolean tryPourBucket(ItemStack stack, CrucibleBlockEntity crucible,
            Player player, InteractionHand hand) {
        if (!(stack.getItem() instanceof BucketOfGooItem)) { return false; }
        GooContents contents = BucketOfGooItem.getContents(stack);
        if (contents.isEmpty()) { return false; }

        crucible.insertGooContents(contents);
        BucketOfGooItem.setOrRevert(stack, GooContents.EMPTY, player, hand);
        return true;
    }

    /** Fills a vanilla bucket with the entire reservoir contents as slurry.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return true if the condition is met
     */
    private boolean tryFillBucket(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        if (!stack.is(Items.BUCKET)) { return false; }
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return false; }

        ItemStack filledBucket = createBucketFromReservoir(res);
        crucible.drainReservoir();
        replaceBucketInHand(stack, filledBucket, player);
        return true;
    }

    /** Creates a bucket of goo item from the given reservoir contents.
     *
     * @param contents the goo contents
     * @return true if wall lever facing of extract goo of remove fuel rod
     */
    private static ItemStack createBucketFromReservoir(GooContents contents) {
        ItemStack bucket = new ItemStack(GooItems.BUCKET_OF_GOO.get());
        BucketOfGooItem.setContents(bucket, contents);
        return bucket;
    }

    /** Replaces a single bucket in the player's hand with the filled result.
     *
     * @param bucket the empty bucket stack
     * @param filled the filled bucket stack
     * @param player the interacting player
     */
    private void replaceBucketInHand(ItemStack bucket, ItemStack filled, Player player) {
        bucket.shrink(1);
        PlayerUtils.addOrDrop(player, filled);
    }

    /** Collects matching goo type from the reservoir into a canister.
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @return the block state
     */
    private boolean tryCollectWithCanister(ItemStack stack, CrucibleBlockEntity crucible) {
        if (!(stack.getItem() instanceof CanisterItem)) { return false; }
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return false; }
        GooType type = res.largestType();
        if (type == null) { return false; }
        long added = CanisterItem.addGoo(stack, type, res.getVolume(type));
        if (added <= 0) { return false; }
        crucible.extractGoo(type, added);
        return true;
    }


    /** Inserts a goo blob or omniblob directly into the reservoir (bypass, no fuel needed).
     *
     * @param stack    the item stack
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    private boolean tryInsertBlob(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) { return false; }
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) { return false; }
        crucible.insertGoo(type, volume);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
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
            if (state.getValue(HAS_GASKET)) {
                GasketInstallation.popGasket(level, pos, crucible.getGasketId(GasketRole.TRANSMITTER));
                crucible.clearGasket(GasketRole.TRANSMITTER);
                level.setBlock(pos, state.setValue(HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
                return InteractionResult.SUCCESS;
            }
            return tryRemoveFuelRod(crucible, player);
        }
        return tryExtractGoo(crucible, player);
    }

    /** Removes the fuel rod from the crucible and gives it to the player.
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    private InteractionResult tryRemoveFuelRod(CrucibleBlockEntity crucible, Player player) {
        ItemStack rod = crucible.removeFuelRod();
        if (rod.isEmpty()) { return InteractionResult.PASS; }
        PlayerUtils.addOrDrop(player, rod);
        return InteractionResult.SUCCESS;
    }

    /** Extracts the entire reservoir as one item per goo type (blob stack or omniblob).
     *
     * @param crucible the crucible block entity
     * @param player   the interacting player
     * @return the result
     */
    private InteractionResult tryExtractGoo(CrucibleBlockEntity crucible, Player player) {
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) { return InteractionResult.PASS; }

        for (Map.Entry<GooType, Long> entry : res.getAll().entrySet()) {
            BlobStacks.mergeIntoInventory(player, entry.getKey(), entry.getValue());
        }
        crucible.drainReservoir();
        return InteractionResult.SUCCESS;
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

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return; }
        if (!crucible.isEnabled()) { return; }
        if (!crucible.hasFuel()) { return; }

        tryAbsorbItem(itemEntity, crucible);
    }

    /** Attempts to absorb a single item entity into the crucible's melting pool.
     *
     * @param itemEntity the item entity to absorb
     * @param crucible   the crucible block entity
     */
    private static void tryAbsorbItem(ItemEntity itemEntity, CrucibleBlockEntity crucible) {
        ItemStack stack = itemEntity.getItem();
        boolean isGooBlob = stack.getItem() instanceof GooBlobItem
            || stack.getItem() instanceof GooOmniblobItem;
        if (isGooBlob) {
            absorbBlob(itemEntity, stack, crucible);
        } else if (stack.getItem() instanceof PartiallyMeltedItem) {
            absorbPMI(itemEntity, stack, crucible);
        } else if (crucible.getContainerEvaluator().isContainer(stack)) {
            absorbContainer(itemEntity, stack, crucible);
        } else {
            absorbMeltable(itemEntity, stack, crucible);
        }
    }

    /** Re-inserts a dropped PMI's remaining goo directly into the melt pool.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbPMI(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooContents contents = PartiallyMeltedItem.getContents(stack);
        if (contents.isEmpty()) { return; }
        crucible.insertPMI(contents);
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts a blob or omniblob directly into the reservoir, bypassing the melt pipeline.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbBlob(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooType type = BlobStacks.gooTypeOf(stack);
        long volume = BlobStacks.volumeOf(stack);
        if (type == null || volume <= 0) { return; }
        crucible.insertGoo(type, volume);
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts all items in the stack into the PMI pool as one pooled merge.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbMeltable(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        if (crucible.insertItem(stack, stack.getCount())) {
            entity.discard();
            spawnMeltEffects(entity.level(), crucible);
        }
    }

    /**
     * Evaluates a container's contents recursively, merges goo values into
     * the melt pool, and ejects items without goo values as item entities.
     *
     * @param entity   the item entity
     * @param stack    the item stack
     * @param crucible the crucible block entity
     */
    private static void absorbContainer(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        List<ItemStack> ejects = crucible.insertContainer(stack);
        if (ejects == null) { return; }
        entity.discard();
        spawnEjectedItems(entity.level(), crucible.getBlockPos(), ejects);
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Spawns ejected items as item entities above the crucible basin.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param ejects the list of items to eject
     */
    private static void spawnEjectedItems(Level level, BlockPos pos,
            List<ItemStack> ejects) {
        for (ItemStack eject : ejects) {
            popResource(level, pos.above(), eject);
        }
    }

    /** Spawns smoke particles and plays a sizzle sound when an item is absorbed.
     *
     * @param level    the current level
     * @param crucible the crucible block entity
     */
    private static void spawnMeltEffects(Level level, CrucibleBlockEntity crucible) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        BlockPos pos = crucible.getBlockPos();
        CrucibleParticleHelper.spawnMeltSmoke(serverLevel, pos);
        if (crucible.shouldPlaySizzle(level.getGameTime())) {
            CrucibleParticleHelper.playSizzle(serverLevel, pos);
        }
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
        dropCrucibleContents(level, pos);
        dropGasket(state, level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops a gasket item if one is installed on the crucible.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     */
    private void dropGasket(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(HAS_GASKET)) {
            popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
    }

    /** Drops all crucible internal state as items when the block is broken.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void dropCrucibleContents(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) { return; }

        dropMeltingItem(crucible, level, pos);
        dropFuelRod(crucible, level, pos);
        dropReservoirAsBlobs(crucible, level, pos);
    }

    /** Drops the PMI with its remaining goo if present.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private void dropMeltingItem(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack pmi = crucible.getMeltingItem();
        if (!pmi.isEmpty()) {
            popResource(level, pos, pmi);
        }
    }

    /** Drops the depleted fuel rod if present.
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private void dropFuelRod(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack rod = crucible.getFuelRod();
        if (!rod.isEmpty()) {
            popResource(level, pos, rod);
        }
    }

    /** Drops reservoir contents as one item per goo type (blob stack or omniblob).
     *
     * @param crucible the crucible block entity
     * @param level    the current level
     * @param pos      the block position
     */
    private void dropReservoirAsBlobs(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        BlobStacks.dropAll(crucible.getReservoir(), level, pos);
    }

}
