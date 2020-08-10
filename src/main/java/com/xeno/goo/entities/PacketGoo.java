package com.xeno.goo.entities;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketGoo
{
    private final RegistryKey<World> worldRegistryKey;
    private final GooEntity e;
    private final UUID sender;
    private final UUID id;
    private static final Vector3d MIN_VEC = new Vector3d(-256d, -256d, -256d);
    private static final Vector3d MAX_VEC = new Vector3d(256d, 256d, 256d);

    public PacketGoo(RegistryKey<World> worldRegistryKey, GooEntity gooEntity, UUID sender)
    {
        this.worldRegistryKey = worldRegistryKey;
        this.e = gooEntity;
        this.sender = sender;
        this.id = gooEntity.getUniqueID();
        this.e.setMotion(e.decayingVector.mul(new Vector3d(e.decayingSpeed, e.decayingSpeed, e.decayingSpeed)));
    }

    public PacketGoo(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.func_240903_a_(Registry.WORLD_KEY, buf.readResourceLocation());
        CompoundNBT tag = buf.readCompoundTag();
        this.e = new GooEntity().deserializeNBT(tag);
        this.sender = buf.readUniqueId();
        this.id = buf.readUniqueId();
    }

    private GooEntity unpackEntity(PacketBuffer buf)
    {


    }

    public void toBytes(PacketBuffer buf)
    {
        buf.writeResourceLocation(worldRegistryKey.getRegistryName());
        buf.writeCompoundTag(e.serializeNBT());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                if (Minecraft.getInstance().world == null) {
                    return;
                }
                if (Minecraft.getInstance().world.func_234923_W_() != worldRegistryKey) {
                    return;
                }
                World world = Minecraft.getInstance().world;
                List<Entity> nearbyEntities = world.getEntitiesWithinAABB(GooEntity.class, new AxisAlignedBB(MIN_VEC, MAX_VEC));
                if (nearbyEntities.size() > 0) {

                }
            }
        });

        supplier.get().setPacketHandled(true);

    }
}
