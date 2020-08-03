package com.xeno.goop.network;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.GoopValue;
import net.minecraft.item.Item;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class GoopValueSyncPacket {
    private final GoopValueSyncPacketData[] data;
    public GoopValueSyncPacket(GoopValueSyncPacketData[] data) {
        this.data = data;
    }

    public static void write(GoopValueSyncPacket packet, PacketBuffer buffer) {
        buffer.writeVarInt(packet.data.length);
        for(GoopValueSyncPacketData data : packet.data) {
            buffer.writeRegistryId(data.getItem());
            buffer.writeVarInt(data.getValues().length);
            for(GoopValue value : data.getValues()) {
                buffer.writeString(value.getFluidResourceLocation());
                buffer.writeDouble(value.getAmount());
            }
        }
    }

    public static GoopValueSyncPacket read(PacketBuffer buffer) {
        int length = buffer.readVarInt();
        GoopValueSyncPacketData[] data = new GoopValueSyncPacketData[length];
        for(int syncIndex = 0; syncIndex < length; syncIndex++) {
            Item evaluatedItem = buffer.readRegistryId();
            int goopCount = buffer.readVarInt();
            GoopValue[] goopValues = new GoopValue[goopCount];
            for(int goopIndex = 0; goopIndex < goopCount; goopIndex++) {
                goopValues[goopIndex] = new GoopValue(buffer.readRegistryId(), buffer.readDouble());
            }
            data[syncIndex] = new GoopValueSyncPacketData(evaluatedItem, goopValues);
        }
        return new GoopValueSyncPacket(data);
    }

    public static class Handler {

        public static void handle(final GoopValueSyncPacket packet, Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                GoopMod.mappingHandler.fromPacket(packet.data);
            });
            context.get().setPacketHandled(true);
        }
    }
}
