package com.xeno.goo.blocks;

import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;

public class GooPump extends Block
{
    public static final EnumProperty<PumpRenderMode> RENDER = EnumProperty.create("render", PumpRenderMode.class);
    private static final VoxelShape RENDER_SHAPE = VoxelShapes.combine(
            VoxelShapes.create(0f, 0f, 0f, 1f, 0.5f, 1f),
            VoxelShapes.create(0.3125f, 0.5f, 0.3125f, 0.6825f, 1f, 0.6825f), IBooleanFunction.AND);

    public GooPump()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooPumpTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.FACING, context.getFace())
                .with(RENDER, PumpRenderMode.STATIC);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING, RENDER);
    }

    @Override
    public VoxelShape getRenderShape(BlockState state, IBlockReader reader, BlockPos pos) {
        return RENDER_SHAPE;
    }
}
