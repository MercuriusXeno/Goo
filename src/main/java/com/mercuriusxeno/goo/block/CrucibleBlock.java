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
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
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
 * The crucible block: melts items into goo. Items for melting are received by
 * detecting item entities landing in the basin, not by right-click. Right-click
 * handles rune ink upgrades, fuel rod insertion, blob insertion, canister
 * collection, and empty-hand goo extraction.
 * Drops internal state (PMI, fuel rod, reservoir blobs) when broken.
 *
 * Blockstate properties: POWERED (redstone gating), PLATE_NORTH/SOUTH/EAST/WEST
 * (decorative mounting plates that appear when a lever is attached to that face),
 * HAS_GASKET (bottom gasket), MATRICES (0-5, drives wall/floor model selection).
 */
public class CrucibleBlock extends BaseEntityBlock {

    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);

    /** Redstone signal present: crucible is disabled when true. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** Decorative mounting plate on the north face (lever attached). */
    public static final BooleanProperty PLATE_NORTH = BooleanProperty.create("plate_north");
    /** Decorative mounting plate on the south face (lever attached). */
    public static final BooleanProperty PLATE_SOUTH = BooleanProperty.create("plate_south");
    /** Decorative mounting plate on the east face (lever attached). */
    public static final BooleanProperty PLATE_EAST = BooleanProperty.create("plate_east");
    /** Decorative mounting plate on the west face (lever attached). */
    public static final BooleanProperty PLATE_WEST = BooleanProperty.create("plate_west");
    /** Whether a gasket is attached to this crucible. */
    public static final BooleanProperty HAS_GASKET = BooleanProperty.create("has_gasket");

    /** Crucible outline/collision shape: 4 legs + basin (open-top cauldron shape). */
    private static final VoxelShape SHAPE = makeShape();
    /** Fuel rod pillar shape, used for targeted interaction ray testing. */
    private static final VoxelShape PILLAR_SHAPE = box(6, 0, 6, 10, 9, 10);
    /** Basin Y threshold: hits at or above this are basin interactions. */
    private static final double BASIN_MIN_Y = 9.0 / 16.0;

    /** Builds the composite VoxelShape matching the crucible model geometry. */
    private static VoxelShape makeShape() {
        VoxelShape basin = makeBasinShape();
        VoxelShape feet = Shapes.or(
            box(1, 0, 1, 4, 1, 4),
            box(12, 0, 1, 15, 1, 4),
            box(1, 0, 12, 4, 1, 15),
            box(12, 0, 12, 15, 1, 15)
        );
        VoxelShape legs = Shapes.or(
            box(2, 1, 2, 4, 9, 4),
            box(12, 1, 2, 14, 9, 4),
            box(2, 1, 12, 4, 9, 14),
            box(12, 1, 12, 14, 9, 14)
        );
        VoxelShape platform = box(6, 0, 6, 10, 9, 10);
        return Shapes.or(basin, feet, legs, platform);
    }

    /** Builds the basin as 5 cuboids: 4 walls + recessed floor, matching the model. */
    private static VoxelShape makeBasinShape() {
        VoxelShape south = box(0, 10, 15, 15, 16, 16);
        VoxelShape north = box(1, 10, 0, 16, 16, 1);
        VoxelShape west = box(0, 10, 0, 1, 16, 15);
        VoxelShape east = box(15, 10, 1, 16, 16, 16);
        VoxelShape floor = box(1, 9, 1, 15, 10, 15);
        return Shapes.or(south, north, west, east, floor);
    }

    public CrucibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(POWERED, false)
            .setValue(PLATE_NORTH, false)
            .setValue(PLATE_SOUTH, false)
            .setValue(PLATE_EAST, false)
            .setValue(PLATE_WEST, false)
            .setValue(HAS_GASKET, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, PLATE_NORTH, PLATE_SOUTH, PLATE_EAST, PLATE_WEST, HAS_GASKET);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Returns full block shape so levers can attach to any face via isFaceSturdy. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, GooBlockEntities.CRUCIBLE.get(), CrucibleBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) return InteractionResult.PASS;

        if (level.isClientSide()) {
            return wouldHandleItem(stack)
                ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (hitsPillar(hitResult, pos)) {
            if (tryInsertFuel(stack, crucible, player)) return InteractionResult.SUCCESS;
        }
        if (hitsBasin(hitResult, pos)) {
            if (tryPourBucket(stack, crucible, player, hand)) return InteractionResult.SUCCESS;
            if (tryFillBucket(stack, crucible, player)) return InteractionResult.SUCCESS;
            if (tryCollectWithCanister(stack, crucible)) return InteractionResult.SUCCESS;
            if (tryInsertBlob(stack, crucible, player)) return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Returns true if this item type would be handled by the crucible on the server. */
    private static boolean wouldHandleItem(ItemStack stack) {
        return isFuelItem(stack)
            || stack.is(Items.BUCKET)
            || stack.getItem() instanceof BucketOfGooItem
            || stack.getItem() instanceof CanisterItem
            || stack.getItem() instanceof GooBlobItem
            || stack.getItem() instanceof GooOmniblobItem;
    }

    /** Inserts a fuel rod only if the platform is empty. */
    private boolean tryInsertFuel(ItemStack stack, CrucibleBlockEntity crucible,
            Player player) {
        if (!isFuelItem(stack)) return false;
        if (crucible.hasFuel()) return false;
        crucible.addFuel(stack);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    /** Returns true if the item can be inserted as crucible fuel. */
    private static boolean isFuelItem(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) || stack.getItem() instanceof DepletedBlazeRodItem;
    }

    /** Pours a filled goo bucket into the crucible reservoir, reverting to vanilla bucket. */
    private boolean tryPourBucket(ItemStack stack, CrucibleBlockEntity crucible,
            Player player, InteractionHand hand) {
        if (!(stack.getItem() instanceof BucketOfGooItem)) return false;
        GooContents contents = BucketOfGooItem.getContents(stack);
        if (contents.isEmpty()) return false;

        crucible.insertGooContents(contents);
        BucketOfGooItem.setOrRevert(stack, GooContents.EMPTY, player, hand);
        return true;
    }

    /** Fills a vanilla bucket with the entire reservoir contents as slurry. */
    private boolean tryFillBucket(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        if (!stack.is(Items.BUCKET)) return false;
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) return false;

        ItemStack filledBucket = createBucketFromReservoir(res);
        crucible.drainReservoir();
        replaceBucketInHand(stack, filledBucket, player);
        return true;
    }

    /** Creates a bucket of goo item from the given reservoir contents. */
    private static ItemStack createBucketFromReservoir(GooContents contents) {
        ItemStack bucket = new ItemStack(GooItems.BUCKET_OF_GOO.get());
        BucketOfGooItem.setContents(bucket, contents);
        return bucket;
    }

    /** Replaces a single bucket in the player's hand with the filled result. */
    private void replaceBucketInHand(ItemStack bucket, ItemStack filled, Player player) {
        bucket.shrink(1);
        PlayerUtils.addOrDrop(player, filled);
    }

    /** Collects matching goo type from the reservoir into a canister. */
    private boolean tryCollectWithCanister(ItemStack stack, CrucibleBlockEntity crucible) {
        if (!(stack.getItem() instanceof CanisterItem)) return false;
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) return false;
        GooType type = res.largestType();
        if (type == null) return false;
        long added = CanisterItem.addGoo(stack, type, res.getVolume(type));
        if (added <= 0) return false;
        crucible.extractGoo(type, added);
        return true;
    }


    /** Inserts a goo blob or omniblob directly into the reservoir (bypass, no fuel needed). */
    private boolean tryInsertBlob(ItemStack stack, CrucibleBlockEntity crucible, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) return false;
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0) return false;
        crucible.insertGoo(type, volume);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) return InteractionResult.PASS;

        // Sneak + empty hand with gasket → remove gasket
        if (player.isShiftKeyDown() && state.getValue(HAS_GASKET)) {
            GasketInstallation.popGasket(level, pos, crucible.getGasketId(GasketRole.TRANSMITTER));
            crucible.clearGasket(GasketRole.TRANSMITTER);
            level.setBlock(pos, state.setValue(HAS_GASKET, false), 3);
            return InteractionResult.SUCCESS;
        }

        if (hitsPillar(hitResult, pos)) {
            return tryRemoveFuelRod(crucible, player);
        }
        if (hitsBasin(hitResult, pos)) {
            return tryExtractGoo(crucible, player);
        }
        return InteractionResult.PASS;
    }

    /** Returns true if the hit location is at basin height or above (Y >= 9/16). */
    private static boolean hitsBasin(BlockHitResult hit, BlockPos pos) {
        double ly = hit.getLocation().y - pos.getY();
        return ly >= BASIN_MIN_Y;
    }

    /**
     * Returns true if the hit location falls on the fuel rod pillar.
     * Tests whether the contact point is inside or on the surface of PILLAR_SHAPE's AABB.
     */
    private static boolean hitsPillar(BlockHitResult hit, BlockPos pos) {
        double lx = hit.getLocation().x - pos.getX();
        double ly = hit.getLocation().y - pos.getY();
        double lz = hit.getLocation().z - pos.getZ();
        var bounds = PILLAR_SHAPE.bounds();
        return lx >= bounds.minX && lx <= bounds.maxX
            && ly >= bounds.minY && ly <= bounds.maxY
            && lz >= bounds.minZ && lz <= bounds.maxZ;
    }

    /** Removes the fuel rod from the crucible and gives it to the player. */
    private InteractionResult tryRemoveFuelRod(CrucibleBlockEntity crucible, Player player) {
        ItemStack rod = crucible.removeFuelRod();
        if (rod.isEmpty()) return InteractionResult.PASS;
        PlayerUtils.addOrDrop(player, rod);
        return InteractionResult.SUCCESS;
    }

    /** Extracts the entire reservoir as one item per goo type (blob stack or omniblob). */
    private InteractionResult tryExtractGoo(CrucibleBlockEntity crucible, Player player) {
        GooContents res = crucible.getReservoir();
        if (res.isEmpty()) return InteractionResult.PASS;

        for (Map.Entry<GooType, Long> entry : res.getAll().entrySet()) {
            ItemStack output = BlobStacks.createForOutput(entry.getKey(), entry.getValue());
            PlayerUtils.addOrDrop(player, output);
        }
        crucible.drainReservoir();
        return InteractionResult.SUCCESS;
    }

    // -- Item entity absorption --

    /**
     * Absorbs item entities that land in the basin, feeding them into the melting pipeline.
     * Only absorbs when the crucible is enabled (no redstone) and has fuel.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean moving) {
        if (level.isClientSide()) return;
        if (!(entity instanceof ItemEntity itemEntity)) return;
        if (itemEntity.isRemoved()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) return;
        if (!crucible.isEnabled()) return;
        if (!crucible.hasFuel()) return;

        tryAbsorbItem(itemEntity, crucible);
    }

    /** Attempts to absorb a single item entity into the crucible's melting pool. */
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

    /** Re-inserts a dropped PMI's remaining goo directly into the melt pool. */
    private static void absorbPMI(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooContents contents = PartiallyMeltedItem.getContents(stack);
        if (contents.isEmpty()) return;
        crucible.insertPMI(contents);
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts a blob or omniblob directly into the reservoir, bypassing the melt pipeline. */
    private static void absorbBlob(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        GooType type = BlobStacks.gooTypeOf(stack);
        long volume = BlobStacks.volumeOf(stack);
        if (type == null || volume <= 0) return;
        crucible.insertGoo(type, volume);
        entity.discard();
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Inserts all items in the stack into the PMI pool as one pooled merge. */
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
     */
    private static void absorbContainer(ItemEntity entity, ItemStack stack,
            CrucibleBlockEntity crucible) {
        List<ItemStack> ejects = crucible.insertContainer(stack);
        if (ejects == null) return;
        entity.discard();
        spawnEjectedItems(entity.level(), crucible.getBlockPos(), ejects);
        spawnMeltEffects(entity.level(), crucible);
    }

    /** Spawns ejected items as item entities above the crucible basin. */
    private static void spawnEjectedItems(Level level, BlockPos pos,
            List<ItemStack> ejects) {
        for (ItemStack eject : ejects) {
            Block.popResource(level, pos.above(), eject);
        }
    }

    /** Spawns smoke particles and plays a sizzle sound when an item is absorbed. */
    private static void spawnMeltEffects(Level level, CrucibleBlockEntity crucible) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = crucible.getBlockPos();
        CrucibleParticleHelper.spawnMeltSmoke(serverLevel, pos);
        if (crucible.shouldPlaySizzle(level.getGameTime())) {
            CrucibleParticleHelper.playSizzle(serverLevel, pos);
        }
    }

    // -- Neighbor updates (redstone + lever detection) --

    /** Updates powered state and plate properties when neighbors change. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;

        BlockState updated = state
            .setValue(POWERED, level.hasNeighborSignal(pos))
            .setValue(PLATE_NORTH, hasLeverOnFace(level, pos, Direction.NORTH))
            .setValue(PLATE_SOUTH, hasLeverOnFace(level, pos, Direction.SOUTH))
            .setValue(PLATE_EAST, hasLeverOnFace(level, pos, Direction.EAST))
            .setValue(PLATE_WEST, hasLeverOnFace(level, pos, Direction.WEST));

        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Returns true if a wall lever is attached to the given face of this block.
     * Checks the adjacent block for a lever with FACE=WALL facing away from us.
     */
    private static boolean hasLeverOnFace(Level level, BlockPos pos, Direction face) {
        BlockPos neighborPos = pos.relative(face);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (!(neighbor.getBlock() instanceof LeverBlock)) return false;
        return isWallLeverFacing(neighbor, face);
    }

    /** Returns true if the lever state is a wall lever attached toward the given direction. */
    private static boolean isWallLeverFacing(BlockState lever, Direction face) {
        if (lever.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) != AttachFace.WALL) {
            return false;
        }
        return lever.getValue(LeverBlock.FACING) == face;
    }

    // -- Block break drops --

    /** Drops all crucible internals (PMI, fuel rod, reservoir, gasket) before the block breaks. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
            BlockState state, Player player) {
        dropCrucibleContents(level, pos);
        dropGasket(state, level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Drops a gasket item if one is installed on the crucible. */
    private void dropGasket(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(HAS_GASKET)) {
            Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        }
    }

    /** Drops all crucible internal state as items when the block is broken. */
    private void dropCrucibleContents(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CrucibleBlockEntity crucible)) return;

        dropMeltingItem(crucible, level, pos);
        dropFuelRod(crucible, level, pos);
        dropReservoirAsBlobs(crucible, level, pos);
    }

    /** Drops the PMI with its remaining goo if present. */
    private void dropMeltingItem(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack pmi = crucible.getMeltingItem();
        if (!pmi.isEmpty()) {
            Block.popResource(level, pos, pmi);
        }
    }

    /** Drops the depleted fuel rod if present. */
    private void dropFuelRod(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        ItemStack rod = crucible.getFuelRod();
        if (!rod.isEmpty()) {
            Block.popResource(level, pos, rod);
        }
    }

    /** Drops reservoir contents as one item per goo type (blob stack or omniblob). */
    private void dropReservoirAsBlobs(CrucibleBlockEntity crucible, Level level, BlockPos pos) {
        BlobStacks.dropAll(crucible.getReservoir(), level, pos);
    }

}
