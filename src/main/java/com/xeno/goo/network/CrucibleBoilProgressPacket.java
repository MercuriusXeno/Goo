package com.xeno.goo.network;

import com.xeno.goo.tiles.CrucibleTile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class CrucibleBoilProgressPacket implements IGooModPacket {

	private RegistryKey<World> worldRegistryKey;
	private BlockPos pos;
	private int boilRate;

	public CrucibleBoilProgressPacket(World world, BlockPos pos, int meltRate) {
		this.worldRegistryKey = world.getDimensionKey();
		this.pos = pos;
		this.boilRate = meltRate;
	}

	public CrucibleBoilProgressPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeResourceLocation(worldRegistryKey.getLocation());
		buf.writeBlockPos(pos);
		buf.writeInt(boilRate);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
		this.pos = buf.readBlockPos();
		this.boilRate = buf.readInt();
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
					((CrucibleTile) te).setBoilRate(this.boilRate);
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}
}
