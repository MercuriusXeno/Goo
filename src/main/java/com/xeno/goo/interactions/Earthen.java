package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;

import java.util.function.Supplier;

public class Earthen
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.EARTHEN_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_stone", Earthen::edifyStone, Earthen::isStone);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_cobblestone", Earthen::edifyCobblestone, Earthen::isCobblestone);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_sand", Earthen::edifySand, Earthen::isSand);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_gravel", Earthen::edifyGravel, Earthen::isGravel);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_coarse_dirt", Earthen::edifyCoarseDirt, Earthen::isCoarseDirt);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_dirt", Earthen::edifyDirt, Earthen::isDirt);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_andesite", Earthen::edifyAndesite, Earthen::isAndesite);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_granite", Earthen::edifyGranite, Earthen::isGranite);
        GooInteractions.registerSplat(fluidSupplier.get(), "edify_diorite", Earthen::edifyDiorite, Earthen::isDiorite);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "earthen_hit", Earthen::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        c.damageVictim(3f);
        c.knockback(1f);
        return true;
    }

    private static boolean edifyAndesite(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.POLISHED_ANDESITE, Blocks.ANDESITE);
    }

    private static boolean edifyGranite(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.POLISHED_GRANITE, Blocks.GRANITE);
    }

    private static boolean edifyDiorite(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.POLISHED_DIORITE, Blocks.DIORITE);
    }

    private static boolean isAndesite(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.ANDESITE);
    }

    private static boolean isGranite(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.GRANITE);
    }

    private static boolean isDiorite(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.DIORITE);
    }

    private static boolean isDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.DIRT);
    }

    private static boolean edifyDirt(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.COARSE_DIRT, Blocks.DIRT);
    }

    private static boolean isCoarseDirt(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.COARSE_DIRT);
    }

    private static boolean edifyCoarseDirt(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.GRAVEL, Blocks.COARSE_DIRT);
    }

    private static boolean isGravel(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.GRAVEL);
    }

    private static boolean edifyGravel(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.COBBLESTONE, Blocks.GRAVEL);
    }

    private static boolean isSand(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.SAND);
    }

    private static boolean edifySand(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.DIRT, Blocks.SAND);
    }

    private static boolean isCobblestone(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.COBBLESTONE);
    }

    private static boolean edifyCobblestone(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.STONE, Blocks.COBBLESTONE);
    }

    private static boolean isStone(SplatContext splatContext) {
        return splatContext.isBlock(Blocks.STONE);
    }

    private static boolean edifyStone(SplatContext splatContext) {
        return exchangeBlock(splatContext, Blocks.SMOOTH_STONE, Blocks.STONE);
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

    private static void doEffects(SplatContext context) {
        playEdifyAudioInContext(context);
        spawnEdifyParticles(context);
    }

    private static void spawnEdifyParticles(SplatContext context) {
        if (context.world() instanceof ServerWorld) {
            ((ServerWorld) context.world()).spawnParticle(new BlockParticleData(ParticleTypes.BLOCK, context.blockState()),
                    context.blockCenterVec().x, context.blockCenterVec().y, context.blockCenterVec().z, 8, 0d, 0d, 0d, 0d);
        }
    }

    private static void playEdifyAudioInContext(SplatContext context) {
        AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.EDIFY_SOUND.get(), SoundCategory.BLOCKS, 1.0f, AudioHelper.PitchFormulas.HalfToOne);
    }
}
