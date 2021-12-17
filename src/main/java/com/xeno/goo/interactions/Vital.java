package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.function.Supplier;

public class Vital
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.VITAL_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerBlobHit(fluidSupplier.get(), "vital_pulse", Vital::vitalPulse, Vital::isLivingInRangeAndHalfSecondPulse);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "vital_hit", Vital::hitEntity);
    }

    static boolean hitEntity(BlobHitContext c) {
        if (c.victim().isEntityUndead()) {
            // damage for undead
            c.damageVictim(7f);
            c.knockback(1f);
            for(int i = 0; i < 4; i++) {
                c.world().addParticle(ParticleTypes.SMOKE, c.blob().getPosX(), c.blob().getPosY(), c.blob().getPosZ(), 0d, 0.1d, 0d);
            }
            AudioHelper.entityAudioEvent(c.blob(), Registry.GOO_SIZZLE_SOUND.get(), SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
        } else {
            c.healVictim(2f);
            for(int i = 0; i < 4; i++) {
                c.world().addParticle(ParticleTypes.HAPPY_VILLAGER, c.blob().getPosX(), c.blob().getPosY(), c.blob().getPosZ(), 0d, 0.1d, 0d);
            }
            c.victim().addPotionEffect(new EffectInstance(Effects.REGENERATION, 40, 2));
        }
        return true;
    }

    private static boolean isLivingInRangeAndHalfSecondPulse(BlobHitContext splatContext) {
        return (splatContext.world().getGameTime() % 10 == 0)
                && splatContext.world()
                .getEntitiesWithinAABB(LivingEntity.class, splatContext.blob().getBoundingBox().grow(1d),
                        (e) -> e.isEntityUndead() || e.getHealth() < e.getMaxHealth()).size() > 0;
    }

    private static boolean vitalPulse(BlobHitContext splatContext) {
        List<LivingEntity> nearbyEntities = splatContext.world()
                .getEntitiesWithinAABB(LivingEntity.class, splatContext.blob().getBoundingBox().grow(1d),
                        (e) -> e.isEntityUndead() || e.getHealth() < e.getMaxHealth());
        for(LivingEntity entity : nearbyEntities) {
            if (entity.isEntityUndead()) {
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(splatContext.blob(), entity), 1f);
            } else {
                entity.heal(1f);
                doEffects(entity);
            }
        }
        return true;
    }

    private static void doEffects(LivingEntity entity) {
        if (entity.world instanceof ServerWorld) {
            ((ServerWorld)entity.world).spawnParticle(ParticleTypes.HAPPY_VILLAGER, entity.getPosXRandom(1.0D), entity.getPosYRandom() + 0.5D,
                    entity.getPosZRandom(1.0D), 4, 0d, 0d, 0d, 0d);
        }
    }
}
