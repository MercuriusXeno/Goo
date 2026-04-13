package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Central registry for chain effect profiles. Each goo type with a chain
 * effect gets a profile that defines fuse duration, max stacks, range
 * formula, and a factory for the post-fuse {@link ChainBehavior}.
 * Profiles are registered during mod init and looked up at runtime by
 * the chain marker block entity.
 */
public final class ChainProfiles {

    private static final int BLAZE_FUSE_TICKS = 30;
    private static final int BLAZE_MAX_STACKS = ChainFootprint.MAX_STACKS;
    private static final int ROCK_FUSE_TICKS = 30;
    private static final int ROCK_MAX_STACKS = ChainFootprint.MAX_STACKS;
    private static final int NETHER_FUSE_TICKS = 30;
    private static final int NETHER_MAX_STACKS = 5;
    private static final int FROST_FUSE_TICKS = 30;
    private static final int FROST_MAX_STACKS = 4;
    private static final int METAL_FUSE_TICKS = 30;
    private static final int METAL_MAX_STACKS = 8;

    private ChainProfiles() {}

    /** Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}. */
    public static void registerAll() {
        registerBlaze();
        registerRock();
        registerNether();
        registerFrost();
        registerMetal();
    }

    /** Registers the blaze chain profile. */
    private static void registerBlaze() {
        ChainProfile.register(GooType.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                BlazeBehavior::new
        ));
    }

    /** Registers the rock chain profile. */
    private static void registerRock() {
        ChainProfile.register(GooType.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                RockBehavior::new
        ));
    }

    /** Registers the frost chain profile. */
    private static void registerFrost() {
        ChainProfile.register(GooType.FROST, new ChainProfile(
                FROST_FUSE_TICKS,
                FROST_MAX_STACKS,
                EffectMath::computeFreezeRadius,
                FrostBehavior::new
        ));
    }

    /** Registers the metal chain profile. */
    private static void registerMetal() {
        ChainProfile.register(GooType.METAL, new ChainProfile(
                METAL_FUSE_TICKS,
                METAL_MAX_STACKS,
                stacks -> 1,
                MetalBehavior::new
        ));
    }

    /** Registers the nether chain profile. */
    private static void registerNether() {
        ChainProfile.register(GooType.NETHER, new ChainProfile(
                NETHER_FUSE_TICKS,
                NETHER_MAX_STACKS,
                EffectMath::computeNetherRadius,
                NetherBehavior::new
        ));
    }

    // ── Profile definition ────────────────────────────────────────────────

    /**
     * Defines the behavior of a chain effect for a specific goo type.
     * The {@code behaviorFactory} is called when the fuse expires to
     * produce a fresh {@link ChainBehavior} that owns the type-specific
     * post-fuse lifecycle.
     *
     * @param fuseTicks       how long the fuse window lasts
     * @param maxStacks       maximum stack count (additional blobs during fuse)
     * @param rangeFormula    computes range/depth from stack count
     * @param behaviorFactory factory that creates a fresh {@link ChainBehavior}
     */
    public record ChainProfile(
            int fuseTicks,
            int maxStacks,
            IntUnaryOperator rangeFormula,
            Supplier<ChainBehavior> behaviorFactory
    ) {
        private static final Map<GooType, ChainProfile> PROFILES = new EnumMap<>(GooType.class);

        /**
         * Registers a chain profile for a goo type. Called during mod init.
         *
         * @param type    the goo type
         * @param profile the chain profile definition
         */
        public static void register(GooType type, ChainProfile profile) {
            PROFILES.put(type, profile);
        }

        /**
         * Looks up the profile for a goo type. Returns null if unregistered.
         *
         * @param type the goo type
         * @return the chain profile, or null if none registered
         */
        public static ChainProfile forType(GooType type) {
            return PROFILES.get(type);
        }

        /**
         * Returns true if the given goo type has a registered chain profile.
         *
         * @param type the goo type to check
         * @return true if a chain profile exists
         */
        public static boolean isChainType(GooType type) {
            return PROFILES.containsKey(type);
        }
    }
}
