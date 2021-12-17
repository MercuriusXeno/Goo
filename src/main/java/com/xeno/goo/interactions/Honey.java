package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;

import java.util.List;
import java.util.function.Supplier;

public class Honey
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.HONEY_GOO;
    private static int EFFECT_DURATION = 10;
    private static int EFFECT_POTENCY = 4;
    public static void registerInteractions()
    {
        GooInteractions.registerBlobHit(fluidSupplier.get(), "trap_living", Honey::trapLiving, Honey::hasLivingTarget);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "honey_hit", Honey::entityHit);
    }

    private static boolean hasLivingTarget(BlobHitContext splatContext) {
        return splatContext.world().getEntitiesWithinAABB(LivingEntity.class, splatContext.blob().getBoundingBox()).size() > 0;
    }

    private static boolean entityHit(BlobHitContext c) {
        c.victim().addPotionEffect(new EffectInstance(Effects.SLOWNESS, 120, EFFECT_POTENCY));
        return true;
    }

    private static boolean trapLiving(BlobHitContext splatContext) {
        List<LivingEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(LivingEntity.class,
                splatContext.blob().getBoundingBox());
        // affect every living entity in BB on the same dime, essentially. One "tick" of effect costs, not per entity.
        for(LivingEntity entity : nearbyEntities) {
            entity.addPotionEffect(new EffectInstance(Effects.SLOWNESS, EFFECT_DURATION, EFFECT_POTENCY));
        }
        return true;
    }
}
