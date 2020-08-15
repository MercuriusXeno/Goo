package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public class CrystalEntity extends GooEntity
{
    public CrystalEntity(World worldIn)
    {
        super(Registry.CRYSTAL.get(), worldIn);
    }

    public CrystalEntity(EntityType<CrystalEntity> crystalEntityEntityType, World world, LivingEntity sender, FluidStack stack)
    {
        super(crystalEntityEntityType, world, sender, stack);
    }

    public CrystalEntity(EntityType<CrystalEntity> type, World world)
    {
        super(type, world);
    }

    @Override
    public ResourceLocation texture()
    {
        return Resources.GooTextures.Entities.CRYSTAL;
    }

    @Override
    public GooBase gooBase()
    {
        return (GooBase)Registry.CRYSTAL_GOO.get().getFluid();
    }

    @Override
    protected Vector3d doEverythingElseCollision(Entity entityHit)
    {
        return Vector3d.ZERO;
    }

    @Override
    protected Vector3d doPlayerCollision(ServerPlayerEntity entityHit)
    {
        return Vector3d.ZERO;
    }

    @Override
    protected Vector3d doGooCollision(Entity entityHit, GooBase collidingGoo)
    {
        return Vector3d.ZERO;
    }
}
