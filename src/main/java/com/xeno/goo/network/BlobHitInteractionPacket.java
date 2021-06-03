package com.xeno.goo.network;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.interactions.GooInteractions;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class BlobHitInteractionPacket implements IGooModPacket {
	private int entityHitId;
	private int ownerId;
	private int blobId;

	public BlobHitInteractionPacket(LivingEntity entityHit, LivingEntity owner, GooBlob blob) {
		this.entityHitId = entityHit.getEntityId();
		if (owner != null) {
			this.ownerId = owner.getEntityId();
		} else {
			this.ownerId = -1;
		}
		this.blobId = blob.getEntityId();
	}

	public BlobHitInteractionPacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.entityHitId = buf.readInt();
		this.ownerId = buf.readInt();
		this.blobId = buf.readInt();
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeInt(entityHitId);
		buf.writeInt(ownerId);
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
					Entity owner = null;
					// owner is allowed to be null, but if it isn't a living entity, there's an issue.
					if (ownerId != -1) {
						owner = world.getEntityByID(ownerId);
						if (!(owner instanceof LivingEntity)) {
							return;
						}
					}

					Entity e = world.getEntityByID(entityHitId);
					if (!(e instanceof LivingEntity)) {
						return;
					}

					Entity blob = world.getEntityByID(blobId);
					if (!(blob instanceof GooBlob)) {
						return;
					}

					GooInteractions.tryResolving((LivingEntity) e, (LivingEntity) owner, (GooBlob) blob);
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}
}
