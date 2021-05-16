package com.xeno.goo.interactions;

import com.xeno.goo.datagen.GooTags.Entities;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;

import java.util.function.Supplier;

public class Snow
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.SNOW_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "freeze_water", Snow::freezeWater, Snow::isWaterSource);
        GooInteractions.registerSplat(fluidSupplier.get(), "cool_lava", Snow::coolLava, Snow::isLavaSource);

        // outrageously, this is allowed
        GooInteractions.registerBlob(fluidSupplier.get(), "extinguish_fire", Aquatic::extinguishFire); // aquatic lolol
        GooInteractions.registerBlob(fluidSupplier.get(), "cool_flowing_lava", Aquatic::waterCoolFlowingLava);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "snow_hit", Snow::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        // extinguish the entity if on fire and do not deal damage
        if (Entities.COLD_HATING_MOBS.contains(c.victim().getType())) {
            // knock the enemy back and deal some damage.
            // deal extra damage to cold-haters.
            c.damageVictim(7f);
        } else {
            c.damageVictim(3f);
        }
        if (c.victim().getFireTimer() > 0) {
            c.victim().extinguish();
            GooInteractions.spawnParticles(c.blob());
            for(int i = 0; i < 4; i++) {
                c.world().addParticle(ParticleTypes.SMOKE, c.blob().getPosX(), c.blob().getPosY(), c.blob().getPosZ(), 0d, 0.1d, 0d);
            }
            AudioHelper.entityAudioEvent(c.blob(), SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
        }
        c.knockback(1.0f);
        c.victim().addPotionEffect(new EffectInstance(Effects.SLOWNESS, 60, 2));
        return true;
    }

    private static boolean isWaterSource(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.WATER)
                && context.fluidState().isSource() && context.isBlockAboveAir();
    }

    public static boolean freezeWater(SplatContext context)
    {
        // freeze water
        if (!context.isRemote()) {
            boolean hasChanges = context.setBlockState(Blocks.ICE.getDefaultState());
            if (!hasChanges) {
                return false;
            }
            AudioHelper.headlessAudioEvent(context.world(), context.blockPos(), Registry.FREEZE_SOUND.get(), SoundCategory.BLOCKS, 1.0f, () -> 1.5f);
        }
        return true;
    }

    private static boolean isLavaSource(SplatContext context) {
        return context.fluidState().getFluid().isEquivalentTo(Fluids.LAVA)
                && context.fluidState().isSource();
    }

    public static boolean coolLava(SplatContext context)
    {
        // spawn some sizzly smoke and sounds
        if (!context.isRemote()) {
                boolean hasChanges = context.setBlockState(net.minecraftforge.event.ForgeEventFactory
                        .fireFluidPlaceBlockEvent(context.world(), context.blockPos(), context.blockPos(), Blocks.OBSIDIAN.getDefaultState()));
                if (!hasChanges) {
                    return false;
                }
        }
        context.world().playEvent(1501, context.blockPos(), 0); // sizzly bits
        return true;
    }
}
