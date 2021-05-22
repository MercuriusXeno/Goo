package com.xeno.goo.network;

import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.interactions.GooInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class SplatInteractionPacket implements IGooModPacket {
	private int splatId;

	public SplatInteractionPacket(GooSplat splat) {
		this.splatId = splat.getEntityId();
	}

	public SplatInteractionPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.splatId = buf.readInt();
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeInt(splatId);
	}

	@Override
	public void handle(Supplier<Context> supplier) {
		supplier.get().enqueueWork(() -> {
			if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
				if (Minecraft.getInstance().world == null) {
					return;
				}
				World world = Minecraft.getInstance().world;

				Entity splat = world.getEntityByID(splatId);
				if (!(splat instanceof GooSplat)) {
					return;
				}

				GooInteractions.tryResolving((GooSplat)splat);
			}
		});

		supplier.get().setPacketHandled(true);
	}
}
