package com.xeno.goo.blocks;

import com.xeno.goo.tiles.MixerTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
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
import net.minecraft.world.World;

public class Mixer extends Block
{
    VoxelShape[] shapes;

    public Mixer()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        shapes = makeShapes();
    }

    private VoxelShape[] makeShapes()
    {
        Vector3d rcs = new Vector3d(0d, 4d, 2d);
        Vector3d rce = new Vector3d(6d, 16d, 14d);
        Vector3d lcs = new Vector3d(10d, 4d, 2d);
        Vector3d lce = new Vector3d(16d, 16d, 14d);
        Vector3d rps = new Vector3d(1d, 0d, 6d);
        Vector3d rpe = new Vector3d(5d, 4d, 10d);
        Vector3d lps = new Vector3d(11d, 0d, 6d);
        Vector3d lpe = new Vector3d(15d, 4d, 10d);
        Vector3d ccs = new Vector3d(5d, 0d, 5d);
        Vector3d cce = new Vector3d(11d, 4d, 11d);
        // x aligned orientation
        VoxelShape[] zShapes = getZCombo(rcs, rce, lcs, lce, rps, rpe, lps, lpe, ccs, cce);
        VoxelShape[] xShapes = getXCombo(rcs, rce, lcs, lce, rps, rpe, lps, lpe, ccs, cce);

        return new VoxelShape[] {fabricateAlignedShape(xShapes), fabricateAlignedShape(zShapes)};
    }

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

    private VoxelShape[] getXCombo(Vector3d rcs, Vector3d rce, Vector3d lcs, Vector3d lce, Vector3d rps, Vector3d rpe, Vector3d lps, Vector3d lpe, Vector3d ccs, Vector3d cce)
    {
        VoxelShape vs = Block.makeCuboidShape(rcs.x, rcs.y, rcs.z, rce.x, rce.y, rce.z);
        VoxelShape vs1 = Block.makeCuboidShape(lcs.x, lcs.y, lcs.z, lce.x, lce.y, lce.z);
        VoxelShape vs2 = Block.makeCuboidShape(rps.x, rps.y, rps.z, rpe.x, rpe.y, rpe.z);
        VoxelShape vs3 = Block.makeCuboidShape(lps.x, lps.y, lps.z, lpe.x, lpe.y, lpe.z);
        VoxelShape vs4 = Block.makeCuboidShape(ccs.x, ccs.y, ccs.z, cce.x, cce.y, cce.z);
        return new VoxelShape[] {vs, vs1, vs2, vs3, vs4};
    }

    private VoxelShape[] getZCombo(Vector3d rcs, Vector3d rce, Vector3d lcs, Vector3d lce, Vector3d rps, Vector3d rpe, Vector3d lps, Vector3d lpe, Vector3d ccs, Vector3d cce)
    {
        VoxelShape vs = Block.makeCuboidShape(rcs.z, rcs.y, rcs.x, rce.z, rce.y, rce.x);
        VoxelShape vs1 = Block.makeCuboidShape(lcs.z, lcs.y, lcs.x, lce.z, lce.y, lce.x);
        VoxelShape vs2 = Block.makeCuboidShape(rps.z, rps.y, rps.x, rpe.z, rpe.y, rpe.x);
        VoxelShape vs3 = Block.makeCuboidShape(lps.z, lps.y, lps.x, lpe.z, lpe.y, lpe.x);
        VoxelShape vs4 = Block.makeCuboidShape(ccs.z, ccs.y, ccs.x, cce.z, cce.y, cce.x);
        return new VoxelShape[] {vs, vs1, vs2, vs3, vs4};
    }

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
                .with(BlockStateProperties.HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player)
    {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof MixerTile) {
            MixerTile mixer = (MixerTile) te;
            if (!world.isRemote) {
                ItemStack stack = mixer.mixerStack(this);
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
    }
}
