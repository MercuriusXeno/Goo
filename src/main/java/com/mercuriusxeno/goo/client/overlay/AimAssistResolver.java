package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Target resolution with two-pass aim assist (exact raytrace + cone scan)
 * and sticky retention to prevent flicker at cone edges.
 *
 * <p>Handles both living entities and placed {@link ChainMarkerBlockEntity}
 * blocks in a single unified pass: chain markers behave exactly like
 * entities for the purposes of targeting, including sticky retention and
 * (via caller) sneak bypass.
 */
final class AimAssistResolver {

    /** Maximum range for blob throwing in blocks. */
    private static final double MAX_RANGE = GooTargetHighlighter.MAX_RANGE;

    /**
     * Aim-assist forgiveness half-angle in degrees. Targets within this cone
     * around the reticle are eligible even if the exact raytrace misses
     * their hitbox.
     */
    private static final double AIM_ASSIST_DEGREES = 4.0;

    /** Cosine of the aim-assist angle - precomputed for dot-product checks. */
    private static final double AIM_ASSIST_COS = Math.cos(Math.toRadians(AIM_ASSIST_DEGREES));

    /**
     * Sticky-target retention half-angle in degrees. Once locked, a target
     * stays selected until the reticle drifts beyond this wider cone,
     * preventing flicker when the cursor is near the edge.
     */
    private static final double STICKY_DEGREES = 5.0;

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

    /**
     * Sealed aim-hit kind produced by the resolver. Packs either a living
     * entity or a chain marker block position so the caller can dispatch
     * render and throw-payload paths differently.
     */
    sealed interface AimHit {
        /** An entity hit (living, pickable, within cone + LOS). */
        record EntityHit(Entity entity) implements AimHit {}
        /** A chain marker block hit, behaving like an entity for targeting. */
        record ChainMarkerHit(BlockPos pos) implements AimHit {}
    }

    private AimAssistResolver() {}

    /**
     * Finds the best aim hit (living entity or chain marker block) using a
     * two-pass approach plus sticky retention on the previous hit.
     *
     * <ol>
     *   <li>Exact raytrace - if the reticle directly clips a candidate AABB,
     *       pick the nearest by distance. Entity exact-hits win over marker
     *       exact-hits when both are present at equal distance.</li>
     *   <li>Cone scan - pick the candidate with highest cosine to the
     *       reticle (entities and markers compared against the same cosine).</li>
     *   <li>Sticky - if the previous hit is still within the wider sticky
     *       cone, keep it.</li>
     * </ol>
     *
     * All passes require line-of-sight (with a self-voxel exemption for
     * chain markers whose own block would otherwise occlude their AABB).
     *
     * @param player the interacting player
     * @param from   the ray start (eye position)
     * @param to     the ray end (eye + look * range)
     * @param previous the previous frame's hit, or null
     * @return the best hit, or null if none in range/cone
     */
    static @Nullable AimHit findClosestAimHit(Player player, Vec3 from, Vec3 to,
            @Nullable AimHit previous) {
        Vec3 lookDir = to.subtract(from).normalize();
        Level level = player.level();
        List<Entity> entities = gatherCandidates(player, from, to);
        List<BlockPos> markers = gatherChainMarkers(level, from, to);

        AimHit exact = findExactHit(entities, markers, level, player, from, to);
        if (exact != null) { return exact; }

        AimHit bestCone = findBestConeHit(entities, markers, level, player, from, lookDir);

        AimHit sticky = retainSticky(entities, markers, level, player, from,
                lookDir, bestCone, previous);
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
        AABB searchBox = buildSearchBox(player, from, to);
        return player.level().getEntities(
                player, searchBox, e -> e instanceof LivingEntity && e.isPickable());
    }

    /**
     * Gathers placed chain marker block positions within the search box by
     * iterating only the chunks that intersect it. Uses {@code getChunkNow}
     * so we never force-load chunks client-side.
     *
     * @param level the current level
     * @param from  ray start (eye position)
     * @param to    ray end (eye + look * range)
     * @return list of chain marker block positions inside the search box
     */
    private static List<BlockPos> gatherChainMarkers(Level level, Vec3 from, Vec3 to) {
        // Reuse the same AABB as entity gathering so the cone shape is consistent.
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        AABB box = new AABB(from, to).inflate(coneRadius + 1.0);
        int minCX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
        int maxCX = SectionPos.blockToSectionCoord((int) Math.floor(box.maxX));
        int minCZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
        int maxCZ = SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ));
        List<BlockPos> markers = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                collectMarkersInChunk(level, cx, cz, box, markers);
            }
        }
        return markers;
    }

    /**
     * Pulls chain marker block entities out of a single chunk if it is
     * currently loaded client-side.
     *
     * @param level the current level
     * @param cx chunk X coordinate
     * @param cz chunk Z coordinate
     * @param box the search bounding box in world space
     * @param out list to append matching positions to
     */
    private static void collectMarkersInChunk(Level level, int cx, int cz,
            AABB box, List<BlockPos> out) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) { return; }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof ChainMarkerBlockEntity)) { continue; }
            BlockPos pos = be.getBlockPos();
            if (box.contains(Vec3.atCenterOf(pos))) {
                out.add(pos.immutable());
            }
        }
    }

    /**
     * Builds the shared search bounding box used for both entity and marker
     * candidate gathering.
     *
     * @param player the aiming player
     * @param from   ray start
     * @param to     ray end
     * @return the inflated search box
     */
    private static AABB buildSearchBox(Player player, Vec3 from, Vec3 to) {
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        return player.getBoundingBox()
                .expandTowards(to.subtract(from))
                .inflate(coneRadius + 1.0);
    }


    /**
     * Finds the nearest exact-AABB raytrace hit across both entities and
     * chain markers. Entities win ties at equal distance (matches the
     * existing behavior that entity targeting took priority).
     *
     * @param entities entity candidates
     * @param markers chain marker candidates
     * @param level current level
     * @param player aiming player
     * @param from ray start
     * @param to ray end
     * @return the closest exact hit as an AimHit, or null
     */
    private static @Nullable AimHit findExactHit(List<Entity> entities, List<BlockPos> markers,
            Level level, Player player, Vec3 from, Vec3 to) {
        double bestDist = Double.MAX_VALUE;
        AimHit best = null;
        for (Entity e : entities) {
            double d = exactHitDistance(e, level, player, from, to);
            if (d < bestDist) {
                bestDist = d;
                best = new AimHit.EntityHit(e);
            }
        }
        for (BlockPos pos : markers) {
            double d = exactMarkerHitDistance(pos, level, player, from, to);
            if (d < bestDist) {
                bestDist = d;
                best = new AimHit.ChainMarkerHit(pos);
            }
        }
        return best;
    }

    /**
     * Returns the squared distance of an exact raytrace hit on an entity,
     * or MAX_VALUE if the ray misses or line-of-sight is blocked.
     *
     * @param entity the candidate entity
     * @param level  the current level
     * @param player the aiming player
     * @param from   ray start
     * @param to     ray end
     * @return squared hit distance, or Double.MAX_VALUE if no hit
     */
    private static double exactHitDistance(Entity entity, Level level, Player player,
            Vec3 from, Vec3 to) {
        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        Optional<Vec3> clip = box.clip(from, to);
        if (clip.isPresent() && hasLineOfSight(level, player, from, entity)) {
            return from.distanceToSqr(clip.get());
        }
        return Double.MAX_VALUE;
    }

    /**
     * Returns the squared distance of an exact raytrace hit on a chain
     * marker's full block AABB, or MAX_VALUE if the ray misses or LOS is
     * blocked. The marker's own block is exempt from LOS occlusion.
     *
     * @param pos    the marker block position
     * @param level  the current level
     * @param player the aiming player
     * @param from   ray start
     * @param to     ray end
     * @return squared hit distance, or Double.MAX_VALUE if no hit
     */
    private static double exactMarkerHitDistance(BlockPos pos, Level level, Player player,
            Vec3 from, Vec3 to) {
        AABB box = new AABB(pos);
        Optional<Vec3> clip = box.clip(from, to);
        if (clip.isPresent() && hasLineOfSightToBox(level, player, from, box, pos)) {
            return from.distanceToSqr(clip.get());
        }
        return Double.MAX_VALUE;
    }


    /**
     * Scans both entity and chain marker candidates and returns whichever
     * is closest to the reticle (highest cosine), with entities and markers
     * competing in a single pool.
     *
     * @param entities entity candidates
     * @param markers chain marker candidates
     * @param level current level
     * @param player aiming player
     * @param from ray start
     * @param lookDir normalized look direction
     * @return the cone-pass winner, or null if nothing clears the aim-assist cone
     */
    private static @Nullable AimHit findBestConeHit(List<Entity> entities, List<BlockPos> markers,
            Level level, Player player, Vec3 from, Vec3 lookDir) {
        double bestCos = AIM_ASSIST_COS;
        AimHit best = null;
        for (Entity entity : entities) {
            double cos = coneAngleEntity(entity, from, lookDir, level, player);
            if (cos > bestCos) {
                bestCos = cos;
                best = new AimHit.EntityHit(entity);
            }
        }
        for (BlockPos pos : markers) {
            double cos = coneAngleMarker(pos, from, lookDir, level, player);
            if (cos > bestCos) {
                bestCos = cos;
                best = new AimHit.ChainMarkerHit(pos);
            }
        }
        return best;
    }

    /**
     * Returns the cosine of the angle between the look direction and an
     * entity's AABB center, or NO_CONE_ANGLE if out of range or occluded.
     *
     * @param entity  the candidate entity
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param level   the current level
     * @param player  the aiming player
     * @return cosine of the angle, or NO_CONE_ANGLE if invalid
     */
    private static double coneAngleEntity(Entity entity, Vec3 from, Vec3 lookDir,
            Level level, Player player) {
        Vec3 toTarget = entity.getBoundingBox().getCenter().subtract(from);
        double dist = toTarget.length();
        if (dist < CENTER_HALF || dist > MAX_RANGE) { return NO_CONE_ANGLE; }
        double cos = lookDir.dot(toTarget.normalize());
        if (cos > AIM_ASSIST_COS && hasLineOfSight(level, player, from, entity)) {
            return cos;
        }
        return NO_CONE_ANGLE;
    }

    /**
     * Returns the cosine of the angle between the look direction and a
     * chain marker block's center, or NO_CONE_ANGLE if out of range or
     * occluded. The marker's own block is exempt from LOS occlusion.
     *
     * @param pos     the chain marker block position
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param level   the current level
     * @param player  the aiming player
     * @return cosine of the angle, or NO_CONE_ANGLE if invalid
     */
    private static double coneAngleMarker(BlockPos pos, Vec3 from, Vec3 lookDir,
            Level level, Player player) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 toTarget = center.subtract(from);
        double dist = toTarget.length();
        if (dist < CENTER_HALF || dist > MAX_RANGE) { return NO_CONE_ANGLE; }
        double cos = lookDir.dot(toTarget.normalize());
        if (cos > AIM_ASSIST_COS
                && hasLineOfSightToBox(level, player, from, new AABB(pos), pos)) {
            return cos;
        }
        return NO_CONE_ANGLE;
    }


    /**
     * Sticky retention across both hit kinds. If the previous frame's hit
     * is still alive/loaded, in range, inside the wider sticky cone, and
     * visible, keep it to prevent flicker.
     *
     * @param entities entity candidates this frame
     * @param markers chain marker candidates this frame
     * @param level current level
     * @param player aiming player
     * @param from ray start
     * @param lookDir normalized look direction
     * @param bestCone the cone-pass winner this frame (skipped if equal to previous)
     * @param previous the previous frame's hit, or null
     * @return the previous hit if it should be retained, or null
     */
    private static @Nullable AimHit retainSticky(List<Entity> entities, List<BlockPos> markers,
            Level level, Player player, Vec3 from, Vec3 lookDir,
            @Nullable AimHit bestCone, @Nullable AimHit previous) {
        if (previous == null || hitsEqual(previous, bestCone)) { return null; }
        if (previous instanceof AimHit.EntityHit eh) {
            return retainStickyEntity(eh, entities, level, player, from, lookDir);
        }
        if (previous instanceof AimHit.ChainMarkerHit cmh) {
            return retainStickyMarker(cmh, markers, level, player, from, lookDir);
        }
        return null;
    }

    /**
     * Checks whether a previous entity hit should be retained this frame.
     *
     * @param eh       the previous entity hit
     * @param entities live entity candidates this frame
     * @param level    current level
     * @param player   aiming player
     * @param from     ray start
     * @param lookDir  normalized look direction
     * @return the same hit if retained, otherwise null
     */
    private static @Nullable AimHit retainStickyEntity(AimHit.EntityHit eh,
            List<Entity> entities, Level level, Player player, Vec3 from, Vec3 lookDir) {
        Entity e = eh.entity();
        if (!e.isAlive() || !entities.contains(e)) { return null; }
        return isEntityInStickyCone(level, player, from, lookDir, e) ? eh : null;
    }

    /**
     * Checks whether a previous chain marker hit should be retained this frame.
     *
     * @param cmh      the previous chain marker hit
     * @param markers  live marker candidates this frame
     * @param level    current level
     * @param player   aiming player
     * @param from     ray start
     * @param lookDir  normalized look direction
     * @return the same hit if retained, otherwise null
     */
    private static @Nullable AimHit retainStickyMarker(AimHit.ChainMarkerHit cmh,
            List<BlockPos> markers, Level level, Player player, Vec3 from, Vec3 lookDir) {
        BlockPos pos = cmh.pos();
        if (!markers.contains(pos)) { return null; }
        return isMarkerInStickyCone(level, player, from, lookDir, pos) ? cmh : null;
    }

    /**
     * Returns true if two hits refer to the same underlying entity or marker.
     *
     * @param a first hit (may be null)
     * @param b second hit (may be null)
     * @return true if both hits point at the same target
     */
    private static boolean hitsEqual(@Nullable AimHit a, @Nullable AimHit b) {
        if (a == null || b == null) { return false; }
        if (a instanceof AimHit.EntityHit ae) { return matchesEntity(ae, b); }
        if (a instanceof AimHit.ChainMarkerHit am) { return matchesMarker(am, b); }
        return false;
    }

    /**
     * Returns true if the other hit is an entity hit on the same entity.
     *
     * @param a the left-hand entity hit
     * @param b the other hit (may be a marker or an entity)
     * @return true if both hits point at the same entity
     */
    private static boolean matchesEntity(AimHit.EntityHit a, AimHit b) {
        return b instanceof AimHit.EntityHit be && a.entity() == be.entity();
    }

    /**
     * Returns true if the other hit is a chain marker hit at the same pos.
     *
     * @param a the left-hand chain marker hit
     * @param b the other hit (may be a marker or an entity)
     * @return true if both hits point at the same marker position
     */
    private static boolean matchesMarker(AimHit.ChainMarkerHit a, AimHit b) {
        return b instanceof AimHit.ChainMarkerHit bm && a.pos().equals(bm.pos());
    }

    /**
     * Returns true if an entity is within range, inside the sticky cone, and visible.
     *
     * @param level   the current level
     * @param player  the aiming player
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param target  the entity to check
     * @return true if the entity should be retained
     */
    private static boolean isEntityInStickyCone(Level level, Player player, Vec3 from,
            Vec3 lookDir, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(from);
        double dist = to.length();
        if (dist <= CENTER_HALF || dist > MAX_RANGE) { return false; }
        double cos = lookDir.dot(to.normalize());
        return cos > STICKY_COS && hasLineOfSight(level, player, from, target);
    }

    /**
     * Returns true if a chain marker is within range, inside the sticky cone, and visible.
     *
     * @param level   the current level
     * @param player  the aiming player
     * @param from    ray start
     * @param lookDir normalized look direction
     * @param pos     the marker block position
     * @return true if the marker should be retained
     */
    private static boolean isMarkerInStickyCone(Level level, Player player, Vec3 from,
            Vec3 lookDir, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 to = center.subtract(from);
        double dist = to.length();
        if (dist <= CENTER_HALF || dist > MAX_RANGE) { return false; }
        double cos = lookDir.dot(to.normalize());
        return cos > STICKY_COS
                && hasLineOfSightToBox(level, player, from, new AABB(pos), pos);
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
        return hasLineOfSightToBox(level, player, eyePos, target.getBoundingBox(), null);
    }

    /**
     * Line-of-sight to an arbitrary AABB. If {@code selfBlock} is non-null,
     * rays that terminate inside that block position are treated as clear
     * (prevents a chain marker's own voxel from occluding its own LOS).
     *
     * @param level   the current level
     * @param player  the interacting player
     * @param eyePos  the eye position
     * @param box     the target AABB
     * @param selfBlock block position to exempt from occlusion, or null
     * @return true if any sample ray reaches the target
     */
    private static boolean hasLineOfSightToBox(Level level, Player player, Vec3 eyePos,
            AABB box, @Nullable BlockPos selfBlock) {
        Vec3[] samples = buildLosSamples(box);
        for (Vec3 sample : samples) {
            if (isClearRay(level, player, eyePos, sample, selfBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if a ray from origin to target misses all blocks, or only
     * strikes the exempt self-block if one is specified.
     *
     * @param level     the current level
     * @param player    the aiming player
     * @param from      ray origin
     * @param to        ray target
     * @param selfBlock block position to exempt from occlusion, or null
     * @return true if no block intersection (or only the exempt block)
     */
    private static boolean isClearRay(Level level, Player player, Vec3 from, Vec3 to,
            @Nullable BlockPos selfBlock) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || (selfBlock != null && selfBlock.equals(hit.getBlockPos()));
    }

    /**
     * Builds 7 sample points on an AABB: center plus 6 inset face centers.
     *
     * @param box the bounding box
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
     * @param box    the bounding box
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
