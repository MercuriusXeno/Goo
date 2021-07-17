package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent.Context;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Networking {

	private static SimpleChannel INSTANCE;
	private static int ID = 0;

	private static synchronized int nextID() {

		return ID++;
	}

	public static void registerNetworkMessages() {

		INSTANCE = NetworkRegistry.newSimpleChannel(
				new ResourceLocation(GooMod.MOD_ID, "goo"),
				() -> "1.0",
				s -> true,
				s -> true
		);

		registerPacket(
				FluidUpdatePacket.class,
				FluidUpdatePacket::new
		);

		registerPacket(
				GooFlowPacket.class,
				GooFlowPacket::new
		);

		registerPacket(
				ChangeItemTargetPacket.class,
				ChangeItemTargetPacket::new
		);

		registerPacket(
				SolidifierPoppedPacket.class,
				SolidifierPoppedPacket::new
		);

		registerPacket(
				GooGauntletSwapPacket.class,
				GooGauntletSwapPacket::new
		);

		registerPacket(
				GooVesselSwapPacket.class,
				GooVesselSwapPacket::new
		);

		registerPacket(
				UpdateBulbCrystalProgressPacket.class,
				UpdateBulbCrystalProgressPacket::new
		);

		registerPacket(
				CrystalProgressTickPacket.class,
				CrystalProgressTickPacket::new
		);

		registerPacket(
				ShrinkPacket.class,
				ShrinkPacket::new
		);

		registerPacket(
				FertilizePacket.class,
				FertilizePacket::new
		);

		registerPacket(
				BlobHitInteractionPacket.class,
				BlobHitInteractionPacket::new
		);

		registerPacket(
				BlobInteractionPacket.class,
				BlobInteractionPacket::new
		);

		registerPacket(
				SplatInteractionPacket.class,
				SplatInteractionPacket::new
		);

		registerPacket(
				MixerAnimationPacket.class,
				MixerAnimationPacket::new
		);

		registerPacket(
				MixerRecipePacket.class,
				MixerRecipePacket::new
		);

		registerPacket(
				CrucibleCurrentItemPacket.class,
				CrucibleCurrentItemPacket::new
		);

		registerPacket(
				CrucibleMeltProgressPacket.class,
				CrucibleMeltProgressPacket::new
		);

		registerPacket(
				CrucibleBoilProgressPacket.class,
				CrucibleBoilProgressPacket::new
		);
	}

	private static <M extends IGooModPacket> void registerPacket(Class<M> clazz, Function<PacketBuffer, M> constructor) {

		registerPacket(clazz, constructor, () -> IGooModPacket::handle);
	}

	private static <M extends IGooModPacket> void registerPacket(Class<M> clazz, Function<PacketBuffer, M> constructor, Supplier<BiConsumer<M, Supplier<Context>>> handler) {

		INSTANCE.messageBuilder(clazz, nextID())
				.consumer(handler.get())
				.decoder(constructor)
				.encoder(IGooModPacket::toBytes)
				.add();
	}

	public static void sendToClientsAround(Object msg, ServerWorld serverWorld, BlockPos position) {

		Chunk chunk = serverWorld.getChunkAt(position);

		INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
	}

	public static void sendToClientsNearTarget(Object msg, ServerWorld world, BlockPos pos, int radius) {

		INSTANCE.send(PacketDistributor.NEAR.with(
				PacketDistributor.TargetPoint.p(
						pos.getX() + 0.5d,
						pos.getY() + 0.5d,
						pos.getZ() + 0.5d,
						radius,
						world.getDimensionKey()
				)
		), msg);
	}

	public static void sendRemotePacket(Object msg, ServerPlayerEntity player) {

		if (player.server.isDedicatedServer() || !player.getGameProfile().getName().equals(player.server.getServerOwner())) {
			INSTANCE.sendTo(msg, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
		}
	}

	public static void sendToServer(Object msg, ClientPlayerEntity player) {

		if (player.world.isRemote()) {
			INSTANCE.sendTo(msg, player.connection.getNetworkManager(), NetworkDirection.PLAY_TO_SERVER);
		}
	}

	public static void send(PacketDistributor.PacketTarget target, Object msg) {

		INSTANCE.send(target, msg);
	}

	public static void syncGooValuesForPlayer(ServerPlayerEntity player) {

		// GooValueSyncPacket packet = GooMod.handler.createPacketData();
		// sendRemotePacket(packet, player);
	}
}
