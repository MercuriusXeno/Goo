package com.xeno.goo.network;

import com.xeno.goo.items.Vessel;
import com.xeno.goo.items.VesselAbstractionCapability;
import com.xeno.goo.items.Gauntlet;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class GooGauntletSwapPacket implements IGooModPacket
{
    private FluidStack target;
    private Hand hand;
    public GooGauntletSwapPacket(FluidStack stack, Hand hand) {
        this.target = stack;
        this.hand = hand;
    }

    public GooGauntletSwapPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.target = buf.readFluidStack();
        this.hand = buf.readEnumValue(Hand.class);
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
        buf.writeFluidStack(this.target);
        buf.writeEnumValue(hand);
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
                if (player.getHeldItem(hand).getItem() instanceof Gauntlet) {
                    LazyOptional<IFluidHandlerItem> lazyCap = player.getHeldItem(hand)
                            .getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                    lazyCap.ifPresent(c -> tryRaidingInventoryForGoo(player, c));
                    player.swing(hand, true);
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
            if (!(i.getItem() instanceof Vessel)) {
                continue;
            }

            LazyOptional<IFluidHandlerItem> lazyCap = i.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            if (lazyCap.isPresent()) {
                boolean[] hadResult = {false};
                lazyCap.ifPresent((c) -> hadResult[0] = raidVesselForGoo((VesselAbstractionCapability) c, cap));
                if (hadResult[0]) {
                    return;
                }
            }
        }
    }

    private boolean raidVesselForGoo(VesselAbstractionCapability c, IFluidHandlerItem cap) {
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
            if (!(i.getItem() instanceof Vessel)) {
                continue;
            }

            LazyOptional<IFluidHandlerItem> lazyCap = i.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            if (lazyCap.isPresent()) {
                boolean[] hadResult = {false};
                lazyCap.ifPresent((c) -> hadResult[0] = dumpGooInVessel((VesselAbstractionCapability) c, cap));
                if (hadResult[0]) {
                    return;
                }
            }
        }
    }

    private boolean dumpGooInVessel(VesselAbstractionCapability c, IFluidHandlerItem cap) {
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
