package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for goo value sync packets.
 * Applies received values to the client's registry on the main thread.
 */
public final class GooValueSyncHandler {

    private GooValueSyncHandler() {}

    /** Handles the sync payload by replacing client-side effective values. */
    public static void handle(GooValueSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Goo.GOO_VALUES.receiveClientValues(payload.values());
            Goo.LOGGER.info("Received {} goo values from server", payload.values().size());
        });
    }
}
