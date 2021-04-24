package com.xeno.goo.network;

import com.xeno.goo.util.IGooTank;
import io.netty.buffer.Unpooled;
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

public class FluidUpdatePacket implements IGooModPacket {
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private int indexes;
    private PacketBuffer fluids;

    public FluidUpdatePacket(PacketBuffer buf) {
        read(buf);
    }

    public void read(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.fluids = new PacketBuffer(Unpooled.buffer());
        for (int i = 0, e = buf.readVarInt(), dummy = fluids.writeVarInt(e).refCnt(); i < e; ++i) {
            IGooTank.writeFluidStack(fluids, IGooTank.readFluidStack(buf));
        }
    }

    public FluidUpdatePacket(RegistryKey<World> registryKey, BlockPos pos, IGooTank fluidStacks) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.fluids = new PacketBuffer(Unpooled.buffer());
        fluidStacks.writeToPacket(fluids);
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeBlockPos(pos);
        fluids.readerIndex(0);
        buf.writeBytes(fluids);
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
                if (te instanceof IFluidPacketReceiver) {
                    ((IFluidPacketReceiver) te).updateFluidsTo(fluids);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    public interface IFluidPacketReceiver {

        /**
         * Updates the current fluid to the specified value
         *
         * @param data New fluidstack
         */
        void updateFluidsTo(PacketBuffer data);
    }
}
