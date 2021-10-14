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

public class SolidifierProgressPacket implements IGooModPacket {
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private float progress;

    public SolidifierProgressPacket(PacketBuffer buf) {
        read(buf);
    }

    public void read(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.progress = buf.readVarInt();
    }

    public SolidifierProgressPacket(RegistryKey<World> registryKey, BlockPos pos, float progress) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.progress = progress;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeBlockPos(pos);
        buf.writeFloat(progress);
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
                TileEntity te = Minecraft.getInstance().world.getTileEntity(pos);
                if (te instanceof SolidifierTile) {
                    ((SolidifierTile) te).updateProgressVisuals(this.progress);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
