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
 *
 * @param startX         the starting X position
 * @param startY         the starting Y position
 * @param startZ         the starting Z position
 * @param gooTypeId      the goo type string identifier
 * @param targetEntityId the target entity ID, or -1 for block targets
 * @param targetPos      the target block position
 * @param targetFace     the target face ordinal
 * @param travelTicks    the number of ticks for the flight arc
 * @param grannyArc      whether to use the boosted arc trajectory
 * @param abilityId      the selected ability id string, or empty for legacy
 */
public record BlobFlightPayload(double startX, double startY, double startZ,
                                String gooTypeId, int targetEntityId,
                                BlockPos targetPos, int targetFace,
                                int travelTicks, boolean grannyArc,
                                String abilityId)
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

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, BlobFlightPayload payload) {
        encodeStart(buf, payload);
        encodeTarget(buf, payload);
    }

    /** Writes start position and goo type.
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeStart(FriendlyByteBuf buf, BlobFlightPayload payload) {
        buf.writeDouble(payload.startX);
        buf.writeDouble(payload.startY);
        buf.writeDouble(payload.startZ);
        buf.writeUtf(payload.gooTypeId);
    }

    /** Writes target entity, position, face, travel time, and arc flag.
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeTarget(FriendlyByteBuf buf, BlobFlightPayload payload) {
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFace);
        buf.writeVarInt(payload.travelTicks);
        buf.writeBoolean(payload.grannyArc);
        buf.writeUtf(payload.abilityId);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static BlobFlightPayload decode(FriendlyByteBuf buf) {
        return new BlobFlightPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readUtf(), buf.readVarInt(), buf.readBlockPos(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf());
    }
}
