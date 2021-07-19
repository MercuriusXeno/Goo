package com.xeno.goo.network;

import com.xeno.goo.tiles.CrucibleTile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class CrucibleWastingFluidPacket implements IGooModPacket {

	private RegistryKey<World> worldRegistryKey;
	private BlockPos pos;
	private FluidStack wastedFluid;

	public CrucibleWastingFluidPacket(World world, BlockPos pos, FluidStack wastedFluid) {
		this.worldRegistryKey = world.getDimensionKey();
		this.pos = pos;
		this.wastedFluid = wastedFluid;
	}

	public CrucibleWastingFluidPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeResourceLocation(worldRegistryKey.getLocation());
		buf.writeBlockPos(pos);
		buf.writeCompoundTag(wastedFluid.writeToNBT(new CompoundNBT()));
	}

	@Override
	public void read(PacketBuffer buf) {
		this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
		this.pos = buf.readBlockPos();
		this.wastedFluid = FluidStack.loadFluidStackFromNBT(buf.readCompoundTag());
	}

	@Override
	public void handle(Supplier<Context> supplier) {
		supplier.get().enqueueWork(() -> {
			if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
				if (Minecraft.getInstance().world == null) {
					return;
				}
				if (Minecraft.getInstance().world.getDimensionKey() != worldRegistryKey) {
					return;
				}
				TileEntity te = Minecraft.getInstance().world.getTileEntity(pos);
				if (te instanceof CrucibleTile) {
					((CrucibleTile) te).setWastedFluid(this.wastedFluid);
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}
}
