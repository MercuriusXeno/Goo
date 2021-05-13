package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.function.Supplier;

public class Vital
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.VITAL_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "vital_pulse", Vital::vitalPulse, Vital::isLivingInRangeAndHalfSecondPulse);
    }

    private static boolean isLivingInRangeAndHalfSecondPulse(SplatContext splatContext) {
        return (splatContext.world().getGameTime() % 10 == 0)
                && splatContext.world()
                .getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox().grow(1d),
                        (e) -> e.isEntityUndead() || e.getHealth() < e.getMaxHealth()).size() > 0;
    }

    private static boolean vitalPulse(SplatContext splatContext) {
        List<LivingEntity> nearbyEntities = splatContext.world()
                .getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox().grow(1d),
                        (e) -> e.isEntityUndead() || e.getHealth() < e.getMaxHealth());
        for(LivingEntity entity : nearbyEntities) {
            if (entity.isEntityUndead()) {
                entity.attackEntityFrom(DamageSource.causeIndirectDamage(splatContext.splat(), entity), 1f);
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
