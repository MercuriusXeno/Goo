package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.Compare;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.library.GooValue;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class GooValueSyncPacket
{
    private final Map<String, GooEntry> data;
    public GooValueSyncPacket(Map<String, GooEntry> data) {
        this.data = data;
    }

    public GooValueSyncPacket(PacketBuffer buffer) {
        Map<String, GooEntry> data = new TreeMap<>(Compare.stringLexicographicalComparator);
        int mappingCount = buffer.readVarInt();
        for(int syncIndex = 0; syncIndex < mappingCount; syncIndex++) {
            String key = buffer.readString();
            boolean isEmpty = buffer.readBoolean();
            boolean isUnknown = buffer.readBoolean();
            boolean isDenied = buffer.readBoolean();
            int count = buffer.readVarInt();
            List<GooValue> values = new ArrayList<>();
            for(int i = 0; i < count; i++) {
                values.add(new GooValue(buffer.readString(), buffer.readDouble()));
            }
            if (isEmpty) {
                data.put(key, GooEntry.EMPTY);
            } else if (isUnknown) {
                data.put(key, GooEntry.UNKNOWN);
            } else if (isDenied) {
                data.put(key, GooEntry.DENIED);
            } else {
                data.put(key, new GooEntry(values));
            }
        }
        this.data = data;
    }

    public void toBytes(PacketBuffer buffer) {
        buffer.writeVarInt(data.entrySet().size());
        for(Map.Entry<String, GooEntry> e : data.entrySet()) {
            buffer.writeString(e.getKey());
            buffer.writeBoolean(e.getValue().isEmpty());
            buffer.writeBoolean(e.getValue().isUnknown());
            buffer.writeBoolean(e.getValue().isDenied());
            buffer.writeVarInt(e.getValue().values().size());
            for(GooValue value : e.getValue().values()) {
                buffer.writeString(value.getFluidResourceLocation());
                buffer.writeDouble(value.amount());
            }
        }
    }

    public static void handle(final GooValueSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                GooMod.handler.fromPacket(packet.data);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
