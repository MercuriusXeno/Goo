package com.xeno.goo.network;

import com.xeno.goo.entities.GooEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class DetatchGooFromPlayerPacket
{
    private final boolean isShooting;
    public DetatchGooFromPlayerPacket(boolean isShooting)
    {
        this.isShooting = isShooting;
    }

    public DetatchGooFromPlayerPacket(PacketBuffer buf) {
        this.isShooting = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buffer) {
        buffer.writeBoolean(isShooting);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }

                List<GooEntity> entities = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(1.0d), p -> p.isHeld() && p.owner() == player);
                for(GooEntity e : entities) {
                    e.detachGooFromSender(isShooting);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
