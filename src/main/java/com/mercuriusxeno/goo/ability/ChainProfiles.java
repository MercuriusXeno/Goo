package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.world.*;
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
    private static final int CRYSTAL_FUSE_TICKS = 30;
    private static final int CRYSTAL_MAX_STACKS = 8;
    private static final int UNSTABLE_FUSE_TICKS = 20;
    private static final int UNSTABLE_MAX_STACKS = 8;
    private static final int GLOW_FUSE_TICKS = 30;
    private static final int GLOW_MAX_STACKS = 4;
    private static final int DEFAULT_PREVIEW_DELAY = 8;
    private static final String AREA_TUNNEL = "tunnel";

    private ChainProfiles() {
    }

    /**
     * Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}.
     */
    public static void registerAll() {
        registerBlaze();
        registerRock();
        registerNether();
        registerFrost();
        registerMetal();
        registerCrystal();
        registerUnstable();
        registerGlow();
    }

    /**
     * Registers the blaze chain profile. Legacy non-ability path
     * (no abilityId selected) builds a tunnel ProgressiveAreaBlock
     * with fortune-smelt + blaze visuals + generic-explode audio,
     * matching the {@code blaze_tunnel} ability defaults.
     */
    private static void registerBlaze() {
        ChainProfile.register(GooType.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                () -> new ProgressiveAreaBlock(
                        AREA_TUNNEL,
                        BlockEffectType.byName(BlockEffectType.FORTUNE_SMELT_BREAK),
                        LayerVisualsType.byName(LayerVisualsType.BLAZE_FLAME),
                        LayerAudioType.byName(LayerAudioType.GENERIC_EXPLODE),
                        DEFAULT_PREVIEW_DELAY)
        ));
    }

    /**
     * Registers the rock chain profile. Legacy non-ability path
     * (no abilityId selected) builds a tunnel ProgressiveAreaBlock
     * with silk-break + rock visuals + stone-break audio, matching
     * the {@code rock_tunnel} ability defaults.
     */
    private static void registerRock() {
        ChainProfile.register(GooType.ROCK, new ChainProfile(
                ROCK_FUSE_TICKS,
                ROCK_MAX_STACKS,
                ChainFootprint::tunnelDepth,
                () -> new ProgressiveAreaBlock(
                        AREA_TUNNEL,
                        BlockEffectType.byName(BlockEffectType.SILK_BREAK),
                        LayerVisualsType.byName(LayerVisualsType.ROCK_DUST),
                        LayerAudioType.byName(LayerAudioType.STONE_BREAK),
                        DEFAULT_PREVIEW_DELAY)
        ));
    }

    /**
     * Registers the crystal chain profile.
     */
    private static void registerCrystal() {
        ChainProfile.register(GooType.CRYSTAL, new ChainProfile(
                CRYSTAL_FUSE_TICKS,
                CRYSTAL_MAX_STACKS,
                stacks -> 1,
                CrystalBehavior::new
        ));
    }

    /**
     * Registers the unstable chain profile.
     */
    private static void registerUnstable() {
        ChainProfile.register(GooType.UNSTABLE, new ChainProfile(
                UNSTABLE_FUSE_TICKS,
                UNSTABLE_MAX_STACKS,
                stacks -> 1,
                UnstableBehavior::new
        ));
    }

    /**
     * Registers the frost chain profile.
     */
    private static void registerFrost() {
        ChainProfile.register(GooType.FROST, new ChainProfile(
                FROST_FUSE_TICKS,
                FROST_MAX_STACKS,
                AbilityMath::computeFreezeRadius,
                FrostBehavior::new
        ));
    }

    /**
     * Registers the metal chain profile.
     */
    private static void registerMetal() {
        ChainProfile.register(GooType.METAL, new ChainProfile(
                METAL_FUSE_TICKS,
                METAL_MAX_STACKS,
                stacks -> 1,
                MetalBehavior::new
        ));
    }

    /**
     * Registers the glow chain profile. Legacy non-ability path
     * (no abilityId selected) builds a BlockPlaceBehavior with the
     * glow-crystal placer, matching the {@code glow_crystal} ability
     * defaults.
     */
    private static void registerGlow() {
        ChainProfile.register(GooType.GLOW, new ChainProfile(
                GLOW_FUSE_TICKS,
                GLOW_MAX_STACKS,
                stacks -> 1,
                () -> new BlockPlaceBehavior(
                        BlockPlacerType.byName(BlockPlacerType.GLOW_CRYSTAL))
        ));
    }

    /**
     * Registers the nether chain profile.
     */
    private static void registerNether() {
        ChainProfile.register(GooType.NETHER, new ChainProfile(
                NETHER_FUSE_TICKS,
                NETHER_MAX_STACKS,
                AbilityMath::computeNetherRadius,
                NetherBehavior::new
        ));
    }


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
