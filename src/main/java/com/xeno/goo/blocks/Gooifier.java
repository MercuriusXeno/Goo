package com.xeno.goo.blocks;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooifierTile;
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
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.util.Direction.*;

public class Gooifier extends BlockWithConnections {
    VoxelShape[] shapes;
    public Gooifier() {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(4.0f));
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.POWERED, true)
                .with(BlockStateProperties.HORIZONTAL_FACING, NORTH)
        );
        shapes = makeShapes();
    }

    double gasketThickness = 0.25d;
    double borderLimit = 16f - gasketThickness;
    double gasketStart = 6d;
    double gasketEnd = 16d - gasketStart;

    double hatchSide = 5d;
    double hatchEnd = 11d;
    double hatchBottom = 4d;
    double hatchTop = 9d;
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

        Vector3d hns = new Vector3d(hatchSide, hatchBottom, 0f);
        Vector3d hne = new Vector3d(hatchEnd, hatchTop, gasketThickness);
        Vector3d hss = new Vector3d(hatchSide, hatchBottom, 16f - gasketThickness);
        Vector3d hse = new Vector3d(hatchEnd, hatchTop, 16f);
        Vector3d hws = new Vector3d(0f, hatchBottom, hatchSide);
        Vector3d hwe = new Vector3d(gasketThickness, hatchTop, hatchEnd);
        Vector3d hes = new Vector3d(16f - gasketThickness, hatchBottom, hatchSide);
        Vector3d hee = new Vector3d(16f, hatchTop, hatchEnd);

        VoxelShape central = VoxelHelper.cuboid(cs, ce);
        VoxelShape top = VoxelHelper.cuboid(ts, te);
        VoxelShape east = VoxelHelper.cuboid(es, ee);
        VoxelShape west = VoxelHelper.cuboid(ws, we);
        VoxelShape south = VoxelHelper.cuboid(ss, se);
        VoxelShape north = VoxelHelper.cuboid(ns, ne);

        // hatches
        VoxelShape northHatch = VoxelHelper.cuboid(hns, hne);
        VoxelShape southHatch = VoxelHelper.cuboid(hss, hse);
        VoxelShape westHatch = VoxelHelper.cuboid(hws, hwe);
        VoxelShape eastHatch = VoxelHelper.cuboid(hes, hee);

        return
                new VoxelShape[] {
                        // south
                        VoxelHelper.mergeAll(central, top, east, west, northHatch),
                        // west
                        VoxelHelper.mergeAll(central, top, south, north, eastHatch),
                        // north
                        VoxelHelper.mergeAll(central, top, east, west, southHatch),
                        // east
                        VoxelHelper.mergeAll(central, top, south, north, westHatch),
                };
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        return shapes[state.get(BlockStateProperties.HORIZONTAL_FACING).getHorizontalIndex()];
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
                AudioHelper.tileAudioEvent(worldIn, pos, Registry.GOOIFIER_SOUND.get(), SoundCategory.BLOCKS, 1.0F, AudioHelper.PitchFormulas.FlatOne);
            }

            Direction direction = stateIn.get(HORIZONTAL_FACING);
            Axis axis = direction.getAxis();
            double d4 = rand.nextDouble() * 0.6D - 0.3D;
            double d5 = axis == Axis.X ? (double)direction.getXOffset() * 0.52D : d4;
            double d6 = rand.nextDouble() * 9.0D / 16.0D;
            double d7 = axis == Axis.Z ? (double)direction.getZOffset() * 0.52D : d4;
            worldIn.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new GooifierTile();
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

    public static final Map<Axis, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
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
        return RELEVANT_DIRECTIONS.get(state.get(HORIZONTAL_FACING).getAxis());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.cycleValue(BlockStateProperties.POWERED), 2);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.matchesBlock(newState.getBlock())) {
            TileEntity tileentity = worldIn.getTileEntity(pos);
            if (tileentity instanceof GooifierTile) {
                ((GooifierTile) tileentity).spewItems();
                worldIn.updateComparatorOutputLevel(pos, this);
            }

            super.onReplaced(state, worldIn, pos, newState, isMoving);
        }
    }
}
