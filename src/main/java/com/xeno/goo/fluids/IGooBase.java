package com.xeno.goo.fluids;

import com.xeno.goo.entities.GooEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

public interface IGooBase
{
    void doEffect(ServerWorld world, ServerPlayerEntity player, GooEntity goo, Entity entityHit, BlockPos pos);
}
