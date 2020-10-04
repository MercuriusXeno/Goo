package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.util.DamageSource;

import java.util.Comparator;
import java.util.List;

public class Weird
{
    private static final double BOUNDS_REACH = 16d;

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.WEIRD_GOO.get(), "weird_transport", Weird::weirdTransport);

    }

    private static boolean weirdTransport(SplatContext splatContext) {
//        if (splatContext.world().getGameTime() % 10 > 0) {
//            return false;
//        }
//        List<GooSplat> nearbyWarpSplat = splatContext.world().getEntitiesWithinAABB(GooSplat.class, splatContext.splat().getBoundingBox().grow(BOUNDS_REACH), (e) -> e.goo().getFluid().equals(Registry.WEIRD_GOO.get()));
//        if (nearbyWarpSplat.size() == 0) {
//            return false;
//        }
//        nearbyWarpSplat.sort(Comparator.comparingDouble(c -> c.getDistance(splatContext.splat())));
//        GooSplat nearestWarp = nearbyWarpSplat.get(0);
//        List<Entity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(Entity.class, splatContext.splat().getBoundingBox());
//        for(Entity entity : nearbyEntities) {
//            EndermanEntity
//            if (entity.setPositionAndRotationDirect();)
//        }
        return false;
    }
}
