package com.xeno.goop.network;

import com.xeno.goop.GoopMod;
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

    public static void registerNetworkMessages() {
        INSTANCE = NetworkRegistry.newSimpleChannel(new ResourceLocation(GoopMod.MOD_ID, "goop"),
                () -> "1.0",
                s -> true,
                s -> true);

        INSTANCE.messageBuilder(FluidUpdatePacket.class, nextID())
                .encoder(FluidUpdatePacket::toBytes)
                .decoder(FluidUpdatePacket::new)
                .consumer(FluidUpdatePacket::handle)
                .add();

        INSTANCE.messageBuilder(BulbVerticalFillPacket.class, nextID())
                .encoder(BulbVerticalFillPacket::toBytes)
                .decoder(BulbVerticalFillPacket::new)
                .consumer(BulbVerticalFillPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChangeSolidifierTargetPacket.class, nextID())
                .encoder(ChangeSolidifierTargetPacket::toBytes)
                .decoder(ChangeSolidifierTargetPacket::new)
                .consumer(ChangeSolidifierTargetPacket::handle)
                .add();

        INSTANCE.messageBuilder(GoopValueSyncPacket.class, nextID())
                .encoder(GoopValueSyncPacket::toBytes)
                .decoder(GoopValueSyncPacket::new)
                .consumer(GoopValueSyncPacket::handle)
                .add();
    }

    public static void sendToClientsAround(Object msg, ServerWorld serverWorld, BlockPos position) {
        Chunk chunk = serverWorld.getChunkAt(position);

        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static void sendRemotePacket(Object msg, ServerPlayerEntity player) {
        if (player.server.isDedicatedServer() || !player.getGameProfile().getName().equals(player.server.getServerOwner())) {
            INSTANCE.sendTo(msg, player.connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    public static void syncGoopValuesForPlayer(ServerPlayerEntity player)
    {
        GoopValueSyncPacket packet = GoopMod.mappingHandler.createPacketData();
        sendRemotePacket(packet, player);
    }
}
