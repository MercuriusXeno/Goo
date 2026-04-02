package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.client.TunerAwaitState;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload.FeedbackType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * Client-side handler for tuner feedback payloads. Dispatches by feedback type:
 * updates TunerAwaitState for AWAITING/CANCEL/LINK_COMPLETE, shows action bar
 * messages for all types.
 */
public final class TunerFeedbackHandler {

    private TunerFeedbackHandler() {}

    /** Handles the payload by dispatching based on feedback type. */
    public static void handle(TunerFeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.feedbackType()) {
                case AWAITING -> handleAwaiting(payload, context);
                case LINK_COMPLETE -> handleLinkComplete(payload, context);
                case CANCEL -> handleCancel(payload, context);
                case BRIEF, CONFIRM_PROMPT -> handleMessage(payload, context);
            }
        });
    }

    /** Sets the client-side await state and shows the message. */
    private static void handleAwaiting(
            TunerFeedbackPayload payload, IPayloadContext context) {
        if (payload.awaitingPos() != null && payload.awaitingRole() != null) {
            TunerAwaitState.set(payload.awaitingPos(),
                payload.awaitingSlot(), payload.awaitingRole());
        }
        showActionBar(payload.lines(), context);
    }

    /** Clears the await state and shows the link-complete message. */
    private static void handleLinkComplete(
            TunerFeedbackPayload payload, IPayloadContext context) {
        TunerAwaitState.clear();
        showActionBar(payload.lines(), context);
    }

    /** Clears the await state and shows the cancellation message. */
    private static void handleCancel(
            TunerFeedbackPayload payload, IPayloadContext context) {
        TunerAwaitState.clear();
        showActionBar(payload.lines(), context);
    }

    /** Shows lines as an action bar message. */
    private static void handleMessage(
            TunerFeedbackPayload payload, IPayloadContext context) {
        showActionBar(payload.lines(), context);
    }

    /** Joins lines and shows in the player's action bar. */
    private static void showActionBar(List<String> lines, IPayloadContext context) {
        if (lines.isEmpty()) return;
        String message = String.join(" | ", lines);
        context.player().sendOverlayMessage(
            Component.literal(message));
    }
}
