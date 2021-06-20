package com.xeno.goo.blocks;

import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.tiles.TroughTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class GooTrough extends BlockWithConnections
{
    VoxelShape[] shapes;

    public GooTrough()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        shapes = makeShapes();
        setDefaultState(this.getDefaultState().with(HORIZONTAL_FACING, Direction.NORTH));
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        int index = getIndexFromState(state.get(HORIZONTAL_FACING));
        return  shapes[index];
    }

    private int getIndexFromState(Direction direction)
    {
        return direction.getHorizontalIndex();
    }

    private VoxelShape[] makeShapes()
    {
        // base bottom is always the same
        Vector3d baseBottomStart = new Vector3d(1d, 0d, 1d);
        Vector3d baseBottomEnd = new Vector3d(15d, 1d, 15d);

        // north side dimensions
        Vector3d baseSideStart = new Vector3d(1d, 1d, 1d);
        Vector3d baseSideEnd = new Vector3d(15d, 4d, 2d);

        Vector3d fixtureStart = new Vector3d(4d, 0d, 0d);
        Vector3d fixtureEnd = new Vector3d(12d, 12d, 4d);

        // the base is always the same, no matter what direction the block is facing, no need to rotate the voxel more.
        VoxelShape base = VoxelHelper.cuboid(baseBottomStart, baseBottomEnd);
        VoxelShape northWall = VoxelHelper.cuboidWithHorizontalRotation(Direction.NORTH, baseSideStart, baseSideEnd);
        VoxelShape eastWall = VoxelHelper.cuboidWithHorizontalRotation(Direction.EAST, baseSideStart, baseSideEnd);
        VoxelShape southWall = VoxelHelper.cuboidWithHorizontalRotation(Direction.SOUTH, baseSideStart, baseSideEnd);
        VoxelShape westWall = VoxelHelper.cuboidWithHorizontalRotation(Direction.WEST, baseSideStart, baseSideEnd);

        // mash the base together, it's always the same shape.
        VoxelShape wholeBase = VoxelHelper.mergeAll(base, northWall, eastWall, southWall, westWall);

        VoxelShape[] result = new VoxelShape[4];
        for(int i = 0; i < 4; i++) {
            // figure out the fixture by rotation and then mash it together with the base. Slap that in result.
            Direction d = Direction.byHorizontalIndex(i);
            VoxelShape rotatedFixture = VoxelHelper.cuboidWithHorizontalRotation(d, fixtureStart, fixtureEnd);
            result[i] = VoxelHelper.mergeAll(wholeBase, rotatedFixture);
        }

        return result;
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new TroughTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction d = context.getFace().getOpposite();
        if (d.getAxis() == Direction.Axis.Y) {
            d = context.getPlacementHorizontalFacing();
        }
        return getDefaultState()
                .with(HORIZONTAL_FACING, d);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }
//
//    @SuppressWarnings("deprecation")
//    @Override
//    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
//    {
//        if (state.get(FACING).getAxis() == hit.getFace().getAxis()) {
//            return ActionResultType.PASS;
//        }
//        boolean isClient = false;
//        if (worldIn != null && worldIn.isRemote()) {
//            isClient = true;
//        }
//
//        if (worldIn != null) {
//            TileEntity tile = worldIn.getTileEntity(pos);
//            if (!(tile instanceof GooPumpTile)) {
//                return ActionResultType.func_233537_a_(worldIn.isRemote);
//            }
//
//            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
//            if(!isClient) {
//                ((GooPumpTile) tile).changeTargetItem(itemToSwap);
//            }
//
//            return ActionResultType.func_233537_a_(worldIn.isRemote);
//        }
//        if (!player.isSneaking()) {
//            return ActionResultType.PASS;
//        }
//        return ActionResultType.func_233537_a_(worldIn.isRemote);
//    }

    public static final Map<Direction, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction d : Direction.values()) {
            if (d.getAxis() == Direction.Axis.Y) {
                continue;
            }
            RELEVANT_DIRECTIONS.put(d, new Direction[] { d, d.getOpposite()});
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(HORIZONTAL_FACING));
    }
}
