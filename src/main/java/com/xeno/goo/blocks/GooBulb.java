package com.xeno.goo.blocks;

import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class GooBulb extends BlockWithConnections
{
    public GooBulb()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooBulbTile();
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        int holding = EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
        GooBulbTile tile = (GooBulbTile)worldIn.getTileEntity(pos);
        tile.enchantHolding(holding);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.isIn(newState.getBlock())) {
            TileEntity tileentity = worldIn.getTileEntity(pos);
            if (tileentity instanceof GooBulbTile) {
                ((GooBulbTile) tileentity).spewItems();
                worldIn.updateComparatorOutputLevel(pos, this);
            }

            super.onReplaced(state, worldIn, pos, newState, isMoving);
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return Direction.values();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        TileEntity tile = worldIn.getTileEntity(pos);
        if (!(tile instanceof GooBulbTile)) {
            return ActionResultType.FAIL;
        }

        GooBulbTile bulb = ((GooBulbTile) tile);
        if (bulb.hasCrystal()) {
            if (!worldIn.isRemote) {
                bulb.spitOutCrystal(player, hit.getFace());
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote);
        } else {
            // bulb is empty so it can take a crystal if you're holding one.
            Item item = player.getHeldItem(handIn).getItem();
            if (!item.equals(Items.QUARTZ) && !(item instanceof CrystallizedGooAbstract)) {
                return ActionResultType.FAIL;
            }
            if (!worldIn.isRemote) {
                player.getHeldItem(handIn).shrink(1);
                ((GooBulbTile) tile).addCrystal(item);
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote);
        }
    }
}
