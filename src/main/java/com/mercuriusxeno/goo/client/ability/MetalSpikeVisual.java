package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.world.MetalBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import java.util.List;

/**
 * Metal spike trap visual: extends goo-textured cone spikes from the
 * chain marker orb out to each tracked entity. Spike snapshots come from
 * {@link MetalBehavior}; each animates independently through windup,
 * extension, and retract phases.
 */
public final class MetalSpikeVisual {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Half-block offset for face and edge positioning. */
    private static final float HALF = 0.5f;

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
    /** Array offset for the X target coordinate in spike anim snapshots. */
    private static final int SNAP_TX = 2;
    /** Array offset for the Y target coordinate in spike anim snapshots. */
    private static final int SNAP_TY = 3;
    /** Array offset for the Z target coordinate in spike anim snapshots. */
    private static final int SNAP_TZ = 4;

    private MetalSpikeVisual() {
    }

    /**
     * Populates {@code state} with metal-trap fields from the BE.
     * @param be    the chain marker block entity
     * @param state the render state to populate
     */
    public static void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
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
     * Renders goo-textured cone spikes from the orb center toward each
     * tracked entity, with per-entity animation phasing.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     */
    public static void submit(ChainMarkerRenderState state,
                              PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        int color = ARGB.color(SPIKE_ALPHA, GooColors.highlight(state.gooType));
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);
        Direction face = state.placedFace;
        float cx = HALF - face.getStepX() * HALF;
        float cy = HALF - face.getStepY() * HALF;
        float cz = HALF - face.getStepZ() * HALF;

        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer,
                            LightCoordsUtil.FULL_BRIGHT);
                    for (int[] snap : state.spikeAnims) {
                        emitSingleSpike(ctx, snap, state, cx, cy, cz, color, uv);
                    }
                });
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
     * Emits a single spike cone toward a tracked entity position.
     *
     * @param ctx   the render context
     * @param snap  the spike animation snapshot array
     * @param state the chain marker render state
     * @param cx    orb center X
     * @param cy    orb center Y
     * @param cz    orb center Z
     * @param color packed ARGB spike color
     * @param uv    fluid sprite UV rectangle
     */
    private static void emitSingleSpike(RenderContext ctx, int[] snap,
                                        ChainMarkerRenderState state, float cx, float cy, float cz,
                                        int color, GooRenderUtil.UvRect uv) {
        float tx = Float.intBitsToFloat(snap[SNAP_TX]);
        float ty = Float.intBitsToFloat(snap[SNAP_TY]);
        float tz = Float.intBitsToFloat(snap[SNAP_TZ]);
        float dx = tx - state.blockPos.getX() - cx;
        float dy = ty - state.blockPos.getY() - cy;
        float dz = tz - state.blockPos.getZ() - cz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < SPIKE_EPSILON) {
            return;
        }
        float ext = MetalBehavior.extensionFraction(snap[1], state.partialTick);
        float tipDist = (len + SPIKE_OVERSHOOT) * ext;
        emitSpikeCone(ctx, cx, cy, cz, dx / len, dy / len, dz / len, tipDist, color, uv);
    }

    /**
     * Emits a 3-sided cone from the base point toward the direction.
     *
     * @param ctx    the render context
     * @param bx     base center X
     * @param by     base center Y
     * @param bz     base center Z
     * @param dirX   normalized direction X
     * @param dirY   normalized direction Y
     * @param dirZ   normalized direction Z
     * @param length the cone length
     * @param color  the ARGB color
     * @param uv     the fluid sprite UV rectangle
     */
    private static void emitSpikeCone(RenderContext ctx,
                                      float bx, float by, float bz,
                                      float dirX, float dirY, float dirZ,
                                      float length, int color, GooRenderUtil.UvRect uv) {
        float tipX = bx + dirX * length;
        float tipY = by + dirY * length;
        float tipZ = bz + dirZ * length;

        float[] basis = ConeGeometry.computeBasis(dirX, dirY, dirZ);
        emitConeFaces(ctx, bx, by, bz, tipX, tipY, tipZ, dirX, dirY, dirZ,
                basis, color, uv);
    }

    /**
     * Emits textured triangular fan faces around the cone from base to tip.
     *
     * @param ctx   the render context
     * @param bx    cone base X
     * @param by    cone base Y
     * @param bz    cone base Z
     * @param tipX  cone tip X
     * @param tipY  cone tip Y
     * @param tipZ  cone tip Z
     * @param dirX  cone direction X
     * @param dirY  cone direction Y
     * @param dirZ  cone direction Z
     * @param basis orthonormal basis from {@link #computeConeBasis}
     * @param color packed ARGB color
     * @param uv    goo fluid sprite UV rectangle
     */
    private static void emitConeFaces(RenderContext ctx,
                                      float bx, float by, float bz,
                                      float tipX, float tipY, float tipZ,
                                      float dirX, float dirY, float dirZ,
                                      float[] basis, int color, GooRenderUtil.UvRect uv) {
        float uMid = (uv.u0() + uv.u1()) * HALF;
        for (int i = 0; i < SPIKE_SIDES; i++) {
            emitConeSegment(ctx, basis, color, uv, uMid,
                    bx, by, bz, tipX, tipY, tipZ, dirX, dirY, dirZ, i);
        }
    }

    /**
     * Emits one triangular segment of a spike cone.
     *
     * @param ctx   the render context
     * @param basis orthonormal basis vectors
     * @param color packed ARGB cone color
     * @param uv    fluid sprite UV rectangle
     * @param uMid  U-axis midpoint for the tip vertex
     * @param bx    cone base center X
     * @param by    cone base center Y
     * @param bz    cone base center Z
     * @param tipX  cone tip X
     * @param tipY  cone tip Y
     * @param tipZ  cone tip Z
     * @param dirX  cone direction X for tip normal
     * @param dirY  cone direction Y for tip normal
     * @param dirZ  cone direction Z for tip normal
     * @param i     segment index around the cone
     */
    private static void emitConeSegment(RenderContext ctx, float[] basis,
                                        int color, GooRenderUtil.UvRect uv, float uMid,
                                        float bx, float by, float bz, float tipX, float tipY, float tipZ,
                                        float dirX, float dirY, float dirZ, int i) {
        float a0 = TWO_PI * i / SPIKE_SIDES;
        float a1 = TWO_PI * (i + 1) / SPIKE_SIDES;
        float cos0 = (float) Math.cos(a0) * SPIKE_BASE_RADIUS;
        float sin0 = (float) Math.sin(a0) * SPIKE_BASE_RADIUS;
        float cos1 = (float) Math.cos(a1) * SPIKE_BASE_RADIUS;
        float sin1 = (float) Math.sin(a1) * SPIKE_BASE_RADIUS;
        float midCos = (float) Math.cos(a0 + a1) * HALF;
        float midSin = (float) Math.sin(a0 + a1) * HALF;
        float nx = basis[ConeGeometry.PERP_X] * midCos + basis[ConeGeometry.CROSS_X] * midSin;
        float ny = basis[ConeGeometry.PERP_Y] * midCos + basis[ConeGeometry.CROSS_Y] * midSin;
        float nz = basis[ConeGeometry.PERP_Z] * midCos + basis[ConeGeometry.CROSS_Z] * midSin;
        ctx.vertexColored(color,
                bx + basis[ConeGeometry.PERP_X] * cos0 + basis[ConeGeometry.CROSS_X] * sin0,
                by + basis[ConeGeometry.PERP_Y] * cos0 + basis[ConeGeometry.CROSS_Y] * sin0,
                bz + basis[ConeGeometry.PERP_Z] * cos0 + basis[ConeGeometry.CROSS_Z] * sin0,
                uv.u0(), uv.v0(), nx, ny, nz);
        ctx.vertexColored(color,
                bx + basis[ConeGeometry.PERP_X] * cos1 + basis[ConeGeometry.CROSS_X] * sin1,
                by + basis[ConeGeometry.PERP_Y] * cos1 + basis[ConeGeometry.CROSS_Y] * sin1,
                bz + basis[ConeGeometry.PERP_Z] * cos1 + basis[ConeGeometry.CROSS_Z] * sin1,
                uv.u1(), uv.v0(), nx, ny, nz);
        ctx.vertexColored(color, tipX, tipY, tipZ, uMid, uv.v1(), dirX, dirY, dirZ);
        ctx.vertexColored(color, tipX, tipY, tipZ, uMid, uv.v1(), dirX, dirY, dirZ);
    }
}
