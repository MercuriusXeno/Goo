package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.world.CrystalBehavior;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Random;

/**
 * Renders the crystal shard cloud as scattered glass splinters floating
 * in air within the cloud volume. Each sliver is a thin elongated quad
 * at a random position and orientation. Some tumble slowly, most are still.
 */
public final class CrystalCloudVisual {

    private static final float BLOCK_CENTER = 0.5f;
    private static final float CLOUD_RADIUS = (float) CrystalBehavior.CLOUD_RADIUS;

    /**
     * Total slivers at full charge.
     */
    private static final int MAX_SLIVERS = 256;
    /**
     * Half-length of each sliver quad along its long axis.
     */
    private static final float SLIVER_HALF_LENGTH = 0.08f;
    /**
     * Full opacity alpha for ARGB packing.
     */
    private static final int FULL_ALPHA = 0xFF;
    /**
     * Max value for packing a float [0-1] into a color byte.
     */
    private static final float BYTE_SCALE = 255f;
    /**
     * Seed for deterministic sliver placement.
     */
    private static final long SLIVER_SEED = 0xC5745_5A4DL;

    /**
     * Probability that a sliver has zero spin.
     */
    private static final float NO_SPIN_CHANCE = 0.3f;
    /**
     * Maximum spin speed in radians per tick for spinning shards.
     */
    private static final float MAX_SPIN_SPEED = 0.06f;
    /**
     * Minimum spin speed when spinning.
     */
    private static final float MIN_SPIN_SPEED = 0.008f;

    /**
     * Pre-computed per-sliver data stride. Layout:
     * [cx, cy, cz, axisX, axisY, axisZ, perpX, perpY, perpZ, halfLen,
     * spinAxisX, spinAxisY, spinAxisZ, spinSpeed]
     */
    private static final int SLIVER_STRIDE = 17;
    private static final int OFF_CY = 1;
    private static final int OFF_CZ = 2;
    private static final int OFF_AX = 3;
    private static final int OFF_AY = 4;
    private static final int OFF_AZ = 5;
    private static final int OFF_PX = 6;
    private static final int OFF_PY = 7;
    private static final int OFF_PZ = 8;
    private static final int OFF_HALF_LEN = 9;
    private static final int OFF_SPIN_AX = 10;
    private static final int OFF_SPIN_AY = 11;
    private static final int OFF_SPIN_AZ = 12;
    private static final int OFF_SPIN_SPEED = 13;
    /**
     * 0 = single spike (triangle), 1 = diamond, 2 = asymmetric diamond.
     */
    private static final int OFF_SHAPE = 14;
    /**
     * Width ratio: how wide the perp arm is relative to half-length.
     */
    private static final int OFF_WIDTH_RATIO = 15;
    /**
     * Pyramid depth: how far the apex protrudes along the face normal.
     */
    private static final int OFF_DEPTH = 16;

    private static final float SHAPE_SINGLE_SPIKE = 0f;
    private static final float SHAPE_DIAMOND = 1f;
    private static final float SHAPE_ASYMMETRIC = 2f;
    /**
     * Chance of single spike vs diamond shapes.
     */
    private static final float SINGLE_SPIKE_CHANCE = 0.2f;
    /**
     * Chance of symmetric diamond (of the non-spike remainder).
     */
    private static final float SYMMETRIC_CHANCE = 0.15f;
    /**
     * Min width ratio for perpendicular arm.
     */
    private static final float MIN_WIDTH_RATIO = 0.02f;
    /**
     * Max width ratio for perpendicular arm.
     */
    private static final float MAX_WIDTH_RATIO = 0.10f;
    /**
     * Min pyramid depth as fraction of half-length.
     */
    private static final float MIN_DEPTH_RATIO = 0.08f;
    /**
     * Max pyramid depth as fraction of half-length.
     */
    private static final float MAX_DEPTH_RATIO = 0.25f;
    /**
     * Long arm multiplier for asymmetric diamonds.
     */
    private static final float ASYM_LONG_FACTOR = 1.0f;
    /**
     * Short arm multiplier for asymmetric diamonds - very short to create sliver shapes.
     */
    private static final float ASYM_SHORT_FACTOR = 0.1f;
    /**
     * Maps [-1,1] random floats into [-radius, radius] range.
     */
    private static final float RNG_RANGE = 2f;
    /**
     * Minimum half-length scale factor for size variation.
     */
    private static final float MIN_LEN_SCALE = 0.5f;
    /**
     * Range of half-length scale variation added to min.
     */
    private static final float LEN_SCALE_RANGE = 1.0f;
    /**
     * Threshold for near-parallel detection in perpendicular vector construction.
     */
    private static final float PARALLEL_THRESHOLD = 0.9f;
    /**
     * Minimum vector length to avoid normalizing near-zero vectors.
     */
    private static final float NORMALIZE_EPSILON = 0.001f;
    /**
     * Z index in a 3-element vector array.
     */
    private static final int VEC_Z = 2;
    /**
     * Offset of perp X in the resolved axes array {ax,ay,az,px,py,pz}.
     */
    private static final int AXES_PX = 3;
    /**
     * Offset of perp Y in the resolved axes array.
     */
    private static final int AXES_PY = 4;
    /**
     * Offset of perp Z in the resolved axes array.
     */
    private static final int AXES_PZ = 5;

    /**
     * How far to raycast for reflected block color (in blocks).
     */
    private static final double RAYCAST_RANGE = 16.0;
    /**
     * Brightness multiplier on reflected block colors.
     */
    private static final float REFLECT_BRIGHTNESS = 1.3f;
    /**
     * Base alpha for shards.
     */
    private static final float BASE_ALPHA = 0.8f;
    /**
     * Fallback color when raycast misses (sky blue).
     */
    private static final int SKY_COLOR = 0x87CEEB;
    /**
     * Minimum density floor for alpha calculation.
     */
    private static final float MIN_DENSITY_FLOOR = 0.2f;
    /**
     * Reflection formula coefficient (v - 2*(v.n)*n).
     */
    private static final double REFLECT_COEFF = 2.0;

    private static final float[] SLIVER_DATA = buildSliverData();

    private CrystalCloudVisual() {
    }

    /**
     * Populates {@code state} with crystal-cloud fields from the BE.
     *
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity be,
                               ChainMarkerRenderState state) {
        if (be.getBehavior() instanceof CrystalBehavior crystal
                && (crystal.getDensity() > 0f || crystal.isAnimating())) {
            state.crystalActive = true;
            state.crystalDensity = crystal.getDensity();
            state.crystalRadiusFraction = crystal.getRadiusFraction();
            long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
            state.crystalAnimationTime = (float) gameTime;
        } else {
            state.crystalActive = false;
            state.crystalDensity = 0f;
            state.crystalRadiusFraction = 0f;
            state.crystalAnimationTime = 0f;
        }
    }

    /**
     * Submits crystal shard splinters for rendering.
     *
     * @param state         the chain marker render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state,
                              PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float radiusFrac = state.crystalRadiusFraction;
        if (radiusFrac <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Level level = mc.level;
        Vec3 camPos = mc.player.getEyePosition(state.partialTick);
        BlockPos bePos = state.blockPos;
        float density = state.crystalDensity;
        int alpha = (int) (BASE_ALPHA * Math.max(density, MIN_DENSITY_FLOOR) * radiusFrac * BYTE_SCALE);
        int visibleCount = Math.max(1, (int) (MAX_SLIVERS * Math.max(density, radiusFrac)));
        float time = state.crystalAnimationTime;
        float radius = CLOUD_RADIUS * radiusFrac;

        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.CRYSTAL_SHARD_TYPE,
                (pose, c) -> {
                    for (int i = 0; i < visibleCount; i++) {
                        emitSliver(pose, c, i, alpha, time, radius, level, camPos, bePos);
                    }
                });
    }

    /**
     * Emits one sliver quad, applying spin rotation if the shard has one.
     *
     * @param pose   the current pose entry
     * @param c      the vertex consumer
     * @param index  the sliver index
     * @param time   the game time in ticks for spin animation
     * @param radius the current cloud radius (animated)
     * @param alpha  pre-computed vertex alpha [0-255]
     * @param level  the client level for raycasting
     * @param camPos the camera eye position
     * @param bePos  the block entity position
     */
    private static void emitSliver(PoseStack.Pose pose, VertexConsumer c,
                                   int index, int alpha, float time, float radius,
                                   Level level, Vec3 camPos, BlockPos bePos) {
        int off = index * SLIVER_STRIDE;
        float cx = BLOCK_CENTER + SLIVER_DATA[off] * radius;
        float cy = BLOCK_CENTER + SLIVER_DATA[off + OFF_CY] * radius;
        float cz = BLOCK_CENTER + SLIVER_DATA[off + OFF_CZ] * radius;
        float halfLen = SLIVER_DATA[off + OFF_HALF_LEN];
        float shape = SLIVER_DATA[off + OFF_SHAPE];
        float widthRatio = SLIVER_DATA[off + OFF_WIDTH_RATIO];
        float depth = SLIVER_DATA[off + OFF_DEPTH] * halfLen;
        float[] axes = resolveAxes(off, time);
        float hw = halfLen * widthRatio;
        // World position of the shard center for raycasting
        Vec3 worldCenter = new Vec3(
                bePos.getX() + cx, bePos.getY() + cy, bePos.getZ() + cz);
        emitShape(pose, c, cx, cy, cz,
                axes[0], axes[1], axes[VEC_Z],
                axes[AXES_PX], axes[AXES_PY], axes[AXES_PZ],
                halfLen, hw, depth, shape, alpha, level, camPos, worldCenter);
    }

    /**
     * Returns {ax, ay, az, px, py, pz} after applying spin rotation if any.
     *
     * @param off  the sliver data offset
     * @param time the game time for spin animation
     * @return the axis and perp vectors, possibly rotated
     */
    private static float[] resolveAxes(int off, float time) {
        float ax = SLIVER_DATA[off + OFF_AX];
        float ay = SLIVER_DATA[off + OFF_AY];
        float az = SLIVER_DATA[off + OFF_AZ];
        float px = SLIVER_DATA[off + OFF_PX];
        float py = SLIVER_DATA[off + OFF_PY];
        float pz = SLIVER_DATA[off + OFF_PZ];
        float spinSpeed = SLIVER_DATA[off + OFF_SPIN_SPEED];
        if (spinSpeed <= 0f) {
            return new float[]{ax, ay, az, px, py, pz};
        }
        float angle = time * spinSpeed;
        float sax = SLIVER_DATA[off + OFF_SPIN_AX];
        float say = SLIVER_DATA[off + OFF_SPIN_AY];
        float saz = SLIVER_DATA[off + OFF_SPIN_AZ];
        float[] ra = rodrigues(ax, ay, az, sax, say, saz, angle);
        float[] rp = rodrigues(px, py, pz, sax, say, saz, angle);
        return new float[]{ra[0], ra[1], ra[VEC_Z], rp[0], rp[1], rp[VEC_Z]};
    }

    /**
     * Emits a shard shape: single spike, diamond, or asymmetric diamond.
     * All shapes are emitted as quads (degenerate for triangles).
     *
     * @param pose        the pose entry
     * @param c           the vertex consumer
     * @param cx          center X
     * @param cy          center Y
     * @param cz          center Z
     * @param ax          long axis X
     * @param ay          long axis Y
     * @param az          long axis Z
     * @param px          perp axis X
     * @param py          perp axis Y
     * @param pz          perp axis Z
     * @param hl          half-length along the long axis
     * @param hw          half-width along the perp axis
     * @param shape       the shape type (0=spike, 1=diamond, 2=asymmetric)
     * @param depth       pyramid height along the normal
     * @param alpha       pre-computed vertex alpha [0-255]
     * @param level       the client level for raycasting
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     */
    private static void emitShape(PoseStack.Pose pose, VertexConsumer c,
                                  float cx, float cy, float cz,
                                  float ax, float ay, float az,
                                  float px, float py, float pz,
                                  float hl, float hw, float depth, float shape, int alpha,
                                  Level level, Vec3 camPos, Vec3 worldCenter) {
        // Normal = cross(axis, perp) - used for pyramid apex direction
        float nx = ay * pz - az * py;
        float ny = az * px - ax * pz;
        float nz = ax * py - ay * px;
        float apexX = cx + nx * depth;
        float apexY = cy + ny * depth;
        float apexZ = cz + nz * depth;

        if (shape < SHAPE_DIAMOND) {
            emitSpikePyramid(pose, c, cx, cy, cz, ax, ay, az, px, py, pz,
                    hl, hw, apexX, apexY, apexZ, alpha, level, camPos, worldCenter);
        } else if (shape < SHAPE_ASYMMETRIC) {
            emitDiamondPyramid(pose, c, cx, cy, cz, ax, ay, az, px, py, pz,
                    hl, hw, apexX, apexY, apexZ, alpha, level, camPos, worldCenter);
        } else {
            emitAsymPyramid(pose, c, cx, cy, cz, ax, ay, az, px, py, pz,
                    hl, hw, apexX, apexY, apexZ, alpha, level, camPos, worldCenter);
        }
    }

    /**
     * Spike pyramid: 3 base verts (tip, base-left, base-right) + apex. 3 faces.
     *
     * @param pose        the pose matrix entry
     * @param c           the vertex consumer
     * @param cx          center X
     * @param cy          center Y
     * @param cz          center Z
     * @param ax          long axis X
     * @param ay          long axis Y
     * @param az          long axis Z
     * @param px          perp axis X
     * @param py          perp axis Y
     * @param pz          perp axis Z
     * @param hl          half-length along long axis
     * @param hw          half-width along perp axis
     * @param apX         apex X
     * @param apY         apex Y
     * @param apZ         apex Z
     * @param alpha       pre-computed vertex alpha [0-255]
     * @param level       the client level for raycasting
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     */
    private static void emitSpikePyramid(PoseStack.Pose pose, VertexConsumer c,
                                         float cx, float cy, float cz,
                                         float ax, float ay, float az, float px, float py, float pz,
                                         float hl, float hw,
                                         float apX, float apY, float apZ, int alpha,
                                         Level level, Vec3 camPos, Vec3 worldCenter) {
        float tipX = cx + ax * hl, tipY = cy + ay * hl, tipZ = cz + az * hl;
        float blX = cx - ax * hl - px * hw, blY = cy - ay * hl - py * hw, blZ = cz - az * hl - pz * hw;
        float brX = cx - ax * hl + px * hw, brY = cy - ay * hl + py * hw, brZ = cz - az * hl + pz * hw;
        emitPyramidFace(pose, c, tipX, tipY, tipZ, blX, blY, blZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, blX, blY, blZ, brX, brY, brZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, brX, brY, brZ, tipX, tipY, tipZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
    }

    /**
     * Diamond pyramid: 4 base verts (+axis, +perp, -axis, -perp) + apex. 4 faces.
     *
     * @param pose        the pose matrix entry
     * @param c           the vertex consumer
     * @param cx          center X
     * @param cy          center Y
     * @param cz          center Z
     * @param ax          long axis X
     * @param ay          long axis Y
     * @param az          long axis Z
     * @param px          perp axis X
     * @param py          perp axis Y
     * @param pz          perp axis Z
     * @param hl          half-length along long axis
     * @param hw          half-width along perp axis
     * @param apX         apex X
     * @param apY         apex Y
     * @param apZ         apex Z
     * @param alpha       pre-computed vertex alpha [0-255]
     * @param level       the client level for raycasting
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     */
    private static void emitDiamondPyramid(PoseStack.Pose pose, VertexConsumer c,
                                           float cx, float cy, float cz,
                                           float ax, float ay, float az, float px, float py, float pz,
                                           float hl, float hw,
                                           float apX, float apY, float apZ, int alpha,
                                           Level level, Vec3 camPos, Vec3 worldCenter) {
        float tX = cx + ax * hl, tY = cy + ay * hl, tZ = cz + az * hl;
        float rX = cx + px * hw, rY = cy + py * hw, rZ = cz + pz * hw;
        float bX = cx - ax * hl, bY = cy - ay * hl, bZ = cz - az * hl;
        float lX = cx - px * hw, lY = cy - py * hw, lZ = cz - pz * hw;
        emitPyramidFace(pose, c, tX, tY, tZ, rX, rY, rZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, rX, rY, rZ, bX, bY, bZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, bX, bY, bZ, lX, lY, lZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, lX, lY, lZ, tX, tY, tZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
    }

    /**
     * Asymmetric diamond pyramid: uneven arm lengths + apex. 4 faces.
     *
     * @param pose        the pose matrix entry
     * @param c           the vertex consumer
     * @param cx          center X
     * @param cy          center Y
     * @param cz          center Z
     * @param ax          long axis X
     * @param ay          long axis Y
     * @param az          long axis Z
     * @param px          perp axis X
     * @param py          perp axis Y
     * @param pz          perp axis Z
     * @param hl          half-length along long axis
     * @param hw          half-width along perp axis
     * @param apX         apex X
     * @param apY         apex Y
     * @param apZ         apex Z
     * @param alpha       pre-computed vertex alpha [0-255]
     * @param level       the client level for raycasting
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     */
    private static void emitAsymPyramid(PoseStack.Pose pose, VertexConsumer c,
                                        float cx, float cy, float cz,
                                        float ax, float ay, float az, float px, float py, float pz,
                                        float hl, float hw,
                                        float apX, float apY, float apZ, int alpha,
                                        Level level, Vec3 camPos, Vec3 worldCenter) {
        float la = hl * ASYM_LONG_FACTOR, sa = hl * ASYM_SHORT_FACTOR;
        float tX = cx + ax * la, tY = cy + ay * la, tZ = cz + az * la;
        float rX = cx + px * hw, rY = cy + py * hw, rZ = cz + pz * hw;
        float bX = cx - ax * sa, bY = cy - ay * sa, bZ = cz - az * sa;
        float lX = cx - px * hw, lY = cy - py * hw, lZ = cz - pz * hw;
        emitPyramidFace(pose, c, tX, tY, tZ, rX, rY, rZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, rX, rY, rZ, bX, bY, bZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, bX, bY, bZ, lX, lY, lZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
        emitPyramidFace(pose, c, lX, lY, lZ, tX, tY, tZ, apX, apY, apZ,
                alpha, level, camPos, worldCenter);
    }

    /**
     * Emits one triangular pyramid face as a degenerate quad (v0, v1, apex, apex).
     *
     * @param pose        the pose entry
     * @param c           the vertex consumer
     * @param v0x         first base vertex X
     * @param v0y         first base vertex Y
     * @param v0z         first base vertex Z
     * @param v1x         second base vertex X
     * @param v1y         second base vertex Y
     * @param v1z         second base vertex Z
     * @param apX         apex X
     * @param apY         apex Y
     * @param apZ         apex Z
     * @param alpha       pre-computed vertex alpha [0-255]
     * @param level       the client level for raycasting
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     */
    private static void emitPyramidFace(PoseStack.Pose pose, VertexConsumer c,
                                        float v0x, float v0y, float v0z,
                                        float v1x, float v1y, float v1z,
                                        float apX, float apY, float apZ, int alpha,
                                        Level level, Vec3 camPos, Vec3 worldCenter) {
        float e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        float e1x = apX - v0x, e1y = apY - v0y, e1z = apZ - v0z;
        float nx = e0y * e1z - e0z * e1y;
        float ny = e0z * e1x - e0x * e1z;
        float nz = e0x * e1y - e0y * e1x;
        int color = reflectColor(level, camPos, worldCenter, nx, ny, nz, alpha);
        c.addVertex(pose, v0x, v0y, v0z).setColor(color).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, v1x, v1y, v1z).setColor(color).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, apX, apY, apZ).setColor(color).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, apX, apY, apZ).setColor(color).setNormal(pose, nx, ny, nz);
    }

    /**
     * Raycasts along the reflected camera direction to find a block color.
     *
     * @param level       the client level
     * @param camPos      the camera eye position
     * @param worldCenter the shard's world position
     * @param nx          face normal X (unnormalized)
     * @param ny          face normal Y
     * @param nz          face normal Z
     * @param alpha       pre-computed alpha [0-255]
     * @return packed ARGB with reflected block color and the given alpha
     */
    private static int reflectColor(Level level, Vec3 camPos, Vec3 worldCenter,
                                    float nx, float ny, float nz, int alpha) {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < NORMALIZE_EPSILON) {
            return ARGB.color(alpha, SKY_COLOR);
        }
        float invLen = 1f / len;
        Vec3 reflDir = computeReflection(camPos, worldCenter, nx * invLen, ny * invLen, nz * invLen);
        int rgb = raycastBlockColor(level, worldCenter, reflDir);
        int r = Math.min((int) (ARGB.red(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        int g = Math.min((int) (ARGB.green(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        int b = Math.min((int) (ARGB.blue(rgb) * REFLECT_BRIGHTNESS), FULL_ALPHA);
        return ARGB.color(Math.max(1, alpha), r, g, b);
    }

    /**
     * Reflects the camera-to-shard direction off the given unit normal.
     *
     * @param camPos      the camera eye position
     * @param worldCenter the shard world position
     * @param fnx         unit face normal X
     * @param fny         unit face normal Y
     * @param fnz         unit face normal Z
     * @return the reflected direction vector
     */
    private static Vec3 computeReflection(Vec3 camPos, Vec3 worldCenter,
                                          float fnx, float fny, float fnz) {
        Vec3 toShard = worldCenter.subtract(camPos).normalize();
        double dot = toShard.x * fnx + toShard.y * fny + toShard.z * fnz;
        return new Vec3(
                toShard.x - REFLECT_COEFF * dot * fnx,
                toShard.y - REFLECT_COEFF * dot * fny,
                toShard.z - REFLECT_COEFF * dot * fnz);
    }

    /**
     * Raycasts along a direction and returns the hit block's map color, or sky.
     *
     * @param level  the client level
     * @param origin the ray start position
     * @param dir    the ray direction
     * @return the hit block's ARGB map color, or sky color on miss
     */
    private static int raycastBlockColor(Level level, Vec3 origin, Vec3 dir) {
        Vec3 end = origin.add(dir.scale(RAYCAST_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(
                origin, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return SKY_COLOR;
        }
        MapColor mc = level.getBlockState(hit.getBlockPos()).getMapColor(level, hit.getBlockPos());
        return mc.calculateARGBColor(MapColor.Brightness.NORMAL);
    }

    /**
     * Rodrigues rotation: rotates vector (vx,vy,vz) around unit axis (ux,uy,uz) by angle.
     *
     * @param vx    vector X
     * @param vy    vector Y
     * @param vz    vector Z
     * @param ux    rotation axis X (unit)
     * @param uy    rotation axis Y (unit)
     * @param uz    rotation axis Z (unit)
     * @param angle rotation angle in radians
     * @return rotated vector as {x, y, z}
     */
    private static float[] rodrigues(float vx, float vy, float vz,
                                     float ux, float uy, float uz, float angle) {
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);
        float dot = ux * vx + uy * vy + uz * vz;
        // cross(u, v)
        float kx = uy * vz - uz * vy;
        float ky = uz * vx - ux * vz;
        float kz = ux * vy - uy * vx;
        return new float[]{
                vx * cosA + kx * sinA + ux * dot * (1f - cosA),
                vy * cosA + ky * sinA + uy * dot * (1f - cosA),
                vz * cosA + kz * sinA + uz * dot * (1f - cosA),
        };
    }


    /**
     * Builds deterministic sliver positions, orientations, sizes, and spin data.
     *
     * @return packed float array of sliver geometry data
     */
    private static float[] buildSliverData() {
        Random rng = new Random(SLIVER_SEED);
        float[] data = new float[MAX_SLIVERS * SLIVER_STRIDE];
        for (int i = 0; i < MAX_SLIVERS; i++) {
            buildOneSliver(rng, data, i * SLIVER_STRIDE);
        }
        return data;
    }

    /**
     * Populates one sliver's geometry and spin parameters.
     *
     * @param rng  the seeded random source
     * @param data the output float array
     * @param off  the write offset into data
     */
    private static void buildOneSliver(Random rng, float[] data, int off) {
        Vector3f pos = randomUnitSpherePoint(rng);
        data[off] = pos.x();
        data[off + OFF_CY] = pos.y();
        data[off + OFF_CZ] = pos.z();
        Vector3f axis = randomUnitVector(rng);
        data[off + OFF_AX] = axis.x();
        data[off + OFF_AY] = axis.y();
        data[off + OFF_AZ] = axis.z();
        Vector3f perp = buildPerp(axis);
        data[off + OFF_PX] = perp.x();
        data[off + OFF_PY] = perp.y();
        data[off + OFF_PZ] = perp.z();
        data[off + OFF_HALF_LEN] = SLIVER_HALF_LENGTH * (MIN_LEN_SCALE + rng.nextFloat() * LEN_SCALE_RANGE);
        buildShapeData(rng, data, off);
        buildSpinData(rng, data, off);
    }

    /**
     * Assigns a random shape type and width ratio to the shard.
     *
     * @param rng  the random source
     * @param data the output array
     * @param off  the sliver offset
     */
    private static void buildShapeData(Random rng, float[] data, int off) {
        float roll = rng.nextFloat();
        if (roll < SINGLE_SPIKE_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_SINGLE_SPIKE;
        } else if (roll < SINGLE_SPIKE_CHANCE + SYMMETRIC_CHANCE) {
            data[off + OFF_SHAPE] = SHAPE_DIAMOND;
        } else {
            data[off + OFF_SHAPE] = SHAPE_ASYMMETRIC;
        }
        data[off + OFF_WIDTH_RATIO] = MIN_WIDTH_RATIO + rng.nextFloat() * (MAX_WIDTH_RATIO - MIN_WIDTH_RATIO);
        data[off + OFF_DEPTH] = MIN_DEPTH_RATIO + rng.nextFloat() * (MAX_DEPTH_RATIO - MIN_DEPTH_RATIO);
    }

    /**
     * Assigns spin axis and speed. Many shards are still, some tumble slowly.
     *
     * @param rng  the random source
     * @param data the output array
     * @param off  the sliver offset
     */
    private static void buildSpinData(Random rng, float[] data, int off) {
        if (rng.nextFloat() < NO_SPIN_CHANCE) {
            data[off + OFF_SPIN_SPEED] = 0f;
            return;
        }
        Vector3f spinAxis = randomUnitVector(rng);
        data[off + OFF_SPIN_AX] = spinAxis.x();
        data[off + OFF_SPIN_AY] = spinAxis.y();
        data[off + OFF_SPIN_AZ] = spinAxis.z();
        data[off + OFF_SPIN_SPEED] = MIN_SPIN_SPEED + rng.nextFloat() * (MAX_SPIN_SPEED - MIN_SPIN_SPEED);
    }

    /**
     * Returns a random point uniformly distributed inside the unit sphere.
     *
     * @param rng the seeded random source
     * @return a point with length less than 1
     */
    private static Vector3f randomUnitSpherePoint(Random rng) {
        float px;
        float py;
        float pz;
        do {
            px = rng.nextFloat() * RNG_RANGE - 1f;
            py = rng.nextFloat() * RNG_RANGE - 1f;
            pz = rng.nextFloat() * RNG_RANGE - 1f;
        } while (px * px + py * py + pz * pz > 1f);
        return new Vector3f(px, py, pz);
    }

    private static Vector3f randomUnitVector(Random rng) {
        float x = rng.nextFloat() * RNG_RANGE - 1f;
        float y = rng.nextFloat() * RNG_RANGE - 1f;
        float z = rng.nextFloat() * RNG_RANGE - 1f;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < NORMALIZE_EPSILON) {
            return new Vector3f(0f, 1f, 0f);
        }
        return new Vector3f(x / len, y / len, z / len);
    }

    private static Vector3f buildPerp(Vector3f axis) {
        float ax = axis.x();
        float ay = axis.y();
        float az = axis.z();
        float sx = Math.abs(ay) < PARALLEL_THRESHOLD ? 0f : 1f;
        float sy = Math.abs(ay) < PARALLEL_THRESHOLD ? 1f : 0f;
        float px = ay * 0f - az * sy;
        float py = az * sx - ax * 0f;
        float pz = ax * sy - ay * sx;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        return new Vector3f(px / len, py / len, pz / len);
    }
}
