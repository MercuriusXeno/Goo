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
    private Hand hand;
    public GooLobPacket(Hand hand) {
        this.hand = hand;
    }

    public GooLobPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.hand = buf.readEnumValue(Hand.class);
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
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
                    GauntletAbstraction.tryLobbingGoo(player, hand);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
