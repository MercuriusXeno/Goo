package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.ability.world.CrystalBehavior;
import com.mercuriusxeno.goo.ability.world.GlowBehavior;
import com.mercuriusxeno.goo.ability.world.MetalBehavior;
import com.mercuriusxeno.goo.ability.world.NetherBehavior;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry of behavior building block factories. Each registered type
 * name maps to a factory that creates a {@link ChainBehavior} from
 * the JSON parameters in a {@link BehaviorEntry}.
 */
public final class BehaviorType {

    /** Factory that creates a ChainBehavior from parsed parameters. */
    @FunctionalInterface
    public interface Factory {

        /**
         * Creates a behavior from the given entry's parameters.
         *
         * @param entry the behavior entry with type and params
         * @param def   the parent ability definition for context
         * @return a new chain behavior instance
         */
        ChainBehavior create(BehaviorEntry entry, AbilityDefinition def);
    }

    private static final Map<String, Factory> FACTORIES = new HashMap<>();

    static {
        register("explosion", ParameterizedExplosion::fromEntry);
        register("progressive_area", ProgressiveAreaBlock::fromEntry);
        register("glow_crystal", (e, d) -> new GlowBehavior());
        register("metal_spikes", (e, d) -> new MetalBehavior());
        register("crystal_cloud", (e, d) -> new CrystalBehavior());
        register("black_hole", (e, d) -> new NetherBehavior());
        register("entity_effect", MobAbilityBehavior::fromEntry);
    }

    private BehaviorType() {}

    /**
     * Registers a behavior type factory.
     *
     * @param name    the type name matching JSON "type" field
     * @param factory the factory function
     */
    public static void register(String name, Factory factory) {
        FACTORIES.put(name, factory);
    }

    /**
     * Creates a behavior from a behavior entry.
     *
     * @param entry the behavior entry
     * @param def   the parent ability definition
     * @return the created behavior
     * @throws IllegalArgumentException if the type name is unknown
     */
    public static ChainBehavior create(BehaviorEntry entry, AbilityDefinition def) {
        Factory factory = FACTORIES.get(entry.type());
        if (factory == null) {
            throw new IllegalArgumentException(entry.type());
        }
        return factory.create(entry, def);
    }
}
