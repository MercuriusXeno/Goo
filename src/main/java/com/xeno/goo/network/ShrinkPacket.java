package com.xeno.goo.network;

import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class ShrinkPacket implements IGooModPacket {

	private CompoundNBT nbt;
	private int entityId;
	public ShrinkPacket(PacketBuffer b) {
		read(b);
	}

	public ShrinkPacket(int entityId, CompoundNBT nbt)
	{
		this.entityId = entityId;
		this.nbt = nbt;
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeInt(entityId);
		buf.writeCompoundTag(nbt);
	}

	@Override
	public void read(PacketBuffer b) {
		this.entityId = b.readInt();
		this.nbt = b.readCompoundTag();
	}

	@Override
	public void handle(Supplier<Context> ctx) {

		ctx.get().enqueueWork(new Runnable() {

			@Override
			public void run() {

				World world = Minecraft.getInstance().world;

				if (world != null) {
					Entity entity = world.getEntityByID(ShrinkPacket.this.entityId);

					if (entity instanceof LivingEntity) {
						entity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
						{
							iShrinkProvider.deserializeNBT(ShrinkPacket.this.nbt);

							if (iShrinkProvider.isShrunk()) {
								entity.size = new EntitySize(iShrinkProvider.widthScale(), iShrinkProvider.heightScale(), true);
								entity.eyeHeight = iShrinkProvider.defaultEyeHeight() * iShrinkProvider.scale();
							} else {
								entity.size = iShrinkProvider.defaultEntitySize();
								entity.eyeHeight = iShrinkProvider.defaultEyeHeight();
							}
						});
					}
				}
			}
		});
		ctx.get().setPacketHandled(true);
	}

}
