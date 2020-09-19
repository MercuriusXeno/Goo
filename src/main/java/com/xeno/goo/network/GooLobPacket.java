package com.xeno.goo.network;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class GooLobPacket implements IGooModPacket
{
    private UUID goo;

    public GooLobPacket(GooBlob goo) {
        this.goo = goo.getUniqueID();
    }

    public GooLobPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.goo = buf.readUniqueId();
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
        buf.writeUniqueId(this.goo);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier)
    {
//        supplier.get().enqueueWork(() -> {
//            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
//                ServerPlayerEntity player = supplier.get().getSender();
//                if (player == null) {
//                    return;
//                }
//
//                Optional<GooEntity> goo = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(8d), g -> g.getUniqueID().equals(this.goo) && g.isHeld() && g.owner() == player).stream().findFirst();
//                goo.ifPresent(g -> {
//                    g.detachGooFromSender(true);
//                });
//            }
//        });
//
//        supplier.get().setPacketHandled(true);
    }
}
