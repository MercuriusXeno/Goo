package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.BlobFlightManager;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for blob flight broadcasts. Receives the flight data
 * and registers it with BlobFlightManager for rendering the projectile arc.
 */
public final class BlobFlightHandler {

    private BlobFlightHandler() {}

    /** Handles the flight payload on the client render thread. */
    public static void handle(BlobFlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Goo.LOGGER.debug("Flight received: {} -> target in {} ticks",
                    payload.gooTypeId(), payload.travelTicks());
            BlobFlightManager.addFlight(payload);
        });
    }
}
