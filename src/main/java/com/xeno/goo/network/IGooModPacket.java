package com.xeno.goo.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public interface IGooModPacket
{
    void toBytes(PacketBuffer buf);

    void handle(Supplier<NetworkEvent.Context> supplier);

    void read(PacketBuffer buf);
}
