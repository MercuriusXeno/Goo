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
import net.minecraft.util.math.shapes.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Lobber extends BlockWithConnections
{
    private VoxelShape[] shapes;
    public Lobber()
    {
        super(Properties.create(Material.IRON)
                .sound(SoundType.METAL)
                .hardnessAndResistance(1.0f)
        );
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.TRIGGERED, false)
                .with(BlockStateProperties.FACING, Direction.UP)
        );
        shapes = makeShapes();
    }

    private VoxelShape[] makeShapes() {
        VoxelShape[] result = new VoxelShape[6];
        // north facing default
        Vector3d min = new Vector3d(3, 3, 6);
        Vector3d max = new Vector3d(13, 13, 16);

        for(int i = 0; i < 6; i++) {
            // figure out the fixture by rotation and then mash it together with the base. Slap that in result.
            Direction d = Direction.byIndex(i);
            VoxelShape shape = VoxelHelper.cuboidWithRotation(d, min, max);
            result[i] = shape;
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
        return shapes[state.get(BlockStateProperties.FACING).getIndex()];
    }

    /**
     * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos, context);
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
        return shapes[state.get(BlockStateProperties.FACING).getIndex()];
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
        ((LobberTile) te).tryPushingFluid(worldIn.getBlockState(pos).get(BlockStateProperties.FACING).getOpposite());
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
                .with(BlockStateProperties.FACING, context.getFace());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.TRIGGERED, BlockStateProperties.FACING);
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state) {
        return new Direction[] { state.get(BlockStateProperties.FACING).getOpposite() };
    }
}
