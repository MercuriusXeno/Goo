package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Network payload carrying the full effective goo value map from server to client.
 * Wire format: VarInt entry count, then per entry: Identifier + VarInt type count + per type: VarInt ordinal + VarInt amount.
 */
public record GooValueSyncPayload(Map<Identifier, GooValue> values) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooValueSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_value_sync"));

    /** Stream codec for encoding/decoding the payload. */
    public static final StreamCodec<FriendlyByteBuf, GooValueSyncPayload> STREAM_CODEC =
            StreamCodec.of(GooValueSyncPayload::encode, GooValueSyncPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Writes the full value map to the buffer. */
    private static void encode(FriendlyByteBuf buf, GooValueSyncPayload payload) {
        buf.writeVarInt(payload.values.size());
        for (Map.Entry<Identifier, GooValue> entry : payload.values.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            encodeGooValue(buf, entry.getValue());
        }
    }

    /** Reads the full value map from the buffer. */
    private static GooValueSyncPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<Identifier, GooValue> values = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            Identifier id = buf.readIdentifier();
            values.put(id, decodeGooValue(buf));
        }
        return new GooValueSyncPayload(values);
    }

    /** Writes a single GooValue: VarInt type count, then ordinal + amount pairs. */
    private static void encodeGooValue(FriendlyByteBuf buf, GooValue value) {
        Map<GooType, Integer> all = value.getAll();
        buf.writeVarInt(all.size());
        for (Map.Entry<GooType, Integer> entry : all.entrySet()) {
            buf.writeVarInt(entry.getKey().ordinal());
            buf.writeVarInt(entry.getValue());
        }
    }

    /** Reads a single GooValue: VarInt type count, then ordinal + amount pairs. */
    private static GooValue decodeGooValue(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        Map<GooType, Integer> map = new LinkedHashMap<>(typeCount);
        GooType[] types = GooType.values();
        for (int i = 0; i < typeCount; i++) {
            int ordinal = buf.readVarInt();
            int amount = buf.readVarInt();
            if (ordinal >= 0 && ordinal < types.length) {
                map.put(types[ordinal], amount);
            }
        }
        return new GooValue(map);
    }
}
