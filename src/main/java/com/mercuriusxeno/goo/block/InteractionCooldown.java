package com.mercuriusxeno.goo.block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents rapid re-triggering of canister placement and pickup
 * when the player holds the interact button across multiple ticks.
 */
public final class InteractionCooldown {

    /** Minimum ticks between consecutive canister place/pickup interactions. */
    private static final long COOLDOWN_TICKS = 10;

    /** Tracks the last successful interaction tick per player. */
    private static final Map<UUID, Long> LAST_INTERACTION = new HashMap<>();

    private InteractionCooldown() {}

    /**
     * Returns true if the player is still within the cooldown window.
     *
     * @param playerId the player's UUID
     * @param gameTick the current game tick from Level.getGameTime()
     * @return true if the cooldown has not yet elapsed
     */
    public static boolean isOnCooldown(UUID playerId, long gameTick) {
        Long last = LAST_INTERACTION.get(playerId);
        return last != null && gameTick - last < COOLDOWN_TICKS;
    }

    /**
     * Records a successful interaction for cooldown tracking.
     *
     * @param playerId the player's UUID
     * @param gameTick the current game tick from Level.getGameTime()
     */
    public static void markInteraction(UUID playerId, long gameTick) {
        LAST_INTERACTION.put(playerId, gameTick);
    }
}
