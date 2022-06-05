package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.data.ExistingFileHelper;

public class GooEntityTags extends EntityTypeTagsProvider {
	// "conversion" means changing a mob to a given element: petrification, metallicize and crystallization are all 3 types of conversion
	// the litmus for what doesn't allow a mob to convert is the same for all 3, so they use the same tag: either it's an elemental enemy or a boss.
	// essentially this is the list of mobs that the mod considers elemental, plus bosses.
	public static TagKey<EntityType<?>> CONVERSION_IMMUNE_ENTITY_TYPES = create(GooMod.location("conversion_immune_entity_types"));
	public GooEntityTags(DataGenerator pGenerator, ExistingFileHelper helper) {
		super(pGenerator, GooMod.MOD_ID, helper);
	}

	@Override
	protected void addTags() {
		tag(CONVERSION_IMMUNE_ENTITY_TYPES)
				// "elemental" enemies
				.add(EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.SLIME)
				// bosses
				.add(EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN, EntityType.WITHER);
	}

	private static TagKey<EntityType<?>> create(ResourceLocation rl) {
		return TagKey.create(Registry.ENTITY_TYPE_REGISTRY, rl);
	}
}
