package com.xeno.goo.features;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.loot.LootTables;
import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SnailSpawnFeature extends Feature<NoFeatureConfig> {
	public SnailSpawnFeature(Codec<NoFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(ISeedReader reader, ChunkGenerator generator, Random rand, BlockPos pos, NoFeatureConfig config) {
		// TODO figure this out as an alternative to raw biome spawn injection. This requires retrogen for existing worlds, and isn't done.
//		ChunkPos chunkpos = new ChunkPos(pos);
//		List<Integer> xList = IntStream.rangeClosed(chunkpos.getXStart(), chunkpos.getXEnd()).boxed().collect(Collectors.toList());
//		Collections.shuffle(xList, rand);
//		List<Integer> zList = IntStream.rangeClosed(chunkpos.getZStart(), chunkpos.getZEnd()).boxed().collect(Collectors.toList());
//		Collections.shuffle(zList, rand);
//		BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();
//
//		for(Integer x : xList) {
//			for(Integer z : zList) {
//				blockpos$mutable.setPos(x, 0, z);
//				BlockPos blockpos = reader.getHeight(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockpos$mutable);
//				if (reader.isAirBlock(blockpos) && reader.getBlockState(blockpos)) {
//					reader.setBlockState(blockpos, Blocks.CHEST.getDefaultState(), 2);
//					LockableLootTileEntity.setLootTable(reader, rand, blockpos, LootTables.CHESTS_SPAWN_BONUS_CHEST);
//					BlockState blockstate = Blocks.TORCH.getDefaultState();
//
//					for(Direction direction : Direction.Plane.HORIZONTAL) {
//						BlockPos blockpos1 = blockpos.offset(direction);
//						if (blockstate.isValidPosition(reader, blockpos1)) {
//							reader.setBlockState(blockpos1, blockstate, 2);
//						}
//					}
//
//					return true;
//				}
//			}
//		}

		return false;
	}
}