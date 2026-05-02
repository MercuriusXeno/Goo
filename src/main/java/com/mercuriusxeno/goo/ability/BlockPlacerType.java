package com.mercuriusxeno.goo.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * Named registry of {@link BlockPlacer} singletons. JSON ability
 * params carry a string name (e.g. {@code "glow_crystal"}); the
 * pipeline resolves it once at construction into the singleton placer
 * and invokes it on fuse expiry without further string lookups.
 */
public final class BlockPlacerType {

    /** Glow crystal placer: FACING/SHAPE/SIZE derived from marker state. */
    public static final String GLOW_CRYSTAL = "glow_crystal";

    private static final String UNKNOWN_PREFIX = "Unknown BlockPlacer: ";

    private static final Map<String, BlockPlacer> PLACERS = new HashMap<>();

    static {
        PLACERS.put(GLOW_CRYSTAL, GlowCrystalPlacer.INSTANCE);
    }

    private BlockPlacerType() {
    }

    /**
     * Looks up a placer by registered name.
     *
     * @param name the registered placer name
     * @return the singleton placer
     * @throws IllegalArgumentException if no placer is registered under {@code name}
     */
    public static BlockPlacer byName(String name) {
        BlockPlacer placer = PLACERS.get(name);
        if (placer == null) {
            throw new IllegalArgumentException(UNKNOWN_PREFIX + name);
        }
        return placer;
    }

    /**
     * Registers a placer under a name. Intended for downstream extension.
     *
     * @param name   the registered name (must be unique)
     * @param placer the placer to register
     */
    public static void register(String name, BlockPlacer placer) {
        PLACERS.put(name, placer);
    }
}
