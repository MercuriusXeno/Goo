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
 */
public record GloveSelectPayload(String gooTypeId) implements CustomPacketPayload {

    public static final Type<GloveSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "glove_select"));

    public static final StreamCodec<FriendlyByteBuf, GloveSelectPayload> STREAM_CODEC =
            StreamCodec.of(GloveSelectPayload::encode, GloveSelectPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GloveSelectPayload payload) {
        buf.writeUtf(payload.gooTypeId);
    }

    private static GloveSelectPayload decode(FriendlyByteBuf buf) {
        return new GloveSelectPayload(buf.readUtf());
    }
}
