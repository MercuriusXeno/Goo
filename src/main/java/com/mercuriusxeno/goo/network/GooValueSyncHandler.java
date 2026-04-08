package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for goo value sync packets.
 * Applies received values to the client's registry on the main thread.
 */
public final class GooValueSyncHandler {

    /** Log message for received value sync. */
    private static final String LOG_RECEIVED = "Received {} goo values from server";

    private GooValueSyncHandler() {}

    /**
     * Handles the sync payload by replacing client-side effective values.
     *
     * @param payload the sync payload data
     * @param context the network context
     */
    public static void handle(GooValueSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Goo.GOO_VALUES.receiveClientValues(payload.values());
            if (Goo.LOGGER.isInfoEnabled()) { Goo.LOGGER.info(LOG_RECEIVED, payload.values().size()); }
        });
    }
}
