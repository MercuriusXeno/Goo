package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.ability.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ability.CrystalCloudVisual;
import com.mercuriusxeno.goo.client.ability.FuseOrbVisual;
import com.mercuriusxeno.goo.client.ability.GhostMineVisual;
import com.mercuriusxeno.goo.client.ability.MetalSpikeVisual;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thin dispatcher for chain marker visuals. Each ability-specific
 * appearance lives in its own visualizer in {@code client.ability/}; this
 * class extracts the common state fields, delegates ability-specific
 * extraction to each visualizer, and dispatches submit() based on which
 * behavior is active.
 *
 * <p>Active visualizers:
 * <ul>
 *   <li>{@link FuseOrbVisual} - the slime-like orb during the fuse phase</li>
 *   <li>{@link MetalSpikeVisual} - cone spikes from the marker to tracked entities</li>
 *   <li>{@link CrystalCloudVisual} - shard-cloud cloud after a crystal detonation</li>
 *   <li>{@link GhostMineVisual} - destruction-footprint outline (rock/blaze/frost)</li>
 *   <li>{@link NetherHoleStyles#ACTIVE} - the swappable nether black-hole style</li>
 * </ul>
 */
public class ChainMarkerBlockEntityRenderer
        implements BlockEntityRenderer<ChainMarkerBlockEntity, ChainMarkerRenderState> {

    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Half-extent of the render bounding box around a chain marker, in blocks.
     * Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;

    public ChainMarkerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Copies goo type, stacks, fuse, and partial tick from the block entity.
     *
     * @param be          the block entity
     * @param state       the render state to populate
     * @param partialTick the partial tick for interpolation
     */
    private static void extractCoreFields(ChainMarkerBlockEntity be,
                                          ChainMarkerRenderState state, float partialTick) {
        state.gooType = be.getGooType();
        state.stackCount = be.getStackCount();
        state.maxStacks = be.getMaxStacks();
        state.fuseRemaining = be.getFuseRemaining();
        state.partialTick = partialTick;
        state.blobShape = be.getBlobShape();
        state.areaMode = be.getAreaMode();
        state.lastStackTick = be.getLastStackTick();
        state.gameTime = be.getLevel() != null
                ? be.getLevel().getGameTime() + partialTick : 0f;
    }

    /**
     * Resolves fuse duration from the chain profile and detects aim targeting.
     *
     * @param be    the block entity
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

    @Override
    public ChainMarkerRenderState createRenderState() {
        return new ChainMarkerRenderState();
    }

    /**
     * Extends the render bounding box so the implosion sphere (up to the
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
        MetalSpikeVisual.extract(be, state);
        CrystalCloudVisual.extract(be, state);
        NetherHoleStyles.ACTIVE.extract(be, state);
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.netherActive) {
            NetherHoleStyles.ACTIVE.submit(state, poseStack, nodeCollector);
            return;
        }
        FuseOrbVisual.submit(state, poseStack, nodeCollector);
        if (state.crystalActive) {
            CrystalCloudVisual.submit(state, poseStack, nodeCollector);
        }
        GhostMineVisual.submit(state, poseStack, nodeCollector);
        if (state.metalActive && !state.spikeAnims.isEmpty()) {
            MetalSpikeVisual.submit(state, poseStack, nodeCollector);
        }
    }
}
