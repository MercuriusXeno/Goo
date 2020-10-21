package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
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

        INSTANCE.messageBuilder(CrystalProgressTickPacket.class, nextID())
                .encoder(CrystalProgressTickPacket::toBytes)
                .decoder(CrystalProgressTickPacket::new)
                .consumer(CrystalProgressTickPacket::handle)
                .add();
    }

    public static void sendToClientsAround(Object msg, ServerWorld serverWorld, BlockPos position) {
        Chunk chunk = serverWorld.getChunkAt(position);

        INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), msg);
    }

    public static void sendToClientsNearTarget(Object msg, ServerWorld world, BlockPos pos, int radius) {
        INSTANCE.send(PacketDistributor.NEAR
                .with(PacketDistributor.TargetPoint.p(pos.getX() + 0.5d, pos.getY() + 0.5d,
                        pos.getZ() + 0.5d, radius, world.getDimensionKey()))
                , msg);
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
