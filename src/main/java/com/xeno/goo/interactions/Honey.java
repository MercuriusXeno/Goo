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
        GooInteractions.registerSplat(Registry.HONEY_GOO.get(), "trap_living", Honey::trapLiving);
    }

    private static boolean trapLiving(SplatContext splatContext) {
        List<Entity> nearbyEntities = splatContext.world().getEntitiesWithinAABBExcludingEntity(splatContext.splat(),
                splatContext.splat().getBoundingBox());
        boolean didThings = false;
        for(Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).addPotionEffect(new EffectInstance(Effects.SLOWNESS, 1, 6));
            }
            didThings = true;
        }
        return didThings;
    }
}
