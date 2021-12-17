package com.xeno.goo.network;

import com.xeno.goo.entities.HexController;
import com.xeno.goo.interactions.GooInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class BlobHitInteractionPacket implements IGooModPacket {
	private BlockPos blockHitPos;
	private int blobId;

	public BlobHitInteractionPacket(BlockPos blockHitPos, HexController blob) {
		this.blockHitPos = blockHitPos;
		this.blobId = blob.getEntityId();
	}

	public BlobHitInteractionPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.blockHitPos = buf.readBlockPos();
		this.blobId = buf.readInt();
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeBlockPos(blockHitPos);
		buf.writeInt(blobId);
	}

	@Override
	public void handle(Supplier<Context> supplier) {
		supplier.get().enqueueWork(new Runnable() {

			@Override
			public void run() {

				if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
					if (Minecraft.getInstance().world == null) {
						return;
					}
					World world = Minecraft.getInstance().world;

					Entity blob = world.getEntityByID(blobId);
					if (!(blob instanceof HexController)) {
						return;
					}

					GooInteractions.tryResolving(blockHitPos, (HexController) blob);
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}
}
