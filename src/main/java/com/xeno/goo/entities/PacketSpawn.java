package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

public class PacketSpawn implements IPacket<IClientPlayNetHandler>
{
    private RegistryKey<World> worldRegistryKey;
    private CompoundNBT tag;
    private ResourceLocation id;
    private BlockPos pos;

    public PacketSpawn(RegistryKey<World> worldRegistryKey, CompoundNBT tag, ResourceLocation id, BlockPos pos)
    {
        readPacketData(worldRegistryKey, id, tag, pos);
    }

    private void readPacketData(RegistryKey<World> worldRegistryKey, ResourceLocation id, CompoundNBT tag, BlockPos pos)
    {
        this.worldRegistryKey = worldRegistryKey;
        this.id = id;
        this.tag = tag;
        this.pos = pos;
    }

    public PacketSpawn(PacketBuffer buf) {
        try {
            readPacketData(buf);
        } catch (IOException e) {
            GooMod.error("Entity packet spawn had problems!");
        }
    }

    public void toBytes(PacketBuffer buf)
    {
        buf.writeResourceLocation(worldRegistryKey.getRegistryName());
        buf.writeResourceLocation(id);
        buf.writeCompoundTag(tag);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                ServerPlayerEntity sender = Objects.requireNonNull(supplier.get().getSender());
                handleSpawn(sender);
            }
        });

        supplier.get().setPacketHandled(true);
    }

    private void handleSpawn(ServerPlayerEntity sender)
    {
        MinecraftServer server = Objects.requireNonNull(sender.world.getServer());
        ServerWorld spawnWorld = server.getWorld(worldRegistryKey);
        if (spawnWorld == null) {
            throw new IllegalStateException("Problem handling entity packet! Unknown world '" + worldRegistryKey.toString() + "'!");
        }
        EntityType<?> entityType = ForgeRegistries.ENTITIES.getValue(id);
        if (entityType == null) {
            throw new IllegalStateException("Problem handling entity packet! Unknown id '" + id.toString() + "'!");
        }
        entityType.spawn(spawnWorld, tag, null, sender, pos, SpawnReason.EVENT, true, true);
    }


    @Override
    public void readPacketData(PacketBuffer buf) throws IOException
    {
        readPacketData(
                RegistryKey.func_240903_a_(Registry.WORLD_KEY, buf.readResourceLocation()),
                buf.readResourceLocation(),
                buf.readCompoundTag(),
                buf.readBlockPos());
    }

    @Override
    public void writePacketData(PacketBuffer buf) throws IOException
    {
        this.toBytes(buf);
    }

    @Override
    public void processPacket(IClientPlayNetHandler handler)
    {
        //
    }
}
