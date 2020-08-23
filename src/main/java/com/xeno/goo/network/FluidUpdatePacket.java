package com.xeno.goo.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FluidUpdatePacket implements IGooModPacket {
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private int indexes;
    private List<FluidStack> fluids;

    public FluidUpdatePacket(PacketBuffer buf) {
        read(buf);
    }

    public void read(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.func_240903_a_(Registry.WORLD_KEY, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.indexes = buf.readInt();
        this.fluids = new ArrayList<>();
        for(int i = 0; i < indexes; i++) {
            this.fluids.add(buf.readFluidStack());
        }
    }

    public FluidUpdatePacket(RegistryKey<World> registryKey, BlockPos pos, List<FluidStack> fluidStacks) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.indexes = fluidStacks.size();
        this.fluids = new ArrayList<>();
        fluids.addAll(fluidStacks);
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.func_240901_a_());
        buf.writeBlockPos(pos);
        buf.writeInt(indexes);
        for(int i = 0; i < indexes; i++) {
            buf.writeFluidStack(fluids.get(i));
        }
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
         * @param fluids New fluidstack
         */
        void updateFluidsTo(List<FluidStack> fluids);
    }
}
