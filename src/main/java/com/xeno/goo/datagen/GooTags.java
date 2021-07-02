package com.xeno.goo.datagen;

import com.google.common.collect.Sets;
import com.xeno.goo.GooMod;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.EntityTypeTagsProvider;
import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ITag;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GooTags extends EntityTypeTagsProvider {
	public static class Entities {

		public static ITag.INamedTag<EntityType<?>> WATER_HATING_MOBS = EntityTypeTags.getTagById("water_hating_mobs");
		public static ITag.INamedTag<EntityType<?>> COLD_HATING_MOBS = EntityTypeTags.getTagById("cold_hating_mobs");
		public static ITag.INamedTag<EntityType<?>> PRIMORDIAL_INSTANT_DEATH_IMMUNE_MOBS = EntityTypeTags.getTagById("primordial_instant_death_immune_mobs");
		public static ITag.INamedTag<EntityType<?>> PRIMORDIAL_SPAWN_EGGS_ALLOWED = EntityTypeTags.getTagById("primordial_spawn_eggs_allowed");
	}

	public GooTags(DataGenerator g, ExistingFileHelper existingFileHelper) {
		super(g, GooMod.MOD_ID, existingFileHelper);
	}
	private static final HashSet<EntityType<?>> primordialImmuneMobs = Sets.newLinkedHashSet(Arrays.asList(EntityType.ELDER_GUARDIAN, EntityType.ENDER_DRAGON, EntityType.WITHER));
	@Override
	protected void registerTags() {
		registerWaterHatingMobs();
		registerColdHatingMobs();

		registerPrimordialInstantDeathEntities();

		registerPrimordialSpawnEggEntities();
	}

	private void registerPrimordialInstantDeathEntities() {
		for(EntityType<?> immuneMob : primordialImmuneMobs) {
			this.getOrCreateBuilder(Entities.PRIMORDIAL_INSTANT_DEATH_IMMUNE_MOBS)
					.add(immuneMob);
		}
	}

	private void registerPrimordialSpawnEggEntities() {
		Stream<SpawnEggItem> eggs = StreamSupport.stream(SpawnEggItem.getEggs().spliterator(), false).sorted(this::sortSpawnEggs);
		for(SpawnEggItem egg : eggs.collect(Collectors.toList())) {
			EntityType<?> eType = egg.getType(null);
			// don't allow spawn egg production of mobs that are immune to primordial, that would be weird.
			if (primordialImmuneMobs.contains(eType)) {
				continue;
			}
			this.getOrCreateBuilder(Entities.PRIMORDIAL_SPAWN_EGGS_ALLOWED)
					.add(egg.getType(null));
		}
	}

	private int sortSpawnEggs(SpawnEggItem spawnEggItem, SpawnEggItem spawnEggItem1) {
		return spawnEggItem.getType(null).getRegistryName().compareNamespaced(spawnEggItem1.getType(null).getRegistryName());
	}

	private void registerWaterHatingMobs() {
		this.getOrCreateBuilder(Entities.WATER_HATING_MOBS)
				.add(EntityType.BLAZE)
				.add(EntityType.ENDERMAN)
				.add(EntityType.IRON_GOLEM)
				.add(EntityType.MAGMA_CUBE);
	}

	private void registerColdHatingMobs() {
		this.getOrCreateBuilder(Entities.COLD_HATING_MOBS)
				.add(EntityType.BEE)
				.add(EntityType.BLAZE)
				.add(EntityType.CAVE_SPIDER)
				.add(EntityType.GHAST)
				.add(EntityType.HOGLIN)
				.add(EntityType.MAGMA_CUBE)
				.add(EntityType.PIGLIN)
				.add(EntityType.SPIDER)
				.add(EntityType.STRIDER)
				.add(EntityType.ZOMBIFIED_PIGLIN);
	}
}
