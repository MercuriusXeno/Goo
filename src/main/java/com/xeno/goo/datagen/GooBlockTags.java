package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public class GooBlockTags extends BlockTagsProvider {

	public GooBlockTags(DataGenerator generator, ExistingFileHelper existingFileHelper) {
		super(generator, GooMod.MOD_ID, existingFileHelper);
	}


}
