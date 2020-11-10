package com.xeno.goo.blocks;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.CrucibleTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Random;

public class Crucible extends BlockWithConnections {
    VoxelShape shape;

    public Crucible() {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(4.0f)
                .notSolid()
        );
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.POWERED, true)
        );
        shape = makeShape();
    }

    private VoxelShape makeShape()
    {
        Vector3d cs = new Vector3d(1d, 1d, 1d);
        Vector3d ce = new Vector3d(15d, 15d, 15d);
        Vector3d bs = new Vector3d (5d, 0d, 5d);
        Vector3d be = new Vector3d (11d, 1d, 11d);
        Vector3d ts = new Vector3d (5d, 15d, 5d);
        Vector3d te = new Vector3d (11d, 16d, 11d);
        Vector3d es = new Vector3d(15d, 5d, 5d);
        Vector3d ee = new Vector3d(16d, 11d, 11d);
        Vector3d ws = new Vector3d(0d, 5d, 5d);
        Vector3d we = new Vector3d(1d, 11d, 11d);
        Vector3d ss = new Vector3d(5d, 5d, 15d);
        Vector3d se = new Vector3d(11d, 11d, 16d);
        Vector3d ns = new Vector3d(5d, 5d, 0d);
        Vector3d ne = new Vector3d(11d, 11d, 1d);

        VoxelShape central = VoxelHelper.cuboid(cs, ce);
        VoxelShape bottom = VoxelHelper.cuboid(bs, be);
        VoxelShape top = VoxelHelper.cuboid(ts, te);
        VoxelShape east = VoxelHelper.cuboid(es, ee);
        VoxelShape west = VoxelHelper.cuboid(ws, we);
        VoxelShape south = VoxelHelper.cuboid(ss, se);
        VoxelShape north = VoxelHelper.cuboid(ns, ne);

        return VoxelHelper.mergeAll(central, top, bottom, east, west, south, north);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        return shape;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos);
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return !state.get(BlockStateProperties.POWERED) ? 12 : 0;
    }

    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand)
    {
        if (!stateIn.get(BlockStateProperties.POWERED)) {
            double d0 = pos.getX() + 0.5D;
            double d1 = pos.getY();
            double d2 = pos.getZ() + 0.5D;
            if (rand.nextDouble() < 0.1D) {
                AudioHelper.tileAudioEvent(worldIn, pos, Registry.GOO_CRUCIBLE_SOUND.get(), SoundCategory.BLOCKS, 1.0F, AudioHelper.PitchFormulas.FlatOne);
            }

            for(Direction direction : Direction.values()) {
                if (direction.getAxis().isVertical()) {
                    continue;
                }
                Direction.Axis axis = direction.getAxis();
                double d4 = rand.nextDouble() * 0.6D - 0.3D;
                double d5 = axis == Direction.Axis.X ? (double)direction.getXOffset() * 0.52D : d4;
                double d6 = rand.nextDouble() * 6.0D / 16.0D;
                double d7 = axis == Direction.Axis.Z ? (double)direction.getZOffset() * 0.52D : d4;
                worldIn.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new CrucibleTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, context.getWorld().isBlockPowered(context.getPos()));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (!worldIn.isRemote) {
            boolean flag = state.get(BlockStateProperties.POWERED);
            if (flag != worldIn.isBlockPowered(pos)) {
                if (flag) {
                    worldIn.getPendingBlockTicks().scheduleTick(pos, this, 4);
                } else {
                    worldIn.setBlockState(pos, state.func_235896_a_(BlockStateProperties.POWERED), 2);
                }
            }
        }
    }

    private static final Direction[] RELEVANT_DIRECTIONS = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.func_235896_a_(BlockStateProperties.POWERED), 2);
        }
    }
}
