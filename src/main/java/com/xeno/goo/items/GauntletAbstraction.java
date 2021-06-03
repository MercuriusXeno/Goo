package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.ItemFluidContainer;

public class GauntletAbstraction extends ItemFluidContainer
{
    public GauntletAbstraction()
    {
        super(
                new Item.Properties()
                        .maxStackSize(1)
                        .isImmuneToFire()
                        .group(GooMod.ITEM_GROUP), 0);


    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt)
    {
        return new GauntletAbstractionCapability(stack);
    }

    public static boolean tryLobbingGoo(PlayerEntity player, Hand hand) {
        if (player.getEntityWorld().isRemote()) {
            return false;
        }

        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof GauntletAbstraction)) {
            return false;
        }
        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return false;
        }

        // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
        int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

        // -1 is disabled
        if (drainAmountThrown == -1) {
            return false;
        }

        if (drainAmountThrown == 0) {
            drainAmountThrown = 1;
        }

        FluidStack thrownStack = cap.drain(drainAmountThrown, IFluidHandler.FluidAction.EXECUTE);
        player.getEntityWorld().addEntity(new GooBlob(Registry.GOO_BLOB.get(), player.getEntityWorld(), player, thrownStack));
        AudioHelper.playerAudioEvent(player, Registry.GOO_LOB_SOUND.get(), 1.0f);
        player.swing(hand, true);

        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null || fh.getFluidInTank(0).isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public int getMaxDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null) {
            return 0;
        }
        return fh.getTankCapacity(0) + 1;
    }

    @Override
    public int getDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null || fh.getFluidInTank(0).isEmpty()) {
            return 0;
        }
        return fh.getTankCapacity(0) - fh.getFluidInTank(0).getAmount();
    }

    public static float getHeldLiquidOverride(ItemStack stack, World world, LivingEntity e) {
        if (!stack.hasTag() || stack.getTag() == null) {
            return 0f;
        }

        if (!stack.getTag().contains(Gauntlet.HELD_LIQUID_TAG_NAME)) {
            return 0f;
        }

        return stack.getTag().getFloat(Gauntlet.HELD_LIQUID_TAG_NAME);
    }
}
