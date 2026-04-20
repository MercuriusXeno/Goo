package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all network payloads for the Goo mod.
 * Uses mod event bus via {@code @EventBusSubscriber} to handle payload registration.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooNetworking {

    /** Protocol version for network payload registration. */
    private static final String PROTOCOL_VERSION = "1";

    private GooNetworking() {}

    /**
     * Registers all network payloads for both directions.
     *
     * @param event the payload registration event
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Goo.MODID).versioned(PROTOCOL_VERSION);
        registerClientPayloads(r);
        registerServerPayloads(r);
    }

    /** Registers client-bound payloads. Lambdas defer client-only class loading on dedicated servers.
     *
     * @param r the payload registrar
     */
    private static void registerClientPayloads(PayloadRegistrar r) {
        r.playToClient(GooValueSyncPayload.TYPE, GooValueSyncPayload.STREAM_CODEC, GooValueSyncHandler::handle);
        r.playToClient(OpenNamingScreenPayload.TYPE, OpenNamingScreenPayload.STREAM_CODEC,
                OpenNamingScreenHandler::handle);
        r.playToClient(TunerFeedbackPayload.TYPE, TunerFeedbackPayload.STREAM_CODEC,
                TunerFeedbackHandler::handle);
        r.playToClient(BlobFlightPayload.TYPE, BlobFlightPayload.STREAM_CODEC,
                BlobFlightHandler::handle);
        r.playToClient(AbilitySyncPayload.TYPE, AbilitySyncPayload.STREAM_CODEC,
                AbilitySyncHandler::handle);
    }

    /** Registers server-bound payloads.
     *
     * @param r the payload registrar
     */
    private static void registerServerPayloads(PayloadRegistrar r) {
        r.playToServer(CanisterRenamePayload.TYPE, CanisterRenamePayload.STREAM_CODEC, CanisterRenameHandler::handle);
        r.playToServer(CanisterUnlinkPayload.TYPE, CanisterUnlinkPayload.STREAM_CODEC, CanisterUnlinkHandler::handle);
        r.playToServer(BlobThrowPayload.TYPE, BlobThrowPayload.STREAM_CODEC, BlobThrowHandler::handle);
        r.playToServer(GloveSelectPayload.TYPE, GloveSelectPayload.STREAM_CODEC, GloveSelectHandler::handle);
    }
}
