package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class Networking {
    private static SimpleChannel INSTANCE;
    private static int ID = 0;

    private static int nextID() {
        return ID++;
    }

    public static void registerNetworkMessages() {
        INSTANCE = NetworkRegistry.newSimpleChannel(new ResourceLocation(GooMod.MOD_ID, "goo"),
                () -> "1.0",
                s -> true,
                s -> true);

        INSTANCE.messageBuilder(FluidUpdatePacket.class, nextID())
                .encoder(FluidUpdatePacket::toBytes)
                .decoder(FluidUpdatePacket::new)
                .consumer(FluidUpdatePacket::handle)
                .add();

        INSTANCE.messageBuilder(GooFlowPacket.class, nextID())
                .encoder(GooFlowPacket::toBytes)
                .decoder(GooFlowPacket::new)
                .consumer(GooFlowPacket::handle)
                .add();

        INSTANCE.messageBuilder(ChangeSolidifierTargetPacket.class, nextID())
                .encoder(ChangeSolidifierTargetPacket::toBytes)
                .decoder(ChangeSolidifierTargetPacket::new)
                .consumer(ChangeSolidifierTargetPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooValueSyncPacket.class, nextID())
                .encoder(GooValueSyncPacket::toBytes)
                .decoder(GooValueSyncPacket::new)
                .consumer(GooValueSyncPacket::handle)
                .add();

        INSTANCE.messageBuilder(SolidifierPoppedPacket.class, nextID())
                .encoder(SolidifierPoppedPacket::toBytes)
                .decoder(SolidifierPoppedPacket::new)
                .consumer(SolidifierPoppedPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooGrabPacket.class, nextID())
                .encoder(GooGrabPacket::toBytes)
                .decoder(GooGrabPacket::new)
                .consumer(GooGrabPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooLobPacket.class, nextID())
                .encoder(GooLobPacket::toBytes)
                .decoder(GooLobPacket::new)
                .consumer(GooLobPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooLobConfirmationPacket.class, nextID())
                .encoder(GooLobConfirmationPacket::toBytes)
                .decoder(GooLobConfirmationPacket::new)
                .consumer(GooLobConfirmationPacket::handle)
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

    public static void sendToServer(Object msg, ClientPlayerEntity player)
    {
        if (player.world.isRemote()) {
            INSTANCE.sendTo(msg, player.connection.getNetworkManager(), NetworkDirection.PLAY_TO_SERVER);
        }
    }

    public static void syncGooValuesForPlayer(ServerPlayerEntity player)
    {
        // GooValueSyncPacket packet = GooMod.handler.createPacketData();
        // sendRemotePacket(packet, player);
    }
}
