package com.xeno.goo.network;

import com.xeno.goo.fertilize.FertilizeCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class FertilizePacket implements IGooModPacket {

	private CompoundNBT nbt;
	private int entityId;
	public FertilizePacket(PacketBuffer b) {
		read(b);
	}

	public FertilizePacket(int entityId, CompoundNBT nbt)
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
					Entity entity = world.getEntityByID(FertilizePacket.this.entityId);

					if (entity instanceof LivingEntity) {
						entity.getCapability(FertilizeCapability.FERTILIZE_CAPABILITY).ifPresent(fertilizeProvider ->
								fertilizeProvider.deserializeNBT(FertilizePacket.this.nbt));
					}
				}
			}
		});
		ctx.get().setPacketHandled(true);
	}

}
