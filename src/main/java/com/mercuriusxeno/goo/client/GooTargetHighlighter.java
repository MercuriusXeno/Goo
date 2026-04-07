package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Client-side aim highlighting for goo blob throwing. When the player holds
 * a goo glove with a selected type, this renders:
 * - Vanilla spectral/glowing outline on the targeted mob (goo-colored)
 * - A goo-colored translucent face highlight on the targeted block face
 * - Nothing when aiming at air or beyond range
 *
 * The entity outline uses NeoForge's render state modifier system to inject
 * the outline color into the entity render pipeline, producing the same
 * visual effect as spectral arrows or the Glowing potion.
 *
 * Also exposes {@link #resolveTarget} for the throw system to reuse.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {
    /** Maximum range for blob throwing in blocks. */
    public static final double MAX_RANGE = 32.0;

    /** Face highlight alpha (translucent enough to see texture beneath). */
    private static final int FACE_ALPHA = 80;

    /** Wireframe outline alpha for block face edges. */
    private static final int WIRE_ALPHA = 200;

    /** Offset from the block face to prevent z-fighting. */
    private static final double FACE_OFFSET = 0.005;

    /**
     * Granny-arc threshold: when a side-face hit lands in the upper 15% of
     * the shape's height, redirect targeting to the UP face so the blob
     * arcs onto the top of the block instead of hitting the side.
     */
    private static final double GRANNY_ARC_THRESHOLD = 0.85;

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

    // --- Dashed arc constants ---

    /** Dash segment length in world units. */
    private static final float DASH_ON = 0.5f;
    /** Gap length between dashes in world units. */
    private static final float DASH_OFF = 0.5f;
    /** Full dash cycle length (segment + gap = 1 meter). */
    private static final float DASH_CYCLE = DASH_ON + DASH_OFF;
    /** Scroll speed in world units per second - dashes flow toward the target. */
    private static final float SCROLL_SPEED = 1.5f;
    /** Core alpha for the arc dashes. */
    private static final int ARC_ALPHA = 180;
    /** Distance between polyline sample points on the arc. */
    private static final float SAMPLE_SPACING = 0.25f;
    /** Number of bloom passes for the glow effect (core + outer halos). */
    private static final int ARC_GLOW_PASSES = 3;
    /** Width multiplier step per bloom pass. */
    private static final float ARC_GLOW_WIDTH_STEP = 1.5f;
    /** Alpha decay per bloom pass (exponential). */
    private static final float ARC_GLOW_ALPHA_DECAY = 0.35f;

    /** Half divisor for centering AABB calculations. */
    private static final double CENTER_HALF = 0.5;
    /** Half block offset for face center calculations. */
    private static final double FACE_CENTER_OFFSET = 0.5;
    /** Minimum arc segment count. */
    private static final int MIN_ARC_SEGMENTS = 8;
    /** Maximum arc segment count. */
    private static final int MAX_ARC_SEGMENTS = 128;
    /** Maximum alpha channel value. */
    private static final int MAX_ALPHA = 255;
    /** Half segment midpoint for dash calculations. */
    private static final float DASH_MID = 0.5f;
    /** Sentinel for no valid hand position frame. */
    private static final long NO_FRAME = -1;
    /** Sentinel cosine value indicating an invalid or out-of-range cone angle. */
    private static final double NO_CONE_ANGLE = -1;
    /** Axis sample index: west face center. */
    private static final int AXIS_WEST = 0;
    /** Axis sample index: east face center. */
    private static final int AXIS_EAST = 1;
    /** Axis sample index: north face center. */
    private static final int AXIS_NORTH = 2;
    /** Axis sample index: south face center. */
    private static final int AXIS_SOUTH = 3;

    // --- Entity outline state ---

    /** The entity currently targeted by the glove, or null. Updated each tick. */
    private static @Nullable Entity targetedEntity;

    /** Opaque ARGB outline color for the targeted entity, or 0 if none. */
    private static int targetOutlineColor;

    // --- Line-of-sight ---

    /** Inset from AABB face to avoid sampling right at block boundaries. */
    private static final double LOS_INSET = 0.05;

    // --- Rendering: dashed arc to entity target ---

    /** Ticks-per-second divisor for converting game time to seconds. */
    private static final float TICKS_PER_SECOND = 20.0f;

    private GooTargetHighlighter() {}

    /**
     * Client tick: resolves aim target and stores entity + goo color for the
     * render state modifier to pick up during entity rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearTarget();
            return;
        }
        tickGloveTarget(mc);
    }

    /**
     * Resolves goo type selection and updates aim target for the local player.
     *
     * @param mc the Minecraft client instance
     */
    private static void tickGloveTarget(Minecraft mc) {
        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) {
            clearTarget();
            return;
        }
        updateTarget(mc.player, selectedType);
    }

    /** Resets entity target and outline color when no valid aim exists. */
    private static void clearTarget() {
        targetedEntity = null;
        targetOutlineColor = 0;
    }

    /**
     * Resolves aim and updates the targeted entity and outline color.
     *
     * @param player       the local player
     * @param selectedType the currently selected goo type
     */
    private static void updateTarget(Player player, GooType selectedType) {
        TargetResult target = resolveTarget(player, 1.0f);
        targetedEntity = (target instanceof TargetResult.EntityTarget et)
                ? et.entity() : null;
        targetOutlineColor = (targetedEntity != null)
                ? ARGB.opaque(selectedType.getColor()) : 0;
    }

    /**
     * Render state modifier callback: sets outlineColor on entities targeted
     * by the glove so vanilla renders the spectral glow outline in the goo color.
     * Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the target entity
     * @param state the block state
     */
    public static void modifyEntityRenderState(Entity entity, EntityRenderState state) {
        if (entity == targetedEntity && targetOutlineColor != 0) {
            state.outlineColor = targetOutlineColor;
        }
    }

    // --- Target resolution (public API for throw system) ---

    /**
     * Resolves what the player is aiming at within throw range.
     * Entity hits take priority over block hits unless sneaking,
     * which forces block-only targeting with no aim assist.
     *
     * @param player the local player
     * @param partialTick interpolation factor for smooth rendering
     * @return entity target, block face target, or NONE
     */
    public static TargetResult resolveTarget(Player player, float partialTick) {
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 reach = eyePos.add(player.getViewVector(partialTick).scale(MAX_RANGE));
        TargetResult entityResult = resolveEntityTarget(player, eyePos, reach);
        if (entityResult != null) { return entityResult; }
        return resolveBlockTarget(player, eyePos, reach);
    }

    /**
     * Attempts entity targeting unless the player is sneaking.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return an entity target result, or null if none found or sneaking
     */
    private static @Nullable TargetResult resolveEntityTarget(
            Player player, Vec3 eyePos, Vec3 reach) {
        if (player.isShiftKeyDown()) { return null; }
        Entity entityHit = findClosestEntity(player, eyePos, reach);
        if (entityHit != null) { return TargetResult.entity(entityHit); }
        return null;
    }

    /**
     * Clips against blocks and returns a block or granny-arc target, or NONE.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return the resolved block target or NONE
     */
    private static TargetResult resolveBlockTarget(Player player, Vec3 eyePos, Vec3 reach) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return TargetResult.NONE;
        }
        return classifyBlockHit(player.level(), hit);
    }

    /**
     * Classifies a confirmed block hit as a granny-arc or normal face target.
     *
     * @param level the current level
     * @param hit   the confirmed block hit
     * @return granny-arc or block-face target result
     */
    private static TargetResult classifyBlockHit(Level level, BlockHitResult hit) {
        Direction face = hit.getDirection();
        if (face.getAxis() != Direction.Axis.Y && isUpperEdge(level, hit)) {
            return TargetResult.grannyArc(hit.getBlockPos());
        }
        return TargetResult.block(hit.getBlockPos(), face);
    }

    /**
     * Returns true if the hit landed in the upper portion of the block's
     * voxel shape, measured against the shape's actual Y extent so slabs,
     * stairs, etc. use their real geometry, not a full cube.
     *
     * @param level the current level
     * @param hit the block hit result
     * @return true if upperEdge
     */
    private static boolean isUpperEdge(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) { return false; }
        AABB bounds = shape.bounds();
        double range = bounds.maxY - bounds.minY;
        if (range <= 0) { return false; }
        double hitY = hit.getLocation().y - pos.getY();
        double relative = (hitY - bounds.minY) / range;
        return relative >= GRANNY_ARC_THRESHOLD;
    }

    // --- Event handlers ---

    /**
     * Renders goo-colored target visuals: dashed arc to entity targets,
     * translucent face highlight for block targets. Entity spectral outlines
     * are handled separately by the render state modifier.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { return; }
        if (!mc.options.getCameraType().isFirstPerson()) { return; }
        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) { return; }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TargetResult target = resolveTarget(mc.player, partialTick);
        dispatchTargetRendering(event, mc, target, selectedType, partialTick);
    }

    /**
     * Dispatches arc and face rendering based on target type.
     *
     * @param event        the render event for pose stack access
     * @param mc           the Minecraft instance
     * @param target       the resolved aim target
     * @param selectedType the selected goo type
     * @param partialTick  the partial tick for animation
     */
    private static void dispatchTargetRendering(
            RenderLevelStageEvent.AfterOpaqueFeatures event, Minecraft mc,
            TargetResult target, GooType selectedType, float partialTick) {
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        if (target instanceof TargetResult.EntityTarget et) {
            renderEntityArc(ps, buf, camera, mc.player, et, selectedType, partialTick);
        } else if (target instanceof TargetResult.BlockTarget bt) {
            renderBlockArcAndFace(ps, buf, camera, mc.player, bt, selectedType, partialTick);
        }
    }

    /**
     * Renders the dashed arc toward an entity target.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param et           the entity target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderEntityArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.EntityTarget et,
            GooType gooType, float partialTick) {
        Vec3 end = et.entity().getBoundingBox().getCenter();
        renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType, partialTick, false);
    }

    /**
     * Renders the dashed arc and face highlight for a block target.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param player       the local player
     * @param bt           the block target
     * @param gooType      the goo type for coloring
     * @param partialTick  the partial tick for animation
     */
    private static void renderBlockArcAndFace(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, TargetResult.BlockTarget bt,
            GooType gooType, float partialTick) {
        Vec3 end = Vec3.atCenterOf(bt.pos())
                .add(bt.face().getUnitVec3().scale(FACE_CENTER_OFFSET));
        renderTargetArc(poseStack, bufferSource, camera,
                player, end, gooType, partialTick, bt.grannyArc());
        renderBlockFace(poseStack, bufferSource, camera,
                bt.pos(), bt.face(), gooType);
    }

    /**
     * Checks whether the player has line-of-sight to an entity by casting rays
     * to multiple sample points on the entity's AABB. Returns true if ANY ray
     * reaches without hitting a block, so partial occlusion (peeking around a
     * corner) still counts as visible.
     *
     * Uses OUTLINE clip mode - glass, fences, bars all block LOS, matching
     * what a thrown blob would physically collide with.
     *
     * @param level the current level
     * @param player the interacting player
     * @param eyePos the eyePos position
     * @param target the current aim target
     * @return true if lineOfSight is present
     */
    private static boolean hasLineOfSight(Level level, Player player, Vec3 eyePos, Entity target) {
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

    // --- Entity raytrace with aim assist ---

    /**
     * Finds the best living entity target using a two-pass approach:
     * 1. Exact raytrace - if the reticle is directly on an entity, pick it.
     * 2. Cone-based aim assist - if no exact hit, find the closest entity
     *    within the forgiveness cone ({@link #AIM_ASSIST_DEGREES}).
     *
     * Sticky retention: if the previous target is still within the wider
     * {@link #STICKY_DEGREES} cone, keep it to prevent flicker.
     *
     * Both passes and sticky retention require line-of-sight - entities
     * behind solid blocks are not targetable.
     *
     * @return the best target entity, or null if none in range/cone
     *
     * @param player the interacting player
     * @param from the start world position
     * @param to the end world position
     */
    private static @Nullable Entity findClosestEntity(Player player, Vec3 from, Vec3 to) {
        Vec3 lookDir = to.subtract(from).normalize();
        Level level = player.level();
        List<Entity> candidates = gatherCandidates(player, from, to);

        Entity exactHit = findExactRaytraceHit(candidates, level, player, from, to);
        if (exactHit != null) { return exactHit; }

        Entity bestCone = findBestConeTarget(candidates, level, player, from, lookDir);

        Entity sticky = retainStickyTarget(candidates, level, player, from, lookDir, bestCone);
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
     * @return the previous target if it should be retained, or null
     */
    private static @Nullable Entity retainStickyTarget(
            List<Entity> candidates, Level level, Player player,
            Vec3 from, Vec3 lookDir, @Nullable Entity bestCone) {
        if (targetedEntity == null || targetedEntity == bestCone) { return null; }
        if (!targetedEntity.isAlive() || !candidates.contains(targetedEntity)) { return null; }
        if (isInStickyCone(level, player, from, lookDir)) { return targetedEntity; }
        return null;
    }

    /**
     * Returns true if the previous target is within range, inside the sticky cone, and visible.
     *
     * @param level   the current level
     * @param player  the aiming player
     * @param from    ray start
     * @param lookDir normalized look direction
     * @return true if the previous target should be retained
     */
    private static boolean isInStickyCone(Level level, Player player, Vec3 from, Vec3 lookDir) {
        Vec3 toPrev = targetedEntity.getBoundingBox().getCenter().subtract(from);
        double prevDist = toPrev.length();
        if (prevDist <= CENTER_HALF || prevDist > MAX_RANGE) { return false; }
        double prevCos = lookDir.dot(toPrev.normalize());
        return prevCos > STICKY_COS
                && hasLineOfSight(level, player, from, targetedEntity);
    }

    /**
     * Renders a glowing animated dashed arc from the glove hand to the target,
     * previewing the throw trajectory. Orchestrator: delegates arc sampling to
     * {@link ThrowArc} and emits dashed line segments with multi-pass bloom.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param player       the local player
     * @param end          the target endpoint position
     * @param gooType      the goo type for color tinting
     * @param partialTick  the partial tick for animation
     * @param grannyArc    if true, uses the boosted granny-arc peak height
     */
    private static void renderTargetArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, Vec3 end,
            GooType gooType, float partialTick, boolean grannyArc) {
        Vec3 start = getGloveHandPosition(player, camera);
        Vec3[] points = sampleArcPoints(start, end, grannyArc);
        float dashOffset = computeDashOffset(partialTick);
        Minecraft mc = Minecraft.getInstance();
        emitDashedGlow(poseStack, bufferSource, camera, points,
                points.length - 1, gooType.getColor(), dashOffset,
                mc.getWindow().getAppropriateLineWidth());
    }

    /**
     * Samples the throw arc polyline from start to end.
     *
     * @param start     arc origin (hand position)
     * @param end       arc destination (target center)
     * @param grannyArc true for boosted granny-arc peak height
     * @return sampled polyline points
     */
    private static Vec3[] sampleArcPoints(Vec3 start, Vec3 end, boolean grannyArc) {
        double distance = start.distanceTo(end);
        double travelTicks = ThrowArc.travelTicks(distance);
        double peak = computeArcPeak(travelTicks, grannyArc);
        int segments = Mth.clamp(
                (int) (distance / SAMPLE_SPACING),
                MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS);
        return ThrowArc.sampleArc(start, end, peak, segments);
    }

    /**
     * Selects the arc peak height based on whether this is a granny arc.
     *
     * @param travelTicks estimated travel time in ticks
     * @param grannyArc   true for boosted granny-arc peak
     * @return the arc peak height
     */
    private static double computeArcPeak(double travelTicks, boolean grannyArc) {
        return grannyArc
                ? ThrowArc.grannyPeak(travelTicks)
                : ThrowArc.basePeak(travelTicks);
    }

    /**
     * Computes the scrolling dash offset based on game time.
     *
     * @param partialTick the partial tick for smooth animation
     * @return dash offset in world units
     */
    private static float computeDashOffset(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level.getGameTime() + partialTick)
                / TICKS_PER_SECOND * SCROLL_SPEED;
    }

    /**
     * Emits multi-pass glowing dashed lines for the sampled arc polyline.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param points the sampled arc polyline points
     * @param segments the number of arc segments
     * @param rgb the RGB color value
     * @param dashOffset the dash scroll offset
     * @param baseWidth the base line width
     */
    private static void emitDashedGlow(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3[] points, int segments,
            int rgb, float dashOffset, float baseWidth) {
        Vec3 cam = camera.position();
        VertexConsumer line = bufferSource.getBuffer(GooRenderTypes.LINES_GLOW);
        for (int pass = ARC_GLOW_PASSES - 1; pass >= 0; pass--) {
            emitDashedPass(poseStack, line, cam, points, segments,
                    dashOffset, computeGlowPassColor(rgb, pass),
                    baseWidth * (1.0f + pass * ARC_GLOW_WIDTH_STEP));
        }
        bufferSource.endLastBatch();
    }

    /**
     * Computes the ARGB color for a bloom pass with exponential alpha decay.
     *
     * @param rgb  the base RGB color
     * @param pass the bloom pass index (0 = core, higher = dimmer)
     * @return the ARGB color with decayed alpha
     */
    private static int computeGlowPassColor(int rgb, int pass) {
        float alphaScale = (float) Math.pow(ARC_GLOW_ALPHA_DECAY, pass);
        int alpha = Mth.clamp((int) (ARC_ALPHA * alphaScale), 0, MAX_ALPHA);
        return colorWithAlpha(rgb, alpha);
    }

    /**
     * Emits one pass of dashed line segments for the arc polyline.
     *
     * @param poseStack the pose stack for rendering
     * @param line the line
     * @param cam the cam
     * @param points the sampled arc polyline points
     * @param segments the number of arc segments
     * @param dashOffset the dash scroll offset
     * @param color the ARGB color value
     * @param width the width
     */
    private static void emitDashedPass(
            PoseStack poseStack, VertexConsumer line, Vec3 cam,
            Vec3[] points, int segments,
            float dashOffset, int color, float width) {
        float arcLen = 0f;
        for (int i = 0; i < segments; i++) {
            arcLen = emitDashSegment(poseStack, line, cam,
                    points[i], points[i + 1],
                    arcLen, dashOffset, color, width);
        }
    }

    /**
     * Emits a single dash segment if it falls in the "on" phase of the pattern.
     *
     * @param poseStack  the pose stack
     * @param line       the vertex consumer
     * @param cam        camera position
     * @param a          segment start
     * @param b          segment end
     * @param arcLen     accumulated arc length before this segment
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color
     * @param width      the line width
     * @return the updated accumulated arc length
     */
    private static float emitDashSegment(
            PoseStack poseStack, VertexConsumer line, Vec3 cam,
            Vec3 a, Vec3 b, float arcLen,
            float dashOffset, int color, float width) {
        float segLen = (float) a.distanceTo(b);
        if (isDashOn(arcLen + segLen * DASH_MID, dashOffset)) {
            SlotOutlineRenderer.emitEdge(poseStack, line,
                    a.x - cam.x, a.y - cam.y, a.z - cam.z,
                    b.x - cam.x, b.y - cam.y, b.z - cam.z,
                    color, width);
        }
        return arcLen + segLen;
    }

    /**
     * Returns true if the dash at the given arc-length is in the "on" phase.
     *
     * @param midArcLen the midArcLen
     * @param dashOffset the dash scroll offset
     * @return true if dashOn
     */
    private static boolean isDashOn(float midArcLen, float dashOffset) {
        float phase = (midArcLen - dashOffset) % DASH_CYCLE;
        if (phase < 0) { phase += DASH_CYCLE; }
        return phase < DASH_ON;
    }

    // --- Rendering: voxel shape highlight ---

    /**
     * Renders goo-colored translucent fill and wireframe edges tracing the
     * block's voxel outline shape. Stairs, slabs, fences, etc. highlight
     * their actual geometry instead of a flat face quad.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param pos the block position
     * @param face the block face direction
     * @param type the goo type
     */
    private static void renderBlockFace(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, Direction face, GooType type) {
        Minecraft mc = Minecraft.getInstance();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) { return; }
        Vec3 offset = cameraOffset(pos, camera);
        int rgb = type.getColor();
        emitFillBoxes(poseStack, bufferSource, shape, offset.x, offset.y, offset.z, rgb);
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset.x, offset.y, offset.z, rgb);
    }

    /**
     * Computes the camera-relative offset for a block position.
     *
     * @param pos    the block position
     * @param camera the render camera
     * @return the camera-relative offset vector
     */
    private static Vec3 cameraOffset(BlockPos pos, Camera camera) {
        return new Vec3(
                pos.getX() - camera.position().x,
                pos.getY() - camera.position().y,
                pos.getZ() - camera.position().z);
    }

    /**
     * Emits translucent fill quads for every AABB in the voxel shape.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitFillBoxes(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            VoxelShape shape, double ox, double oy, double oz, int rgb) {
        int fillColor = colorWithAlpha(rgb, FACE_ALPHA);
        VertexConsumer quad = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            emitOffsetBox(pose, quad, ox, oy, oz, x0, y0, z0, x1, y1, z1, fillColor);
        });
        bufferSource.endLastBatch();
    }

    /**
     * Creates an ARGB color from an RGB value and alpha channel.
     *
     * @param rgb   the RGB color
     * @param alpha the alpha value (0-255)
     * @return the ARGB color
     */
    private static int colorWithAlpha(int rgb, int alpha) {
        return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Computes camera-relative face-offset coords and emits a box.
     *
     * @param pose     the pose matrix entry
     * @param consumer the vertex consumer
     * @param ox       camera-relative X offset
     * @param oy       camera-relative Y offset
     * @param oz       camera-relative Z offset
     * @param x0       shape-local min X
     * @param y0       shape-local min Y
     * @param z0       shape-local min Z
     * @param x1       shape-local max X
     * @param y1       shape-local max Y
     * @param z1       shape-local max Z
     * @param color    the ARGB color
     */
    private static void emitOffsetBox(
            PoseStack.Pose pose, VertexConsumer consumer,
            double ox, double oy, double oz,
            double x0, double y0, double z0,
            double x1, double y1, double z1, int color) {
        emitBox(pose, consumer,
                offsetMin(ox, x0), offsetMin(oy, y0), offsetMin(oz, z0),
                offsetMax(ox, x1), offsetMax(oy, y1), offsetMax(oz, z1),
                color);
    }

    /**
     * Computes a camera-relative coordinate with inward face offset for the minimum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMin(double camOffset, double coord) {
        return (float) (camOffset + coord - FACE_OFFSET);
    }

    /**
     * Computes a camera-relative coordinate with outward face offset for the maximum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMax(double camOffset, double coord) {
        return (float) (camOffset + coord + FACE_OFFSET);
    }

    /**
     * Emits wireframe edges along the shape outline.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param mc           the Minecraft instance
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitWireframeEdges(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Minecraft mc, VoxelShape shape,
            double ox, double oy, double oz, int rgb) {
        int wireColor = colorWithAlpha(rgb, WIRE_ALPHA);
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        VertexConsumer line = bufferSource.getBuffer(RenderTypes.lines());
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
            emitEdgeOffset(poseStack, line, ox, oy, oz,
                    x0, y0, z0, x1, y1, z1, wireColor, lineWidth));
        bufferSource.endLastBatch();
    }

    /**
     * Emits a single wireframe edge with camera-relative offsets applied.
     *
     * @param poseStack the pose stack
     * @param line      the vertex consumer
     * @param ox        camera-relative X offset
     * @param oy        camera-relative Y offset
     * @param oz        camera-relative Z offset
     * @param x0        edge start X
     * @param y0        edge start Y
     * @param z0        edge start Z
     * @param x1        edge end X
     * @param y1        edge end Y
     * @param z1        edge end Z
     * @param color     the ARGB color
     * @param width     the line width
     */
    private static void emitEdgeOffset(
            PoseStack poseStack, VertexConsumer line,
            double ox, double oy, double oz,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            int color, float width) {
        SlotOutlineRenderer.emitEdge(poseStack, line,
                ox + x0, oy + y0, oz + z0,
                ox + x1, oy + y1, oz + z1,
                color, width);
    }

    /**
     * Emits six quads (one per face) for an axis-aligned box.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param color the ARGB color value
     */
    private static void emitBox(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        emitFaceUp(pose, c, x0, y0, z0, x1, y1, z1, color);
        emitFaceDown(pose, c, x0, y0, z0, x1, y1, z1, color);
        emitFaceNorth(pose, c, x0, y0, z0, x1, y1, z1, color);
        emitFaceSouth(pose, c, x0, y0, z0, x1, y1, z1, color);
        emitFaceWest(pose, c, x0, y0, z0, x1, y1, z1, color);
        emitFaceEast(pose, c, x0, y0, z0, x1, y1, z1, color);
    }

    /**
     * Emits the +Y face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceUp(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
    }

    /**
     * Emits the -Y face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceDown(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z1).setColor(color);
    }

    /**
     * Emits the -Z face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceNorth(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
    }

    /**
     * Emits the +Z face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceSouth(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x1, y0, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y0, z1).setColor(color);
    }

    /**
     * Emits the -X face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceWest(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
    }

    /**
     * Emits the +X face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceEast(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        c.addVertex(pose, x1, y0, z0).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x1, y0, z1).setColor(color);
    }

    // --- Hand position ---

    /**
     * Returns the world-space arc origin. Prefers the exact blob center
     * captured during item rendering (pixel-accurate). Falls back to a
     * camera-basis approximation when the blob wasn't rendered this frame.
     *
     * @param player the interacting player
     * @param camera the render camera
     * @return the gloveHandPosition
     */
    public static Vec3 getGloveHandPosition(Player player, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.level != null ? mc.level.getGameTime() : NO_FRAME;
        Vec3 captured = GloveSpecialRenderer.getBlobCenterCamRel(frame);
        if (captured != null) {
            return camera.position().add(captured);
        }
        return computeCameraFallback(player, camera);
    }

    /**
     * Computes a camera-basis hand position when the blob wasn't rendered this frame.
     *
     * @param player the local player
     * @param camera the render camera
     * @return the fallback hand position in world space
     */
    private static Vec3 computeCameraFallback(Player player, Camera camera) {
        float side = ThrowArc.gloveSide(player.getMainHandItem(), player.getMainArm());
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        Vec3 offset = ThrowArc.handOffset(
                new Vec3(-left.x(), -left.y(), -left.z()),
                new Vec3(up.x(), up.y(), up.z()),
                side, player.getScale());
        return camera.position().add(offset);
    }

    // --- Glove detection ---

    /**
     * Checks both hands for a goo glove with a selected type. Main hand priority.
     * Returns null if the player has no goo of that type (suppresses visuals).
     *
     * @param player the interacting player
     * @return the matching result, or null if not found
     */
    private static @Nullable GooType findSelectedGooType(Player player) {
        GooType type = tryGloveInHand(player.getMainHandItem());
        if (type != null) { return GloveUseTracker.isSelectedTypeAvailable() ? type : null; }
        type = tryGloveInHand(player.getOffhandItem());
        if (type != null) { return GloveUseTracker.isSelectedTypeAvailable() ? type : null; }
        return null;
    }

    /**
     * Returns the selected type if the stack is a glove with a selection.
     *
     * @param stack the item stack
     * @return the result
     */
    private static @Nullable GooType tryGloveInHand(ItemStack stack) {
        if (stack.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(stack);
        }
        return null;
    }
}
