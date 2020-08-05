package com.xeno.goop.network;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.Compare;
import com.xeno.goop.library.GoopMapping;
import com.xeno.goop.library.GoopValue;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class GoopValueSyncPacket {
    private final Map<String, GoopMapping> data;
    public GoopValueSyncPacket(Map<String, GoopMapping> data) {
        this.data = data;
    }

    public GoopValueSyncPacket(PacketBuffer buffer) {
        Map<String, GoopMapping> data = new TreeMap<>(Compare.stringLexicographicalComparator);
        int mappingCount = buffer.readVarInt();
        for(int syncIndex = 0; syncIndex < mappingCount; syncIndex++) {
            String key = buffer.readString();
            boolean isEmpty = buffer.readBoolean();
            boolean isUnknown = buffer.readBoolean();
            boolean isDenied = buffer.readBoolean();
            int goopCount = buffer.readVarInt();
            List<GoopValue> goopValues = new ArrayList<>();
            for(int goopIndex = 0; goopIndex < goopCount; goopIndex++) {
                goopValues.add(new GoopValue(buffer.readString(), buffer.readDouble()));
            }
            if (isEmpty) {
                data.put(key, GoopMapping.EMPTY);
            } else if (isUnknown) {
                data.put(key, GoopMapping.UNKNOWN);
            } else if (isDenied) {
                data.put(key, GoopMapping.DENIED);
            } else {
                data.put(key, new GoopMapping(goopValues));
            }
        }
        this.data = data;
    }

    public void toBytes(PacketBuffer buffer) {
        buffer.writeVarInt(data.entrySet().size());
        for(Map.Entry<String, GoopMapping> e : data.entrySet()) {
            buffer.writeString(e.getKey());
            buffer.writeBoolean(e.getValue().isEmpty());
            buffer.writeBoolean(e.getValue().isUnknown());
            buffer.writeBoolean(e.getValue().isDenied());
            buffer.writeVarInt(e.getValue().values().size());
            for(GoopValue value : e.getValue().values()) {
                buffer.writeString(value.getFluidResourceLocation());
                buffer.writeDouble(value.getAmount());
            }
        }
    }

    public static void handle(final GoopValueSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                GoopMod.mappingHandler.fromPacket(packet.data);
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
