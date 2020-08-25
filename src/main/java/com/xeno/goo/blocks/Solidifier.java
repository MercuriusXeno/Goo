package com.xeno.goo.blocks;

import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import java.util.List;

public class Solidifier extends Block {
    public Solidifier() {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(4.0f));
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return state.get(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    public void addInformation(ItemStack stack, IBlockReader worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        SolidifierTile.addInformation(stack, tooltip);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new SolidifierTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, false)
                .with(BlockStateProperties.HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.POWERED);
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof SolidifierTile) {
            SolidifierTile solidifier = (SolidifierTile)te;
            if (!world.isRemote) {
                ItemStack stack = solidifier.getSolidifierStack();
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
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
            // ignore all hits other than north facing hit.
            if (hit.getFace() != state.get(BlockStateProperties.HORIZONTAL_FACING)) {
                return ActionResultType.SUCCESS;
            }

            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof SolidifierTile)) {
                return ActionResultType.SUCCESS;
            }

            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
            if(!isClient) {
                ((SolidifierTile) tile).changeTargetItem(itemToSwap);
            }

            return ActionResultType.SUCCESS;
        }
        if (!player.isSneaking()) {
            return ActionResultType.PASS;
        }
        return ActionResultType.SUCCESS;
    }
}
