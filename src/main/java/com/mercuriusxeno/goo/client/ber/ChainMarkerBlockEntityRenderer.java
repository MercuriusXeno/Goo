package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.effect.ChainFootprint;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.effect.CrystalBehavior;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.effect.MetalBehavior;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the chain marker as a slime-like glowing orb during the FUSE
 * phase. Two layers: inner core with the goo fluid texture, outer
 * translucent shell with goo-tinted color. Both are emissive (fullbright).
 * Size scales with stack count. Implodes inward in the final ticks
 * before detonation.
 *
 * <p>Type-specific post-detonation visuals (currently only the nether
 * black-hole sphere/corona/disk) live in dedicated helper classes and
 * are dispatched from {@link #submit} when the matching behavior is
 * active on the BE. The BER itself only knows about the orb and the
 * thin dispatch check.
 */
public class ChainMarkerBlockEntityRenderer
        implements BlockEntityRenderer<ChainMarkerBlockEntity, ChainMarkerRenderState> {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Base inner core half-size in block units (2 pixels) at 1 stack. */
    private static final float CORE_BASE = 2f / 16f;

    /** Shell extends 1 pixel beyond core in each direction. */
    private static final float SHELL_MARGIN = 1f / 16f;

    /** Core growth per additional stack (1/32 block = 0.5 pixel). */
    private static final float CORE_GROWTH = 1f / 32f;

    /** Splat squish factor along placed face axis (half height). */
    private static final float SPLAT_HEIGHT = 0.5f;

    /** Splat widen factor perpendicular to placed face (sqrt 2). */
    private static final float SPLAT_WIDTH = 1.414f;

    /** Pulse amplitude: 10% size increase on stack add. */
    private static final float PULSE_AMPLITUDE = 0.10f;

    /** Pulse duration in ticks. */
    private static final int PULSE_TICKS = 4;

    /** Outer shell alpha (translucent). */
    private static final int SHELL_ALPHA = 0x60;

    /** Outer shell alpha when the player is aiming at the node. */
    private static final int SHELL_ALPHA_TARGETED = 0xC0;

    /** Extra scale bump when targeted. */
    private static final float TARGET_SCALE_BOOST = 1.15f;

    /** Ticks before detonation where implosion starts. */
    private static final int IMPLOSION_TICKS = 6;

    /** Minimum scale during implosion (fraction of normal). */
    private static final float IMPLOSION_MIN = 0.3f;

    /** Bit shift for alpha channel in ARGB. */
    private static final int ALPHA_SHIFT = 24;
    /** Mask for stripping alpha from an ARGB color. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Alpha for the ghost fill quads. */
    private static final int GHOST_FILL_ALPHA = 0x26;
    /** Alpha for the perimeter wireframe. */
    private static final int GHOST_WIRE_ALPHA = 0xC0;
    /** Per-layer alpha decay factor for tunnel depth falloff. */
    private static final float DEPTH_ALPHA_DECAY = 0.80f;
    /** Line width for the perimeter wireframe. */
    private static final float GHOST_LINE_WIDTH = 2.0f;
    /** Bit mask for 21-bit coordinate packing. */
    private static final long PACK_MASK = 0x1FFFFF;
    /** Bit shift for Y coordinate in packed position. */
    private static final int PACK_Y_SHIFT = 21;
    /** Bit shift for Z coordinate in packed position. */
    private static final int PACK_Z_SHIFT = 42;
    /** Half-block offset for face and edge positioning. */
    private static final float HALF = 0.5f;
    /** Array index for X component in offset triples. */
    private static final int X = 0;
    /** Array index for Y component in offset triples. */
    private static final int Y = 1;
    /** Array index for Z component in offset triples. */
    private static final int Z = 2;
    /** Half-extent of the render bounding box around a chain marker, in blocks. Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;
    /** Spike cone base radius in blocks (30% thicker than original). */
    private static final float SPIKE_BASE_RADIUS = 0.104f;
    /** Number of triangular faces on the spike cone. */
    private static final int SPIKE_SIDES = 3;
    /** Two pi for angle computation. */
    private static final float TWO_PI = (float) (2 * Math.PI);
    /** Alpha for spike cone color. */
    private static final int SPIKE_ALPHA = 0xCC;
    /** Epsilon for near-zero spike length checks. */
    private static final float SPIKE_EPSILON = 1e-4f;
    /** Overshoot past the entity center so the spike pierces through. */
    private static final float SPIKE_OVERSHOOT = 1.0f;
    /** Threshold for choosing perpendicular basis vector. */
    private static final float DIRECTION_THRESHOLD = 0.9f;
    /** Divisor for converting crystal extent to half-size in block units. */
    private static final float CRYSTAL_HALF_DIVISOR = 2f;
    /** Array offset for the X target coordinate in spike anim snapshots. */
    private static final int SNAP_TX = 2;
    /** Array offset for the Y target coordinate in spike anim snapshots. */
    private static final int SNAP_TY = 3;
    /** Array offset for the Z target coordinate in spike anim snapshots. */
    private static final int SNAP_TZ = 4;

    public ChainMarkerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ChainMarkerRenderState createRenderState() {
        return new ChainMarkerRenderState();
    }

    /** Extends the render bounding box so the implosion sphere (up to the
     * nether max radius of 9) is not frustum-culled when the player looks
     * slightly away from the marker block.
     *
     * @param blockEntity the chain marker block entity
     * @return an AABB large enough to contain the maximum implosion sphere
     */
    @Override
    public @NonNull AABB getRenderBoundingBox(@NonNull ChainMarkerBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        double cx = pos.getX() + BLOCK_CENTER;
        double cy = pos.getY() + BLOCK_CENTER;
        double cz = pos.getZ() + BLOCK_CENTER;
        return new AABB(
            cx - RENDER_BOX_HALF_EXTENT, cy - RENDER_BOX_HALF_EXTENT, cz - RENDER_BOX_HALF_EXTENT,
            cx + RENDER_BOX_HALF_EXTENT, cy + RENDER_BOX_HALF_EXTENT, cz + RENDER_BOX_HALF_EXTENT);
    }

    @Override
    public void extractRenderState(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        extractCoreFields(be, state, partialTick);
        extractFuseAndTarget(be, state);
        extractMetalState(be, state);
        extractCrystalState(be, state);
        NetherHoleStyles.ACTIVE.extract(be, state);
    }

    /**
     * Copies goo type, stacks, fuse, and partial tick from the block entity.
     *
     * @param be the block entity
     * @param state the render state to populate
     * @param partialTick the partial tick for interpolation
     */
    private static void extractCoreFields(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state, float partialTick) {
        state.gooType = be.getGooType();
        state.stackCount = be.getStackCount();
        state.maxStacks = be.getMaxStacks();
        state.fuseRemaining = be.getFuseRemaining();
        state.partialTick = partialTick;
        state.flatMode = be.isFlatMode();
        state.lastStackTick = be.getLastStackTick();
        state.gameTime = be.getLevel() != null
                ? be.getLevel().getGameTime() + partialTick : 0f;
    }

    /**
     * Resolves fuse duration from chain profile and checks aim targeting.
     *
     * @param be the block entity
     * @param state the render state to populate
     */
    private static void extractFuseAndTarget(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state) {
        ChainProfile profile = ChainProfile.forType(be.getGooType());
        state.fuseTicks = profile != null ? profile.fuseTicks() : 1;
        // Highlight when ANY source of aim is on this marker: vanilla
        // crosshair (no-glove case), the goo cone-based aim assist
        // (glove held), or the post-throw freeze window (aim locked from
        // the previous throw and the marker was just placed on the spot).
        BlockPos pos = be.getBlockPos();
        state.targeted = GooRenderUtil.isBlockTargeted(pos)
                || GooTargetHighlighter.isChainMarkerTargeted(pos)
                || ThrowFreezeState.isFrozenOnChainMarker(pos);
        state.placedFace = be.getPlacedFace();
        state.behaviorActive = be.getBehavior() != null;
        state.minedLayers = be.getBehavior() != null ? be.getBehavior().getMinedLayers() : 0;
    }

    /**
     * Extracts metal spike trap state from the block entity.
     *
     * @param be    the block entity
     * @param state the render state to populate
     */
    private static void extractMetalState(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state) {
        if (be.getBehavior() instanceof MetalBehavior metal) {
            state.metalActive = true;
            state.metalCharges = be.getStackCount();
            state.spikeAnims = metal.hasActiveSpikes()
                    ? metal.getSpikeSnapshots() : List.of();
        } else {
            state.metalActive = false;
            state.spikeAnims = List.of();
            state.metalCharges = 0;
        }
    }

    /**
     * Extracts crystal shard cloud state from the block entity.
     *
     * @param be    the block entity
     * @param state the render state to populate
     */
    private static void extractCrystalState(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state) {
        if (be.getBehavior() instanceof CrystalBehavior crystal) {
            state.crystalActive = true;
            state.crystalDensity = crystal.getDensity();
        } else {
            state.crystalActive = false;
            state.crystalDensity = 0f;
        }
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.netherActive) {
            NetherHoleStyles.ACTIVE.submit(state, poseStack, nodeCollector);
            return;
        }
        submitFuseOrb(state, poseStack, nodeCollector);
        submitGhostOutline(state, poseStack, nodeCollector);
        if (state.metalActive && !state.spikeAnims.isEmpty()) {
            submitMetalSpikes(state, poseStack, nodeCollector);
        }
    }

    /**
     * Draws the orb visual used during the FUSE phase (existing behavior).
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    private static void submitFuseOrb(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float coreHalf = computeCoreHalf(state);
        float shellHalf = state.gooType == GooType.GLOW
                ? coreHalf : coreHalf + SHELL_MARGIN;
        float implosion = state.behaviorActive
                ? 1f : computeImplosionScale(state.fuseRemaining, state.partialTick);
        float pulse = computePulseScale(state);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        float spikeContract = computeSpikeContraction(state);
        float modifier = implosion * pulse * targetBoost * spikeContract;
        int shellColor = computeShellColor(state);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);

        poseStack.pushPose();
        translateToFace(poseStack, state);
        if (state.gooType == GooType.GLOW) {
            applyGlowScale(poseStack, state, coreHalf);
        } else if (state.flatMode) {
            applySplatScale(poseStack, state.placedFace, modifier);
        } else {
            poseStack.scale(modifier, modifier, modifier);
        }
        submitCubeLayer(poseStack, nodeCollector, GooRenderUtil.OPAQUE_WHITE, coreHalf, uv);
        submitCubeLayer(poseStack, nodeCollector, shellColor, shellHalf, uv);
        poseStack.popPose();
    }

    /**
     * Computes the core half-size based on stack count. For GLOW type,
     * smoothly interpolates from the standard blob size down to the
     * crystal's lateral extent over the fuse duration.
     *
     * @param state the chain marker render state
     * @return the core half-size in block units
     */
    private static float computeCoreHalf(ChainMarkerRenderState state) {
        if (state.gooType == GooType.GLOW) {
            return computeGlowCoreHalf(state);
        }
        return CORE_BASE + (state.stackCount - 1) * CORE_GROWTH;
    }

    /**
     * Returns the crystal's lateral half-extent so the glow orb matches
     * the crystal voxel shape from the moment it lands.
     *
     * @param state the chain marker render state
     * @return the crystal half-size in block units
     */
    private static float computeGlowCoreHalf(ChainMarkerRenderState state) {
        GlowCrystalBlock.CrystalSize cs =
                GlowCrystalBlock.CrystalSize.fromStacks(state.stackCount);
        return (float) ((cs.max - cs.min) / CRYSTAL_HALF_DIVISOR);
    }

    /**
     * Returns fuse progress from 0 (just placed) to 1 (about to expire),
     * with partial-tick smoothing.
     *
     * @param state the chain marker render state
     * @return fuse progress in [0, 1]
     */
    private static float fuseProgress(ChainMarkerRenderState state) {
        if (state.fuseTicks <= 0) { return 1f; }
        float remaining = state.fuseRemaining - state.partialTick;
        return 1f - Math.max(0f, remaining / state.fuseTicks);
    }

    /**
     * Computes a brief pulse multiplier that spikes on stack add.
     * Compares current game time against the recorded stack tick
     * for smooth partial-tick interpolation.
     *
     * @param state the chain marker render state
     * @return pulse scale factor (1.0 normally, up to 1+PULSE_AMPLITUDE)
     */
    private static float computePulseScale(ChainMarkerRenderState state) {
        if (state.lastStackTick <= 0) { return 1f; }
        float elapsed = state.gameTime - state.lastStackTick;
        if (elapsed < 0 || elapsed >= PULSE_TICKS) { return 1f; }
        float t = elapsed / PULSE_TICKS;
        return 1f + PULSE_AMPLITUDE * (float) Math.sin(t * Math.PI);
    }

    /**
     * Packs shell alpha and goo tint into an ARGB color.
     *
     * @param state the chain marker render state
     * @return the packed ARGB shell color
     */
    private static int computeShellColor(ChainMarkerRenderState state) {
        int baseShellAlpha = state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        int rgb = state.gooType == GooType.GLOW
                ? RGB_MASK : state.gooType.getColor();
        return (baseShellAlpha << ALPHA_SHIFT) | (rgb & RGB_MASK);
    }

    /**
     * Returns the minimum blob contraction across all active spike
     * animations. During windup the blob squeezes before the spike
     * emerges.
     *
     * @param state the chain marker render state
     * @return contraction scale [0.85, 1.0]
     */
    private static float computeSpikeContraction(ChainMarkerRenderState state) {
        if (state.spikeAnims.isEmpty()) { return 1f; }
        float minScale = 1f;
        for (int[] snap : state.spikeAnims) {
            float s = MetalBehavior.blobContraction(snap[1], state.partialTick);
            if (s < minScale) { minScale = s; }
        }
        return minScale;
    }

    /**
     * Looks up the fluid sprite and wraps its UV bounds.
     *
     * @param type the goo type to look up
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect lookupSpriteUv(GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        return new GooRenderUtil.UvRect(
                sprite.getU(0f), sprite.getV(0f),
                sprite.getU(1f), sprite.getV(1f));
    }

    /**
     * Translates to the face boundary where the blob splats into the wall.
     * All chain marker types now splat against their placed face.
     *
     * @param poseStack the pose stack for rendering
     * @param state the chain marker render state
     */
    private static void translateToFace(PoseStack poseStack, ChainMarkerRenderState state) {
        Direction face = state.placedFace;
        float ox = face.getStepX() * BLOCK_CENTER;
        float oy = face.getStepY() * BLOCK_CENTER;
        float oz = face.getStepZ() * BLOCK_CENTER;
        poseStack.translate(BLOCK_CENTER - ox, BLOCK_CENTER - oy, BLOCK_CENTER - oz);
    }

    /**
     * Applies the splat deformation: squish along the placed face axis,
     * widen perpendicular. Also applies the combined modifier (implosion,
     * pulse, target boost).
     *
     * @param poseStack the pose stack to scale
     * @param face      the placed face direction
     * @param modifier  combined implosion/pulse/target scale
     */
    private static void applySplatScale(PoseStack poseStack, Direction face, float modifier) {
        float wide = SPLAT_WIDTH * modifier;
        float thin = SPLAT_HEIGHT * modifier;
        float sx = face.getAxis() == Direction.Axis.X ? thin : wide;
        float sy = face.getAxis() == Direction.Axis.Y ? thin : wide;
        float sz = face.getAxis() == Direction.Axis.Z ? thin : wide;
        poseStack.scale(sx, sy, sz);
    }


    /**
     * Scales the glow orb so the face axis depth matches the crystal
     * model exactly (2px for bump, 0.01 for flat).
     *
     * @param poseStack the pose stack to scale
     * @param state     the render state
     * @param coreHalf  the lateral half-size (used to compute depth ratio)
     */
    private static void applyGlowScale(PoseStack poseStack,
            ChainMarkerRenderState state, float coreHalf) {
        float visibleDepth = (float) (state.flatMode
                ? GlowCrystalBlock.FLAT_DEPTH : GlowCrystalBlock.BUMP_DEPTH);
        float depthScale = visibleDepth / coreHalf;
        Direction face = state.placedFace;
        float sx = face.getAxis() == Direction.Axis.X ? depthScale : 1f;
        float sy = face.getAxis() == Direction.Axis.Y ? depthScale : 1f;
        float sz = face.getAxis() == Direction.Axis.Z ? depthScale : 1f;
        poseStack.scale(sx, sy, sz);
    }


    /**
     * Submits one fullbright translucent cube layer.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param color the ARGB tint color
     * @param half the half-size of the cube
     * @param uv the UV texture rectangle
     */
    private static void submitCubeLayer(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int color, float half,
            GooRenderUtil.UvRect uv) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        CuboidBounds box = new CuboidBounds(-half, half, -half, half, -half, half);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> new RenderContext(pose, c, light).emitBox(color, box, uv));
    }


    /**
     * Implosion scale: 1.0 normally, shrinks to IMPLOSION_MIN in the
     * final IMPLOSION_TICKS before detonation. Smooth via partial tick.
     *
     * @param fuseRemaining the fuse ticks remaining
     * @param partialTick the partial tick for interpolation
     * @return the computed implosionScale
     */
    private static float computeImplosionScale(int fuseRemaining, float partialTick) {
        if (fuseRemaining > IMPLOSION_TICKS) { return 1f; }
        float smoothFuse = Math.max(0f, fuseRemaining - partialTick);
        float t = 1f - (smoothFuse / IMPLOSION_TICKS);
        return 1f - t * (1f - IMPLOSION_MIN);
    }


    /**
     * Renders goo-textured cone spikes from the orb center toward each
     * tracked entity, with per-entity animation phasing. Each spike
     * tracks its target entity's live position on the client.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     */
    private static void submitMetalSpikes(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        int color = (SPIKE_ALPHA << ALPHA_SHIFT) | (GooColors.highlight(state.gooType) & RGB_MASK);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);
        BlockPos pos = state.blockPos;
        Direction face = state.placedFace;
        float cx = HALF - face.getStepX() * HALF;
        float cy = HALF - face.getStepY() * HALF;
        float cz = HALF - face.getStepZ() * HALF;
        float partial = state.partialTick;

        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer,
                            LightCoordsUtil.FULL_BRIGHT);
                    for (int[] snap : state.spikeAnims) {
                        int animTick = snap[1];
                        float tx = Float.intBitsToFloat(snap[SNAP_TX]);
                        float ty = Float.intBitsToFloat(snap[SNAP_TY]);
                        float tz = Float.intBitsToFloat(snap[SNAP_TZ]);
                        float dx = tx - pos.getX() - cx;
                        float dy = ty - pos.getY() - cy;
                        float dz = tz - pos.getZ() - cz;
                        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (len < SPIKE_EPSILON) { continue; }
                        float ext = MetalBehavior.extensionFraction(animTick, partial);
                        float tipDist = (len + SPIKE_OVERSHOOT) * ext;
                        emitSpikeCone(ctx, cx, cy, cz,
                                dx / len, dy / len, dz / len,
                                tipDist, color, uv);
                    }
                });
    }

    /**
     * Emits a 3-sided cone from the base point toward the direction,
     * textured with the goo fluid sprite.
     *
     * @param ctx     the render context (pose, consumer, light)
     * @param bx      base center X
     * @param by      base center Y
     * @param bz      base center Z
     * @param dirX    normalized direction X
     * @param dirY    normalized direction Y
     * @param dirZ    normalized direction Z
     * @param length  the cone length
     * @param color   the ARGB color
     * @param uv      the fluid sprite UV rectangle
     */
    private static void emitSpikeCone(RenderContext ctx,
            float bx, float by, float bz,
            float dirX, float dirY, float dirZ,
            float length, int color, GooRenderUtil.UvRect uv) {
        float tipX = bx + dirX * length;
        float tipY = by + dirY * length;
        float tipZ = bz + dirZ * length;

        float[] basis = computeConeBasis(dirX, dirY, dirZ);
        emitConeFaces(ctx, bx, by, bz, tipX, tipY, tipZ, dirX, dirY, dirZ,
                basis, color, uv);
    }

    /** Computes orthonormal perp + cross basis vectors for a cone direction.
     *
     * @param dirX cone direction X component
     * @param dirY cone direction Y component
     * @param dirZ cone direction Z component
     * @return array of {perpX, perpY, perpZ, crossX, crossY, crossZ}
     */
    private static float[] computeConeBasis(float dirX, float dirY, float dirZ) {
        float perpX;
        float perpY;
        float perpZ;
        if (Math.abs(dirY) < DIRECTION_THRESHOLD) {
            perpX = -dirZ;
            perpY = 0;
            perpZ = dirX;
        } else {
            perpX = 1;
            perpY = 0;
            perpZ = 0;
        }
        float dot = perpX * dirX + perpY * dirY + perpZ * dirZ;
        perpX -= dot * dirX;
        perpY -= dot * dirY;
        perpZ -= dot * dirZ;
        float pLen = (float) Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        perpX /= pLen;
        perpY /= pLen;
        perpZ /= pLen;
        float crossX = dirY * perpZ - dirZ * perpY;
        float crossY = dirZ * perpX - dirX * perpZ;
        float crossZ = dirX * perpY - dirY * perpX;
        return new float[]{perpX, perpY, perpZ, crossX, crossY, crossZ};
    }

    /** Index of the perp-Y component in the cone basis array. */
    private static final int BASIS_PERP_Y = 1;
    /** Index of the perp-Z component in the cone basis array. */
    private static final int BASIS_PERP_Z = 2;
    /** Index of the cross-X component in the cone basis array. */
    private static final int BASIS_CROSS_X = 3;
    /** Index of the cross-Y component in the cone basis array. */
    private static final int BASIS_CROSS_Y = 4;
    /** Index of the cross-Z component in the cone basis array. */
    private static final int BASIS_CROSS_Z = 5;

    /** Emits textured triangular fan faces around the cone from base to tip.
     * Each triangle maps the goo fluid sprite across the face for a
     * goo-colored/textured appearance.
     *
     * @param ctx   the render context
     * @param bx    cone base X
     * @param by    cone base Y
     * @param bz    cone base Z
     * @param tipX  cone tip X
     * @param tipY  cone tip Y
     * @param tipZ  cone tip Z
     * @param dirX  cone direction X (for normal)
     * @param dirY  cone direction Y (for normal)
     * @param dirZ  cone direction Z (for normal)
     * @param basis orthonormal basis from {@link #computeConeBasis}
     * @param color packed ARGB color
     * @param uv    goo fluid sprite UV rectangle
     */
    private static void emitConeFaces(RenderContext ctx,
            float bx, float by, float bz,
            float tipX, float tipY, float tipZ,
            float dirX, float dirY, float dirZ,
            float[] basis, int color, GooRenderUtil.UvRect uv) {
        float perpX = basis[0], perpY = basis[BASIS_PERP_Y], perpZ = basis[BASIS_PERP_Z];
        float crossX = basis[BASIS_CROSS_X], crossY = basis[BASIS_CROSS_Y], crossZ = basis[BASIS_CROSS_Z];
        float uMid = (uv.u0() + uv.u1()) * HALF;
        for (int i = 0; i < SPIKE_SIDES; i++) {
            float a0 = TWO_PI * i / SPIKE_SIDES;
            float a1 = TWO_PI * (i + 1) / SPIKE_SIDES;
            float cos0 = (float) Math.cos(a0) * SPIKE_BASE_RADIUS;
            float sin0 = (float) Math.sin(a0) * SPIKE_BASE_RADIUS;
            float cos1 = (float) Math.cos(a1) * SPIKE_BASE_RADIUS;
            float sin1 = (float) Math.sin(a1) * SPIKE_BASE_RADIUS;
            float nx = perpX * ((float) Math.cos(a0 + a1) * HALF)
                    + crossX * ((float) Math.sin(a0 + a1) * HALF);
            float ny = perpY * ((float) Math.cos(a0 + a1) * HALF)
                    + crossY * ((float) Math.sin(a0 + a1) * HALF);
            float nz = perpZ * ((float) Math.cos(a0 + a1) * HALF)
                    + crossZ * ((float) Math.sin(a0 + a1) * HALF);
            ctx.vertexColored(color,
                    bx + perpX * cos0 + crossX * sin0,
                    by + perpY * cos0 + crossY * sin0,
                    bz + perpZ * cos0 + crossZ * sin0,
                    uv.u0(), uv.v0(), nx, ny, nz);
            ctx.vertexColored(color,
                    bx + perpX * cos1 + crossX * sin1,
                    by + perpY * cos1 + crossY * sin1,
                    bz + perpZ * cos1 + crossZ * sin1,
                    uv.u1(), uv.v0(), nx, ny, nz);
            ctx.vertexColored(color, tipX, tipY, tipZ,
                    uMid, uv.v1(), dirX, dirY, dirZ);
            ctx.vertexColored(color, tipX, tipY, tipZ,
                    uMid, uv.v1(), dirX, dirY, dirZ);
        }
    }


    /**
     * Renders the effect region as connected translucent fill with
     * wireframe only on the outer perimeter. Interior faces between
     * adjacent blocks are eliminated; interior edges between coplanar
     * exterior faces are eliminated.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    private static void submitGhostOutline(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (!shouldShowGhostOutline(state)) { return; }

        List<int[]> offsets = computeFilteredOffsets(state);
        Set<Long> filled = packOffsets(offsets);

        int edgeRgb = GooColors.edge(state.gooType) & RGB_MASK;
        int fillColor = (GHOST_FILL_ALPHA << ALPHA_SHIFT) | edgeRgb;
        int wireColor = (GHOST_WIRE_ALPHA << ALPHA_SHIFT) | edgeRgb;

        Direction blastDir = state.flatMode ? null : state.placedFace.getOpposite();
        int minedLayers = state.minedLayers;
        submitGhostFill(poseStack, nodeCollector, offsets, filled, fillColor, blastDir, minedLayers);
        submitGhostWireframe(poseStack, nodeCollector, offsets, filled, wireColor, blastDir, minedLayers);
        AuroraFadeWallRenderer.submit(poseStack, nodeCollector, offsets,
                filled, fillColor, state.placedFace, state.gameTime);
    }

    /** Returns true if the ghost outline should render for this state.
     *
     * @param state the render state snapshot
     * @return true if the ghost outline should be drawn
     */
    private static boolean shouldShowGhostOutline(ChainMarkerRenderState state) {
        if (state.fuseRemaining <= 0 && !state.behaviorActive) { return false; }
        GooType type = state.gooType;
        return type == GooType.ROCK || type == GooType.BLAZE || type == GooType.FROST
                || type == GooType.CRYSTAL;
    }

    /** Computes ghost offsets with mined-layer and air-block filtering applied.
     *
     * @param state the render state snapshot
     * @return filtered list of block offsets
     */
    private static List<int[]> computeFilteredOffsets(ChainMarkerRenderState state) {
        List<int[]> allOffsets = computeGhostOffsets(state.gooType, state);
        List<int[]> afterMined = excludeMinedLayers(allOffsets, state.placedFace, state.minedLayers);
        return excludeAirBlocks(afterMined, state.blockPos);
    }

    /** Packs a list of offsets into a position set for neighbor lookups.
     *
     * @param offsets the block offsets to pack
     * @return set of packed position keys
     */
    private static Set<Long> packOffsets(List<int[]> offsets) {
        Set<Long> filled = new HashSet<>(offsets.size());
        for (int[] o : offsets) {
            filled.add(packPos(o[X], o[Y], o[Z]));
        }
        return filled;
    }

    /**
     * Filters out offsets that correspond to air blocks in the world.
     * The ghost outline only highlights solid blocks that will actually
     * be affected by the chain effect.
     *
     * @param offsets   the block offsets to filter
     * @param markerPos the chain marker's world position
     * @return the filtered offset list with air blocks removed
     */
    private static List<int[]> excludeAirBlocks(List<int[]> offsets, BlockPos markerPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return offsets; }
        List<int[]> result = new ArrayList<>(offsets.size());
        for (int[] o : offsets) {
            BlockPos worldPos = markerPos.offset(o[X], o[Y], o[Z]);
            if (!mc.level.getBlockState(worldPos).isAir()) {
                result.add(o);
            }
        }
        return result;
    }

    /**
     * Computes the ghost outline offsets based on goo type. Frost uses
     * a spheroid shape; rock/blaze use the tunnel footprint.
     *
     * @param type  the goo type
     * @param state the render state
     * @return the block offsets for the ghost outline
     */
    private static List<int[]> computeGhostOffsets(GooType type, ChainMarkerRenderState state) {
        if (!state.flatMode) {
            if (type == GooType.FROST) {
                int radius = EffectMath.computeFreezeRadius(state.stackCount);
                return ChainFootprint.computeSphereOffsets(radius, state.placedFace);
            }
            if (type == GooType.CRYSTAL) {
                int radius = (int) CrystalBehavior.CLOUD_RADIUS;
                return ChainFootprint.computeSphereOffsets(radius, state.placedFace);
            }
        }
        return ChainFootprint.computeRegionOffsets(
                state.stackCount, state.flatMode, state.placedFace);
    }

    /**
     * Attenuates a color's alpha by the block's effective depth along
     * the blast axis. Mined layers shift the origin forward so the
     * remaining front face is always at full alpha.
     *
     * @param color       the base ARGB color
     * @param offset      the block offset {dx, dy, dz}
     * @param blastDir    the blast direction, or null for flat mode
     * @param minedLayers layers already mined (subtracted from depth)
     * @return the depth-attenuated ARGB color
     */
    private static int attenuateByDepth(int color, int[] offset,
            @Nullable Direction blastDir, int minedLayers) {
        if (blastDir == null) { return color; }
        int rawDepth = offset[X] * blastDir.getStepX()
                + offset[Y] * blastDir.getStepY()
                + offset[Z] * blastDir.getStepZ();
        int effectiveDepth = rawDepth - minedLayers;
        if (effectiveDepth <= 0) { return color; }
        float factor = (float) Math.sqrt(Math.pow(DEPTH_ALPHA_DECAY, effectiveDepth));
        int alpha = (int) ((color >>> ALPHA_SHIFT) * factor);
        return (alpha << ALPHA_SHIFT) | (color & RGB_MASK);
    }

    /**
     * Filters out block offsets belonging to layers already mined.
     * Depth is measured along the blast direction (placedFace opposite).
     * Layer 0 is the block immediately behind the marker.
     *
     * @param offsets     all block offsets in the region
     * @param placedFace  the placed face direction
     * @param minedLayers the number of layers already mined
     * @return the filtered offset list
     */
    private static List<int[]> excludeMinedLayers(List<int[]> offsets,
            Direction placedFace, int minedLayers) {
        if (minedLayers <= 0) { return offsets; }
        Direction blast = placedFace.getOpposite();
        int bx = blast.getStepX();
        int by = blast.getStepY();
        int bz = blast.getStepZ();
        List<int[]> result = new ArrayList<>(offsets.size());
        for (int[] o : offsets) {
            int depth = o[X] * bx + o[Y] * by + o[Z] * bz;
            if (depth >= minedLayers) { result.add(o); }
        }
        return result;
    }

    /**
     * Emits translucent fill quads for exterior faces only. In tunnel
     * mode, alpha decays with depth along the blast axis.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB fill color
     * @param blastDir      the blast direction for depth falloff, or null for flat mode
     * @param minedLayers   layers already mined (shifts depth origin forward)
     */
    private static void submitGhostFill(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, List<int[]> offsets,
            Set<Long> filled, int color, @Nullable Direction blastDir,
            int minedLayers) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.QUADS_NO_DEPTH,
                (pose, c) -> {
                    FlatQuadContext ctx = new FlatQuadContext(pose, c);
                    for (int[] o : offsets) {
                        int depthColor = attenuateByDepth(color, o, blastDir, minedLayers);
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (!filled.contains(packPos(nx, ny, nz))) {
                                emitFaceQuad(ctx, o, dir, depthColor);
                            }
                        }
                    }
                });
    }

    /**
     * Emits wireframe edges only on the perimeter of the region. In
     * tunnel mode, alpha decays with depth along the blast axis.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB wire color
     * @param blastDir      the blast direction for depth falloff, or null for flat mode
     * @param minedLayers   layers already mined (shifts depth origin forward)
     */
    private static void submitGhostWireframe(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, List<int[]> offsets,
            Set<Long> filled, int color, @Nullable Direction blastDir,
            int minedLayers) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.LINES_NO_DEPTH,
                (pose, c) -> {
                    LineContext ctx = new LineContext(pose, c);
                    for (int[] o : offsets) {
                        int depthColor = attenuateByDepth(color, o, blastDir, minedLayers);
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (filled.contains(packPos(nx, ny, nz))) { continue; }
                            emitPerimeterEdges(ctx, o, dir, filled, depthColor);
                        }
                    }
                });
    }

    /**
     * For one exterior face, emits only perimeter edges. Interior
     * edges shared with coplanar neighbors are skipped.
     *
     * @param ctx     the line render context
     * @param pos     the block offset {dx, dy, dz}
     * @param faceDir the exterior face direction
     * @param filled  packed position set for neighbor checks
     * @param color   the ARGB wire color
     */
    private static void emitPerimeterEdges(LineContext ctx, int[] pos,
            Direction faceDir, Set<Long> filled, int color) {
        FaceEdge[] edges = getFaceEdges(faceDir);
        float cx = pos[X] + HALF + faceDir.getStepX() * HALF;
        float cy = pos[Y] + HALF + faceDir.getStepY() * HALF;
        float cz = pos[Z] + HALF + faceDir.getStepZ() * HALF;

        for (FaceEdge edge : edges) {
            if (isCoplanarNeighbor(pos, faceDir, edge.neighborDir, filled)) {
                continue;
            }
            emitSingleEdge(ctx, cx, cy, cz, edge, color);
        }
    }

    /**
     * Checks if the adjacent block has a coplanar exterior face.
     *
     * @param pos         the block offset
     * @param faceDir     the exterior face direction
     * @param neighborDir the direction to the adjacent block
     * @param filled      packed position set for neighbor checks
     * @return true if the neighbor has an exterior face on the same side
     */
    static boolean isCoplanarNeighbor(int[] pos, Direction faceDir,
            Direction neighborDir, Set<Long> filled) {
        int adjX = pos[X] + neighborDir.getStepX();
        int adjY = pos[Y] + neighborDir.getStepY();
        int adjZ = pos[Z] + neighborDir.getStepZ();
        return filled.contains(packPos(adjX, adjY, adjZ))
                && !filled.contains(packPos(
                        adjX + faceDir.getStepX(),
                        adjY + faceDir.getStepY(),
                        adjZ + faceDir.getStepZ()));
    }

    /**
     * Emits one wireframe edge segment for a face edge.
     *
     * @param ctx   the line render context
     * @param cx    face center X
     * @param cy    face center Y
     * @param cz    face center Z
     * @param edge  the edge descriptor
     * @param color the ARGB wire color
     */
    private static void emitSingleEdge(LineContext ctx, float cx, float cy,
            float cz, FaceEdge edge, int color) {
        float nx = edge.neighborDir.getStepX() * HALF;
        float ny = edge.neighborDir.getStepY() * HALF;
        float nz = edge.neighborDir.getStepZ() * HALF;
        float rx = edge.runDir.getStepX() * HALF;
        float ry = edge.runDir.getStepY() * HALF;
        float rz = edge.runDir.getStepZ() * HALF;
        float ex = cx + nx;
        float ey = cy + ny;
        float ez = cz + nz;
        ctx.emitEdge(ex - rx, ey - ry, ez - rz,
                     ex + rx, ey + ry, ez + rz,
                     color, GHOST_LINE_WIDTH);
    }


    /**
     * Describes one edge of a face quad: which neighboring block shares
     * this edge (neighborDir) and which axis the edge runs along (runDir).
     *
     * @param neighborDir the direction to the adjacent block sharing this edge
     * @param runDir      the axis the edge runs along
     */
    record FaceEdge(Direction neighborDir, Direction runDir) {}

    /**
     * Returns the 4 edges of a face, each identified by the adjacent
     * block direction and the axis the edge runs along.
     *
     * @param face the face direction
     * @return the four face edge descriptors
     */
    static FaceEdge[] getFaceEdges(Direction face) {
        return switch (face.getAxis()) {
            case X -> new FaceEdge[]{
                new FaceEdge(Direction.UP, Direction.NORTH),
                new FaceEdge(Direction.DOWN, Direction.NORTH),
                new FaceEdge(Direction.NORTH, Direction.UP),
                new FaceEdge(Direction.SOUTH, Direction.UP)
            };
            case Y -> new FaceEdge[]{
                new FaceEdge(Direction.NORTH, Direction.EAST),
                new FaceEdge(Direction.SOUTH, Direction.EAST),
                new FaceEdge(Direction.WEST, Direction.NORTH),
                new FaceEdge(Direction.EAST, Direction.NORTH)
            };
            case Z -> new FaceEdge[]{
                new FaceEdge(Direction.UP, Direction.EAST),
                new FaceEdge(Direction.DOWN, Direction.EAST),
                new FaceEdge(Direction.WEST, Direction.UP),
                new FaceEdge(Direction.EAST, Direction.UP)
            };
        };
    }

    /**
     * Emits a single face quad at the given position and direction.
     *
     * @param ctx   the flat quad render context
     * @param pos   the block offset {dx, dy, dz}
     * @param dir   the face direction to emit
     * @param color the ARGB fill color
     */
    private static void emitFaceQuad(FlatQuadContext ctx, int[] pos,
            Direction dir, int color) {
        CuboidBounds box = new CuboidBounds(
                pos[X], pos[X] + 1, pos[Z], pos[Z] + 1, pos[Y], pos[Y] + 1);
        ctx.emitFace(color, box, dir);
    }

    /**
     * Packs three 21-bit coordinates into a single long for set lookup.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @return the packed position
     */
    static long packPos(int x, int y, int z) {
        long px = x & PACK_MASK;
        long py = (y & PACK_MASK) << PACK_Y_SHIFT;
        long pz = (z & PACK_MASK) << PACK_Z_SHIFT;
        return px | py | pz;
    }

}
