package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Short-lived fuse entity for chain effects (Blaze, Frost, Nether, Rock).
 * Configured per goo type via {@link ChainProfile} - each type defines its
 * own fuse duration, max stacks, range formula, and execute behavior.
 * Additional blobs landing within the fuse window increment the stack count.
 * On fuse expiry, computes range from stacks and fires the executor.
 */
public class ChainMarkerEffect extends GooWorldEffect {

    private static final String TAG_FUSE_REMAINING = "FuseRemaining";

    private int fuseRemaining;

    public ChainMarkerEffect(EntityType<? extends ChainMarkerEffect> type, Level level) {
        super(type, level);
        this.fuseRemaining = 0;
    }

    // ── Profile-aware initialization ──────────────────────────────────────

    /**
     * Configures this marker from the profile for its goo type.
     * Call after {@link #init(BlockPos, GooType, int)}.
     */
    public void initFromProfile() {
        ChainProfile profile = ChainProfile.forType(getGooType());
        fuseRemaining = profile.fuseTicks();
    }

    /**
     * Convenience: init + profile in one call.
     *
     * @param anchor   the block position to anchor to
     * @param gooType  the goo type (determines chain behavior)
     */
    public void initChain(BlockPos anchor, GooType gooType) {
        ChainProfile profile = ChainProfile.forType(gooType);
        init(anchor, gooType, profile.maxStacks());
        fuseRemaining = profile.fuseTicks();
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        fuseRemaining--;
        if (!EffectMath.isFuseLive(fuseRemaining)) {
            ChainProfile profile = ChainProfile.forType(getGooType());
            int range = profile.rangeFormula().applyAsInt(getStackCount());
            profile.executor().execute((ServerLevel) level(), getAnchorPos(), range, getStackCount());
            discard();
        }
    }

    public int getFuseRemaining() {
        return fuseRemaining;
    }

    // ── Persistence ───────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fuseRemaining = input.getIntOr(TAG_FUSE_REMAINING, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt(TAG_FUSE_REMAINING, fuseRemaining);
    }

    // ── Profile ───────────────────────────────────────────────────────────

    /**
     * Defines the behavior of a chain effect for a specific goo type.
     *
     * @param fuseTicks    how long the fuse window lasts
     * @param maxStacks    maximum stack count (additional blobs during fuse)
     * @param rangeFormula computes range/depth from stack count
     * @param executor     fires the actual effect on fuse expiry
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula,
            ChainExecutor executor
    ) {
        private static final Map<GooType, ChainProfile> PROFILES = new EnumMap<>(GooType.class);

        /**
         * Registers a chain profile for a goo type. Called during mod init
         * by each effect's setup code.
         */
        public static void register(GooType type, ChainProfile profile) {
            PROFILES.put(type, profile);
        }

        /**
         * Looks up the profile for a goo type. Returns null if the type
         * has no chain effect registered.
         */
        public static ChainProfile forType(GooType type) {
            return PROFILES.get(type);
        }

        /** Returns true if the given goo type has a registered chain profile. */
        public static boolean isChainType(GooType type) {
            return PROFILES.containsKey(type);
        }
    }

    /**
     * Functional interface for the chain effect's execute behavior.
     * Receives the server level, anchor position, computed range, and stack count.
     */
    @FunctionalInterface
    public interface ChainExecutor {
        /**
         * Fires the chain effect.
         *
         * @param level      the server level
         * @param pos        the anchor block position
         * @param range      computed from rangeFormula(stackCount)
         * @param stackCount the raw stack count (for effects that need it directly)
         */
        void execute(ServerLevel level, BlockPos pos, int range, int stackCount);
    }
}
