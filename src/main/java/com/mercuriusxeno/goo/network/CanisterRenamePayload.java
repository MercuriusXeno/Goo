package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: requests renaming a canister at a specific slot
 * within a multi-canister block.
 *
 * @param pos      the block position of the canister block
 * @param slot     the targeted sub-slot index
 * @param newLabel the new label text
 */
public record CanisterRenamePayload(BlockPos pos, int slot, String newLabel)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<CanisterRenamePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "canister_rename"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, CanisterRenamePayload> STREAM_CODEC =
        StreamCodec.of(CanisterRenamePayload::encode, CanisterRenamePayload::decode);

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
    private static void encode(FriendlyByteBuf buf, CanisterRenamePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeUtf(payload.newLabel);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static CanisterRenamePayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        String label = buf.readUtf();
        return new CanisterRenamePayload(pos, slot, label);
    }
}
