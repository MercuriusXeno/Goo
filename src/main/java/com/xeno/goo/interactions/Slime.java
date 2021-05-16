package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.function.Supplier;

public class Slime
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.SLIME_GOO;
    // arbitrary threshold for detecting that a player seems to be jumping in the zone of control.
    // player jumps are slightly over 0.4f on initial acceleration, which seems to work pretty well.
    private static final double HEURISTIC_JUMP_DETECTION_SPEED = 0.4d;

    // you must be going downward at least this fast for the catch-bounce to trigger
    // this is just around standard gravity, for reference.
    private static final double JUMP_BOOST_POWER = 0.7d;

    // you must be going downward at least this fast for the catch-bounce to trigger
    // this is just around standard gravity, for reference.
    private static final double DOWNWARD_MOTION_THRESHOLD = 0.06d;

    // arbitrary amount of fall distance before the downward bounce will trigger.
    // this is to prevent the player from bouncing forever if they fall from a high enough position.
    private static final float FALL_HEIGHT_THRESHOLD = 0.9f;

    // coefficient of kinetic decay when bouncing. Experimental. High numbers mean bouncier bouncing.
    // anything above 1 is a positive feedback loop and inadvisable. Anything under 0.35d is very dull.
    private static final double VERTICAL_BOUNCE_DECAY = 0.65d;
    private static final double HORIZONTAL_BOUNCE_DECAY = 0.9d;

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "bounce_living", Slime::bounceLiving, Slime::isLivingInBounceArea);
        GooInteractions.registerBlobHit(fluidSupplier.get(), "slime_hit", Slime::entityHit);
    }

    private static boolean entityHit(BlobHitContext c) {
        c.damageVictim(3f);
        c.knockback(4f);
        return true;
    }

    private static boolean isLivingInBounceArea(SplatContext splatContext) {
        return splatContext.world().getEntitiesWithinAABB(LivingEntity.class,
                splatContext.splat().getBoundingBox().grow(0.25d, 0.9d, 0.25d), e -> !e.isOnGround()).size() > 0;
    }

    private static boolean bounceLiving(SplatContext splatContext) {
        List<LivingEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(LivingEntity.class,
                splatContext.splat().getBoundingBox().grow(0.25d, 0.9d, 0.25d), e -> !e.isOnGround());
        boolean didThings = false;
        for(LivingEntity entity : nearbyEntities) {
            if (splatContext.sideHit() == Direction.UP) {
                if (entity.getMotion().y > HEURISTIC_JUMP_DETECTION_SPEED) {
                    // we don't apply to airborne because it effects bouncing annoyingly.
                    if (!entity.isSneaking() && !entity.isAirBorne) {
                        if (entity.getMotion().y < JUMP_BOOST_POWER) {
                            entity.setMotion(entity.getMotion().x, JUMP_BOOST_POWER, entity.getMotion().z);
                            entity.velocityChanged = true;
                            didThings = true;
                        }
                    }
                } else if (entity.getMotion().y < -DOWNWARD_MOTION_THRESHOLD && entity.fallDistance > FALL_HEIGHT_THRESHOLD){
                    // fall breaker
                    entity.fallDistance = 0;
                    if (!entity.isSneaking()) {
                        bounceEntity(entity, splatContext.sideHit());
                    }
                    didThings = true;
                }
            } else {
                if (!entity.isSneaking()) {
                    bounceEntity(entity, splatContext.sideHit());
                    didThings = true;
                }
            }
        }
        if (didThings) {
            doEffects(splatContext);
        }
        return didThings;
    }

    private static void doEffects(SplatContext context) {
        AudioHelper.entityAudioEvent(context.splat(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.AMBIENT,
                1f, () -> 1f);
        if (context.world() instanceof ServerWorld) {
            Vector3d particlePos = context.splat().getPositionVec();
            AxisAlignedBB bounds = context.splat().getBoundingBox();
            // vec representing the "domain" of the bounding box.
            Vector3d rangeVec = new Vector3d(
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ);
            for (int i = 0; i < 5; i++) {
                Vector3d finalPos = particlePos.add(
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.x,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.y,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.z
                );
                ((ServerWorld) context.world()).spawnParticle(
                        new BlockParticleData(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.getDefaultState()),
                        finalPos.x, finalPos.y, finalPos.z, 1, 0d, 0d, 0d, 0d);
            }
        }
    }

    public static void bounceEntity(Entity e, Direction face)
    {
        switch(face.getAxis()) {
            case Y:
                e.setMotion(e.getMotion().mul(1d, -VERTICAL_BOUNCE_DECAY, 1d));
                break;
            case X:
                e.setMotion(e.getMotion().mul(-HORIZONTAL_BOUNCE_DECAY, 1d, 1d));
                break;
            case Z:
                e.setMotion(e.getMotion().mul(1d, 1d, -HORIZONTAL_BOUNCE_DECAY));
                break;
        }
        e.velocityChanged = true;
    }
}
