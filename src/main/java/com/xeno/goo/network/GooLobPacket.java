package com.xeno.goo.network;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.GauntletAbstraction;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class GooLobPacket implements IGooModPacket
{
    public GooLobPacket() {
    }

    public GooLobPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
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
                    GauntletAbstraction.tryLobbingGoo(player);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
