package com.xeno.goop.network;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.MappingHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

public class Networking {
    private static SimpleChannel INSTANCE;
    private static int ID = 0;

    private static int nextID() {
        return ID++;
    }

    public static void registerMessages() {
        INSTANCE = NetworkRegistry.newSimpleChannel(new ResourceLocation(GoopMod.MOD_ID, "goop"),
                () -> "1.0",
                s -> true,
                s -> true);

        INSTANCE.messageBuilder(FluidUpdatePacket.class, nextID())
                .encoder(FluidUpdatePacket::toBytes)
                .decoder(FluidUpdatePacket::new)
                .consumer(FluidUpdatePacket::handle)
                .add();
    }

    public static void sendToClientsAround(Object msg, ServerWorld serverWorld, BlockPos position) {
        Chunk chunk = serverWorld.getChunkAt(position);

        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static void sendToClient(Object packet, ServerPlayerEntity player) {
        INSTANCE.sendTo(packet, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void syncGoopValuesForEveryone() {
        GoopValueSyncPacket packet = new GoopValueSyncPacket(serializeGoopMappings());
        for (ServerPlayerEntity player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            sendRemotePacket(packet, player);
        }
    }

    private static GoopValueSyncPacketData[] serializeGoopMappings() {
        GoopValueSyncPacketData[] data = GoopMod.mappingHandler.createPacketData();
        PacketBuffer packetBuffer = new PacketBuffer(Unpooled.buffer());
        GoopValueSyncPacket.write(new GoopValueSyncPacket(data), packetBuffer);
        packetBuffer.release();
        return data;
    }

    public static void sendRemotePacket(Object msg, ServerPlayerEntity player) {
        if (player.server.isDedicatedServer() || !player.getGameProfile().getName().equals(player.server.getServerOwner())) {
            INSTANCE.sendTo(msg, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}
