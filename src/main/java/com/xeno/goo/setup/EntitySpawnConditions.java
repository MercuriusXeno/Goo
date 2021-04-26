package com.xeno.goo.setup;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IServerWorld;

import java.util.Random;

public class EntitySpawnConditions {

	public static <T extends MobEntity> boolean snailSpawnConditions(EntityType<T> entity, IServerWorld worldIn, SpawnReason spawnReason, BlockPos pos, Random rand) {
		BlockState belowState = worldIn.getBlockState(pos.down());
		BlockState airState = worldIn.getBlockState(pos);
		return belowState.matchesBlock(Blocks.STONE) && airState.getBlock().equals(Blocks.CAVE_AIR) && pos.getY() < 36;
	}
}
