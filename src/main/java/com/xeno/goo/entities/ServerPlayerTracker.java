package com.xeno.goo.entities;

import net.minecraft.entity.player.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerPlayerTracker
{
    private static Map<UUID, ServerPlayerState> serverPlayers = new HashMap<>();

    public static ServerPlayerState getOrDefault(UUID ownerUuid)
    {
        return serverPlayers.getOrDefault(ownerUuid, ServerPlayerState.UNKNOWN);
    }

    public static void update(ServerPlayerEntity playerUuid, boolean isRightMouseHeld)
    {
        serverPlayers.put(playerUuid.getUniqueID(), new ServerPlayerState(isRightMouseHeld));
    }
}
