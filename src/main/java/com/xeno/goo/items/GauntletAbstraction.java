package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
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

    public static void tryLobbingGoo(PlayerEntity player) {
        if (player.getEntityWorld().isRemote()) {
            return;
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(player.getHeldItem(Hand.MAIN_HAND));
        if (cap == null) {
            return;
        }

        // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
        int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());

        // -1 is disabled
        if (drainAmountThrown == -1) {
            return;
        }

        FluidStack thrownStack = cap.drain(drainAmountThrown, IFluidHandler.FluidAction.EXECUTE);
        player.getEntityWorld().addEntity(new GooBlob(Registry.GOO_BLOB.get(), player.getEntityWorld(), player, thrownStack));
        AudioHelper.playerAudioEvent(player, Registry.GOO_LOB_SOUND.get(), 1.0f);

        player.swing(Hand.MAIN_HAND, false);
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

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        return ActionResultType.SUCCESS;
    }
}
