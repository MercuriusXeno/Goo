package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.client.CanisterNamingScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for the open naming screen payload.
 * Opens or toggles the canister naming screen for a specific slot.
 */
public final class OpenNamingScreenHandler {

    private OpenNamingScreenHandler() {}

    /**
     * Handles the payload by opening the naming screen on the main client thread.
     *
     * @param payload the naming screen payload data
     * @param context the network context
     */
    public static void handle(OpenNamingScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> toggleNamingScreen(payload));
    }

    /** Toggles the naming screen: closes if already open, otherwise opens a new one.
     *
     * @param payload the naming screen payload data
     */
    private static void toggleNamingScreen(OpenNamingScreenPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CanisterNamingScreen) {
            mc.setScreen(null);
        } else {
            mc.setScreen(new CanisterNamingScreen(
                payload.pos(), payload.slot(), payload.currentLabel(),
                payload.hasLink()));
        }
    }
}
