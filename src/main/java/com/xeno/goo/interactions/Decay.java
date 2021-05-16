package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import net.minecraft.potion.Effects;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.function.Supplier;

public class Decay
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.DECAY_GOO;
    private static final int HALF_SECOND_INTERVAL = 10;

    public static void registerInteractions()
    {
        // splat interactions
        GooInteractions.registerSplat(fluidSupplier.get(), "decay_shrooms", Decay::decayShrooms, Decay::isShroom);
        GooInteractions.registerSplat(fluidSupplier.get(), "strip_log", Decay::stripLog, Decay::isLogWithBark);
        GooInteractions.registerSplat(fluidSupplier.get(), "strip_stem", Decay::stripStem, Decay::isStemWithBark);
        GooInteractions.registerSplat(fluidSupplier.get(), "eat_grass", Decay::eatGrass, Decay::isGrassBlock);
        GooInteractions.registerSplat(fluidSupplier.get(), "eat_moss", Decay::eatMoss, Decay::hasMoss);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_stone", Decay::deteriorateStone, Decay::isStone);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_cobblestone", Decay::deteriorateCobblestone, Decay::isCobblestone);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_sand_stone", Decay::deteriorateSandStone, Decay::isSandstone);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_gravel", Decay::deteriorateGravel, Decay::isGravel);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_coarse_dirt", Decay::deteriorateCoarseDirt, Decay::isCoarseDirt);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_dirt", Decay::deteriorateDirt, Decay::isDirt);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_nylium", Decay::deteriorateNylium, Decay::isNylium);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_mycelium", Decay::deteriorateMycelium, Decay::isMycelium);
        GooInteractions.registerSplat(fluidSupplier.get(), "deteriorate_podzol", Decay::deterioratePodzol, Decay::isPodzol);
        GooInteractions.registerSplat(fluidSupplier.get(), "death_pulse", Decay::deathPulse, Decay::isLivingNearbyAndHalfSecondInterval);

        GooInteractions.registerPassThroughPredicate(fluidSupplier.get(), Decay::blobPassThroughPredicate);

        // blob interactions
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_leaves", Decay::destroyLeaves);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_bushes", Decay::destroyBushes);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_vines", Decay::destroyVines);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_grass", Decay::destroyGrass);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_crops", Decay::destroyCrops);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_cactus", Decay::destroyCactus);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_flowers", Decay::destroyFlowers);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_sugar_cane", Decay::destroySugarCane);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_bamboo", Decay::destroyBamboo);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_bamboo_sapling", Decay::destroyBambooSapling);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_sapling", Decay::destroySapling);
        GooInteractions.registerBlob(fluidSupplier.get(), "destroy_lilypad", Decay::destroyLilypad);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "decay_hit", Decay::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        if (c.victim().isEntityUndead()) {
            c.healVictim(2f);
            GooInteractions.spawnParticles(c.blob());
            c.spawnScatteredFloatingParticles(ParticleTypes.HAPPY_VILLAGER, 4);
        } else {
            c.damageVictim(2f);
            c.knockback(1f);
            c.applyEffect(Effects.WITHER, 120, 2);
        }
        return true;
    }

    private static boolean isMycelium(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.MYCELIUM);
    }

    private static boolean isDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.DIRT);
    }

    private static boolean isCoarseDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.COARSE_DIRT);
    }

    private static boolean isGravel(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.GRAVEL);
    }

    private static boolean isSandstone(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE);
    }

    private static boolean isCobblestone(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.COBBLESTONE);
    }

    private static boolean isLivingNearbyAndHalfSecondInterval(SplatContext splatContext) {
        if (splatContext.world().getGameTime() % HALF_SECOND_INTERVAL > 0) {
            return false;
        }
        return splatContext.world().getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox().grow(1d), null).size() > 0;
    }

    private static boolean deathPulse(SplatContext splatContext) {
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

    private static boolean isPodzol(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.PODZOL);
    }

    private static boolean deterioratePodzol(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.DIRT, Blocks.PODZOL);
    }

    private static boolean deteriorateMycelium(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.PODZOL, Blocks.MYCELIUM);
    }

    private static boolean isNylium(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM);
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
                || state.getBlock() instanceof TallGrassBlock
                || state.getBlock() instanceof CropsBlock
                || state.getBlock() instanceof LilyPadBlock
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

    private static boolean isStone(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean deteriorateStone(SplatContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS, Blocks.STONE_BRICKS);
    }

    private static boolean hasMoss(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS);
    }

    private static boolean eatMoss(SplatContext context) {
        return exchangeBlock(context, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE)
                || exchangeBlock(context, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
    }

    private static boolean isGrassBlock(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.GRASS_BLOCK);
    }

    private static boolean eatGrass(SplatContext context) {
        return exchangeBlock(context, Blocks.DIRT, Blocks.GRASS_BLOCK);
    }

    private static boolean isLogWithBark(SplatContext splatContext) {
        return Floral.logBarkPairs.containsKey(splatContext.block());
    }


    private static boolean isStemWithBark(SplatContext splatContext) {
        return Fungal.stemPairs.containsKey(splatContext.block());
    }

    private static boolean stripLog(SplatContext context) {
        return exchangeLog(context, context.block(), Floral.logBarkPairs.get(context.block()));
    }

    private static boolean stripStem(SplatContext context) {
        return exchangeLog(context, context.block(), Fungal.stemPairs.get(context.block()));
    }

    private static boolean isShroom(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK);
    }

    private static boolean decayShrooms(SplatContext context) {
        return exchangeBlock(context, Blocks.AIR, Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK);
    }
}
