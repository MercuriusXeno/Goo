package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.ability.world.UnstableBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Data-driven explosion behavior. Replaces the hardcoded
 * {@link UnstableBehavior} with
 * parameters read from the ability JSON.
 */
public final class ParameterizedExplosion implements ChainBehavior {

    /** Block center offset. */
    private static final double BLOCK_CENTER = 0.5;
    private static final String PARAM_BASE_POWER = "basePower";
    private static final String PARAM_POWER_PER_STACK = "powerPerStack";
    private static final String PARAM_PROXIMITY_TRIGGER = "proximityTrigger";
    private static final String PARAM_TRIGGER_RADIUS = "triggerRadius";
    private static final float DEFAULT_BASE_POWER = 2f;
    private static final float DEFAULT_POWER_PER_STACK = 1f;
    private static final float DEFAULT_TRIGGER_RADIUS = 3f;
    /** Diameter multiplier for AABB sizing from radius. */
    private static final int DIAMETER_MULT = 2;

    private final float basePower;
    private final float powerPerStack;
    private final boolean proximityTrigger;
    private final float triggerRadius;
    private boolean detonated;

    /**
     * Creates a parameterized explosion behavior.
     *
     * @param basePower        explosion power at 1 stack
     * @param powerPerStack    additional power per extra stack
     * @param proximityTrigger if true, detonates when an entity enters triggerRadius
     * @param triggerRadius    detection radius for proximity mode
     */
    public ParameterizedExplosion(float basePower, float powerPerStack,
            boolean proximityTrigger, float triggerRadius) {
        this.basePower = basePower;
        this.powerPerStack = powerPerStack;
        this.proximityTrigger = proximityTrigger;
        this.triggerRadius = triggerRadius;
    }

    /**
     * Factory method for BehaviorType registration.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new ParameterizedExplosion
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        return new ParameterizedExplosion(
                entry.getFloat(PARAM_BASE_POWER, DEFAULT_BASE_POWER),
                entry.getFloat(PARAM_POWER_PER_STACK, DEFAULT_POWER_PER_STACK),
                entry.getBool(PARAM_PROXIMITY_TRIGGER, false),
                entry.getFloat(PARAM_TRIGGER_RADIUS, DEFAULT_TRIGGER_RADIUS));
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (proximityTrigger) { return; }
        detonate(level, pos, be);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (!proximityTrigger || detonated) { return; }
        if (detectEntity(level, pos)) {
            detonate(level, pos, be);
        }
    }

    @Override
    public boolean isActive() {
        if (proximityTrigger) { return !detonated; }
        return false;
    }

    /**
     * Triggers the explosion.
     * @param level the server level
     * @param pos   the block position
     * @param be    the owning block entity
     */
    private void detonate(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        float power = basePower + (be.getStackCount() - 1) * powerPerStack;
        level.explode(null,
                pos.getX() + BLOCK_CENTER,
                pos.getY() + BLOCK_CENTER,
                pos.getZ() + BLOCK_CENTER,
                power, Level.ExplosionInteraction.TNT);
        detonated = true;
    }

    /**
     * Returns true if any living entity is within trigger radius.
     * @param level the server level
     * @param pos   the block position
     * @return true if an entity is detected
     */
    private boolean detectEntity(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        double diameter = triggerRadius * DIAMETER_MULT;
        AABB area = AABB.ofSize(center, diameter, diameter, diameter);
        List<Entity> entities = level.getEntities(null, area);
        for (Entity e : entities) {
            if (e instanceof net.minecraft.world.entity.LivingEntity
                    && e.position().distanceTo(center) <= triggerRadius) {
                return true;
            }
        }
        return false;
    }

    private static final String TAG_DETONATED = "Detonated";

    @Override
    public void saveAdditional(ValueOutput output) {
        output.putBoolean(TAG_DETONATED, detonated);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        detonated = input.getBooleanOr(TAG_DETONATED, false);
    }
}
