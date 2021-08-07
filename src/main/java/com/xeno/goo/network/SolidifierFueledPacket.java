package com.xeno.goo.network;

import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SolidifierFueledPacket implements IGooModPacket
{
    private RegistryKey<World> worldRegistryKey;
    private BlockPos blockPos;
    private int fuelTime;

    public SolidifierFueledPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.blockPos = buf.readBlockPos();
    }

    public SolidifierFueledPacket(RegistryKey<World> k, BlockPos pos, int fuel) {
        worldRegistryKey = k;
        blockPos = pos;
        fuelTime = fuel;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeBlockPos(blockPos);
        buf.writeInt(fuelTime);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                if (Minecraft.getInstance().world == null) {
                    return;
                }
                if (Minecraft.getInstance().world.getDimensionKey() != worldRegistryKey) {
                    return;
                }
                TileEntity e = Minecraft.getInstance().world.getTileEntity(blockPos);
                if (e instanceof SolidifierTile) {
                    ((SolidifierTile)e).setFuelTime(fuelTime);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
