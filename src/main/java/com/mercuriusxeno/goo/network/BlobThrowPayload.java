package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: requests throwing the selected goo type at a target.
 * The server validates goo availability and range before executing the throw.
 */
public record BlobThrowPayload(String gooTypeId, int targetEntityId,
                               BlockPos targetPos, int targetFace)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<BlobThrowPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "blob_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, BlobThrowPayload> STREAM_CODEC =
        StreamCodec.of(BlobThrowPayload::encode, BlobThrowPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the payload to the buffer. */
    private static void encode(FriendlyByteBuf buf, BlobThrowPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFace);
    }

    /** Reads the payload from the buffer. */
    private static BlobThrowPayload decode(FriendlyByteBuf buf) {
        String gooTypeId = buf.readUtf();
        int targetEntityId = buf.readVarInt();
        BlockPos targetPos = buf.readBlockPos();
        int targetFace = buf.readVarInt();
        return new BlobThrowPayload(gooTypeId, targetEntityId, targetPos, targetFace);
    }
}
