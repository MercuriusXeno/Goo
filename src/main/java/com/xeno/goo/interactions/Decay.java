package com.xeno.goo.interactions;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Tuple;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;

public class Decay
{
    public static void registerInteractions()
    {
        GooInteractions.register(Registry.DECAY_GOO.get(), "decay_shrooms", 0, Decay::decayShrooms);
        GooInteractions.register(Registry.DECAY_GOO.get(), "strip_bark", 1, Decay::stripBark);
        GooInteractions.register(Registry.DECAY_GOO.get(), "eat_grass", 2, Decay::eatGrass);
        GooInteractions.register(Registry.DECAY_GOO.get(), "eat_moss", 3, Decay::eatMoss);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_stone", 4, Decay::deteriorateStone);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_cobblestone", 5, Decay::deteriorateStone);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_sand_stone", 6, Decay::deteriorateSandStone);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_gravel", 7, Decay::deteriorateGravel);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_coarse_dirt", 8, Decay::deteriorateCoarseDirt);
        GooInteractions.register(Registry.DECAY_GOO.get(), "deteriorate_dirt", 9, Decay::deteriorateDirt);
    }

    private static boolean exchangeBlock(InteractionContext context, Block target, Block... sources) {
        // do conversion
        for(Block source : sources) {
            if (context.block().equals(source)) {
                if (!context.isRemote()) {
                    context.setBlockState(target.getDefaultState());
                }
                // spawn particles and stuff
                doEffects(context);
                return true;
            }
        }
        return false;
    }

    // similar to exchange block but respect the state of the original log
    private static boolean exchangeLog(InteractionContext context, Block source, Block target) {
        if (context.block().equals(source)) {
            Direction.Axis preservedAxis = context.blockState().get(BlockStateProperties.AXIS);
            if (!context.isRemote()) {
                context.setBlockState(target.getDefaultState().with(BlockStateProperties.AXIS, preservedAxis));
            }
            // spawn particles and stuff
            doEffects(context);
            return true;
        }
        return false;
    }

    private static void doEffects(InteractionContext context) {
        playDeteriorateAudioInContext(context);
        spawnDeteriorateParticles(context);
    }

    private static void spawnDeteriorateParticles(InteractionContext context) {
        if (context.world() instanceof ServerWorld) {
            ((ServerWorld) context.world()).spawnParticle(ParticleTypes.SMOKE, context.blockCenterVec().x, context.blockCenterVec().y, context.blockCenterVec().z, 1, 0d, 0d, 0d, 0d);
        }
    }

    private static void playDeteriorateAudioInContext(InteractionContext context) {
        AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
    }

    private static boolean deteriorateDirt(InteractionContext context) {
        return exchangeBlock(context, Blocks.SAND, Blocks.DIRT);
    }

    private static boolean deteriorateCoarseDirt(InteractionContext context) {
        return exchangeBlock(context, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean deteriorateGravel(InteractionContext context) {
        return exchangeBlock(context, Blocks.COARSE_DIRT, Blocks.GRAVEL);
    }

    private static boolean deteriorateSandStone(InteractionContext context) {
        return exchangeBlock(context, Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE);
    }

    private static boolean deteriorateStone(InteractionContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean eatMoss(InteractionContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
                || exchangeBlock(context, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
    }

    private static boolean eatGrass(InteractionContext context) {
        return exchangeBlock(context, Blocks.DIRT, Blocks.GRASS_BLOCK);
    }

    private static List<Tuple<Block, Block>> logBarkPairs = new ArrayList<>();
    static {
        logBarkPairs.add(new Tuple<>(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG));
        logBarkPairs.add(new Tuple<>(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG));
        logBarkPairs.add(new Tuple<>(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG));
        logBarkPairs.add(new Tuple<>(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG));
        logBarkPairs.add(new Tuple<>(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG));
        logBarkPairs.add(new Tuple<>(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG));
    }
    private static boolean stripBark(InteractionContext context) {
        for(Tuple<Block, Block> blockPair : logBarkPairs) {
            if (exchangeLog(context, blockPair.getA(), blockPair.getB())) {
                return true;
            }
        }
        return false;
    }

    private static boolean decayShrooms(InteractionContext context) {
        return exchangeBlock(context, Blocks.AIR, Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK);
    }
}
