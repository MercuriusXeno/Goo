package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.fluids.throwing.Breakpoint;
import com.xeno.goo.fluids.throwing.ThrownEffect;
import com.xeno.goo.library.GooEntry;
import com.xeno.goo.library.GooValue;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;

public class GooBlob extends Entity
{
    GooEntry goo;
    Breakpoint breakpoint;

    Entity sender;
    Vector3d vector;
    Vector3d location;
    String key;
    double quantity;

    public GooBlob(EntityType<?> entityTypeIn, World worldIn, Entity e, Vector3d v, Vector3d l, String k, double q, GooValue... goo)
    {
        super(entityTypeIn, worldIn);

        this.goo = new GooEntry(goo);
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
