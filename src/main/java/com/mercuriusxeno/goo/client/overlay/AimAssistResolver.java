package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Entity targeting with two-pass aim assist (exact raytrace + cone scan)
 * and sticky retention to prevent target flicker at cone edges.
 */
final class AimAssistResolver {
    /** Maximum range for blob throwing in blocks. */
    private static final double MAX_RANGE = GooTargetHighlighter.MAX_RANGE;

    /**
     * Aim-assist forgiveness half-angle in degrees. Entities within this cone
     * around the reticle are eligible for targeting even if the exact raytrace
     * misses their hitbox.
     */
    private static final double AIM_ASSIST_DEGREES = 5.0;

    /** Cosine of the aim-assist angle - precomputed for dot-product checks. */
    private static final double AIM_ASSIST_COS = Math.cos(Math.toRadians(AIM_ASSIST_DEGREES));

    /**
     * Sticky-target retention half-angle in degrees. Once locked, a target
     * stays selected until the reticle drifts beyond this wider cone,
     * preventing flicker when the cursor is near the edge.
     */
    private static final double STICKY_DEGREES = 7.0;

    /** Cosine of the sticky retention angle. */
    private static final double STICKY_COS = Math.cos(Math.toRadians(STICKY_DEGREES));

    /** Half divisor for centering AABB calculations. */
    private static final double CENTER_HALF = 0.5;

    /** Sentinel cosine value indicating an invalid or out-of-range cone angle. */
    private static final double NO_CONE_ANGLE = -1;

    /** Inset from AABB face to avoid sampling right at block boundaries. */
    private static final double LOS_INSET = 0.05;

    /** Axis sample index: west face center. */
    private static final int AXIS_WEST = 0;
    /** Axis sample index: east face center. */
    private static final int AXIS_EAST = 1;
    /** Axis sample index: north face center. */
    private static final int AXIS_NORTH = 2;
    /** Axis sample index: south face center. */
    private static final int AXIS_SOUTH = 3;

    private AimAssistResolver() {}

    /**
     * Finds the best living entity target using a two-pass approach:
     * 1. Exact raytrace - if the reticle is directly on an entity, pick it.
     * 2. Cone-based aim assist - if no exact hit, find the closest entity
     *    within the forgiveness cone.
     *
     * Sticky retention: if the previous target is still within the wider
     * sticky cone, keep it to prevent flicker.
     *
     * Both passes and sticky retention require line-of-sight.
     *
     * @param player the interacting player
     * @param from the start world position
     * @param to the end world position
     * @param previousTarget the previous frame's target entity, or null
     * @return the best target entity, or null if none in range/cone
     */
    static @Nullable Entity findClosestEntity(Player player, Vec3 from, Vec3 to,
            @Nullable Entity previousTarget) {
        Vec3 lookDir = to.subtract(from).normalize();
        Level level = player.level();
        List<Entity> candidates = gatherCandidates(player, from, to);

        Entity exactHit = findExactRaytraceHit(candidates, level, player, from, to);
        if (exactHit != null) { return exactHit; }

        Entity bestCone = findBestConeTarget(candidates, level, player, from, lookDir);

        Entity sticky = retainStickyTarget(candidates, level, player, from, lookDir,
                bestCone, previousTarget);
        return (sticky != null) ? sticky : bestCone;
    }

    /**
     * Gathers living, pickable entities within the aim-assist cone's bounding volume.
     *
     * @param player the aiming player (excluded from results)
     * @param from   ray start (eye position)
     * @param to     ray end (eye + look * range)
     * @return candidate entities in the inflated search box
     */
    private static List<Entity> gatherCandidates(Player player, Vec3 from, Vec3 to) {
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(to.subtract(from))
                .inflate(coneRadius + 1.0);
        return player.level().getEntities(
                player, searchBox, e -> e instanceof LivingEntity && e.isPickable());
    }

    /**
     * Pass 1: exact AABB raytrace. Returns the closest entity whose inflated
     * bounding box intersects the look ray and has line-of-sight.
     *
     * @param candidates pre-filtered entity list
     * @param level      the current level
     * @param player     the aiming player
     * @param from       ray start
     * @param to         ray end
     * @return closest exact hit, or null
     */
    private static @Nullable Entity findExactRaytraceHit(
            List<Entity> candidates, Level level, Player player, Vec3 from, Vec3 to) {
        return closestByDistance(candidates, e -> exactHitDistance(e, level, player, from, to));
    }

    /**
     * Returns the entity with the smallest distance from a distance function,
     * or null if all return MAX_VALUE.
     *
     * @param candidates the entity candidates
     * @param distFn     distance function (MAX_VALUE means skip)
     * @return the closest entity, or null
     */
    private static @Nullable Entity closestByDistance(
            List<Entity> candidates, ToDoubleFunction<Entity> distFn) {
        double[] bestDist = {Double.MAX_VALUE};
        Entity[] best = {null};
        candidates.forEach(e -> updateNearest(e, distFn.applyAsDouble(e), bestDist, best));
        return best[0];
    }

    /**
     * Updates the nearest-entity tracking arrays if this entity is closer.
     *
     * @param entity   the candidate
     * @param dist     the computed distance
     * @param bestDist single-element array holding the best distance
     * @param best     single-element array holding the best entity
     */
    @SuppressWarnings("PMD.UseVarargs") // arrays are mutable out-params, not varargs
    private static void updateNearest(
            Entity entity, double dist, double[] bestDist, Entity[] best) {
        if (dist < bestDist[0]) {
            bestDist[0] = dist;
            best[0] = entity;
        }
    }

    /**
     * Returns the squared distance of an exact raytrace hit, or MAX_VALUE if
     * the ray misses or line-of-sight is blocked.
     *
     * @param entity the candidate entity
     * @param level  the current level
     * @param player the aiming player
     * @param from   ray start
     * @param to     ray end
     * @return squared hit distance, or Double.MAX_VALUE if no hit
     */
    private static double exactHitDistance(
            Entity entity, Level level, Player player, Vec3 from, Vec3 to) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        Optional<Vec3> clip = box.clip(from, to);
        if (clip.isPresent() && hasLineOfSight(level, player, from, entity)) {
            return from.distanceToSqr(clip.get());
        }
        return Double.MAX_VALUE;
    }

    /**
     * Pass 2: aim-assist cone scan. Returns the entity closest to the reticle
     * center (highest cosine) that passes range and line-of-sight checks.
     *
     * @param candidates pre-filtered entity list
     * @param level      the current level
     * @param player     the aiming player
     * @param from       ray start
     * @param lookDir    normalized look direction
     * @return best cone target, or null
     */
    private static @Nullable Entity findBestConeTarget(
            List<Entity> candidates, Level level, Player player, Vec3 from, Vec3 lookDir) {
        double[] bestAngle = {AIM_ASSIST_COS};
        Entity[] best = {null};
        for (Entity entity : candidates) {
            double cos = coneAngle(entity, from, lookDir, level, player);
            updateBestAngle(entity, cos, bestAngle, best);
        }
        return best[0];
    }

    /**
     * Updates the best-angle tracking arrays if this entity has a higher cosine.
     *
     * @param entity    the candidate
     * @param cos       the computed cosine angle
     * @param bestAngle single-element array holding the best cosine
     * @param best      single-element array holding the best entity
     */
    @SuppressWarnings("PMD.UseVarargs") // arrays are mutable out-params, not varargs
    private static void updateBestAngle(
            Entity entity, double cos, double[] bestAngle, Entity[] best) {
        if (cos > bestAngle[0]) {
            bestAngle[0] = cos;
            best[0] = entity;
        }
    }

    /**
     * Returns the cosine of the angle between the look direction and the entity,
     * or -1 if the entity is out of range or occluded.
     *
     * @param entity  the candidate entity
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param level   the current level
     * @param player  the aiming player
     * @return cosine of the angle, or NO_CONE_ANGLE if invalid
     */
    private static double coneAngle(
            Entity entity, Vec3 from, Vec3 lookDir, Level level, Player player) {
        Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(from);
        double dist = toEntity.length();
        if (dist < CENTER_HALF || dist > MAX_RANGE) { return NO_CONE_ANGLE; }
        double cos = lookDir.dot(toEntity.normalize());
        if (cos > AIM_ASSIST_COS && hasLineOfSight(level, player, from, entity)) {
            return cos;
        }
        return NO_CONE_ANGLE;
    }

    /**
     * Sticky retention: if the previous frame's target is still alive, in range,
     * inside the wider sticky cone, and visible, keep it to prevent flicker.
     *
     * @param candidates pre-filtered entity list
     * @param level      the current level
     * @param player     the aiming player
     * @param from       ray start
     * @param lookDir    normalized look direction
     * @param bestCone   the cone-pass winner (skipped if already equal)
     * @param previousTarget the previous frame's target entity, or null
     * @return the previous target if it should be retained, or null
     */
    private static @Nullable Entity retainStickyTarget(
            List<Entity> candidates, Level level, Player player,
            Vec3 from, Vec3 lookDir, @Nullable Entity bestCone,
            @Nullable Entity previousTarget) {
        if (previousTarget == null || previousTarget == bestCone) { return null; }
        if (!previousTarget.isAlive() || !candidates.contains(previousTarget)) { return null; }
        if (isInStickyCone(level, player, from, lookDir, previousTarget)) { return previousTarget; }
        return null;
    }

    /**
     * Returns true if the given entity is within range, inside the sticky cone, and visible.
     *
     * @param level   the current level
     * @param player  the aiming player
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param target  the entity to check
     * @return true if the entity should be retained
     */
    private static boolean isInStickyCone(Level level, Player player, Vec3 from,
            Vec3 lookDir, Entity target) {
        Vec3 toPrev = target.getBoundingBox().getCenter().subtract(from);
        double prevDist = toPrev.length();
        if (prevDist <= CENTER_HALF || prevDist > MAX_RANGE) { return false; }
        double prevCos = lookDir.dot(toPrev.normalize());
        return prevCos > STICKY_COS
                && hasLineOfSight(level, player, from, target);
    }

    /**
     * Checks whether the player has line-of-sight to an entity by casting rays
     * to multiple sample points on the entity's AABB. Returns true if ANY ray
     * reaches without hitting a block.
     *
     * @param level the current level
     * @param player the interacting player
     * @param eyePos the eye position
     * @param target the current aim target
     * @return true if lineOfSight is present
     */
    static boolean hasLineOfSight(Level level, Player player, Vec3 eyePos, Entity target) {
        Vec3[] samples = buildLosSamples(target.getBoundingBox());
        for (Vec3 sample : samples) {
            if (isClearRay(level, player, eyePos, sample)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if a ray from origin to target misses all blocks (clear sightline).
     *
     * @param level  the current level
     * @param player the aiming player
     * @param from   ray origin
     * @param to     ray target
     * @return true if no block intersection
     */
    private static boolean isClearRay(Level level, Player player, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Builds 7 sample points on an AABB: center plus 6 inset face centers.
     *
     * @param box the entity bounding box
     * @return sample points for line-of-sight raycasts
     */
    private static Vec3[] buildLosSamples(AABB box) {
        Vec3 c = box.getCenter();
        Vec3 top = new Vec3(c.x, box.maxY - LOS_INSET, c.z);
        Vec3 bottom = new Vec3(c.x, box.minY + LOS_INSET, c.z);
        Vec3[] axis = buildAxisSamples(box, c);
        return new Vec3[] {
            c, top, bottom, axis[AXIS_WEST], axis[AXIS_EAST], axis[AXIS_NORTH], axis[AXIS_SOUTH],
        };
    }

    /**
     * Builds the 4 horizontal axis face-center samples (west, east, north, south).
     *
     * @param box    the entity bounding box
     * @param center the box center
     * @return array of [west, east, north, south] samples
     */
    private static Vec3[] buildAxisSamples(AABB box, Vec3 center) {
        return new Vec3[] {
            new Vec3(box.minX + LOS_INSET, center.y, center.z),
            new Vec3(box.maxX - LOS_INSET, center.y, center.z),
            new Vec3(center.x, center.y, box.minZ + LOS_INSET),
            new Vec3(center.x, center.y, box.maxZ - LOS_INSET),
        };
    }
}
