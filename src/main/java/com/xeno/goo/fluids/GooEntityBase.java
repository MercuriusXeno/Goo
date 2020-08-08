package com.xeno.goo.fluids;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class GooEntityBase extends Entity
{
    Entity sender;
    Vector3d vector;
    Vector3d location;
    String key;
    double quantity;

    public GooEntityBase(EntityType<?> entityTypeIn, World worldIn, Entity e, Vector3d v, Vector3d l, String k, double q)
    {
        super(entityTypeIn, worldIn);
        sender = e;
        vector = v;
        location = l;
        key = k;
        quantity = q;
    }

    @Override
    protected void registerData()
    {

    }

    @Override
    protected void readAdditional(CompoundNBT compound)
    {
    }

    @Override
    protected void writeAdditional(CompoundNBT tag)
    {
    }

    @Override
    public IPacket<?> createSpawnPacket()
    {
        return null;
    }
}
