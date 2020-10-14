package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.block.WebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.vector.Vector3d;

import java.util.List;

public class Honey
{
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
            entity.addPotionEffect(new EffectInstance(Effects.SLOWNESS, 1, 6));
        }
        return true;
    }
}
