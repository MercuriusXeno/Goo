package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;

import java.util.List;

public class Honey
{
    private static int EFFECT_DURATION = 10;
    private static int EFFECT_POTENCY = 4;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.HONEY_GOO.get(), "trap_living", Honey::trapLiving, Honey::hasLivingTarget);
    }

    private static boolean hasLivingTarget(SplatContext splatContext) {
        return splatContext.world().getEntitiesWithinAABB(LivingEntity.class, splatContext.splat().getBoundingBox()).size() > 0;
    }

    private static boolean trapLiving(SplatContext splatContext) {
        List<LivingEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(LivingEntity.class,
                splatContext.splat().getBoundingBox());
        // affect every living entity in BB on the same dime, essentially. One "tick" of effect costs, not per entity.
        for(LivingEntity entity : nearbyEntities) {
            entity.addPotionEffect(new EffectInstance(Effects.SLOWNESS, EFFECT_DURATION, EFFECT_POTENCY));
        }
        return true;
    }
}
