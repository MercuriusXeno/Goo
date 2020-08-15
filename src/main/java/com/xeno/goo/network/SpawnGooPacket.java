package com.xeno.goo.network;

import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.entities.ServerPlayerTracker;
import com.xeno.goo.items.GooHolder;
import com.xeno.goo.items.GooHolderData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SpawnGooPacket
{
    public SpawnGooPacket()
    {
    }

    public SpawnGooPacket(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buffer) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }

                ItemStack tool = player.getHeldItemMainhand();
                if (tool.isEmpty() || !(tool.getItem() instanceof GooHolder)) {
                    return;
                }

                GooHolderData data = ((GooHolder)tool.getItem()).data(tool);
                data.trySpawningGoo(player.world, player, tool, Hand.MAIN_HAND);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
