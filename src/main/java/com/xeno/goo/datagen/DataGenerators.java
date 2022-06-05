package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forge.event.lifecycle.GatherDataEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {
	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		DataGenerator generator = event.getGenerator();
		if (event.includeServer()) {
			generator.addProvider(new AequivaleoInformationProvider(generator));
			// generator.addProvider(new GooEquivalencyDb(generator));
			// generator.addProvider(new GooRecipes(generator));
			// generator.addProvider(new GooLootTables(generator));
			generator.addProvider(new GooBlockTags(generator, event.getExistingFileHelper()));
			generator.addProvider(new GooEntityTags(generator, event.getExistingFileHelper()));
			// generator.addProvider(new GooItemTags(generator, blockTags, event.getExistingFileHelper()));
		}
		if (event.includeClient()) {
			// generator.addProvider(new GooLangProvider(generator, "en_us"));
			// generator.addProvider(new GooBlockStates(generator, event.getExistingFileHelper()));
			generator.addProvider(new GooItemModels(generator, event.getExistingFileHelper()));

		}
	}
}
