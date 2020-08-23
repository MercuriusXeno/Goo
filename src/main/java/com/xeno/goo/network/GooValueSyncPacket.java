package com.xeno.goo.network;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.Compare;
import com.xeno.goo.evaluations.GooEntry;
import com.xeno.goo.evaluations.GooValue;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class GooValueSyncPacket implements IGooModPacket
{
    private Map<String, GooEntry> data;

    public GooValueSyncPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        Map<String, GooEntry> data = new TreeMap<>(Compare.stringLexicographicalComparator);
        int mappingCount = buf.readVarInt();
        for(int syncIndex = 0; syncIndex < mappingCount; syncIndex++) {
            String key = buf.readString();
            boolean isEmpty = buf.readBoolean();
            boolean isUnknown = buf.readBoolean();
            boolean isDenied = buf.readBoolean();
            int count = buf.readVarInt();
            List<GooValue> values = new ArrayList<>();
            for(int i = 0; i < count; i++) {
                values.add(new GooValue(buf.readString(), buf.readDouble()));
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

    public GooValueSyncPacket(Map<String, GooEntry> data) {
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

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                GooMod.handler.fromPacket(data);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
