package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.fluids.IGooBase;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Objects;

public class GooEntity extends Entity
{
    Vector3d decayingVector;
    Vector3d location;
    List<Vector3d> collisions;
    double decayingSpeed;
    double mB;
    boolean isHeld;
    Entity holder;
    IGooBase goo;

    List<AxisAlignedBB> proportions;

    public GooEntity(World worldIn) {
        super(Registry.GOO_ENTITY.get(), worldIn);
    }

    public GooEntity(World worldIn, CompoundNBT tag) {
        super(Registry.GOO_ENTITY.get(), worldIn);
        read(tag);
    }

    public GooEntity(World worldIn, Entity sender)
    {
        super(Registry.GOO_ENTITY.get(), worldIn);
        decayingVector = holder.getLookVec();
        if (holder instanceof ServerPlayerEntity) {
            location = holder.getEyePosition(0f).add(decayingVector.mul(0.125d, 0.125d, 0.125d));
            isHeld = true;
        }
        decayingSpeed = 0d;
        mB = 0d;
    }

    public GooEntity(World worldIn, Entity sender, String gooType, double enchantedSpeed, double quantity)
    {
        super(Registry.GOO_ENTITY.get(), worldIn);
        decayingVector = holder.getLookVec();
        if (holder instanceof ServerPlayerEntity) {
            location = holder.getEyePosition(0f).add(decayingVector.mul(0.125d, 0.125d, 0.125d));
            isHeld = true;
        }
        decayingSpeed = enchantedSpeed;
        mB = quantity;
        goo = (GooBase)Registry.getFluid(gooType);

    }

    public GooEntity(EntityType<Entity> entityEntityType, World world)
    {
        super(entityEntityType, world);
    }

    @Override
    protected void registerData()
    {

    }

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        decayingVector = new Vector3d(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz"));
        location = new Vector3d(tag.getDouble("lx"), tag.getDouble("ly"), tag.getDouble("lz"));
        decayingSpeed = tag.getDouble("speed");
        mB = tag.getDouble("mb");
        isHeld = tag.getBoolean("held");
        holder = world.getPlayerByUuid(getUniqueID());
        goo = (IGooBase)Registry.getFluid(tag.getString("goo"));
        CompoundNBT p = tag.getCompound("proportions");
        for(int i = 0; i < p.getInt("boxes"); i++) {
            proportions.set(i, new AxisAlignedBB(
            new Vector3d(p.getDouble("lx"), p.getDouble("ly"), p.getDouble("lz")),
            new Vector3d(p.getDouble("hx"), p.getDouble("hy"), p.getDouble("hz"))));
        }
    }

    @Override
    protected void readAdditional(CompoundNBT compound)
    {

    }

    @Override
    protected void writeAdditional(CompoundNBT compound)
    {

    }

    @Override
    public IPacket<?> createSpawnPacket()
    {
        return null;
    }

    @Override
    public CompoundNBT serializeNBT()
    {
        CompoundNBT e = new CompoundNBT();
        e.putDouble("vx", decayingVector.x);
        e.putDouble("vy", decayingVector.y);
        e.putDouble("vz", decayingVector.z);
        e.putDouble("lx", location.x);
        e.putDouble("ly", location.x);
        e.putDouble("lz", location.x);
        e.putDouble("speed", decayingSpeed);
        e.putDouble("mb", mB);
        e.putBoolean("held", isHeld);
        e.putUniqueId("holder", holder.getUniqueID());
        e.putString("goo", Objects.requireNonNull(((Fluid) goo).getRegistryName()).toString());
        CompoundNBT p = new CompoundNBT();
        p.putInt("boxes", proportions.size());
        for(int i = 0; i < proportions.size(); i++) {
            p.putDouble("lx" + i, proportions.get(i).minX);
            p.putDouble("ly" + i, proportions.get(i).minY);
            p.putDouble("lz" + i, proportions.get(i).minZ);
            p.putDouble("hx" + i, proportions.get(i).maxX);
            p.putDouble("hy" + i, proportions.get(i).maxY);
            p.putDouble("hz" + i, proportions.get(i).maxZ);
        }
        e.put("proportions", p);
        return e;
    }
}
