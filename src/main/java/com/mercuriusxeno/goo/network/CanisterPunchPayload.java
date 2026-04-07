package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: requests popping a specific canister out of a
 * multi-canister block by left-clicking (punching) the targeted slot.
 *
 * @param pos  the block position of the canister block
 * @param slot the targeted sub-slot index
 */
public record CanisterPunchPayload(BlockPos pos, int slot)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<CanisterPunchPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "canister_punch"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, CanisterPunchPayload> STREAM_CODEC =
        StreamCodec.of(CanisterPunchPayload::encode, CanisterPunchPayload::decode);

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
    private static void encode(FriendlyByteBuf buf, CanisterPunchPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static CanisterPunchPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        return new CanisterPunchPayload(pos, slot);
    }
}
