package com.xeno.goo.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;
import java.util.function.Supplier;

public class SolidifierPoppedPacket implements IGooModPacket
{
    private RegistryKey<World> worldRegistryKey;
    private Vector3d vector;
    private Vector3d nozzle;

    public SolidifierPoppedPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.vector = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.nozzle = new Vector3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public SolidifierPoppedPacket(RegistryKey<World> k, Vector3d v, Vector3d n) {
        worldRegistryKey = k;
        vector = v;
        nozzle = n;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeDouble(vector.x);
        buf.writeDouble(vector.y);
        buf.writeDouble(vector.z);
        buf.writeDouble(nozzle.x);
        buf.writeDouble(nozzle.y);
        buf.writeDouble(nozzle.z);
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
                Minecraft.getInstance().world.addParticle(ParticleTypes.SMOKE, nozzle.x, nozzle.y, nozzle.z, vector.x, vector.y, vector.z);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
