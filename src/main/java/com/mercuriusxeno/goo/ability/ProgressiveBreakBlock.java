package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.effect.BlazeExecutor;
import com.mercuriusxeno.goo.effect.ChainBehavior;
import com.mercuriusxeno.goo.effect.ChainFootprint;
import com.mercuriusxeno.goo.effect.RockExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Unified progressive block-break behavior. Replaces both BlazeBehavior
 * and RockBehavior with a parameterized pipeline that dispatches to the
 * appropriate executor based on the configured break mode.
 *
 * <p>The pipeline previews each layer, waits a configurable delay, then
 * mines the layer. Area mode selects tunnel (directional) or flat circle.
 * Break mode selects silk-touch drops or fortune+smelt drops.</p>
 */
public final class ProgressiveBreakBlock implements ChainBehavior {

    private static final String AREA_TUNNEL = "tunnel";
    private static final String BREAK_SILK = "silk";
    private static final String BREAK_FORTUNE_SMELT = "fortune_smelt";
    private static final String STYLE_BLAZE = "blaze";

    private static final int DEFAULT_PREVIEW_DELAY = 8;
    private static final String DEFAULT_FACE = "up";
    private static final String PARAM_AREA_MODE = "areaMode";
    private static final String PARAM_BREAK_MODE = "breakMode";
    private static final String PARAM_PREVIEW_DELAY = "previewDelayTicks";
    private static final String PARAM_PARTICLE_STYLE = "particleStyle";
    private static final String DEFAULT_PARTICLE_STYLE = "rock";

    private static final String TAG_PIPELINE_TICK = "BreakPipelineTick";
    private static final String TAG_MINING_DEPTH = "BreakMiningDepth";
    private static final String TAG_STACK_SNAPSHOT = "BreakStackSnapshot";
    private static final String TAG_FACE_SNAPSHOT = "BreakFace";
    private static final String TAG_FLAT_MODE = "BreakFlatMode";

    private final String areaMode;
    private final String breakMode;
    private final int previewDelayTicks;
    private final String particleStyle;

    private int pipelineTick;
    private int miningDepth;
    private int stackCount;
    private Direction placedFace = Direction.UP;
    private boolean flatMode;

    /**
     * Creates a progressive break behavior with the given configuration.
     *
     * @param areaMode         "tunnel" or "flat_circle"
     * @param breakMode        "silk" or "fortune_smelt"
     * @param previewDelayTicks ticks between preview and mine
     * @param particleStyle    "blaze" or "rock" for executor dispatch
     */
    public ProgressiveBreakBlock(String areaMode, String breakMode,
            int previewDelayTicks, String particleStyle) {
        this.areaMode = areaMode;
        this.breakMode = breakMode;
        this.previewDelayTicks = previewDelayTicks;
        this.particleStyle = particleStyle;
    }

    /**
     * Factory method for BehaviorType registration.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new ProgressiveBreakBlock
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        return new ProgressiveBreakBlock(
                entry.params().getOrDefault(PARAM_AREA_MODE, AREA_TUNNEL),
                entry.params().getOrDefault(PARAM_BREAK_MODE, BREAK_SILK),
                (int) entry.getFloat(PARAM_PREVIEW_DELAY, DEFAULT_PREVIEW_DELAY),
                entry.params().getOrDefault(PARAM_PARTICLE_STYLE, DEFAULT_PARTICLE_STYLE));
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        this.placedFace = be.getPlacedFace();
        this.flatMode = !AREA_TUNNEL.equals(areaMode);
        this.miningDepth = flatMode ? 1 : ChainFootprint.tunnelDepth(stackCount);
        this.pipelineTick = 0;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (pipelineTick < miningDepth) {
            previewLayer(level, pos);
        }
        int breakIndex = pipelineTick - previewDelayTicks;
        if (breakIndex >= 0 && breakIndex < miningDepth) {
            mineLayer(level, pos, breakIndex);
        }
        pipelineTick++;
    }

    @Override
    public boolean isActive() {
        return pipelineTick < miningDepth + previewDelayTicks;
    }

    @Override
    public int getMinedLayers() {
        return Math.max(0, pipelineTick - previewDelayTicks);
    }

    /** Dispatches preview to the appropriate executor.
     *
     * @param level the server level
     * @param pos   the marker block position
     */
    private void previewLayer(ServerLevel level, BlockPos pos) {
        if (STYLE_BLAZE.equals(particleStyle)) {
            BlazeExecutor.previewLayer(level, pos, placedFace, pipelineTick,
                    stackCount, flatMode);
        } else {
            RockExecutor.previewLayer(level, pos, placedFace, pipelineTick);
        }
    }

    /** Dispatches mine to the appropriate executor.
     *
     * @param level      the server level
     * @param pos        the marker block position
     * @param breakIndex the current layer depth index
     */
    private void mineLayer(ServerLevel level, BlockPos pos, int breakIndex) {
        if (BREAK_FORTUNE_SMELT.equals(breakMode)) {
            BlazeExecutor.mineLayer(level, pos, placedFace, breakIndex,
                    stackCount, flatMode);
        } else {
            RockExecutor.mineLayer(level, pos, placedFace, breakIndex,
                    stackCount, flatMode);
        }
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putInt(TAG_PIPELINE_TICK, pipelineTick);
        output.putInt(TAG_MINING_DEPTH, miningDepth);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        output.putString(TAG_FACE_SNAPSHOT, placedFace.getName());
        output.putBoolean(TAG_FLAT_MODE, flatMode);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        pipelineTick = input.getIntOr(TAG_PIPELINE_TICK, 0);
        miningDepth = input.getIntOr(TAG_MINING_DEPTH, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        flatMode = input.getBooleanOr(TAG_FLAT_MODE, false);
        String faceName = input.getStringOr(TAG_FACE_SNAPSHOT, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        placedFace = dir != null ? dir : Direction.UP;
    }
}
