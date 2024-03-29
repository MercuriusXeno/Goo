package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.Registry;
import com.xeno.goo.elements.ElementEnum;
import net.minecraft.data.DataGenerator;
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
		registerBlobItems();
	}

	private void registerBlobItems() {
		registerBlobItem(Registry.EARTH_BLOB_ITEM, ElementEnum.EARTH.elementName());
		registerBlobItem(Registry.AIR_BLOB_ITEM, ElementEnum.AIR.elementName());
		registerBlobItem(Registry.FIRE_BLOB_ITEM, ElementEnum.FIRE.elementName());
		registerBlobItem(Registry.WATER_BLOB_ITEM, ElementEnum.WATER.elementName());
		registerBlobItem(Registry.ICE_BLOB_ITEM, ElementEnum.ICE.elementName());
		registerBlobItem(Registry.LIGHTNING_BLOB_ITEM, ElementEnum.LIGHTNING.elementName());
		registerBlobItem(Registry.DARK_BLOB_ITEM, ElementEnum.DARK.elementName());
		registerBlobItem(Registry.LIGHT_BLOB_ITEM, ElementEnum.LIGHT.elementName());
		registerBlobItem(Registry.CRYSTAL_BLOB_ITEM, ElementEnum.CRYSTAL.elementName());
		registerBlobItem(Registry.METAL_BLOB_ITEM, ElementEnum.METAL.elementName());
		registerBlobItem(Registry.NATURE_BLOB_ITEM, ElementEnum.NATURE.elementName());
		registerBlobItem(Registry.ENDER_BLOB_ITEM, ElementEnum.ENDER.elementName());
		registerBlobItem(Registry.NETHER_BLOB_ITEM, ElementEnum.NETHER.elementName());
	}

	private void registerBlobItem(RegistryObject<Item> blobItem, String elementName) {
		singleTexture(blobItem.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
				"layer0", GooMod.location("item/" + elementName + "_blob"));

	}
}
