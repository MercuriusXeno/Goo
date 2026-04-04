package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: broadcasts a blob in flight so nearby clients
 * can render the projectile arc. Sent to all players tracking the thrower.
 */
public record BlobFlightPayload(double startX, double startY, double startZ,
                                String gooTypeId, int targetEntityId,
                                BlockPos targetPos, int targetFace,
                                int travelTicks, boolean grannyArc)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<BlobFlightPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "blob_flight"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, BlobFlightPayload> STREAM_CODEC =
        StreamCodec.of(BlobFlightPayload::encode, BlobFlightPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the payload to the buffer. */
    private static void encode(FriendlyByteBuf buf, BlobFlightPayload payload) {
        buf.writeDouble(payload.startX);
        buf.writeDouble(payload.startY);
        buf.writeDouble(payload.startZ);
        buf.writeUtf(payload.gooTypeId);
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFace);
        buf.writeVarInt(payload.travelTicks);
        buf.writeBoolean(payload.grannyArc);
    }

    /** Reads the payload from the buffer. */
    private static BlobFlightPayload decode(FriendlyByteBuf buf) {
        double startX = buf.readDouble();
        double startY = buf.readDouble();
        double startZ = buf.readDouble();
        String gooTypeId = buf.readUtf();
        int targetEntityId = buf.readVarInt();
        BlockPos targetPos = buf.readBlockPos();
        int targetFace = buf.readVarInt();
        int travelTicks = buf.readVarInt();
        boolean grannyArc = buf.readBoolean();
        return new BlobFlightPayload(startX, startY, startZ, gooTypeId,
                targetEntityId, targetPos, targetFace, travelTicks, grannyArc);
    }
}
