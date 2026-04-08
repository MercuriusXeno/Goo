package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Client-to-server payload: requests unlinking gaskets at a specific block
 * position and slot. Includes an optional GasketRole for vat unlinking
 * (to target cap vs base specifically). When role is null, both gaskets
 * on the slot are unlinked (canister/hub behavior).
 *
 * @param pos  the block position
 * @param slot the sub-slot index
 * @param role the gasket role to unlink, or null for both
 */
public record CanisterUnlinkPayload(BlockPos pos, int slot,
        @Nullable GasketRole role) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<CanisterUnlinkPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "canister_unlink"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, CanisterUnlinkPayload> STREAM_CODEC =
        StreamCodec.of(CanisterUnlinkPayload::encode, CanisterUnlinkPayload::decode);

    /**
     * Convenience constructor for canister/hub (unlink both gaskets).
     *
     * @param pos  the block position
     * @param slot the canister slot index
     */
    public CanisterUnlinkPayload(BlockPos pos, int slot) {
        this(pos, slot, null);
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
    private static void encode(FriendlyByteBuf buf, CanisterUnlinkPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.slot);
        buf.writeBoolean(payload.role != null);
        if (payload.role != null) {
            buf.writeBoolean(payload.role == GasketRole.RECEIVER);
        }
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static CanisterUnlinkPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readVarInt();
        GasketRole role = null;
        if (buf.readBoolean()) {
            role = buf.readBoolean() ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        }
        return new CanisterUnlinkPayload(pos, slot, role);
    }
}
