package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: syncs the player's goo type selection on their
 * glove so the data component persists on the server (and survives reload).
 *
 * @param gooTypeId the selected goo type id, or empty string to clear
 * @param abilityId the selected ability id string, or empty for type-only
 */
public record GloveSelectPayload(String gooTypeId, String abilityId) implements CustomPacketPayload {

    public static final Type<GloveSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glove_select"));

    public static final StreamCodec<FriendlyByteBuf, GloveSelectPayload> STREAM_CODEC =
            StreamCodec.of(GloveSelectPayload::encode, GloveSelectPayload::decode);

    /** Backward-compat constructor for type-only selection. */
    public GloveSelectPayload(String gooTypeId) {
        this(gooTypeId, "");
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
    private static void encode(FriendlyByteBuf buf, GloveSelectPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static GloveSelectPayload decode(FriendlyByteBuf buf) {
        return new GloveSelectPayload(buf.readUtf(), buf.readUtf());
    }
}
