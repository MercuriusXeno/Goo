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

    private GooNetworking() {}

    /** Registers all network payloads for both directions. */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Goo.MODID).versioned("1");
        // Client-bound payloads use lambdas (not method references) to defer
        // class loading of client-only handler classes on the dedicated server.
        registrar.playToClient(
                GooValueSyncPayload.TYPE,
                GooValueSyncPayload.STREAM_CODEC,
                (payload, context) -> GooValueSyncHandler.handle(payload, context)
        );
        registrar.playToClient(
                OpenNamingScreenPayload.TYPE,
                OpenNamingScreenPayload.STREAM_CODEC,
                (payload, context) -> OpenNamingScreenHandler.handle(payload, context)
        );
        registrar.playToClient(
                TunerFeedbackPayload.TYPE,
                TunerFeedbackPayload.STREAM_CODEC,
                (payload, context) -> TunerFeedbackHandler.handle(payload, context)
        );
        registrar.playToServer(
                CanisterRenamePayload.TYPE,
                CanisterRenamePayload.STREAM_CODEC,
                CanisterRenameHandler::handle
        );
        registrar.playToServer(
                CanisterPunchPayload.TYPE,
                CanisterPunchPayload.STREAM_CODEC,
                CanisterPunchHandler::handle
        );
        registrar.playToServer(
                CanisterUnlinkPayload.TYPE,
                CanisterUnlinkPayload.STREAM_CODEC,
                CanisterUnlinkHandler::handle
        );
        registrar.playToServer(
                BlobThrowPayload.TYPE,
                BlobThrowPayload.STREAM_CODEC,
                BlobThrowHandler::handle
        );
        registrar.playToServer(
                GloveSelectPayload.TYPE,
                GloveSelectPayload.STREAM_CODEC,
                GloveSelectHandler::handle
        );
        registrar.playToClient(
                BlobFlightPayload.TYPE,
                BlobFlightPayload.STREAM_CODEC,
                (payload, context) -> BlobFlightHandler.handle(payload, context)
        );
    }
}
