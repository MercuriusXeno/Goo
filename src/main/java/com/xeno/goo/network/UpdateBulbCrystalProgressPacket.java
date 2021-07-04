package com.xeno.goo.network;

import com.xeno.goo.tiles.BulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateBulbCrystalProgressPacket implements IGooModPacket {
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private ItemStack crystal;
    private FluidStack crystalProgress;
    private int lastIncrement;
    private ResourceLocation crystalFluid;

    public UpdateBulbCrystalProgressPacket(PacketBuffer buf) {
        read(buf);
    }

    public void read(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.crystal = buf.readItemStack();
        this.crystalProgress = buf.readFluidStack();
        this.lastIncrement = buf.readInt();
        this.crystalFluid = buf.readResourceLocation();
    }

    public UpdateBulbCrystalProgressPacket(RegistryKey<World> registryKey, BlockPos pos, ItemStack stack, Fluid crystalFluid, FluidStack fluidStack,
                                           int lastIncrement) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.crystal = stack;
        this.crystalProgress = fluidStack;
        this.lastIncrement = lastIncrement;
        this.crystalFluid = crystalFluid.getRegistryName();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeBlockPos(pos);
        buf.writeItemStack(crystal);
        buf.writeFluidStack(crystalProgress);
        buf.writeInt(lastIncrement);
        buf.writeResourceLocation(crystalFluid);
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
                if (te instanceof BulbTile) {
                    ((BulbTile) te).updateCrystalProgress(this.crystal, this.lastIncrement,
                            this.crystalFluid, this.crystalProgress);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
