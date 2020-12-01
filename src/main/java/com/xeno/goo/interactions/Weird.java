package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Weird
{
    private static final double BOUNDS_REACH = 16d;

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.WEIRD_GOO.get(), "weird_transport", Weird::weirdTransport, (c) -> true); // too complicated
    }

    private static boolean weirdTransport(SplatContext splatContext) {
        int warpCost = GooMod.config.costOfSplatInteraction(splatContext.fluid(), splatContext.interactionKey());
        List<GooSplat> nearbyWarpSplat = splatContext.world().getEntitiesWithinAABB(GooSplat.class,
                splatContext.splat().getBoundingBox().grow(BOUNDS_REACH),
                    (e) -> !e.equals(splatContext.splat())
                        && e.onlyGoo().getFluid().equals(Registry.WEIRD_GOO.get())
                        && e.onlyGoo().getAmount() >= warpCost
        );
        if (nearbyWarpSplat.size() == 0) {
            return false;
        }
        nearbyWarpSplat.sort(Comparator.comparingDouble(c -> c.getDistance(splatContext.splat())));
        GooSplat nearestWarp = nearbyWarpSplat.get(0);
        List<Entity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(Entity.class, splatContext.splat().getBoundingBox(), (e) -> !e.equals(splatContext.splat()));
        for(Entity entity : nearbyEntities) {
            Tuple<Boolean, Vector3d> safePassageResult = testSafePassage(splatContext, nearestWarp, entity);
            if (safePassageResult.getA()) {
                teleportToSafePassage(entity, safePassageResult.getB());
                nearestWarp.drain(warpCost, IFluidHandler.FluidAction.EXECUTE);
                doEffects(nearestWarp, true);
                doEffects(splatContext.splat(), false);
                return true;
            }
        }
        return false;
    }

    private static void doEffects(GooSplat splat, boolean isPlayingSound) {
        if (splat.world instanceof ServerWorld) {
            for (int i = 0; i < 5; i++) {
                ((ServerWorld) splat.world).spawnParticle(ParticleTypes.PORTAL,
                        splat.getPosXRandom(0.5D), splat.getPosYRandom() - 0.25D, splat.getPosZRandom(0.5D),
                        1, 0d, 0d, 0d, 0d);
            }
        }
        if (isPlayingSound) {
            AudioHelper.entityAudioEvent(splat, Registry.WEIRD_TELEPORT_SOUND.get(), SoundCategory.AMBIENT,
                    1f, AudioHelper.PitchFormulas.HalfToOne);
        }
    }

    private static void teleportToSafePassage(Entity entity, Vector3d b) {
        entity.teleportKeepLoaded(b.x, b.y, b.z);
    }

    private static Tuple<Boolean, Vector3d> testSafePassage(SplatContext splatContext, GooSplat nearestWarp, Entity entity) {
        EntitySize size = entity.getSize(entity.getPose());
        Vector3d result = Vector3d.copy(nearestWarp.getPosition());
        // depending on the orientation of the goo, we want to adjust the result a bit.
        switch(splatContext.splat().sideWeLiveOn()) {
            case UP:
                result = result.add(0.5d, 0d, 0.5d); // centered with no y change
                break;
            case DOWN:
                result = result.add(0.5d, -Math.ceil(size.height), 0.5d);
                break;
            case EAST:
                result = result.add(-size.width / 2d, 0.5d, 0.5d);
                break;
            case WEST:
                result = result.add(size.width / 2d, 0.5d, 0.5d);
                break;
            case SOUTH:
                result = result.add(0.5d, 0.5d, -size.width / 2d);
                break;
            case NORTH:
                result = result.add(0.5d, 0.5d, size.width / 2d);
                break;
        }
        // capture a list of distinct testable positions
        List<BlockPos> testablePositions = new ArrayList<>();
        double widthInc = size.width / Math.ceil(size.width);
        double heightInc = size.height / Math.ceil(size.height);
        for (double x = -size.width / 2d; x < size.width / 2d; x += widthInc * 0.99d) {
            for (double z = -size.width / 2d; z < size.width / 2d; z += widthInc * 0.99d) {
                for (double y = 0; y < size.height; y += heightInc * 0.99d) {
                    BlockPos pos = new BlockPos(result.x + x, result.y + y, result.z + z);
                    if (testablePositions.stream().anyMatch((p) -> p.equals(pos))) {
                        continue;
                    }
                    testablePositions.add(pos);
                }
            }
        }
        for(BlockPos pos : testablePositions) {
            if (!entity.getEntityWorld().getBlockState(pos).isAir(entity.getEntityWorld(), pos)) {
                return new Tuple<>(false, result);
            }
        }
        return new Tuple<>(true, result);
    }
}
