package com.xeno.goo.network;

import com.xeno.goo.items.Basin;
import com.xeno.goo.items.BasinAbstractionCapability;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class GooBasinSwapPacket implements IGooModPacket
{
    private FluidStack target;
    private Hand hand;
    public GooBasinSwapPacket(FluidStack stack, Hand hand) {
        this.target = stack;
        this.hand = hand;
    }

    public GooBasinSwapPacket(PacketBuffer buf) {
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
                if (player.getHeldItem(hand).getItem() instanceof Basin) {
                    LazyOptional<IFluidHandlerItem> lazyCap = player.getHeldItem(hand).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                    lazyCap.ifPresent(c -> ((BasinAbstractionCapability)c).swapToFluid(target));
                    player.swing(hand, true);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
