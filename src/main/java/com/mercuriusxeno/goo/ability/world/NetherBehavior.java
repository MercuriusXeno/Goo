package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainBehavior;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Nether goo: black-hole implosion that consumes blocks and recovers
 * their goo values. Stitched merge of the prior NetherEffect (instant
 * world hit, places a chain marker via EffectBlockPlacement),
 * NetherBehavior (the multi-phase EXPAND/HOLD/CONTRACT/POPPING state
 * machine), and NetherExecutor (the sphere walk that accumulates and
 * destroys valued blocks).
 *
 * <p>Entity effects layered on the phase machine:
 * <ul>
 *   <li><b>Blindness</b> + <b>two-tier darkness</b> applied once at
 *       EXPAND entry covering the full EXPAND + HOLD + CONTRACT window
 *       plus a short fade-out tail.</li>
 *   <li><b>Half-HP magic damage</b> to any living entity inside the
 *       blast sphere at the exact EXPAND -&gt; HOLD transition.</li>
 *   <li><b>Gravitic pull</b> during EXPAND and HOLD (not CONTRACT), radius
 *       = 3x blast radius, via {@link LivingEntity#push} so knockback
 *       resistance still applies.</li>
 * </ul>
 */
public final class NetherBehavior implements WorldEffect, ChainBehavior {

    /** Internal sub-phase of the black-hole effect. The chain marker BE
     * only sees {@link ChainBehavior#isActive()}; this enum tracks which
     * segment of the implosion is currently running. {@link #DONE} is
     * the terminal state - once set, {@code isActive} returns false and
     * the BE removes itself. */
    public enum Phase { EXPAND, HOLD, CONTRACT, POPPING, DONE }


    /** Ticks the sphere takes to grow from 0 to full size (.75 s). */
    public static final int EXPAND_DURATION = 15;
    /** Ticks the sphere holds at full size while blocks are destroyed (0.75 s). */
    public static final int HOLD_DURATION = 15;
    /** Ticks the sphere takes to shrink from full size back to 0 (1.5 s). */
    public static final int CONTRACT_DURATION = 30;
    /** Extra blindness/darkness ticks past the full EXPAND+HOLD+CONTRACT
     * window so entities inside get a clean fade-out. */
    private static final int BLINDNESS_EXTRA_TICKS = 10;


    /** Base soul particle count per implosion tick. */
    private static final int IMPLODE_PARTICLE_BASE = 1;
    /** Additional soul particles per stack. */
    private static final int IMPLODE_PARTICLE_PER_STACK = 1;
    /** Placeholder particle spread as a fraction of the implosion radius. */
    private static final double IMPLODE_PARTICLE_SPREAD_PER_RANGE = 0.6;
    /** Placeholder particle velocity. */
    private static final double IMPLODE_PARTICLE_SPEED = 0.02;
    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Volume for the black-hole sound at EXPAND entry. */
    private static final float BLACK_HOLE_SOUND_VOLUME = 6.0f;
    /** Pitch for the black-hole sound at EXPAND entry. */
    private static final float BLACK_HOLE_SOUND_PITCH = 1.0f;
    /** Disc-expansion value at the EXPAND to HOLD transition. */
    private static final float DISK_EXPAND_PEAK = 0.25f;


    /** Fraction of current HP shaved off any living entity caught inside
     * the blast at the EXPAND -> HOLD transition. */
    private static final float DAMAGE_FRACTION = 0.5f;
    /** Per-tick velocity nudge magnitude (blocks/tick) applied toward the
     * marker center during EXPAND and HOLD. */
    private static final double PULL_SPEED = 0.15;
    /** Pull radius is this multiple of the blast radius. */
    private static final int PULL_RADIUS_MULT = 3;
    /** Floor on squared distance-to-center before applying the pull. */
    private static final double PULL_MIN_DIST_SQ = 0.25;
    /** Outer darkness radius is this multiple of the blast radius. */
    private static final int OUTER_DARK_RADIUS_MULT = 3;
    /** Vanilla DARKNESS amplifier inside the blast sphere. */
    private static final int INNER_DARK_AMPLIFIER = 2;
    /** Vanilla DARKNESS amplifier in the outer warning band. */
    private static final int OUTER_DARK_AMPLIFIER = 1;


    private static final String TAG_PHASE = "NetherPhase";
    private static final String TAG_EXPAND_REMAINING = "ExpandRemaining";
    private static final String TAG_EXPAND_INITIAL = "ExpandInitial";
    private static final String TAG_HOLD_REMAINING = "HoldRemaining";
    private static final String TAG_HOLD_INITIAL = "HoldInitial";
    private static final String TAG_CONTRACT_REMAINING = "ContractRemaining";
    private static final String TAG_CONTRACT_INITIAL = "ContractInitial";
    private static final String TAG_IMPLODE_RADIUS = "ImplodeRadius";
    private static final String TAG_ACCUMULATOR = "Accumulator";
    private static final String TAG_STACK_SNAPSHOT = "StackSnapshot";


    /** Lifecycle phase. See {@link Phase}. */
    private Phase phase = Phase.EXPAND;
    /** Ticks remaining in EXPAND. Counts down from {@link #initialExpandTicks}. */
    private int expandTicksRemaining;
    /** Initial EXPAND duration (usually {@link #EXPAND_DURATION}). */
    private int initialExpandTicks;
    /** Ticks remaining in HOLD. Counts down from {@link #initialHoldTicks}. */
    private int holdTicksRemaining;
    /** Initial HOLD duration (usually {@link #HOLD_DURATION}). */
    private int initialHoldTicks;
    /** Ticks remaining in CONTRACT. */
    private int contractTicksRemaining;
    /** Initial CONTRACT duration. */
    private int initialContractTicks;
    /** Effect radius of the nether blast in blocks. */
    private int implodeRadius;
    /** Per-type mB totals filled during EXPAND -> HOLD transition, dropped at POPPING. */
    private GooContents accumulator = GooContents.EMPTY;
    /** Snapshot of the BE's stack count at fuse expiry. */
    private int stackCount;

    // --- WorldEffect (instant blob hit) ---

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.netherConvert(level, pos, targetFace);
    }

    // --- ChainBehavior (fused chain marker detonation) ---

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        this.stackCount = be.getStackCount();
        int range = AbilityMath.computeNetherRadius(stackCount);
        beginImplosion(level, pos, range);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        if (phase == Phase.EXPAND || phase == Phase.HOLD) {
            tickEarlyPhase(level, pos);
            return;
        }
        tickLatePhase(level, pos);
    }

    /**
     * Dispatches the EXPAND / HOLD phase handlers.
     * @param level TODO PARAM DESCRIPTION
     * @param pos TODO PARAM DESCRIPTION
     */
    private void tickEarlyPhase(ServerLevel level, BlockPos pos) {
        if (phase == Phase.EXPAND) {
            tickExpand(level, pos);
            return;
        }
        tickHold(level, pos);
    }

    /**
     * Dispatches the CONTRACT / POPPING / DONE phase handlers.
     * @param level TODO PARAM DESCRIPTION
     * @param pos TODO PARAM DESCRIPTION
     */
    private void tickLatePhase(ServerLevel level, BlockPos pos) {
        if (phase == Phase.CONTRACT) {
            tickContract(level, pos);
            return;
        }
        if (phase == Phase.POPPING) {
            tickPopping(level, pos);
        }
    }

    @Override
    public boolean isActive() {
        return phase != Phase.DONE;
    }

    /** Seeds the state machine and transitions into EXPAND.
     *
     * @param level  the server level
     * @param pos    the marker position
     * @param radius effect radius in blocks
     */
    private void beginImplosion(ServerLevel level, BlockPos pos, int radius) {
        this.implodeRadius = radius;
        this.accumulator = GooContents.EMPTY;
        this.initialExpandTicks = EXPAND_DURATION;
        this.expandTicksRemaining = EXPAND_DURATION;
        this.initialHoldTicks = 0;
        this.holdTicksRemaining = 0;
        this.initialContractTicks = 0;
        this.contractTicksRemaining = 0;
        this.phase = Phase.EXPAND;
        blindEntitiesInSphere(level, pos, radius);
        applyAreaDarkness(level, pos, radius);
        level.playSound(null, pos, GooSounds.BLACK_HOLE.get(), SoundSource.BLOCKS,
            BLACK_HOLE_SOUND_VOLUME, BLACK_HOLE_SOUND_PITCH);
    }

    private void tickExpand(ServerLevel level, BlockPos pos) {
        expandTicksRemaining--;
        spawnImplosionParticles(level, pos);
        pullEntitiesTowardCenter(level, pos, implodeRadius);
        if (expandTicksRemaining <= 0) {
            accumulator = walkAndDestroy(level, pos, implodeRadius);
            damageEntitiesInSphere(level, pos, implodeRadius);
            phase = Phase.HOLD;
            initialHoldTicks = HOLD_DURATION;
            holdTicksRemaining = HOLD_DURATION;
        }
    }

    private void tickHold(ServerLevel level, BlockPos pos) {
        holdTicksRemaining--;
        spawnImplosionParticles(level, pos);
        pullEntitiesTowardCenter(level, pos, implodeRadius);
        if (holdTicksRemaining <= 0) {
            phase = Phase.CONTRACT;
            initialContractTicks = CONTRACT_DURATION;
            contractTicksRemaining = CONTRACT_DURATION;
        }
    }

    private void tickContract(ServerLevel level, BlockPos pos) {
        contractTicksRemaining--;
        spawnImplosionParticles(level, pos);
        if (contractTicksRemaining <= 0) {
            phase = Phase.POPPING;
        }
    }

    private void tickPopping(ServerLevel level, BlockPos pos) {
        BlobStacks.dropAll(accumulator, level, pos);
        phase = Phase.DONE;
    }

    private void spawnImplosionParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;
        int count = IMPLODE_PARTICLE_BASE + IMPLODE_PARTICLE_PER_STACK * stackCount;
        double spread = implodeRadius * IMPLODE_PARTICLE_SPREAD_PER_RANGE;
        level.sendParticles(ParticleTypes.SOUL,
            cx, cy, cz, count, spread, spread, spread, IMPLODE_PARTICLE_SPEED);
    }


    /**
     * Shared AABB + squared-distance filter used by blind/damage/pull/darkness.
     * @param level TODO PARAM DESCRIPTION
     * @param pos TODO PARAM DESCRIPTION
     * @param radius TODO PARAM DESCRIPTION
     * @param action TODO PARAM DESCRIPTION
     */
    private static void forEntitiesInSphere(ServerLevel level, BlockPos pos, int radius,
                                            Consumer<LivingEntity> action) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;
        AABB box = new AABB(cx - radius, cy - radius, cz - radius,
            cx + radius, cy + radius, cz + radius);
        double radiusSq = (double) radius * radius;
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity entity : entities) {
            if (entity.distanceToSqr(cx, cy, cz) > radiusSq) { continue; }
            action.accept(entity);
        }
    }

    private static void blindEntitiesInSphere(ServerLevel level, BlockPos pos, int radius) {
        int duration = EXPAND_DURATION + HOLD_DURATION + CONTRACT_DURATION + BLINDNESS_EXTRA_TICKS;
        forEntitiesInSphere(level, pos, radius, entity ->
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, false)));
    }

    private static void damageEntitiesInSphere(ServerLevel level, BlockPos pos, int radius) {
        forEntitiesInSphere(level, pos, radius, entity -> {
            float damage = entity.getHealth() * DAMAGE_FRACTION;
            entity.hurtServer(level, level.damageSources().magic(), damage);
        });
    }

    private static void pullEntitiesTowardCenter(ServerLevel level, BlockPos pos, int radius) {
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;
        forEntitiesInSphere(level, pos, radius * PULL_RADIUS_MULT, entity -> {
            double dx = cx - entity.getX();
            double dy = cy - entity.getY();
            double dz = cz - entity.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < PULL_MIN_DIST_SQ) { return; }
            double scale = PULL_SPEED / Math.sqrt(distSq);
            entity.push(dx * scale, dy * scale, dz * scale);
            entity.hurtMarked = true;
        });
    }

    private static void applyAreaDarkness(ServerLevel level, BlockPos pos, int radius) {
        int duration = EXPAND_DURATION + HOLD_DURATION + CONTRACT_DURATION + BLINDNESS_EXTRA_TICKS;
        int outerRadius = radius * OUTER_DARK_RADIUS_MULT;
        applyDarknessBand(level, pos, outerRadius, OUTER_DARK_AMPLIFIER, duration);
        applyDarknessBand(level, pos, radius, INNER_DARK_AMPLIFIER, duration);
    }

    private static void applyDarknessBand(ServerLevel level, BlockPos pos, int radius,
                                          int amplifier, int duration) {
        forEntitiesInSphere(level, pos, radius, entity ->
            entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, amplifier, false, false)));
    }


    /**
     * The current internal phase. Used by the BER to branch rendering.
     * @return TODO RETURN DESCRIPTION
     */
    public Phase getPhase() {
        return phase;
    }

    /**
     * The sphere radius in blocks, seeded at EXPAND entry.
     * @return TODO RETURN DESCRIPTION
     */
    public int getCurrentRadius() {
        return implodeRadius;
    }

    /**
     * Per-type mB totals filled at the EXPAND -&gt; HOLD transition.
     * @return TODO RETURN DESCRIPTION
     */
    public GooContents getAccumulator() {
        return accumulator;
    }

    /**
     * Visible scale of the black-hole sphere in {@code [0, 1]}.
     * @return TODO RETURN DESCRIPTION
     */
    public float getVisibleScale() {
        return switch (phase) {
            case EXPAND -> easeOutCubic(expandProgress());
            case HOLD -> 1f;
            case CONTRACT -> easeOutCubic(1f - contractProgress());
            default -> 0f;
        };
    }

    /**
     * Independent disc-expansion curve in {@code [0, 1]}.
     * @return TODO RETURN DESCRIPTION
     */
    public float getDiskExpansionScale() {
        return switch (phase) {
            case EXPAND -> DISK_EXPAND_PEAK * expandProgress();
            case HOLD -> DISK_EXPAND_PEAK + (1f - DISK_EXPAND_PEAK) * holdProgress();
            case CONTRACT -> 1f - contractProgress();
            default -> 0f;
        };
    }

    private float expandProgress() {
        if (initialExpandTicks <= 0) { return 0f; }
        return 1f - ((float) expandTicksRemaining / initialExpandTicks);
    }

    private float holdProgress() {
        if (initialHoldTicks <= 0) { return 0f; }
        return 1f - ((float) holdTicksRemaining / initialHoldTicks);
    }

    private float contractProgress() {
        if (initialContractTicks <= 0) { return 0f; }
        return 1f - ((float) contractTicksRemaining / initialContractTicks);
    }

    /**
     * Cubic ease-out: {@code 1 - (1 - t)^3}.
     * @param t TODO PARAM DESCRIPTION
     * @return TODO RETURN DESCRIPTION
     */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }


    @Override
    public void saveAdditional(ValueOutput output) {
        output.putString(TAG_PHASE, phase.name());
        output.putInt(TAG_EXPAND_REMAINING, expandTicksRemaining);
        output.putInt(TAG_EXPAND_INITIAL, initialExpandTicks);
        output.putInt(TAG_HOLD_REMAINING, holdTicksRemaining);
        output.putInt(TAG_HOLD_INITIAL, initialHoldTicks);
        output.putInt(TAG_CONTRACT_REMAINING, contractTicksRemaining);
        output.putInt(TAG_CONTRACT_INITIAL, initialContractTicks);
        output.putInt(TAG_IMPLODE_RADIUS, implodeRadius);
        output.putInt(TAG_STACK_SNAPSHOT, stackCount);
        if (!accumulator.isEmpty()) {
            output.store(TAG_ACCUMULATOR, GooContents.CODEC, accumulator);
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        phase = parsePhase(input.getStringOr(TAG_PHASE, Phase.DONE.name()));
        expandTicksRemaining = input.getIntOr(TAG_EXPAND_REMAINING, 0);
        initialExpandTicks = input.getIntOr(TAG_EXPAND_INITIAL, 0);
        holdTicksRemaining = input.getIntOr(TAG_HOLD_REMAINING, 0);
        initialHoldTicks = input.getIntOr(TAG_HOLD_INITIAL, 0);
        contractTicksRemaining = input.getIntOr(TAG_CONTRACT_REMAINING, 0);
        initialContractTicks = input.getIntOr(TAG_CONTRACT_INITIAL, 0);
        implodeRadius = input.getIntOr(TAG_IMPLODE_RADIUS, 0);
        stackCount = input.getIntOr(TAG_STACK_SNAPSHOT, 1);
        accumulator = input.read(TAG_ACCUMULATOR, GooContents.CODEC).orElse(GooContents.EMPTY);
    }

    private static Phase parsePhase(String name) {
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Phase.DONE;
        }
    }

    // --- Sphere-walk goo-accumulation utility (used by tickExpand) ---

    /**
     * Walks the spherical volume once, accumulates per-type mB totals,
     * and removes the affected blocks. Called at the EXPAND -&gt; HOLD
     * transition, when the black-hole sphere is fully grown.
     *
     * @param level  the server level
     * @param pos    the marker position (skipped during the walk)
     * @param range  effect radius in blocks
     * @return the accumulated totals, ready to be dropped at POPPING
     */
    public static GooContents walkAndDestroy(ServerLevel level, BlockPos pos, int range) {
        Map<GooType, Integer> totals = new EnumMap<>(GooType.class);
        AbilityMath.forEachInSphere(pos, range, target -> {
            if (target.equals(pos)) { return; }
            accumulateAndRemove(level, target, totals);
        });
        return new GooContents(totals);
    }

    /**
     * Processes a single position in the sphere: blocks with a registered
     * goo value contribute their full composition and are removed,
     * anything else is left untouched.
     * @param level TODO PARAM DESCRIPTION
     * @param target TODO PARAM DESCRIPTION
     * @param totals TODO PARAM DESCRIPTION
     */
    private static void accumulateAndRemove(ServerLevel level, BlockPos target,
                                            Map<GooType, Integer> totals) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) { return; }
        tryAccumulateValuedBlock(level, target, state, totals);
    }

    /**
     * If the block has a registered goo value, merges its full composition
     * into {@code totals} and removes the block.
     * @param level TODO PARAM DESCRIPTION
     * @param target TODO PARAM DESCRIPTION
     * @param state TODO PARAM DESCRIPTION
     * @param totals TODO PARAM DESCRIPTION
     */
    private static void tryAccumulateValuedBlock(ServerLevel level, BlockPos target,
                                                 BlockState state, Map<GooType, Integer> totals) {
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) { return; }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) { return; }
        mergeValue(totals, value);
        level.removeBlock(target, false);
    }

    /**
     * Pure helper: merges a {@link GooValue}'s per-type amounts into a
     * shared totals map. Extracted so unit tests can verify accumulation
     * without spinning up a server level.
     *
     * @param totals the accumulator to merge into
     * @param value  the goo value to add
     */
    public static void mergeValue(Map<GooType, Integer> totals, GooValue value) {
        for (Map.Entry<GooType, Integer> entry : value.getAll().entrySet()) {
            int amount = entry.getValue();
            totals.merge(entry.getKey(), amount, Integer::sum);
        }
    }
}
