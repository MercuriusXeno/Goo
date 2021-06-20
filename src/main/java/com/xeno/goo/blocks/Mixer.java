package com.xeno.goo.blocks;

import com.xeno.goo.tiles.MixerTile;
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
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.util.Direction.*;

public class Mixer extends BlockWithConnections
{
    VoxelShape[] shapes;

    public Mixer()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        setDefaultState(this.getDefaultState()
                .with(HORIZONTAL_FACING, Direction.NORTH)
                .with(BlockStateProperties.POWERED, true)
        );
        shapes = makeShapes();
    }

    private VoxelShape[] makeShapes()
    {
        Vector3d leftTankStart = new Vector3d(0d, 6d, 0d);
        Vector3d leftTankEnd = new Vector3d(6d, 16d, 16d);

        Vector3d rightTankStart = new Vector3d(10d, 6d, 0d);
        Vector3d rightTankEnd = new Vector3d(16d, 16d, 16d);

        Vector3d bottomTankStart = new Vector3d(0d, 0d, 0d);
        Vector3d bottomTankEnd = new Vector3d(16d, 6d, 16d);

        // x aligned orientation
        VoxelShape[] zShapes = getZCombo(leftTankStart, leftTankEnd, rightTankStart, rightTankEnd, bottomTankStart, bottomTankEnd);
        VoxelShape[] xShapes = getXCombo(leftTankStart, leftTankEnd, rightTankStart, rightTankEnd, bottomTankStart, bottomTankEnd);

        return new VoxelShape[] {fabricateAlignedShape(xShapes), fabricateAlignedShape(zShapes)};
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        return state.get(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.X ?
                shapes[1] : shapes[0];
    }

    private VoxelShape fabricateAlignedShape(VoxelShape[] shapes)
    {
        VoxelShape combo = VoxelShapes.empty();
        for(int i = 0; i < shapes.length; i++) {
            combo = VoxelShapes.combine(combo, shapes[i], IBooleanFunction.OR);
        }
        return combo;
    }

    private VoxelShape[] getXCombo(Vector3d leftTankStart, Vector3d leftTankEnd, Vector3d rightTankStart, Vector3d rightTankEnd, Vector3d bottomTankStart, Vector3d bottomTankEnd)
    {
        VoxelShape vs = Block.makeCuboidShape(leftTankStart.x, leftTankStart.y, leftTankStart.z, leftTankEnd.x, leftTankEnd.y, leftTankEnd.z);
        VoxelShape vs1 = Block.makeCuboidShape(rightTankStart.x, rightTankStart.y, rightTankStart.z, rightTankEnd.x, rightTankEnd.y, rightTankEnd.z);
        VoxelShape vs2 = Block.makeCuboidShape(bottomTankStart.x, bottomTankStart.y, bottomTankStart.z, bottomTankEnd.x, bottomTankEnd.y, bottomTankEnd.z);
        return new VoxelShape[] {vs, vs1, vs2};
    }

    private VoxelShape[] getZCombo(Vector3d leftTankStart, Vector3d leftTankEnd, Vector3d rightTankStart, Vector3d rightTankEnd, Vector3d bottomTankStart, Vector3d bottomTankEnd)
    {
        VoxelShape vs = Block.makeCuboidShape(leftTankStart.z, leftTankStart.y, leftTankStart.x, leftTankEnd.z, leftTankEnd.y, leftTankEnd.x);
        VoxelShape vs1 = Block.makeCuboidShape(rightTankStart.z, rightTankStart.y, rightTankStart.x, rightTankEnd.z, rightTankEnd.y, rightTankEnd.x);
        VoxelShape vs2 = Block.makeCuboidShape(bottomTankStart.z, bottomTankStart.y, bottomTankStart.x, bottomTankEnd.z, bottomTankEnd.y, bottomTankEnd.x);
        return new VoxelShape[] {vs, vs1, vs2};
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new MixerTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, context.getWorld().isBlockPowered(context.getPos()))
                .with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());

    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return !state.get(BlockStateProperties.POWERED) ? 12 : 0;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, BlockStateProperties.POWERED);
    }

    public static final Map<Direction.Axis, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction.Axis a : Direction.Axis.values()) {
            switch (a) {
                case Y:
                    break;
                case X:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {NORTH, SOUTH, DOWN});
                    break;
                case Z:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {EAST, WEST, DOWN});
                    break;
            }
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(HORIZONTAL_FACING).getAxis());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
        }
    }
}
