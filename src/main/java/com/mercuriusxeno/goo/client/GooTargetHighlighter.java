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
import net.minecraft.world.level.block.state.BlockState;
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
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

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

    private GooTargetHighlighter() {}

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

    // --- Entity outline state ---

    /** The entity currently targeted by the glove, or null. Updated each tick. */
    private static @Nullable Entity targetedEntity;

    /** Opaque ARGB outline color for the targeted entity, or 0 if none. */
    private static int targetOutlineColor;

    /**
     * Client tick: resolves aim target and stores entity + goo color for the
     * render state modifier to pick up during entity rendering.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            targetedEntity = null;
            targetOutlineColor = 0;
            return;
        }

        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) {
            targetedEntity = null;
            targetOutlineColor = 0;
            return;
        }

        TargetResult target = resolveTarget(mc.player, 1.0f);
        targetedEntity = (target instanceof TargetResult.EntityTarget et) ? et.entity() : null;
        targetOutlineColor = (targetedEntity != null)
                ? ARGB.opaque(selectedType.getColor()) : 0;
    }

    /**
     * Render state modifier callback: sets outlineColor on entities targeted
     * by the glove so vanilla renders the spectral glow outline in the goo color.
     * Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
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
        Vec3 lookVec = player.getViewVector(partialTick);
        Vec3 reach = eyePos.add(lookVec.scale(MAX_RANGE));

        if (!player.isShiftKeyDown()) {
            Entity entityHit = findClosestEntity(player, eyePos, reach);
            if (entityHit != null) {
                return TargetResult.entity(entityHit);
            }
        }

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Direction face = blockHit.getDirection();
            if (face.getAxis() != Direction.Axis.Y
                    && isUpperEdge(player.level(), blockHit)) {
                return TargetResult.grannyArc(blockHit.getBlockPos());
            }
            return TargetResult.block(blockHit.getBlockPos(), face);
        }

        return TargetResult.NONE;
    }

    /**
     * Returns true if the hit landed in the upper portion of the block's
     * voxel shape, measured against the shape's actual Y extent so slabs,
     * stairs, etc. use their real geometry, not a full cube.
     */
    private static boolean isUpperEdge(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) return false;
        AABB bounds = shape.bounds();
        double range = bounds.maxY - bounds.minY;
        if (range <= 0) return false;
        double hitY = hit.getLocation().y - pos.getY();
        double relative = (hitY - bounds.minY) / range;
        return relative >= GRANNY_ARC_THRESHOLD;
    }

    // --- Event handlers ---

    /**
     * Renders goo-colored target visuals: dashed arc to entity targets,
     * translucent face highlight for block targets. Entity spectral outlines
     * are handled separately by the render state modifier.
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Arc is first-person only - hide in third person and from other players
        if (!mc.options.getCameraType().isFirstPerson()) return;

        GooType selectedType = findSelectedGooType(mc.player);
        if (selectedType == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TargetResult target = resolveTarget(mc.player, partialTick);

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        if (target instanceof TargetResult.EntityTarget et) {
            Vec3 end = et.entity().getBoundingBox().getCenter();
            renderTargetArc(poseStack, bufferSource, camera,
                    mc.player, end, selectedType, partialTick, false);
        } else if (target instanceof TargetResult.BlockTarget bt) {
            Vec3 end = Vec3.atCenterOf(bt.pos())
                    .add(bt.face().getUnitVec3().scale(0.5));
            renderTargetArc(poseStack, bufferSource, camera,
                    mc.player, end, selectedType, partialTick, bt.grannyArc());
            renderBlockFace(poseStack, bufferSource, camera, bt.pos(),
                    bt.face(), selectedType);
        }
    }

    // --- Line-of-sight ---

    /** Inset from AABB face to avoid sampling right at block boundaries. */
    private static final double LOS_INSET = 0.05;

    /**
     * Checks whether the player has line-of-sight to an entity by casting rays
     * to multiple sample points on the entity's AABB. Returns true if ANY ray
     * reaches without hitting a block, so partial occlusion (peeking around a
     * corner) still counts as visible.
     *
     * Uses OUTLINE clip mode - glass, fences, bars all block LOS, matching
     * what a thrown blob would physically collide with.
     */
    private static boolean hasLineOfSight(Level level, Player player, Vec3 eyePos, Entity target) {
        AABB box = target.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;

        // 7 sample points: center + 6 face centers (inset to avoid edge issues)
        Vec3[] samples = {
            new Vec3(cx, cy, cz),
            new Vec3(cx, box.maxY - LOS_INSET, cz),  // top
            new Vec3(cx, box.minY + LOS_INSET, cz),  // bottom
            new Vec3(box.minX + LOS_INSET, cy, cz),  // west
            new Vec3(box.maxX - LOS_INSET, cy, cz),  // east
            new Vec3(cx, cy, box.minZ + LOS_INSET),   // north
            new Vec3(cx, cy, box.maxZ - LOS_INSET),   // south
        };

        for (Vec3 sample : samples) {
            BlockHitResult hit = level.clip(new ClipContext(
                    eyePos, sample, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true; // clear sightline - no need to check remaining samples
            }
        }
        return false;
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
     */
    private static @Nullable Entity findClosestEntity(Player player, Vec3 from, Vec3 to) {
        Vec3 lookDir = to.subtract(from).normalize();
        Level level = player.level();

        // Inflate search box enough to cover the cone at max range
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        AABB searchBox = player.getBoundingBox()
                .expandTowards(to.subtract(from))
                .inflate(coneRadius + 1.0);
        List<Entity> candidates = level.getEntities(
                player, searchBox, e -> e instanceof LivingEntity && e.isPickable());

        // Pass 1: exact raytrace - AABB clip then LOS check
        Entity exactHit = null;
        double exactDist = Double.MAX_VALUE;
        for (Entity entity : candidates) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> clip = entityBox.clip(from, to);
            if (clip.isPresent() && hasLineOfSight(level, player, from, entity)) {
                double dist = from.distanceToSqr(clip.get());
                if (dist < exactDist) {
                    exactDist = dist;
                    exactHit = entity;
                }
            }
        }
        if (exactHit != null) return exactHit;

        // Pass 2: aim-assist cone - angle check then LOS check
        Entity bestCone = null;
        double bestConeAngle = AIM_ASSIST_COS; // must beat this (higher cos = smaller angle)

        for (Entity entity : candidates) {
            Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(from);
            double dist = toEntity.length();
            if (dist < 0.5 || dist > MAX_RANGE) continue;
            double cos = lookDir.dot(toEntity.normalize());
            if (cos > bestConeAngle && hasLineOfSight(level, player, from, entity)) {
                bestConeAngle = cos;
                bestCone = entity;
            }
        }

        // Sticky retention: keep previous target if it's still in the wider cone and visible
        if (targetedEntity != null && targetedEntity != bestCone
                && targetedEntity.isAlive() && candidates.contains(targetedEntity)) {
            Vec3 toPrev = targetedEntity.getBoundingBox().getCenter().subtract(from);
            double prevDist = toPrev.length();
            if (prevDist > 0.5 && prevDist <= MAX_RANGE) {
                double prevCos = lookDir.dot(toPrev.normalize());
                if (prevCos > STICKY_COS && hasLineOfSight(level, player, from, targetedEntity)) {
                    return targetedEntity;
                }
            }
        }

        return bestCone;
    }

    // --- Rendering: dashed arc to entity target ---

    /** Ticks-per-second divisor for converting game time to seconds. */
    private static final float TICKS_PER_SECOND = 20.0f;

    /**
     * Renders a glowing animated dashed arc from the glove hand to the target,
     * previewing the throw trajectory. Orchestrator: delegates arc sampling to
     * {@link ThrowArc} and emits dashed line segments with multi-pass bloom.
     *
     * @param grannyArc if true, uses the boosted granny-arc peak height
     */
    private static void renderTargetArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Player player, Vec3 end,
            GooType gooType, float partialTick, boolean grannyArc) {

        Vec3 start = getGloveHandPosition(player, camera);
        double distance = start.distanceTo(end);
        double travelTicks = ThrowArc.travelTicks(distance);
        double peak = grannyArc
                ? ThrowArc.grannyPeak(travelTicks)
                : ThrowArc.basePeak(travelTicks);
        int segments = Mth.clamp((int) (distance / SAMPLE_SPACING), 8, 128);
        Vec3[] points = ThrowArc.sampleArc(start, end, peak, segments);

        Minecraft mc = Minecraft.getInstance();
        float dashOffset = (mc.level.getGameTime() + partialTick)
                / TICKS_PER_SECOND * SCROLL_SPEED;

        emitDashedGlow(poseStack, bufferSource, camera, points,
                segments, gooType.getColor(), dashOffset,
                mc.getWindow().getAppropriateLineWidth());
    }

    /** Emits multi-pass glowing dashed lines for the sampled arc polyline. */
    private static void emitDashedGlow(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3[] points, int segments,
            int rgb, float dashOffset, float baseWidth) {

        Vec3 cam = camera.position();
        VertexConsumer line = bufferSource.getBuffer(RenderTypes.lines());

        for (int pass = ARC_GLOW_PASSES - 1; pass >= 0; pass--) {
            float alphaScale = (float) Math.pow(ARC_GLOW_ALPHA_DECAY, pass);
            int alpha = Mth.clamp((int) (ARC_ALPHA * alphaScale), 0, 255);
            int color = ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
            float width = baseWidth * (1.0f + pass * ARC_GLOW_WIDTH_STEP);
            emitDashedPass(poseStack, line, cam, points, segments,
                    dashOffset, color, width);
        }
        bufferSource.endLastBatch();
    }

    /** Emits one pass of dashed line segments for the arc polyline. */
    private static void emitDashedPass(
            PoseStack poseStack, VertexConsumer line, Vec3 cam,
            Vec3[] points, int segments,
            float dashOffset, int color, float width) {

        float arcLen = 0f;
        for (int i = 0; i < segments; i++) {
            Vec3 a = points[i];
            Vec3 b = points[i + 1];
            float segLen = (float) a.distanceTo(b);
            if (isDashOn(arcLen + segLen * 0.5f, dashOffset)) {
                SlotOutlineRenderer.emitEdge(poseStack, line,
                        a.x - cam.x, a.y - cam.y, a.z - cam.z,
                        b.x - cam.x, b.y - cam.y, b.z - cam.z,
                        color, width);
            }
            arcLen += segLen;
        }
    }

    /** Returns true if the dash at the given arc-length is in the "on" phase. */
    private static boolean isDashOn(float midArcLen, float dashOffset) {
        float phase = (midArcLen - dashOffset) % DASH_CYCLE;
        if (phase < 0) phase += DASH_CYCLE;
        return phase < DASH_ON;
    }

    // --- Rendering: voxel shape highlight ---

    /**
     * Renders goo-colored translucent fill and wireframe edges tracing the
     * block's voxel outline shape. Stairs, slabs, fences, etc. highlight
     * their actual geometry instead of a flat face quad.
     */
    private static void renderBlockFace(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, Direction face, GooType type) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        if (shape.isEmpty()) return;

        int rgb = type.getColor();
        int fillColor = ARGB.color(FACE_ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        int wireColor = ARGB.color(WIRE_ALPHA, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));

        double ox = pos.getX() - camera.position().x;
        double oy = pos.getY() - camera.position().y;
        double oz = pos.getZ() - camera.position().z;

        // Translucent box fill for each AABB in the shape
        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            float fx0 = (float) (ox + x0 - FACE_OFFSET);
            float fy0 = (float) (oy + y0 - FACE_OFFSET);
            float fz0 = (float) (oz + z0 - FACE_OFFSET);
            float fx1 = (float) (ox + x1 + FACE_OFFSET);
            float fy1 = (float) (oy + y1 + FACE_OFFSET);
            float fz1 = (float) (oz + z1 + FACE_OFFSET);
            emitBox(pose, quadConsumer, fx0, fy0, fz0, fx1, fy1, fz1, fillColor);
        });
        bufferSource.endLastBatch();

        // Wireframe edges along the shape outline
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.lines());
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
            SlotOutlineRenderer.emitEdge(poseStack, lineConsumer,
                ox + x0, oy + y0, oz + z0,
                ox + x1, oy + y1, oz + z1,
                wireColor, lineWidth);
        });
        bufferSource.endLastBatch();
    }

    /** Emits six quads (one per face) for an axis-aligned box. */
    private static void emitBox(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        // Up (+Y)
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        // Down (-Y)
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z1).setColor(color);
        // North (-Z)
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
        // South (+Z)
        c.addVertex(pose, x1, y0, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y0, z1).setColor(color);
        // West (-X)
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
        // East (+X)
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
     */
    public static Vec3 getGloveHandPosition(Player player, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.level != null ? mc.level.getGameTime() : -1;
        Vec3 captured = GloveSpecialRenderer.getBlobCenterCamRel(frame);
        if (captured != null) {
            return camera.position().add(captured);
        }
        // Fallback: camera-basis offset via ThrowArc pure math
        float side = ThrowArc.gloveSide(player.getMainHandItem(), player.getMainArm());
        org.joml.Vector3fc left = camera.leftVector();
        org.joml.Vector3fc up = camera.upVector();
        Vec3 offset = ThrowArc.handOffset(
                -left.x(), -left.y(), -left.z(),
                up.x(), up.y(), up.z(),
                side, player.getScale());
        return camera.position().add(offset);
    }

    // --- Glove detection ---

    /**
     * Checks both hands for a goo glove with a selected type. Main hand priority.
     * Returns null if the player has no goo of that type (suppresses visuals).
     */
    private static @Nullable GooType findSelectedGooType(Player player) {
        GooType type = tryGloveInHand(player.getMainHandItem());
        if (type != null) return GloveUseTracker.isSelectedTypeAvailable() ? type : null;
        type = tryGloveInHand(player.getOffhandItem());
        if (type != null) return GloveUseTracker.isSelectedTypeAvailable() ? type : null;
        return null;
    }

    /** Returns the selected type if the stack is a glove with a selection. */
    private static @Nullable GooType tryGloveInHand(ItemStack stack) {
        if (stack.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(stack);
        }
        return null;
    }
}
