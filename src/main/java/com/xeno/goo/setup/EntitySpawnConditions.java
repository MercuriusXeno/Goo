package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooSnail;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.Category;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraftforge.event.world.BiomeLoadingEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EntitySpawnConditions {
	public static <T extends MobEntity> boolean snailSpawnConditions(EntityType<T> tEntityType, IServerWorld worldIn, SpawnReason spawnReason, BlockPos blockPos, Random random) {
		return snailSpawnConditions(worldIn, spawnReason, blockPos, random);
	}
	private static List<SpawnReason> snailNaturalSpawnReasons =
			Arrays.asList(SpawnReason.CHUNK_GENERATION, SpawnReason.NATURAL, SpawnReason.JOCKEY);
	public static boolean snailSpawnConditions(IWorld worldIn, SpawnReason spawnReason, BlockPos pos, Random rand) {
		if (snailNaturalSpawnReasons.contains(spawnReason)) {
			if (rand.nextFloat() >= 0.07f ||
					pos.getY() > 35 ||
					worldIn.canBlockSeeSky(pos.down()) ||
					!worldIn.getBlockState(pos).getBlock().equals(Blocks.CAVE_AIR) ||
					!worldIn.getBlockState(pos.down()).isSolid() ||
					!GooSnail.nearbyLiquidConditionsMet(worldIn, pos)||
					GooSnail.nearbySnail(worldIn, pos)) {
				return false;
			}
		}
		return true;
	}


	// borrowed from Ars Nouveau
	// normal biomes we're okay with snails spawning in caves in
	private static List<Category> VALID_SNAIL_SPAWN_BIOMES = Arrays.asList(Biome.Category.FOREST, Biome.Category.EXTREME_HILLS, Biome.Category.JUNGLE,
			Biome.Category.PLAINS, Biome.Category.SWAMP, Biome.Category.SAVANNA);
	public static void injectSnailSpawnConditions(BiomeLoadingEvent e) {
		if (VALID_SNAIL_SPAWN_BIOMES.contains(e.getCategory())) {
			if (GooMod.config.snailSpawnWeight() > 0) {
				MobSpawnInfo.Spawners spawnInfo = new MobSpawnInfo.Spawners(Registry.GOO_SNAIL, GooMod.config.snailSpawnWeight(), 0, 1);
				e.getSpawns().withSpawner(EntityClassification.getClassificationByName("goo:goo_snail"), spawnInfo);
			}
		}
	}
}
