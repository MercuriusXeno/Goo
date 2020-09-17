package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class GooLobConfirmationPacket implements IGooModPacket
{
    private UUID goo;
    private UUID owner;
    public GooLobConfirmationPacket(GooEntity g, PlayerEntity player) {
        this.goo = g.getUniqueID();
        this.owner = player.getUniqueID();
    }

    public GooLobConfirmationPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
        buf.writeUniqueId(goo);
        buf.writeUniqueId(owner);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier)
    {
//        supplier.get().enqueueWork(() -> {
//            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
//                if (Minecraft.getInstance().world == null) {
//                    return;
//                }
//                PlayerEntity player = Minecraft.getInstance().world.getPlayerByUuid(owner);
//                if (player == null) {
//                    return;
//                }
//                Optional<GooEntity> goo = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(8d), g -> g.getUniqueID().equals(this.goo) && g.isHeld() && g.owner() == player).stream().findFirst();
//                goo.ifPresent(g -> {
//                    g.clearHolder();
//                });
//            }
//        });
//
//        supplier.get().setPacketHandled(true);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.goo = buf.readUniqueId();
        this.owner = buf.readUniqueId();
    }
}
