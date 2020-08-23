package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class Crucible extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }
        if (context.getPlayer() != null && context.getPlayer().isHandActive()) {
            return ActionResultType.PASS;
        }
        ActionResultType result = data(stack).tryGooDrainBehavior(stack, context);
        return result;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        return ActionResult.resultPass(playerIn.getHeldItem(handIn));
    }

    @Override
    public float armstrongMultiplier()
    {
        return 0;
    }

    @Override
    public float thrownSpeed()
    {
        return 1F;
    }

    @Override
    public int capacity()
    {
        return 8000;
    }


    @Override
    public int holdingMultiplier()
    {
        return 2;
    }
}
