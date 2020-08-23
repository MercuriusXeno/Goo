package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nonnull;

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
    protected void interactWithWater(BlockPos pos)
    {
        // INERT
    }

    @Override
    protected void interactWithSolid(BlockPos pos)
    {
        // INERT
        if (this.owner() instanceof PlayerEntity) {
            //if (FluidUtil.tryPlaceFluid((PlayerEntity)this.owner(), this.world, Hand.MAIN_HAND, this.getPosition(), this, this.goo)) {
            if (this.tryPlaceFluid(pos)) {
                this.setDead();
                this.remove();
            }
        }
    }

    @Override
    protected void interactWithGoo(BlockPos pos)
    {

    }

    @Override
    protected void interactWithLava(BlockPos pos)
    {
        // INERT
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
