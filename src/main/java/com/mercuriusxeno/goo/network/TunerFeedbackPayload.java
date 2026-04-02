package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: sends tuner feedback for HUD display.
 * Includes a feedback type to distinguish brief messages, link completions,
 * awaiting state changes, confirmation prompts, and cancellations.
 */
public record TunerFeedbackPayload(
        FeedbackType feedbackType,
        List<String> lines,
        @Nullable BlockPos awaitingPos,
        int awaitingSlot,
        @Nullable GasketRole awaitingRole
) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<TunerFeedbackPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "tuner_feedback"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, TunerFeedbackPayload> STREAM_CODEC =
        StreamCodec.of(TunerFeedbackPayload::encode, TunerFeedbackPayload::decode);

    /** Feedback type categories. */
    public enum FeedbackType {
        /** Brief transient message (single line). */
        BRIEF,
        /** Link completed successfully. */
        LINK_COMPLETE,
        /** Awaiting state set: machine gasket is waiting for a partner. */
        AWAITING,
        /** Confirmation prompt: action requires a second click. */
        CONFIRM_PROMPT,
        /** Selection or await cancelled. */
        CANCEL
    }

    /** Creates a brief feedback with a single message line. */
    public static TunerFeedbackPayload brief(String message) {
        return new TunerFeedbackPayload(FeedbackType.BRIEF,
            List.of(message), null, -1, null);
    }

    /** Creates a link-complete feedback with endpoint descriptions. */
    public static TunerFeedbackPayload linkComplete(List<String> lines) {
        return new TunerFeedbackPayload(FeedbackType.LINK_COMPLETE,
            lines, null, -1, null);
    }

    /** Creates an awaiting feedback that sets the client-side await state. */
    public static TunerFeedbackPayload awaiting(
            String message, BlockPos pos, int slot, GasketRole role) {
        return new TunerFeedbackPayload(FeedbackType.AWAITING,
            List.of(message), pos, slot, role);
    }

    /** Creates a confirmation prompt feedback. */
    public static TunerFeedbackPayload confirmPrompt(String message) {
        return new TunerFeedbackPayload(FeedbackType.CONFIRM_PROMPT,
            List.of(message), null, -1, null);
    }

    /** Creates a cancel feedback that clears client-side await state. */
    public static TunerFeedbackPayload cancel(String message) {
        return new TunerFeedbackPayload(FeedbackType.CANCEL,
            List.of(message), null, -1, null);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the payload to the buffer. */
    private static void encode(FriendlyByteBuf buf, TunerFeedbackPayload payload) {
        buf.writeVarInt(payload.feedbackType().ordinal());
        buf.writeVarInt(payload.lines.size());
        for (String line : payload.lines) {
            buf.writeUtf(line);
        }
        buf.writeBoolean(payload.awaitingPos() != null);
        if (payload.awaitingPos() != null) {
            buf.writeBlockPos(payload.awaitingPos());
            buf.writeVarInt(payload.awaitingSlot());
            buf.writeBoolean(payload.awaitingRole() == GasketRole.RECEIVER);
        }
    }

    /** Reads the payload from the buffer. */
    private static TunerFeedbackPayload decode(FriendlyByteBuf buf) {
        FeedbackType feedbackType = FeedbackType.values()[buf.readVarInt()];
        int count = buf.readVarInt();
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf());
        }
        BlockPos awaitPos = null;
        int awaitSlot = -1;
        GasketRole awaitRole = null;
        if (buf.readBoolean()) {
            awaitPos = buf.readBlockPos();
            awaitSlot = buf.readVarInt();
            awaitRole = buf.readBoolean() ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        }
        return new TunerFeedbackPayload(feedbackType, List.copyOf(lines),
            awaitPos, awaitSlot, awaitRole);
    }
}
