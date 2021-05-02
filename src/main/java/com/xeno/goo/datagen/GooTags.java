package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.EntityTypeTagsProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ITag;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

public class GooTags extends EntityTypeTagsProvider {
	public static class Entities {
		public static ITag.INamedTag<EntityType<?>> WATER_HATING_MOBS = EntityTypeTags.getTagById("water_hating_mobs");
	}

	public GooTags(DataGenerator g, @Nullable ExistingFileHelper existingFileHelper) {
		super(g, GooMod.MOD_ID, existingFileHelper);
	}

	@Override
	protected void registerTags() {
		registerWaterHatingMobs();
	}

	private void registerWaterHatingMobs() {
		this.getOrCreateBuilder(Entities.WATER_HATING_MOBS)
				.add(EntityType.BLAZE)
				.add(EntityType.IRON_GOLEM)
				.add(EntityType.MAGMA_CUBE)
				.add(EntityType.ENDERMAN);
	}
}
