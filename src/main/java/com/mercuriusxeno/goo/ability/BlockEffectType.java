package com.mercuriusxeno.goo.ability;

import java.util.HashMap;
import java.util.Map;

/**
 * Named registry of {@link BlockEffect} singletons. JSON ability params
 * carry a string name (e.g. {@code "silk_break"}); the pipeline resolves
 * it once at fuse expiry into the singleton effect and then fans out
 * per-cell calls without further string lookups.
 */
public final class BlockEffectType {

    /** Silk-touch break: drops blocks intact, no smelt. */
    public static final String SILK_BREAK = "silk_break";
    /** Fortune-3 break with auto-smelt of drops. */
    public static final String FORTUNE_SMELT_BREAK = "fortune_smelt_break";
    /** Freeze conversion: water -> magicked ice, lava -> obsidian, plants/fire -> air. */
    public static final String FREEZE = "freeze";

    private static final String UNKNOWN_PREFIX = "Unknown BlockEffect: ";

    private static final Map<String, BlockEffect> EFFECTS = new HashMap<>();

    static {
        EFFECTS.put(SILK_BREAK, SilkBreakEffect.INSTANCE);
        EFFECTS.put(FORTUNE_SMELT_BREAK, FortuneSmeltBreakEffect.INSTANCE);
        EFFECTS.put(FREEZE, FreezeEffect.INSTANCE);
    }

    private BlockEffectType() {
    }

    /**
     * Looks up an effect by registered name.
     *
     * @param name the registered effect name
     * @return the singleton effect
     * @throws IllegalArgumentException if no effect is registered under {@code name}
     */
    public static BlockEffect byName(String name) {
        BlockEffect effect = EFFECTS.get(name);
        if (effect == null) {
            throw new IllegalArgumentException(UNKNOWN_PREFIX + name);
        }
        return effect;
    }

    /**
     * Registers a BlockEffect under a name. Intended for downstream
     * extension; the core three are registered in the static block.
     *
     * @param name   the registered name (must be unique)
     * @param effect the effect to register
     */
    public static void register(String name, BlockEffect effect) {
        EFFECTS.put(name, effect);
    }
}
