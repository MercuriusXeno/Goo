package com.xeno.goo.network;


import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class BulbVerticalFillPacket
{
    private final RegistryKey<World> worldRegistryKey;
    private final BlockPos pos;
    private final FluidStack fluid;
    private final float intensity;

    public BulbVerticalFillPacket(PacketBuffer buf) {
        worldRegistryKey = RegistryKey.func_240903_a_(Registry.WORLD_KEY, buf.readResourceLocation());
        pos = buf.readBlockPos();
        fluid = buf.readFluidStack();
        intensity = buf.readFloat();
    }

    public BulbVerticalFillPacket(RegistryKey<World> registryKey, BlockPos pos, Fluid fluid, float intensity) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.fluid = new FluidStack(fluid, 1);
        this.intensity = intensity;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.func_240901_a_());
        buf.writeBlockPos(pos);
        buf.writeFluidStack(fluid);
        buf.writeFloat(intensity);
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
                if (te instanceof IVerticalFillReceiver) {
                    ((IVerticalFillReceiver) te).updateVerticalFill(fluid.getFluid(), intensity);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }

    public interface IVerticalFillReceiver {

        /**
         * Updates the current fluid to the specified value
         *
         * @param f fluid type being filled vertically from above
         * @param intensity float representing the strength of the fill visual
         */
        void updateVerticalFill(Fluid f, float intensity);
    }
}
