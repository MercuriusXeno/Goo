package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.world.MetalBehavior;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/**
 * Slime-like glowing orb shown during the chain marker's FUSE phase.
 * Two layers: inner core with the goo fluid texture, outer translucent
 * shell with goo-tinted color. Both are emissive (fullbright). Size
 * scales with stack count and pulses on each stack add. Implodes inward
 * in the final ticks before detonation. GLOW orbs match the crystal
 * voxel shape from placement; FLAT-shape blobs splat against the placed
 * face with axis-asymmetric scaling.
 */
public final class FuseOrbVisual {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Blob shape constant for flat visual. */
    private static final String SHAPE_FLAT = "flat";

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
    /** Divisor for converting crystal extent to half-size in block units. */
    private static final float CRYSTAL_HALF_DIVISOR = 2f;

    private FuseOrbVisual() {
    }

    /**
     * Renders the orb: a textured core layer wrapped in a goo-tinted shell.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state, PoseStack poseStack,
                              SubmitNodeCollector nodeCollector) {
        float coreHalf = computeCoreHalf(state);
        float shellHalf = computeShellHalf(state, coreHalf);
        float modifier = computeOrbModifier(state);
        int shellColor = computeShellColor(state);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);

        poseStack.pushPose();
        translateToFace(poseStack, state);
        applyOrbScale(poseStack, state, coreHalf, modifier);
        submitCubeLayer(poseStack, nodeCollector, GooRenderUtil.OPAQUE_WHITE, coreHalf, uv);
        submitCubeLayer(poseStack, nodeCollector, shellColor, shellHalf, uv);
        poseStack.popPose();
    }

    /**
     * Glow orbs have no shell margin; all others add one.
     *
     * @param state    the chain marker render state
     * @param coreHalf the inner core half-size in block units
     * @return the shell half-size in block units
     */
    private static float computeShellHalf(ChainMarkerRenderState state, float coreHalf) {
        return state.gooType == GooType.GLOW ? coreHalf : coreHalf + SHELL_MARGIN;
    }

    /**
     * Combines implosion, pulse, and spike contraction into a single scale factor.
     *
     * @param state the chain marker render state
     * @return the combined scale modifier
     */
    private static float computeOrbModifier(ChainMarkerRenderState state) {
        float implosion = state.behaviorActive
                ? 1f : computeImplosionScale(state.fuseRemaining, state.partialTick);
        float pulse = computePulseScale(state);
        float spikeContract = computeSpikeContraction(state);
        return implosion * pulse * spikeContract;
    }

    /**
     * Applies the correct scale transform based on goo type and flat mode.
     *
     * @param poseStack the pose stack for rendering
     * @param state     the chain marker render state
     * @param coreHalf  the inner core half-size in block units
     * @param modifier  the combined scale modifier
     */
    private static void applyOrbScale(PoseStack poseStack, ChainMarkerRenderState state,
                                      float coreHalf, float modifier) {
        if (state.gooType == GooType.GLOW) {
            applyGlowScale(poseStack, state, coreHalf);
        } else if (SHAPE_FLAT.equals(state.blobShape)) {
            applySplatScale(poseStack, state.placedFace, modifier);
        } else {
            poseStack.scale(modifier, modifier, modifier);
        }
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
     * Brief pulse multiplier that spikes on stack add. Compares game
     * time against the recorded stack tick for partial-tick smoothing.
     *
     * @param state the chain marker render state
     * @return pulse scale factor (1.0 normally, up to 1+PULSE_AMPLITUDE)
     */
    private static float computePulseScale(ChainMarkerRenderState state) {
        if (state.lastStackTick <= 0) {
            return 1f;
        }
        float elapsed = state.gameTime - state.lastStackTick;
        if (elapsed < 0 || elapsed >= PULSE_TICKS) {
            return 1f;
        }
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
     * Minimum blob contraction across all active spike animations.
     * During windup the blob squeezes before the spike emerges.
     *
     * @param state the chain marker render state
     * @return contraction scale [0.85, 1.0]
     */
    private static float computeSpikeContraction(ChainMarkerRenderState state) {
        if (state.spikeAnims.isEmpty()) {
            return 1f;
        }
        float minScale = 1f;
        for (int[] snap : state.spikeAnims) {
            float s = MetalBehavior.blobContraction(snap[1], state.partialTick);
            if (s < minScale) {
                minScale = s;
            }
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
     *
     * @param poseStack the pose stack for rendering
     * @param state     the chain marker render state
     */
    private static void translateToFace(PoseStack poseStack, ChainMarkerRenderState state) {
        Direction face = state.placedFace;
        float ox = face.getStepX() * BLOCK_CENTER;
        float oy = face.getStepY() * BLOCK_CENTER;
        float oz = face.getStepZ() * BLOCK_CENTER;
        poseStack.translate(BLOCK_CENTER - ox, BLOCK_CENTER - oy, BLOCK_CENTER - oz);
    }

    /**
     * Splat deformation: squish along the placed face axis, widen
     * perpendicular. Also applies the combined modifier.
     *
     * @param poseStack the pose stack to scale
     * @param face      the placed face direction
     * @param modifier  combined implosion/pulse/spike-contract scale
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
        float visibleDepth = (float) (SHAPE_FLAT.equals(state.blobShape)
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
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param color         the ARGB tint color
     * @param half          the half-size of the cube
     * @param uv            the UV texture rectangle
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
     * @param partialTick   the partial tick for interpolation
     * @return the computed implosion scale
     */
    private static float computeImplosionScale(int fuseRemaining, float partialTick) {
        if (fuseRemaining > IMPLOSION_TICKS) {
            return 1f;
        }
        float smoothFuse = Math.max(0f, fuseRemaining - partialTick);
        float t = 1f - (smoothFuse / IMPLOSION_TICKS);
        return 1f - t * (1f - IMPLOSION_MIN);
    }
}
