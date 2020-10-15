package com.xeno.goo.blocks;

import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import com.xeno.goo.tiles.GooBulbTileAbstraction;
import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
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

public abstract class GooBulbAbstraction extends BlockWithConnections
{
    public GooBulbAbstraction()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
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

    @Override
    public abstract TileEntity createTileEntity(BlockState state, IBlockReader world);

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player)    {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof GooBulbTileAbstraction) {
            GooBulbTileAbstraction gooBulb = (GooBulbTileAbstraction) te;
            if (!world.isRemote) {
                gooBulb.spewItems();
                if (player.isCreative() && ((GooBulbTileAbstraction) te).getTotalGoo() == 0) {
                    return;
                }
                ItemStack stack = gooBulb.getBulbStack(this);
                if (stack.isEmpty()) {
                    return;
                }
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
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
        boolean isClient = false;
        if (worldIn.isRemote()) {
            isClient = true;
        }

        TileEntity tile = worldIn.getTileEntity(pos);
        if (!(tile instanceof GooBulbTileAbstraction)) {
            return ActionResultType.CONSUME;
        }

        if (!isClient) {
            GooBulbTileAbstraction bulb = ((GooBulbTileAbstraction) tile);
            if (bulb.hasCrystal()) {
                bulb.spitOutCrystal(player, hit.getFace());
                return ActionResultType.SUCCESS;
            } else {
                // bulb is empty so it can take a crystal if you're holding one.
                Item item = player.getHeldItem(handIn).getItem();
                if (!item.equals(Items.QUARTZ) && !(item instanceof CrystallizedGooAbstract)) {
                    return ActionResultType.FAIL;
                }

                player.getHeldItem(handIn).shrink(1);
                ((GooBulbTileAbstraction) tile).addCrystal(item);
                return ActionResultType.SUCCESS;
            }
        }
        return ActionResultType.CONSUME;
    }
}
