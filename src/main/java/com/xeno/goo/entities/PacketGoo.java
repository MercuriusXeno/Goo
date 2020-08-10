package com.xeno.goo.entities;

import com.xeno.goo.network.BulbVerticalFillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.INetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketGoo
{
    private final RegistryKey<World> worldRegistryKey;
    private final GooEntity e;
    private final UUID id;

    public PacketGoo(RegistryKey<World> worldRegistryKey, GooEntity gooEntity)
    {
        this.worldRegistryKey = worldRegistryKey;
        this.e = gooEntity;
        this.id = gooEntity.getUniqueID();
    }

    public PacketGoo(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.func_240903_a_(Registry.WORLD_KEY, buf.readResourceLocation());
        this.e = unpackEntity(buf);
        this.id = buf.readUniqueId();
    }


    private GooEntity unpackEntity(PacketBuffer buf)
    {
        CompoundNBT tag = buf.readCompoundTag();
        return new GooEntity();
    }

    public void toBytes(PacketBuffer buf)
    {
        buf.writeResourceLocation(worldRegistryKey.getRegistryName());
        buf.writeCompoundTag(e.serialize());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                if (Minecraft.getInstance().world == null) {
                    return;
                }
                if (Minecraft.getInstance().world.func_234923_W_() != worldRegistryKey) {
                    return;
                }
                World world = Minecraft.getInstance().world;
                if (world.get == null) {

                }
            }
        });

        supplier.get().setPacketHandled(true);

    }
}
