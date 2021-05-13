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

import java.util.HashSet;

public class GooTags extends EntityTypeTagsProvider {
	public static class Entities {

		public static ITag.INamedTag<EntityType<?>> WATER_HATING_MOBS = EntityTypeTags.getTagById("water_hating_mobs");
		public static ITag.INamedTag<EntityType<?>> PRIMORDIAL_INSTANT_DEATH_IMMUNE_MOBS = EntityTypeTags.getTagById("primordial_instant_death_immune_mobs");
		public static ITag.INamedTag<EntityType<?>> PRIMORDIAL_SPAWN_EGGS_ALLOWED = EntityTypeTags.getTagById("primordial_spawn_eggs_allowed");
	}

	public GooTags(DataGenerator g, ExistingFileHelper existingFileHelper) {
		super(g, GooMod.MOD_ID, existingFileHelper);
	}
	private static final HashSet<EntityType<?>> primordialImmuneMobs = Sets.newHashSet(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN);
	@Override
	protected void registerTags() {
		registerWaterHatingMobs();

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
		Iterable<SpawnEggItem> eggs = SpawnEggItem.getEggs();
		for(SpawnEggItem egg : eggs) {
			EntityType<?> eType = egg.getType(null);
			// don't allow spawn egg production of mobs that are immune to primordial, that would be weird.
			if (primordialImmuneMobs.contains(eType)) {
				continue;
			}
			this.getOrCreateBuilder(Entities.PRIMORDIAL_SPAWN_EGGS_ALLOWED)
					.add(egg.getType(null));
		}
	}

	private void registerWaterHatingMobs() {
		this.getOrCreateBuilder(Entities.WATER_HATING_MOBS)
				.add(EntityType.BLAZE)
				.add(EntityType.IRON_GOLEM)
				.add(EntityType.MAGMA_CUBE)
				.add(EntityType.ENDERMAN);
	}
}
