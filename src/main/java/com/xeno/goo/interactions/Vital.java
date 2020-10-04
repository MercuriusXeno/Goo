package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

public class Vital
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.VITAL_GOO.get(), "vital_pulse", Vital::vitalPulse);
    }

    private static boolean vitalPulse(SplatContext splatContext) {
        if (splatContext.world().getGameTime() % 10 > 0) {
            return false;
        }
        List<LivingEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox().grow(1d), null);
        for(LivingEntity entity : nearbyEntities) {
            if (entity.isEntityUndead()) {
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(splatContext.splat(), entity), 1f);
            } else {
                if (entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(1f);
                    doEffects(entity);
                    return true;
                }
            }
        }
        return false;
    }

    private static void doEffects(LivingEntity entity) {
        if (entity.world instanceof ServerWorld) {
            ((ServerWorld)entity.world).spawnParticle(ParticleTypes.HAPPY_VILLAGER, entity.getPosXRandom(1.0D), entity.getPosYRandom() + 0.5D,
                    entity.getPosZRandom(1.0D), 4, 0d, 0d, 0d, 0d);
        }
    }
}
