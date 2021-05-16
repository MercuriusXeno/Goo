package com.xeno.goo.blocks;

import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.tiles.LobberTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class Lobber extends BlockWithConnections
{
    VoxelShape[] shapes;

    public Lobber()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        setDefaultState(this.stateContainer.getBaseState()
                .with(BlockStateProperties.TRIGGERED, false)
                .with(BlockStateProperties.FACING, Direction.UP)
        );
        shapes = makeShapes();
    }

    double gasketThickness = 0.25d;
    double borderLimit = 16f - gasketThickness;
    double gasketStart = 6d;
    double gasketEnd = 16d - gasketStart;

    private VoxelShape[] makeShapes()
    {
        Vector3d cs = new Vector3d(gasketThickness, gasketThickness, gasketThickness);
        Vector3d ce = new Vector3d(borderLimit, borderLimit, borderLimit);
        Vector3d bs = new Vector3d (gasketStart, 0d, gasketStart);
        Vector3d be = new Vector3d (gasketEnd, gasketThickness, gasketEnd);
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
        VoxelShape bottom = VoxelHelper.cuboid(bs, be);
        VoxelShape top = VoxelHelper.cuboid(ts, te);
        VoxelShape east = VoxelHelper.cuboid(es, ee);
        VoxelShape west = VoxelHelper.cuboid(ws, we);
        VoxelShape south = VoxelHelper.cuboid(ss, se);
        VoxelShape north = VoxelHelper.cuboid(ns, ne);

        return
                new VoxelShape[] {
                        // down
                        VoxelHelper.mergeAll(central, top, east, west, south, north),
                        // up
                        VoxelHelper.mergeAll(central, bottom, east, west, south, north),
                        // north
                        VoxelHelper.mergeAll(central, bottom, top, east, west, south),
                        // south
                        VoxelHelper.mergeAll(central, bottom, top, east, west, north),
                        // east
                        VoxelHelper.mergeAll(central, bottom, top, west, south, north),
                        // west
                        VoxelHelper.mergeAll(central, bottom, top, east, south, north),
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
        return shapes[state.get(BlockStateProperties.FACING).getIndex()];
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
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side) {
        return true;
    }

    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        boolean isPoweredOrUnderSomethingPowered = worldIn.isBlockPowered(pos) || worldIn.isBlockPowered(pos.up());
        boolean isAlreadyTriggered = state.get(BlockStateProperties.TRIGGERED);
        if (isPoweredOrUnderSomethingPowered && !isAlreadyTriggered) {
            worldIn.getPendingBlockTicks().scheduleTick(pos, this, 4);
            worldIn.setBlockState(pos, state.with(BlockStateProperties.TRIGGERED, Boolean.TRUE), 4);
        } else if (!isPoweredOrUnderSomethingPowered && isAlreadyTriggered) {
            worldIn.setBlockState(pos, state.with(BlockStateProperties.TRIGGERED, Boolean.FALSE), 4);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        this.dispense(worldIn, pos);
    }

    private void dispense(ServerWorld worldIn, BlockPos pos) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof LobberTile)) {
            return;
        }
        ((LobberTile) te).cycleInputsForLob();
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new LobberTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.TRIGGERED, false)
                .with(BlockStateProperties.FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.TRIGGERED, BlockStateProperties.FACING);
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state) {
        Direction[] result = new Direction[5];
        int i = 0;
        for(Direction d : Direction.values()) {
            if (state.get(BlockStateProperties.FACING) == d) {
                continue;
            }
            result[i] = d;
            i++;
        }
        return result;
    }
}
