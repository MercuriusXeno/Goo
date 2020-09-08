package com.xeno.goo.blocks;

import com.xeno.goo.client.render.PumpRenderMode;
import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class GooPump extends Block
{
    public static final EnumProperty<PumpRenderMode> RENDER = EnumProperty.create("render", PumpRenderMode.class);

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
                .with(BlockStateProperties.FACING, context.getFace().getOpposite())
                .with(RENDER, PumpRenderMode.STATIC);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.FACING, RENDER);
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
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof GooPumpTile)) {
                return ActionResultType.SUCCESS;
            }

            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
            if(!isClient) {
                ((GooPumpTile) tile).changeTargetItem(itemToSwap);
            }

            return ActionResultType.SUCCESS;
        }
        if (!player.isSneaking()) {
            return ActionResultType.PASS;
        }
        return ActionResultType.SUCCESS;
    }
}
