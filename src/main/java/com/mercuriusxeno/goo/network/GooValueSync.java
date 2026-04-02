package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;

/**
 * Utility class for sending goo value sync packets to players.
 * Provides methods for single-player and broadcast sends.
 */
public final class GooValueSync {

    private GooValueSync() {}

    /** Sends the full effective value map to a single player. */
    public static void sendToPlayer(ServerPlayer player) {
        GooValueSyncPayload payload = buildPayload();
        PacketDistributor.sendToPlayer(player, payload);
        Goo.LOGGER.debug("Sent {} goo values to {}", payload.values().size(), player.getName().getString());
    }

    /** Sends the full effective value map to all connected players. */
    public static void sendToAll(MinecraftServer server) {
        GooValueSyncPayload payload = buildPayload();
        PacketDistributor.sendToAllPlayers(payload);
        Goo.LOGGER.info("Broadcast {} goo values to all players", payload.values().size());
    }

    /** Builds the sync payload from the current effective values. */
    private static GooValueSyncPayload buildPayload() {
        Map<Identifier, GooValue> values = Goo.GOO_VALUES.getEffectiveValues();
        return new GooValueSyncPayload(values);
    }
}
