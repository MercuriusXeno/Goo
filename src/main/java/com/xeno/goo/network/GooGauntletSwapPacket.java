package com.xeno.goo.network;

import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.BasinAbstractionCapability;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class GooGauntletSwapPacket implements IGooModPacket
{
    private FluidStack target;
    public GooGauntletSwapPacket(FluidStack stack) {
        this.target = stack;
    }

    public GooGauntletSwapPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.target = buf.readFluidStack();
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
        buf.writeFluidStack(this.target);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier)
    {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }
                if (player.getHeldItem(Hand.MAIN_HAND).getItem() instanceof Gauntlet) {
                    LazyOptional<IFluidHandlerItem> lazyCap = player.getHeldItem(Hand.MAIN_HAND).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                    lazyCap.ifPresent(c -> tryRaidingInventoryForGoo(player, c));
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }


    private void tryRaidingInventoryForGoo(PlayerEntity player, IFluidHandlerItem cap)
    {
        if (this.target.isEmpty()) {
            tryEmptyingGauntletIntoInventoryContainer(player, cap);
        } else {
            tryRaidingInventoryForGooForRealThough(player, cap);
        }
    }

    private void tryRaidingInventoryForGooForRealThough(PlayerEntity player, IFluidHandlerItem cap) {
        // first empty the gauntlet. this is easier than trying to swap fluids in place.
        tryEmptyingGauntletIntoInventoryContainer(player, cap);
        if (!cap.getFluidInTank(0).isEmpty()) {
            return;
        }
        for (int index = 0; index < player.inventory.getSizeInventory(); index++) {
            FluidStack heldGoo = cap.getFluidInTank(0);
            if (heldGoo.getAmount() >= cap.getTankCapacity(0)) {
                return;
            }
            ItemStack i = player.inventory.getStackInSlot(index);
            if (!(i.getItem() instanceof Basin)) {
                continue;
            }

            LazyOptional<IFluidHandlerItem> lazyCap = i.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            if (lazyCap.isPresent()) {
                boolean[] hadResult = {false};
                lazyCap.ifPresent((c) -> hadResult[0] = raidBasinForGoo((BasinAbstractionCapability) c, cap));
                if (hadResult[0]) {
                    return;
                }
            }
        }
    }

    private boolean raidBasinForGoo(BasinAbstractionCapability c, IFluidHandlerItem cap) {
        FluidStack result = c.drain(target, IFluidHandler.FluidAction.SIMULATE);
        if (result.isEmpty()) {
            return false;
        }
        int amountCanFill = cap.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (amountCanFill == 0) {
            return false;
        }

        if (amountCanFill < target.getAmount()) {
            target.setAmount(amountCanFill);
        }
        cap.fill(c.drain(target, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private void tryEmptyingGauntletIntoInventoryContainer(PlayerEntity player, IFluidHandlerItem cap) {
        for (int index = 0; index < player.inventory.getSizeInventory(); index++) {
            FluidStack heldGoo = cap.getFluidInTank(0);
            if (heldGoo.isEmpty()) {
                return;
            }
            ItemStack i = player.inventory.getStackInSlot(index);
            if (!(i.getItem() instanceof Basin)) {
                continue;
            }

            LazyOptional<IFluidHandlerItem> lazyCap = i.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            if (lazyCap.isPresent()) {
                boolean[] hadResult = {false};
                lazyCap.ifPresent((c) -> hadResult[0] = dumpGooInBasin((BasinAbstractionCapability) c, cap));
                if (hadResult[0]) {
                    return;
                }
            }
        }
    }

    private boolean dumpGooInBasin(BasinAbstractionCapability c, IFluidHandlerItem cap) {
        FluidStack dumpAll = cap.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (dumpAll.isEmpty()) {
            return false;
        }

        int amountCanDump = c.fill(dumpAll, IFluidHandler.FluidAction.SIMULATE);
        if (amountCanDump == 0) {
            return false;
        }

        dumpAll.setAmount(amountCanDump);
        c.fill(cap.drain(dumpAll, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }
}
