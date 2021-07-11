package com.xeno.goo.datagen;

import com.google.common.collect.Sets;
import com.xeno.goo.GooMod;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.BlockTagsProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag.INamedTag;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.Arrays;
import java.util.HashSet;

public class GooBlockTags extends BlockTagsProvider {
	public static final INamedTag<Block> HEAT_SOURCES_FOR_CRUCIBLE = BlockTags.makeWrapperTag("heat_sources_for_crucible");

	public GooBlockTags(DataGenerator g, ExistingFileHelper existingFileHelper) {
		super(g, GooMod.MOD_ID, existingFileHelper);
	}

	private static final HashSet<Block> heatSourcesForCrucible = Sets.newLinkedHashSet(
			Arrays.asList(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.MAGMA_BLOCK, Blocks.LAVA)
	);

	@Override
	protected void registerTags() {
		registerCrucibleHeatSources();
	}

	private void registerCrucibleHeatSources() {
		for(Block b : heatSourcesForCrucible) {
			this.getOrCreateBuilder(HEAT_SOURCES_FOR_CRUCIBLE)
					.add(b);
		}
	}

}
