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

        INSTANCE.messageBuilder(ChangeItemTargetPacket.class, nextID())
                .encoder(ChangeItemTargetPacket::toBytes)
                .decoder(ChangeItemTargetPacket::new)
                .consumer(ChangeItemTargetPacket::handle)
                .add();

        INSTANCE.messageBuilder(SolidifierPoppedPacket.class, nextID())
                .encoder(SolidifierPoppedPacket::toBytes)
                .decoder(SolidifierPoppedPacket::new)
                .consumer(SolidifierPoppedPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooLobPacket.class, nextID())
                .encoder(GooLobPacket::toBytes)
                .decoder(GooLobPacket::new)
                .consumer(GooLobPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooGrabPacket.class, nextID())
                .encoder(GooGrabPacket::toBytes)
                .decoder(GooGrabPacket::new)
                .consumer(GooGrabPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooGauntletCollectPacket.class, nextID())
                .encoder(GooGauntletCollectPacket::toBytes)
                .decoder(GooGauntletCollectPacket::new)
                .consumer(GooGauntletCollectPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooBasinCollectPacket.class, nextID())
                .encoder(GooBasinCollectPacket::toBytes)
                .decoder(GooBasinCollectPacket::new)
                .consumer(GooBasinCollectPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooPlaceSplatPacket.class, nextID())
                .encoder(GooPlaceSplatPacket::toBytes)
                .decoder(GooPlaceSplatPacket::new)
                .consumer(GooPlaceSplatPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooPlaceSplatAreaPacket.class, nextID())
                .encoder(GooPlaceSplatAreaPacket::toBytes)
                .decoder(GooPlaceSplatAreaPacket::new)
                .consumer(GooPlaceSplatAreaPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooGauntletSwapPacket.class, nextID())
                .encoder(GooGauntletSwapPacket::toBytes)
                .decoder(GooGauntletSwapPacket::new)
                .consumer(GooGauntletSwapPacket::handle)
                .add();

        INSTANCE.messageBuilder(GooBasinSwapPacket.class, nextID())
                .encoder(GooBasinSwapPacket::toBytes)
                .decoder(GooBasinSwapPacket::new)
                .consumer(GooBasinSwapPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdateBulbCrystalProgressPacket.class, nextID())
                .encoder(UpdateBulbCrystalProgressPacket::toBytes)
                .decoder(UpdateBulbCrystalProgressPacket::new)
                .consumer(UpdateBulbCrystalProgressPacket::handle)
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
