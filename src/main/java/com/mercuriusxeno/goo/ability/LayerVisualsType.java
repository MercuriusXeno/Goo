package com.mercuriusxeno.goo.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * Named registry of {@link LayerVisuals} singletons. JSON ability
 * params carry a string name (e.g. {@code "rock_dust"}); the pipeline
 * resolves it once at fuse expiry into the singleton visuals and then
 * fans out per-step calls without further string lookups.
 */
public final class LayerVisualsType {

    /** Sonic-boom preview + dust-plume on struck. */
    public static final String ROCK_DUST = "rock_dust";
    /** Per-block flame preview + flame/lava/ember on struck. */
    public static final String BLAZE_FLAME = "blaze_flame";
    /** No-op for pipelines that emit no per-layer particles. */
    public static final String NONE = "none";

    private static final String UNKNOWN_PREFIX = "Unknown LayerVisuals: ";

    private static final Map<String, LayerVisuals> VISUALS = new HashMap<>();

    static {
        VISUALS.put(ROCK_DUST, RockDustVisuals.INSTANCE);
        VISUALS.put(BLAZE_FLAME, BlazeFlameVisuals.INSTANCE);
        VISUALS.put(NONE, NoneLayerVisuals.INSTANCE);
    }

    private LayerVisualsType() {
    }

    /**
     * Looks up visuals by registered name.
     *
     * @param name the registered visuals name
     * @return the singleton visuals
     * @throws IllegalArgumentException if no visuals are registered under {@code name}
     */
    public static LayerVisuals byName(String name) {
        LayerVisuals visuals = VISUALS.get(name);
        if (visuals == null) {
            throw new IllegalArgumentException(UNKNOWN_PREFIX + name);
        }
        return visuals;
    }

    /**
     * Registers visuals under a name. Intended for downstream extension.
     *
     * @param name    the registered name (must be unique)
     * @param visuals the visuals to register
     */
    public static void register(String name, LayerVisuals visuals) {
        VISUALS.put(name, visuals);
    }
}
