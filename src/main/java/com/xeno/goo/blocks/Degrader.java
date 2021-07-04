package com.xeno.goo.blocks;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.DegraderTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Random;

import static net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class Degrader extends BlockWithConnections {
    public Degrader() {
        super(Properties.create(Material.IRON)
                .sound(SoundType.METAL)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        setDefaultState(this.getDefaultState()
                .with(HORIZONTAL_FACING, Direction.NORTH)
                .with(BlockStateProperties.POWERED, true)
        );
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
        return VoxelShapes.fullCube();
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
        return !state.get(BlockStateProperties.POWERED) ? 12 : 0;
    }

    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand)
    {
        if (!stateIn.get(BlockStateProperties.POWERED)) {
            double d0 = pos.getX() + 0.5D;
            double d1 = pos.getY();
            double d2 = pos.getZ() + 0.5D;
            if (rand.nextDouble() < 0.1D) {
                AudioHelper.tileAudioEvent(worldIn, pos, Registry.DEGRADER_SOUND.get(), SoundCategory.BLOCKS, 1.0F, AudioHelper.PitchFormulas.FlatOne);
            }

            Direction direction = stateIn.get(HORIZONTAL_FACING);
            Direction.Axis axis = direction.getAxis();
            double d4 = rand.nextDouble() * 0.6D - 0.3D;
            double d5 = axis == Direction.Axis.X ? (double)direction.getXOffset() * 0.52D : d4;
            double d6 = rand.nextDouble() * 6.0D / 16.0D;
            double d7 = axis == Direction.Axis.Z ? (double)direction.getZOffset() * 0.52D : d4;
            worldIn.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new DegraderTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, context.getWorld().isBlockPowered(context.getPos()))
                .with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING, BlockStateProperties.POWERED);
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
                    worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
                }
            }
        }
    }

    private static final Direction[] RELEVANT_DIRECTIONS = { Direction.UP, Direction.SOUTH, Direction.DOWN};
    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
        }
    }
}
