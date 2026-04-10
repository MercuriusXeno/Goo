package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
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
 *
 * @param feedbackType the type of feedback being sent
 * @param lines        the display lines for HUD rendering
 * @param awaitingPos  the block position being awaited, or null
 * @param awaitingSlot the sub-slot being awaited
 * @param awaitingRole the gasket role being awaited, or null
 */
public record TunerFeedbackPayload(
        FeedbackType feedbackType,
        List<String> lines,
        @Nullable BlockPos awaitingPos,
        int awaitingSlot,
        @Nullable GasketRole awaitingRole
) implements CustomPacketPayload {

    /** Sentinel slot value meaning no sub-slot awaiting. */
    private static final int NO_SLOT = -1;

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

    /**
     * Creates a brief feedback with a single message line.
     *
     * @param message the feedback message
     * @return the constructed payload
     */
    public static TunerFeedbackPayload brief(String message) {
        return new TunerFeedbackPayload(FeedbackType.BRIEF,
            List.of(message), null, NO_SLOT, null);
    }

    /**
     * Creates a link-complete feedback with endpoint descriptions.
     *
     * @param lines the endpoint description lines
     * @return the constructed payload
     */
    public static TunerFeedbackPayload linkComplete(List<String> lines) {
        return new TunerFeedbackPayload(FeedbackType.LINK_COMPLETE,
            lines, null, NO_SLOT, null);
    }

    /**
     * Creates an awaiting feedback that sets the client-side await state.
     *
     * @param message the feedback message
     * @param pos     the awaiting block position
     * @param slot    the awaiting slot index
     * @param role    the gasket role being awaited
     * @return the constructed payload
     */
    public static TunerFeedbackPayload awaiting(
            String message, BlockPos pos, int slot, GasketRole role) {
        return new TunerFeedbackPayload(FeedbackType.AWAITING,
            List.of(message), pos, slot, role);
    }

    /**
     * Creates a confirmation prompt feedback.
     *
     * @param message the prompt message
     * @return the constructed payload
     */
    public static TunerFeedbackPayload confirmPrompt(String message) {
        return new TunerFeedbackPayload(FeedbackType.CONFIRM_PROMPT,
            List.of(message), null, NO_SLOT, null);
    }

    /**
     * Creates a cancel feedback that clears client-side await state.
     *
     * @param message the cancellation message
     * @return the constructed payload
     */
    public static TunerFeedbackPayload cancel(String message) {
        return new TunerFeedbackPayload(FeedbackType.CANCEL,
            List.of(message), null, NO_SLOT, null);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, TunerFeedbackPayload payload) {
        encodeHeader(buf, payload);
        encodeAwaitState(buf, payload);
    }

    /** Writes feedback type and message lines.
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeHeader(FriendlyByteBuf buf, TunerFeedbackPayload payload) {
        buf.writeVarInt(payload.feedbackType().ordinal());
        buf.writeVarInt(payload.lines.size());
        for (String line : payload.lines) { buf.writeUtf(line); }
    }

    /** Writes optional await state (pos, slot, role).
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeAwaitState(FriendlyByteBuf buf, TunerFeedbackPayload payload) {
        buf.writeBoolean(payload.awaitingPos() != null);
        if (payload.awaitingPos() != null) {
            buf.writeBlockPos(payload.awaitingPos());
            buf.writeVarInt(payload.awaitingSlot());
            buf.writeBoolean(payload.awaitingRole() == GasketRole.RECEIVER);
        }
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static TunerFeedbackPayload decode(FriendlyByteBuf buf) {
        FeedbackType feedbackType = FeedbackType.values()[buf.readVarInt()];
        List<String> lines = decodeLines(buf);
        return decodeWithAwaitState(buf, feedbackType, lines);
    }

    /** Reads the message lines list from the buffer.
     *
     * @param buf the input buffer
     * @return the immutable list of message lines
     */
    private static List<String> decodeLines(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) { lines.add(buf.readUtf()); }
        return List.copyOf(lines);
    }

    /** Reads optional await state and builds the final payload.
     *
     * @param buf          the input buffer
     * @param feedbackType the decoded feedback type
     * @param lines        the decoded message lines
     * @return the decoded payload
     */
    private static TunerFeedbackPayload decodeWithAwaitState(FriendlyByteBuf buf,
            FeedbackType feedbackType, List<String> lines) {
        if (!buf.readBoolean()) {
            return new TunerFeedbackPayload(feedbackType, lines, null, NO_SLOT, null);
        }
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        GasketRole role = buf.readBoolean() ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        return new TunerFeedbackPayload(feedbackType, lines, pos, slot, role);
    }
}
