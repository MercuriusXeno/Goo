package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Progressive area-coverage behavior. Affects blocks in an expanding
 * footprint one layer at a time, fanning out three permutable axes
 * per layer:
 * <ul>
 *   <li>{@link BlockEffect} - the per-cell mutation (silk-break,
 *       fortune-smelt, freeze, ...).</li>
 *   <li>{@link LayerVisuals} - preview particles before each layer and
 *       on-struck particles after.</li>
 *   <li>{@link LayerAudio} - the per-layer sound cue scaled by stack
 *       count and step depth.</li>
 * </ul>
 *
 * <p>The pipeline previews each layer, waits a configurable delay, then
 * applies the BlockEffect cell-by-cell and counts how many cells were
 * actually mutated. The destroyed count drives both visuals and audio
 * scaling. Area mode selects tunnel, flat circle, or sphere.</p>
 */
public final class ProgressiveAreaBlock implements ChainBehavior {

    private static final String AREA_TUNNEL = "tunnel";
    private static final String AREA_SPHERE = "sphere";

    private static final int DEFAULT_PREVIEW_DELAY = 8;
    private static final String DEFAULT_FACE = "up";
    private static final String PARAM_AREA_MODE = "areaMode";
    private static final String PARAM_BLOCK_EFFECT = "blockEffect";
    private static final String PARAM_LAYER_VISUALS = "layerVisuals";
    private static final String PARAM_LAYER_AUDIO = "layerAudio";
    private static final String PARAM_PREVIEW_DELAY = "previewDelayTicks";

    private static final String DEFAULT_BLOCK_EFFECT = BlockEffectType.SILK_BREAK;
    private static final String DEFAULT_LAYER_VISUALS = LayerVisualsType.ROCK_DUST;
    private static final String DEFAULT_LAYER_AUDIO = LayerAudioType.STONE_BREAK;

    private static final String TAG_PIPELINE_TICK = "AreaPipelineTick";
    private static final String TAG_LAYER_DEPTH = "AreaLayerDepth";
    private static final String TAG_STACK_SNAPSHOT = "AreaStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "AreaFace";
    /** Array index for Z component in 3D offset triples. */
    private static final int Z_INDEX = 2;
    /** Negative unit step for blast direction. */
    private static final int NEG_STEP = -1;

    private final String areaMode;
    private final BlockEffect blockEffect;
    private final LayerVisuals layerVisuals;
    private final LayerAudio layerAudio;
    private final int previewDelayTicks;

    private int pipelineTick;
    private int layerDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;
    /** Cached flat rings for ring-by-ring delivery. Null when not flat_circle. */
    private transient List<List<int[]>> cachedFlatRings;

    /**
     * Creates a progressive area behavior with the resolved per-axis
     * delegates already in hand.
     *
     * @param areaMode          {@code "tunnel"}, {@code "flat_circle"}, or {@code "sphere"}
     * @param blockEffect       the per-cell mutation
     * @param layerVisuals      the per-layer particle profile
     * @param layerAudio        the per-layer sound profile
     * @param previewDelayTicks ticks between preview and effect application
     */
    public ProgressiveAreaBlock(String areaMode, BlockEffect blockEffect,
                                LayerVisuals layerVisuals, LayerAudio layerAudio,
                                int previewDelayTicks) {
        this.areaMode = areaMode;
        this.blockEffect = blockEffect;
        this.layerVisuals = layerVisuals;
        this.layerAudio = layerAudio;
        this.previewDelayTicks = previewDelayTicks;
    }

    /**
     * Factory method for {@link BehaviorType} registration. Resolves the
     * three delegate names into singleton instances at construction;
     * downstream pipeline code calls into the resolved delegates without
     * further string lookups.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new ProgressiveAreaBlock
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        String areaMode = entry.params().getOrDefault(PARAM_AREA_MODE, AREA_TUNNEL);
        BlockEffect effect = BlockEffectType.byName(
                entry.params().getOrDefault(PARAM_BLOCK_EFFECT, DEFAULT_BLOCK_EFFECT));
        LayerVisuals visuals = LayerVisualsType.byName(
                entry.params().getOrDefault(PARAM_LAYER_VISUALS, DEFAULT_LAYER_VISUALS));
        LayerAudio audio = LayerAudioType.byName(
                entry.params().getOrDefault(PARAM_LAYER_AUDIO, DEFAULT_LAYER_AUDIO));
        int previewDelay = (int) entry.getFloat(PARAM_PREVIEW_DELAY, DEFAULT_PREVIEW_DELAY);
        return new ProgressiveAreaBlock(areaMode, effect, visuals, audio, previewDelay);
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.pipelineTick = 0;
        initAreaMode();
    }

    /** Sets layerDepth based on the configured areaMode string. */
    private void initAreaMode() {
        if (AREA_SPHERE.equals(areaMode)) {
            this.layerDepth = AbilityMath.computeFreezeRadius(stackCount);
        } else if (AREA_TUNNEL.equals(areaMode)) {
            this.layerDepth = ChainFootprint.tunnelDepth(stackCount);
        } else {
            this.cachedFlatRings = ChainFootprint.flatRings(stackCount);
            this.layerDepth = cachedFlatRings.size();
        }
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < layerDepth) {
            layerVisuals.preview(level, pos, placedFace, pipelineTick, stackCount);
        }
        int actionIndex = pipelineTick - previewDelayTicks;
        if (actionIndex >= 0 && actionIndex < layerDepth) {
            applyLayer(level, pos, actionIndex);
        }
        pipelineTick++;
    }

    @Override
    public boolean isActive() {
        return pipelineTick < layerDepth + previewDelayTicks;
    }

    @Override
    public int getMinedLayers() {
        return Math.max(0, pipelineTick - previewDelayTicks);
    }

    /** Computes the positions for this layer, applies the block effect to
     * each, and dispatches visuals and audio scaled by the destroyed count.
     *
     * @param level      the server level
     * @param pos        the marker block position
     * @param layerIndex the current layer/ring/shell index
     */
    private void applyLayer(ServerLevel level, BlockPos pos, int layerIndex) {
        List<int[]> offsets = computeLayerOffsets(pos, layerIndex);
        int destroyed = 0;
        for (int[] o : offsets) {
            BlockPos cell = pos.offset(o[0], o[1], o[Z_INDEX]);
            if (blockEffect.apply(level, cell)) {
                destroyed++;
            }
        }
        layerVisuals.onLayerStruck(level, pos, placedFace, layerIndex, destroyed);
        layerAudio.onLayerStruck(level, pos, placedFace, layerIndex, destroyed, stackCount);
    }

    /** Computes 3D offsets for one delivery step based on area mode.
     *
     * @param pos        the marker block position
     * @param layerIndex the current step index
     * @return list of {dx, dy, dz} offsets relative to the marker
     */
    private List<int[]> computeLayerOffsets(BlockPos pos, int layerIndex) {
        if (AREA_SPHERE.equals(areaMode)) {
            return ChainFootprint.sphereShellOffsets(layerIndex, placedFace);
        }
        if (cachedFlatRings != null && layerIndex < cachedFlatRings.size()) {
            return expandFlatRing(cachedFlatRings.get(layerIndex));
        }
        return computeTunnelLayerOffsets(layerIndex);
    }

    /** Expands a 2D flat ring into 3D offsets at depth 1 along the blast axis.
     *
     * @param ring the 2D ring offsets
     * @return list of 3D offsets relative to the marker
     */
    private List<int[]> expandFlatRing(List<int[]> ring) {
        Direction blastDir = placedFace.getOpposite();
        Direction.Axis axis = blastDir.getAxis();
        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG_STEP;
        List<int[]> result = new ArrayList<>(ring.size());
        for (int[] fp : ring) {
            result.add(mapToWorld(axis, fp[0], fp[1], step));
        }
        return result;
    }

    /** Computes 3D offsets for one tunnel layer at the given depth.
     *
     * @param layerIndex the layer depth index
     * @return list of 3D offsets relative to the marker
     */
    private List<int[]> computeTunnelLayerOffsets(int layerIndex) {
        Direction blastDir = placedFace.getOpposite();
        Direction.Axis axis = blastDir.getAxis();
        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG_STEP;
        int depthOffset = (layerIndex + 1) * step;
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        List<int[]> result = new ArrayList<>(footprint.size());
        for (int[] fp : footprint) {
            result.add(mapToWorld(axis, fp[0], fp[1], depthOffset));
        }
        return result;
    }

    private static int[] mapToWorld(Direction.Axis axis, int a, int b, int d) {
        return switch (axis) {
            case X -> new int[]{d, a, b};
            case Y -> new int[]{a, d, b};
            case Z -> new int[]{a, b, d};
        };
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_PIPELINE_TICK, pipelineTick);
        output.putInt(TAG_LAYER_DEPTH, layerDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
    }

    @Override
    public void loadAdditional(ValueInput input) {
        pipelineTick = input.getIntOr(TAG_PIPELINE_TICK, 0);
        layerDepth = input.getIntOr(TAG_LAYER_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
