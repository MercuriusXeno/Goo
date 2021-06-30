package com.xeno.goo.network;

import com.xeno.goo.library.MixerRecipe;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent.Context;

import java.util.function.Supplier;

public class MixerRecipePacket implements IGooModPacket {

	private RegistryKey<World> worldRegistryKey;
	private BlockPos pos;
	private MixerRecipe recipe;

	public MixerRecipePacket(BlockPos pos, RegistryKey<World> worldRegistryKey, MixerRecipe recipe) {
		this.pos = pos;
		this.worldRegistryKey = worldRegistryKey;
		this.recipe = recipe;
	}

	public MixerRecipePacket(PacketBuffer buf) {
		read(buf);
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeResourceLocation(this.worldRegistryKey.getLocation());
		buf.writeBlockPos(this.pos);
		buf.writeBoolean(this.recipe != null);
		if (this.recipe != null) {
			buf.writeCompoundTag(this.recipe.serializeNbt(new CompoundNBT()));
		}
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
					((MixerTile) te).setRecipe(this.recipe);
				}
			}
		});

		supplier.get().setPacketHandled(true);
	}

	@Override
	public void read(PacketBuffer buf) {
		this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
		this.pos = buf.readBlockPos();
		boolean hasRecipe = buf.readBoolean();
		if (hasRecipe) {
			this.recipe = MixerRecipe.deserializeNbt(buf.readCompoundTag());
		} else {
			this.recipe = null;
		}
	}
}
