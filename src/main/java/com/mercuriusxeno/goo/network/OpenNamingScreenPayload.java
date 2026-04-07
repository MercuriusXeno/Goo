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
 *
 * @param pos          the block position of the canister block
 * @param slot         the sub-slot index
 * @param currentLabel the current label text
 * @param hasLink      whether the canister has an active gasket link
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

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, OpenNamingScreenPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeUtf(payload.currentLabel);
        buf.writeBoolean(payload.hasLink);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static OpenNamingScreenPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        String label = buf.readUtf();
        boolean hasLink = buf.readBoolean();
        return new OpenNamingScreenPayload(pos, slot, label, hasLink);
    }
}
