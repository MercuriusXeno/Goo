package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.ItemFluidContainer;

public class VesselAbstraction extends ItemFluidContainer
{
    public VesselAbstraction()
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .isImmuneToFire()
                .group(GooMod.ITEM_GROUP), 0);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt)
    {
        return new VesselAbstractionCapability(stack);
    }

    @Override
    public int getMaxDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null) {
            return 0;
        }
        return ((VesselAbstractionCapability)fh).capacity() + 1;
    }

    @Override
    public int getDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null) {
            return 0;
        }
        return ((VesselAbstractionCapability)fh).capacity() - ((VesselAbstractionCapability)fh).totalFluid();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        return ActionResult.resultPass(playerIn.getHeldItem(handIn));
    }
}
