package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class Gauntlet extends GooHolder
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

        data(stack).tryThrowingGoo(worldIn, playerIn, stack);
        return ActionResult.resultSuccess(stack);
    }

    @Override
    public double armstrongMultiplier()
    {
        return GooMod.config.gauntletPowerMultiplier();
    }

    @Override
    public double thrownSpeed()
    {
        return GooMod.config.gauntletLobVelocity();
    }

    @Override
    public int capacity()
    {
        return GooMod.config.gauntletBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.gauntletHoldingMultiplier();
    }
}
