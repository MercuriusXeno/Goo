package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.effect.BlazeExecutor;
import com.mercuriusxeno.goo.effect.ChainBehavior;
import com.mercuriusxeno.goo.effect.ChainFootprint;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.effect.FrostExecutor;
import com.mercuriusxeno.goo.effect.RockExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Progressive area-coverage behavior. Affects blocks in an expanding
 * footprint one layer at a time. The per-block action is determined
 * by the {@code blockAction} parameter: silk-break, fortune-smelt-break,
 * freeze, or other actions added later.
 *
 * <p>The pipeline previews each layer, waits a configurable delay, then
 * applies the action. Area mode selects tunnel, flat circle, or sphere.</p>
 */
public final class ProgressiveAreaBlock implements ChainBehavior {

    private static final String AREA_TUNNEL = "tunnel";
    private static final String AREA_SPHERE = "sphere";
    private static final String ACTION_SILK_BREAK = "silk_break";
    private static final String ACTION_FORTUNE_SMELT = "fortune_smelt_break";
    private static final String ACTION_FREEZE = "freeze";
    private static final String STYLE_BLAZE = "blaze";
    private static final String STYLE_FROST = "frost";

    private static final int DEFAULT_PREVIEW_DELAY = 8;
    private static final String DEFAULT_FACE = "up";
    private static final String PARAM_AREA_MODE = "areaMode";
    private static final String PARAM_BLOCK_ACTION = "blockAction";
    private static final String PARAM_PREVIEW_DELAY = "previewDelayTicks";
    private static final String PARAM_PARTICLE_STYLE = "particleStyle";
    private static final String DEFAULT_PARTICLE_STYLE = "rock";

    private static final String TAG_PIPELINE_TICK = "AreaPipelineTick";
    private static final String TAG_LAYER_DEPTH = "AreaLayerDepth";
    private static final String TAG_STACK_SNAPSHOT = "AreaStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "AreaFace";

    private final String areaMode;
    private final String blockAction;
    private final int previewDelayTicks;
    private final String particleStyle;

    private int pipelineTick;
    private int layerDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;

    /**
     * Creates a progressive area behavior with the given configuration.
     *
     * @param areaMode         "tunnel", "flat_circle", or "sphere"
     * @param blockAction      "silk_break", "fortune_smelt_break", "freeze", etc.
     * @param previewDelayTicks ticks between preview and action
     * @param particleStyle    "blaze", "rock", "frost" for preview dispatch
     */
    public ProgressiveAreaBlock(String areaMode, String blockAction,
            int previewDelayTicks, String particleStyle) {
        this.areaMode = areaMode;
        this.blockAction = blockAction;
        this.previewDelayTicks = previewDelayTicks;
        this.particleStyle = particleStyle;
    }

    /**
     * Factory method for BehaviorType registration.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new ProgressiveAreaBlock
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        return new ProgressiveAreaBlock(
                entry.params().getOrDefault(PARAM_AREA_MODE, AREA_TUNNEL),
                entry.params().getOrDefault(PARAM_BLOCK_ACTION, ACTION_SILK_BREAK),
                (int) entry.getFloat(PARAM_PREVIEW_DELAY, DEFAULT_PREVIEW_DELAY),
                entry.params().getOrDefault(PARAM_PARTICLE_STYLE, DEFAULT_PARTICLE_STYLE));
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
            this.layerDepth = EffectMath.computeFreezeRadius(stackCount);
        } else if (AREA_TUNNEL.equals(areaMode)) {
            this.layerDepth = ChainFootprint.tunnelDepth(stackCount);
        } else {
            this.layerDepth = 1;
        }
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < layerDepth) {
            previewLayer(level, pos);
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

    /** Dispatches preview to the appropriate particle style.
     *
     * @param level the server level
     * @param pos   the marker block position
     */
    private void previewLayer(ServerLevel level, BlockPos pos) {
        boolean flat = isFlatArea();
        switch (particleStyle) {
            case STYLE_BLAZE -> BlazeExecutor.previewLayer(level, pos, placedFace,
                    pipelineTick, stackCount, flat);
            case STYLE_FROST -> {} // Frost has no preview particles yet
            default -> RockExecutor.previewLayer(level, pos, placedFace, pipelineTick);
        }
    }

    /** Dispatches the per-block action to the appropriate executor.
     *
     * @param level      the server level
     * @param pos        the marker block position
     * @param layerIndex the current layer depth index
     */
    private void applyLayer(ServerLevel level, BlockPos pos, int layerIndex) {
        if (AREA_SPHERE.equals(areaMode)) {
            applySphereShell(level, pos, layerIndex);
            return;
        }
        boolean flat = isFlatArea();
        switch (blockAction) {
            case ACTION_FORTUNE_SMELT -> BlazeExecutor.mineLayer(level, pos, placedFace,
                    layerIndex, stackCount, flat);
            case ACTION_FREEZE -> FrostExecutor.freezeLayer(level, pos, placedFace,
                    layerIndex, stackCount, flat);
            default -> RockExecutor.mineLayer(level, pos, placedFace,
                    layerIndex, stackCount, flat);
        }
    }

    /** Returns true if this behavior uses flat area mode.
     *
     * @return true for flat_circle area mode
     */
    private boolean isFlatArea() {
        return !AREA_TUNNEL.equals(areaMode) && !AREA_SPHERE.equals(areaMode);
    }

    /** Applies a single spherical shell at the given radius.
     *
     * @param level      the server level
     * @param pos        the marker block position
     * @param shellIndex the shell radius to apply
     */
    private void applySphereShell(ServerLevel level, BlockPos pos, int shellIndex) {
        if (ACTION_FREEZE.equals(blockAction)) {
            FrostExecutor.freezeShell(level, pos, placedFace, shellIndex);
        }
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
