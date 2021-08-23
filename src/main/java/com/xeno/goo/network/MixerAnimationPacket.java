package com.xeno.goo.network;

import com.xeno.goo.network.FluidUpdatePacket.IFluidPacketReceiver;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.client.Minecraft;
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

public class MixerAnimationPacket implements IGooModPacket {

	private RegistryKey<World> worldRegistryKey;
	private BlockPos pos;
	private boolean isActive;

	public MixerAnimationPacket(BlockPos pos, RegistryKey<World> worldRegistryKey, boolean isActive) {
		this.pos = pos;
		this.worldRegistryKey = worldRegistryKey;
		this.isActive = isActive;
	}

	public MixerAnimationPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeResourceLocation(this.worldRegistryKey.getLocation());
		buf.writeBlockPos(this.pos);
		buf.writeBoolean(this.isActive);
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
				if (te instanceof MixerTile) {
					MixerTile t = (MixerTile)te;
					if (this.isActive && !t.isActive()) {
						t.startAnimation();
					} else if (!this.isActive && t.isActive()){
						t.stopAnimation();
					}
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
		this.pos = buf.readBlockPos();
		this.isActive = buf.readBoolean();
	}
}
