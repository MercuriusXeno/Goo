package com.xeno.goo.blocks;

import com.xeno.goo.client.render.block.HatchOpeningState;
import com.xeno.goo.client.render.block.HatchOpeningState.HatchOpeningStates;
import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class Solidifier extends BlockWithConnections {
    VoxelShape[] closedShapes;
    VoxelShape[] openedShapes;
    VoxelShape[] itemFrameShapes;
    public Solidifier() {
        super(Properties.create(Material.IRON)
                .sound(SoundType.METAL)
                .hardnessAndResistance(2.0f)
                .notSolid());
        setDefaultState(this.getDefaultState()
                .with(HatchOpeningState.OPENING_STATE, HatchOpeningStates.CLOSED)
                .with(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .with(BlockStateProperties.POWERED, true)
        );
        closedShapes = makeShapes(false);
        openedShapes = makeShapes(true);
        itemFrameShapes = makeItemFrameShapes();
    }

    private VoxelShape[] makeShapes(boolean isOpen)
    {
        Vector3d chamberStart = new Vector3d(2, 0, 2);
        Vector3d chamberEnd = new Vector3d(14, 14, 14);
        VoxelShape chamber = VoxelHelper.cuboid(chamberStart, chamberEnd);

        Vector3d sourceStart = new Vector3d(6, 14, 6);
        Vector3d sourceEnd = new Vector3d(10, 16, 10);
        VoxelShape source = VoxelHelper.cuboid(sourceStart, sourceEnd);

        // rotaty bit is here
        Vector3d fuelLineStart = new Vector3d(6, 6, 14);
        Vector3d fuelLineEnd = new Vector3d(10, 10, 16);

        // the difference between open and closed is whether this bit has collision
        Vector3d hollowStart = new Vector3d(2.01d, isOpen ? 0.00d : 0.01d, 2.01d);
        Vector3d hollowEnd = new Vector3d(13.99d, 9.99d, 13.99d);
        VoxelShape hollow = VoxelHelper.cuboid(hollowStart, hollowEnd);

        VoxelShape hollowChamber = VoxelShapes.combineAndSimplify(chamber, hollow, IBooleanFunction.ONLY_FIRST);

        VoxelShape[] result = new VoxelShape[4];
        for(int i = 0; i < 4; i++) {
            Direction d = Direction.byHorizontalIndex(i);
            VoxelShape fuelLine = VoxelHelper.cuboidWithRotation(d, fuelLineStart, fuelLineEnd);
            result[i] = VoxelHelper.mergeAll(hollowChamber, source, fuelLine);
        }

        return result;
    }

    private VoxelShape[] makeItemFrameShapes()
    {
        Vector3d itemFrameStart = new Vector3d(6, 10, 2);
        Vector3d itemFrameEnd = new Vector3d(10, 14, 3);
        VoxelShape[] result = new VoxelShape[4];
        for(int i = 0; i < 4; i++) {
            Direction d = Direction.byHorizontalIndex(i);
            VoxelShape itemFrameTop = VoxelHelper.cuboidWithRotation(d, itemFrameStart, itemFrameEnd);
            result[i] = itemFrameTop;
        }

        return result;
    }

    /**
     * This method is used for the collision shape
     * returning a full cube here so the player doesn't stand on quarter-pixel protrusions
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return this.canCollide ? getShape(state, worldIn, pos, context) : VoxelShapes.empty();
    }

    /**
     * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        int facing = state.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalIndex();
        if (state.get(HatchOpeningState.OPENING_STATE) == HatchOpeningStates.CLOSED) {
            return closedShapes[facing];
        }
        return openedShapes[facing];
    }

    /**
     * This method is used for visual raytraces, so we report what the outline shape is
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getRayTraceShape(BlockState state, IBlockReader reader, BlockPos pos, ISelectionContext context) {
        return getShape(state, reader, pos, context);
    }

    /**
     * This is the override shape used by the raytracer in *all* modes, it changes what face the raytracer reports was hit.
     * We want small protrusions to act like they're *not* protrusions when you hit the thin edges, thus return a larger shape here.
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getRaytraceShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
        return getShape(state, worldIn, pos, ISelectionContext.dummy());
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return 0;
    }

    @Override
    public void addInformation(ItemStack stack, IBlockReader worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        SolidifierTile.addInformation(stack, tooltip);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new SolidifierTile();
    }

    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (!worldIn.isRemote) {
            boolean flag = state.get(BlockStateProperties.POWERED);
            if (flag != worldIn.isBlockPowered(pos)) {
                if (flag) {
                    worldIn.getPendingBlockTicks().scheduleTick(pos, this, 4);
                } else {
                    worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
                }
            }
        }
    }


    public static final Map<Direction, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction a : BlockStateProperties.HORIZONTAL_FACING.getAllowedValues()) {
            RELEVANT_DIRECTIONS.put(a, new Direction[] {a.getOpposite(), Direction.UP});
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(BlockStateProperties.HORIZONTAL_FACING));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, context.getWorld().isBlockPowered(context.getPos()))
                .with(BlockStateProperties.HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite())
                .with(HatchOpeningState.OPENING_STATE, HatchOpeningStates.CLOSED);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.POWERED, HatchOpeningState.OPENING_STATE);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        boolean isClient = false;
        if (worldIn != null && worldIn.isRemote()) {
            isClient = true;
        }

        if (worldIn != null) {
            if (!isInItemFrameBounds(hit, state)) {
                return ActionResultType.PASS;
            }
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof SolidifierTile)) {
                return ActionResultType.PASS;
            }

            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
            if(!isClient) {
                ((SolidifierTile) tile).changeTargetItem(itemToSwap);
            }

            return ActionResultType.func_233537_a_(worldIn.isRemote);
        }
        if (!player.isSneaking()) {
            return ActionResultType.PASS;
        }
        return ActionResultType.func_233537_a_(worldIn.isRemote);
    }

    private boolean isInItemFrameBounds(BlockRayTraceResult hit, BlockState state) {
        AtomicBoolean hitFrame = new AtomicBoolean(false);
        for (int i = 0; i < 4; i++) {
            VoxelShape itemFrameShape = itemFrameShapes[i];
            // minecraft intersection logic is *exclusive* which is 1) mega dumb and 2) breaks this check
            // for this reason we nudge the hit vector to be a very tiny box, which *should* intersect the AABB, if
            // it's supposed to.
            Vector3d hitMin = hit.getHitVec().subtract(0.01d, 0.01d, 0.01d);
            Vector3d hitMax = hit.getHitVec().add(0.01d, 0.01d, 0.01d);
            itemFrameShape.toBoundingBoxList().forEach(
                    (b) -> {
                        if (b.offset(hit.getPos()).intersects(hitMin.x, hitMin.y, hitMin.z, hitMax.x, hitMax.y, hitMax.z)) {
                            hitFrame.set(true);
                        }
                    }
            );
        }
        if (!hitFrame.get()) {
            return false;
        }
        return true;
    }
}
