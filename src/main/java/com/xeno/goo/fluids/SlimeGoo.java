package com.xeno.goo.fluids;

import com.xeno.goo.entities.GooEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

public class SlimeGoo extends GooBase
{
    public SlimeGoo(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) { super(bucket, builder,
            createMaterialMappedCoefficient(GRAVITY_DEFAULT, BARELY, SUPER_BUOYANT, SUPER_BUOYANT, HEAVIER_THAN_NOT),
            createMaterialMappedCoefficient(AIR_DRAG_DEFAULT, WATER_DRAG_DEFAULT, LAVA_DRAG_DEFAULT, SLIGHTLY_STICKY, VERY_STICKY),
            createMaterialMappedCoefficient(NO_TRANSFER, VERY_LOSSY, VERY_LOSSY, SLIGHTLY_BOUNCY, VERY_LOSSY)); };

    @Override
    public void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos) { }


    @Override
    public void createEntity(World world, LivingEntity sender, FluidStack goo, Hand isHeld)
    {
        return;
    }

    @Override
    public int decayRate()
    {
        return 1;
    }

    @Override
    public ResourceLocation getEntityTexture()
    {
        return null;
    }
}
