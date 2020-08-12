package com.xeno.goo.fluids;

import com.xeno.goo.entities.GooEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.function.Supplier;

public class VitalGoo extends GooBase
{
    public VitalGoo(Supplier<? extends Item> bucket, FluidAttributes.Builder builder) {
        super(bucket, builder);
    }

    @Override
    public void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos) { }
}
