package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.BlobFlightManager;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for blob flight broadcasts. Receives the flight data
 * and registers it with BlobFlightManager for rendering the projectile arc.
 */
public final class BlobFlightHandler {

    /** Log message for received flight payloads. */
    private static final String LOG_FLIGHT_RECEIVED = "Flight received: {} -> target in {} ticks";

    private BlobFlightHandler() {}

    /**
     * Handles the flight payload on the client render thread.
     *
     * @param payload the flight payload data
     * @param context the network context
     */
    public static void handle(BlobFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Goo.LOGGER.isDebugEnabled()) { Goo.LOGGER.debug(LOG_FLIGHT_RECEIVED, payload.gooTypeId(), payload.travelTicks()); }
            BlobFlightManager.addFlight(payload);
        });
    }
}
