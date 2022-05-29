package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class GooItemModels extends ItemModelProvider {

	public GooItemModels(DataGenerator generator, ExistingFileHelper existingFileHelper) {
		super(generator, GooMod.MOD_ID, existingFileHelper);
	}

	@Override
	protected void registerModels() {
		registerTestItems();
	}

	private void registerTestItems() {
		registerTestItem(Registry.EARTH_TEST_ITEM);
		registerTestItem(Registry.AIR_TEST_ITEM);
		registerTestItem(Registry.FIRE_TEST_ITEM);
		registerTestItem(Registry.WATER_TEST_ITEM);
		registerTestItem(Registry.ICE_TEST_ITEM);
		registerTestItem(Registry.LIGHTNING_TEST_ITEM);
		registerTestItem(Registry.DARK_TEST_ITEM);
		registerTestItem(Registry.LIGHT_TEST_ITEM);
		registerTestItem(Registry.CRYSTAL_TEST_ITEM);
		registerTestItem(Registry.METAL_TEST_ITEM);
		registerTestItem(Registry.NATURE_TEST_ITEM);
		registerTestItem(Registry.ENDER_TEST_ITEM);
		registerTestItem(Registry.NETHER_TEST_ITEM);
	}

	private void registerTestItem(RegistryObject<Item> testItem) {
		singleTexture(testItem.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
				"layer0", new ResourceLocation("item/redstone"));

	}
}
