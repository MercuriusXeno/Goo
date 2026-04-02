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

    /** Handles the payload by opening the naming screen on the main client thread. */
    public static void handle(OpenNamingScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CanisterNamingScreen) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new CanisterNamingScreen(
                    payload.pos(), payload.slot(), payload.currentLabel(),
                    payload.hasLink()));
            }
        });
    }
}
