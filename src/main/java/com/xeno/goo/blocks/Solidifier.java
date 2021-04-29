package com.xeno.goo.blocks;

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

import static net.minecraft.util.Direction.*;

public class Solidifier extends BlockWithConnections {
    VoxelShape[] shapes;
    public Solidifier() {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .setOpaque(((p_test_1_, p_test_2_, p_test_3_) -> false))
                .hardnessAndResistance(4.0f)
                .notSolid());
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.POWERED, true)
                .with(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        );
        shapes = makeShapes();
    }

    double gasketThickness = 0.25d;
    double borderLimit = 16f - gasketThickness;
    double gasketStart = 6d;
    double gasketEnd = 16d - gasketStart;

    private VoxelShape[] makeShapes()
    {
        Vector3d cs = new Vector3d(gasketThickness, 0d, gasketThickness);
        Vector3d ce = new Vector3d(borderLimit, borderLimit, borderLimit);
        Vector3d ts = new Vector3d (gasketStart, borderLimit, gasketStart);
        Vector3d te = new Vector3d (gasketEnd, 16d, gasketEnd);
        Vector3d es = new Vector3d(borderLimit, gasketStart, gasketStart);
        Vector3d ee = new Vector3d(16d, gasketEnd, gasketEnd);
        Vector3d ws = new Vector3d(0d, gasketStart, gasketStart);
        Vector3d we = new Vector3d(gasketThickness, gasketEnd, gasketEnd);
        Vector3d ss = new Vector3d(gasketStart, gasketStart, borderLimit);
        Vector3d se = new Vector3d(gasketEnd, gasketEnd, 16d);
        Vector3d ns = new Vector3d(gasketStart, gasketStart, 0d);
        Vector3d ne = new Vector3d(gasketEnd, gasketEnd, gasketThickness);

        VoxelShape central = VoxelHelper.cuboid(cs, ce);
        VoxelShape top = VoxelHelper.cuboid(ts, te);
        VoxelShape east = VoxelHelper.cuboid(es, ee);
        VoxelShape west = VoxelHelper.cuboid(ws, we);
        VoxelShape south = VoxelHelper.cuboid(ss, se);
        VoxelShape north = VoxelHelper.cuboid(ns, ne);

        return
                new VoxelShape[] {
                        // south
                        VoxelHelper.mergeAll(central, top, east, west),
                        // west
                        VoxelHelper.mergeAll(central, top, south, north),
                        // north
                        VoxelHelper.mergeAll(central, top, east, west),
                        // east
                        VoxelHelper.mergeAll(central, top, south, north),
                };
    }

    /**
     * This method is used for the collision shape
     * returning a full cube here so the player doesn't stand on quarter-pixel protrusions
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return this.canCollide ? VoxelShapes.fullCube() : VoxelShapes.empty();
    }

    /**
     * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return shapes[state.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalIndex()];
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
        return VoxelShapes.fullCube();
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return !state.get(BlockStateProperties.POWERED) ? 15 : 0;
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


    public static final Map<Direction.Axis, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction.Axis a : Direction.Axis.values()) {
            switch (a) {
                case Y:
                    break;
                case X:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {NORTH, SOUTH, UP});
                    break;
                case Z:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {EAST, WEST, UP});
                    break;
            }
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(BlockStateProperties.HORIZONTAL_FACING).getAxis());
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
                .with(BlockStateProperties.HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.POWERED);
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
            if (!isInItemFrameBounds(hit)) {
                return ActionResultType.func_233537_a_(worldIn.isRemote);
            }
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof SolidifierTile)) {
                return ActionResultType.func_233537_a_(worldIn.isRemote);
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

    private static final float horizontalStart = 0.3125f;
    private static final float horizontalEnd = 0.6875f;
    private static final float verticalStart = 0f;
    private static final float verticalEnd = 0.3125f;
    private boolean isInItemFrameBounds(BlockRayTraceResult hit) {
        Direction side = hit.getFace();
        if (side == Direction.UP || side == Direction.DOWN) {
            return false;
        }
        // 'zero out' the hitvec so that we're comparing sort of raw unit values.
        Vector3d adjustedHitVec = hit.getHitVec().add(-hit.getPos().getX(), -hit.getPos().getY(),
                -hit.getPos().getZ());
        // the item frame bounds are between width (x or z) of 0.3125 to 0.6875
        // and height (y) of 0 to 0.3125
        Direction.Axis axis = side.getAxis();
        if (axis == Axis.Z) {
            return isHitInBounds(adjustedHitVec.x, adjustedHitVec.y);
        } else {
            return isHitInBounds(adjustedHitVec.z, adjustedHitVec.y);
        }
    }

    private boolean isHitInBounds(double z, double y) {
        return z >= horizontalStart && z <= horizontalEnd && y >= verticalStart && y <= verticalEnd;
    }
}
