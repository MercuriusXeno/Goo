package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BambooLeaves;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

public class Decay
{
    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "decay_shrooms", Decay::decayShrooms);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "strip_bark", Decay::stripBark);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "eat_grass", Decay::eatGrass);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "eat_moss", Decay::eatMoss);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_stone", Decay::deteriorateStone);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_cobblestone", Decay::deteriorateCobblestone);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_sand_stone", Decay::deteriorateSandStone);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_gravel", Decay::deteriorateGravel);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_coarse_dirt", Decay::deteriorateCoarseDirt);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_dirt", Decay::deteriorateDirt);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_nylium", Decay::deteriorateNylium);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_mycelium", Decay::deteriorateMycelium);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "deteriorate_podzol", Decay::deterioratePodzol);
        GooInteractions.registerSplat(Registry.DECAY_GOO.get(), "death_pulse", Decay::deathPulse);

        GooInteractions.registerPassThroughPredicate(Registry.DECAY_GOO.get(), Decay::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_leaves", Decay::destroyLeaves);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_bushes", Decay::destroyBushes);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_vines", Decay::destroyVines);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_grass", Decay::destroyGrass);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_crops", Decay::destroyCrops);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_cactus", Decay::destroyCactus);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_flowers", Decay::destroyFlowers);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_sugar_cane", Decay::destroySugarCane);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_bamboo", Decay::destroyBamboo);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_bamboo_sapling", Decay::destroyBambooSapling);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_sapling", Decay::destroySapling);
        GooInteractions.registerBlob(Registry.DECAY_GOO.get(), "destroy_lilypad", Decay::destroyLilypad);
    }

    private static boolean deathPulse(SplatContext splatContext) {
        if (splatContext.world().getGameTime() % 10 > 0) {
            return false;
        }
        List<LivingEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox().grow(1d), null);
        for(LivingEntity entity : nearbyEntities) {
            if (!entity.isEntityUndead()) {
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(splatContext.splat(), entity), 1f);
                spawnDeteriorateParticles(splatContext);
            } else {
                if (entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(1f);
                    spawnDeteriorateParticles(splatContext);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean deterioratePodzol(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.DIRT, Blocks.PODZOL);
    }

    private static boolean deteriorateMycelium(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.PODZOL, Blocks.MYCELIUM);
    }

    private static boolean deteriorateNylium(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.NETHERRACK, Blocks.CRIMSON_NYLIUM)
                || exchangeBlock(splatContext, Blocks.NETHERRACK, Blocks.WARPED_NYLIUM);
    }

    private static boolean destroyFlowers(BlobContext blobContext) {
        if (blobContext.block() instanceof FlowerBlock || blobContext.block() instanceof TallFlowerBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static void smoke(BlobContext blobContext) {
        ((ServerWorld) blobContext.world()).spawnParticle(ParticleTypes.SMOKE, blobContext.blob().getPositionVec().x,
                blobContext.blob().getPositionVec().y, blobContext.blob().getPositionVec().z, 1,
                0d, 0d, 0d, 0d);
    }

    private static boolean destroyCactus(BlobContext blobContext) {
        if (blobContext.block() instanceof CactusBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyLilypad(BlobContext blobContext) {
        if (blobContext.block() instanceof LilyPadBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyBambooSapling(BlobContext blobContext) {
        if (blobContext.block() instanceof BambooSaplingBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroySapling(BlobContext blobContext) {
        if (blobContext.block() instanceof SaplingBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyBamboo(BlobContext blobContext) {
        if (blobContext.block() instanceof BambooBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroySugarCane(BlobContext blobContext) {
        if (blobContext.block() instanceof SugarCaneBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyCrops(BlobContext blobContext) {
        if (blobContext.block() instanceof CropsBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyGrass(BlobContext blobContext) {
        if (blobContext.block() instanceof TallGrassBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyVines(BlobContext blobContext) {
        if (blobContext.block() instanceof VineBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyLeaves(BlobContext blobContext) {
        if (blobContext.block() instanceof LeavesBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static boolean destroyBushes(BlobContext blobContext) {
        if (blobContext.block() instanceof BushBlock) {
            blobContext.world().removeBlock(blobContext.blockPos(), false);
            if (blobContext.world() instanceof ServerWorld) {
                smoke(blobContext);
            }
            AudioHelper.entityAudioEvent(blobContext.blob(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
            blobContext.fluidHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
    }

    private static Boolean blobPassThroughPredicate(BlockRayTraceResult blockRayTraceResult, GooBlob gooBlob) {
        BlockState state = gooBlob.getEntityWorld().getBlockState(blockRayTraceResult.getPos());
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }
        return
                state.getBlock() instanceof LeavesBlock
                || state.getBlock() instanceof VineBlock
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof CropsBlock
                || state.getBlock() instanceof LilyPadBlock
                || state.getBlock() instanceof GrassBlock
                || state.getBlock() instanceof SugarCaneBlock
                || state.getBlock() instanceof BambooBlock
                || state.getBlock() instanceof BambooSaplingBlock
                || state.getBlock() instanceof SaplingBlock
                || state.getBlock() instanceof CactusBlock
                || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof TallFlowerBlock;
    }

    private static boolean exchangeBlock(SplatContext context, Block target, Block... sources) {
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
    private static boolean exchangeLog(SplatContext context, Block source, Block target) {
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

    private static void doEffects(SplatContext context) {
        playDeteriorateAudioInContext(context);
        spawnDeteriorateParticles(context);
    }

    private static void spawnDeteriorateParticles(SplatContext context) {
        if (context.world() instanceof ServerWorld) {
            ((ServerWorld) context.world()).spawnParticle(ParticleTypes.SMOKE, context.splat().getPositionVec().x,
                    context.splat().getPositionVec().y, context.splat().getPositionVec().z, 1,
                    0d, 0d, 0d, 0d);
        }
    }

    private static void playDeteriorateAudioInContext(SplatContext context) {
        AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.DETERIORATE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
    }

    private static boolean deteriorateDirt(SplatContext context) {
        return exchangeBlock(context, Blocks.SAND, Blocks.DIRT);
    }

    private static boolean deteriorateCoarseDirt(SplatContext context) {
        return exchangeBlock(context, Blocks.DIRT, Blocks.COARSE_DIRT);
    }

    private static boolean deteriorateGravel(SplatContext context) {
        return exchangeBlock(context, Blocks.COARSE_DIRT, Blocks.GRAVEL);
    }

    private static boolean deteriorateSandStone(SplatContext context) {
        return exchangeBlock(context, Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE);
    }

    private static boolean deteriorateCobblestone(SplatContext context) {
        return exchangeBlock(context, Blocks.GRAVEL, Blocks.COBBLESTONE);
    }

    private static boolean deteriorateStone(SplatContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean eatMoss(SplatContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
                || exchangeBlock(context, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
    }

    private static boolean eatGrass(SplatContext context) {
        return exchangeBlock(context, Blocks.DIRT, Blocks.GRASS_BLOCK);
    }

    private static boolean stripBark(SplatContext context) {
        for(Tuple<Block, Block> blockPair : Floral.logBarkPairs) {
            if (exchangeLog(context, blockPair.getA(), blockPair.getB())) {
                return true;
            }
        }
        for(Tuple<Block, Block> blockPair : Fungal.stemPairs) {
            if (exchangeLog(context, blockPair.getA(), blockPair.getB())) {
                return true;
            }
        }
        return false;
    }

    private static boolean decayShrooms(SplatContext context) {
        return exchangeBlock(context, Blocks.AIR, Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK);
    }
}
