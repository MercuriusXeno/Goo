package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class Crucible extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        return data(stack).tryGooDrainBehavior(stack, context);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, @NotNull Hand handIn)
    {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (worldIn.isRemote())
        {
            return ActionResult.resultPass(stack);
        }

        data(stack).trySpawningGoo(worldIn, playerIn, stack, handIn);
        return ActionResult.resultSuccess(stack);
    }

    @Override
    public double armstrongMultiplier()
    {
        return GooMod.config.cruciblePowerMultiplier();
    }

    @Override
    public double thrownSpeed()
    {
        return GooMod.config.crucibleLobVelocity();
    }

    @Override
    public int capacity()
    {
        return GooMod.config.crucibleBaseCapacity();
    }


    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.crucibleHoldingMultiplier();
    }
}
