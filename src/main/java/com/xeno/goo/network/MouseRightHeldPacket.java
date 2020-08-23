package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.ServerPlayerTracker;
import com.xeno.goo.evaluations.GooEntry;
import com.xeno.goo.evaluations.GooValue;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class MouseRightHeldPacket implements IGooModPacket
{
    private boolean isRightMouseHeld;
    public MouseRightHeldPacket(boolean isRightMouseHeld) {
        this.isRightMouseHeld = isRightMouseHeld;
    }

    public MouseRightHeldPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.isRightMouseHeld = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buffer) {
        buffer.writeBoolean(isRightMouseHeld);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }
                ServerPlayerTracker.update(player, isRightMouseHeld);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
