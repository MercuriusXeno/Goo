package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: tells the client to open the canister naming screen
 * for a specific slot within a multi-canister block.
 * Includes whether the canister has an active gasket link (for "Clear Link" button).
 */
public record OpenNamingScreenPayload(BlockPos pos, int slot, String currentLabel,
        boolean hasLink)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<OpenNamingScreenPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "open_naming_screen"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, OpenNamingScreenPayload> STREAM_CODEC =
        StreamCodec.of(OpenNamingScreenPayload::encode, OpenNamingScreenPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the payload to the buffer. */
    private static void encode(FriendlyByteBuf buf, OpenNamingScreenPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeUtf(payload.currentLabel);
        buf.writeBoolean(payload.hasLink);
    }

    /** Reads the payload from the buffer. */
    private static OpenNamingScreenPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        String label = buf.readUtf();
        boolean hasLink = buf.readBoolean();
        return new OpenNamingScreenPayload(pos, slot, label, hasLink);
    }
}
