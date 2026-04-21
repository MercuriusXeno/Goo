package com.mercuriusxeno.goo.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * String-keyed registry of entity effect handlers. Each handler name
 * corresponds to one data-driven entity_effect behavior's "handler"
 * param. The actual effect logic lives in GooMobEffects; this registry
 * maps handler names to those methods.
 */
public final class EntityEffectRegistry {

    /** Dispatch context for entity effect handlers.
     *
     * @param level   the current level
     * @param target  the entity being affected
     * @param thrower the entity that threw the blob, or null
     */
    public record Context(Level level, LivingEntity target, @Nullable Entity thrower) {}

    private static final Map<String, Consumer<Context>> HANDLERS = new HashMap<>();

    static {
        register(GooMobEffects.H_METAL_JAVELIN, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_METAL_JAVELIN, ctx));
        register(GooMobEffects.H_CRYSTAL_FLECHETTES, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_CRYSTAL_FLECHETTES, ctx));
        register(GooMobEffects.H_LEAF_ENTANGLE, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_LEAF_ENTANGLE, ctx));
        register(GooMobEffects.H_VITAL_CLONE, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_VITAL_CLONE, ctx));
        register(GooMobEffects.H_SHROOM_DEBUFF, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_SHROOM_DEBUFF, ctx));
        register(GooMobEffects.H_ROCK_PETRIFY, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_ROCK_PETRIFY, ctx));
        register(GooMobEffects.H_BLAZE_IGNITE, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_BLAZE_IGNITE, ctx));
        register(GooMobEffects.H_FROST_SNAP, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_FROST_SNAP, ctx));
        register(GooMobEffects.H_TYPHOON_LEVITATE, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_TYPHOON_LEVITATE, ctx));
        register(GooMobEffects.H_GLOW_LASER, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_GLOW_LASER, ctx));
        register(GooMobEffects.H_HEX_CHARM, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_HEX_CHARM, ctx));
        register(GooMobEffects.H_PULSE_SHORT_CIRCUIT, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_PULSE_SHORT_CIRCUIT, ctx));
        register(GooMobEffects.H_NETHER_WITHER, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_NETHER_WITHER, ctx));
        register(GooMobEffects.H_ENDER_TELEPORT, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_ENDER_TELEPORT, ctx));
        register(GooMobEffects.H_AEON_TIME_STOP, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_AEON_TIME_STOP, ctx));
        register(GooMobEffects.H_UNSTABLE_EXPLODE, ctx -> GooMobEffects.applyNamed(GooMobEffects.H_UNSTABLE_EXPLODE, ctx));
    }

    private EntityEffectRegistry() {}

    /** Registers a named entity effect handler.
     *
     * @param name    the handler name matching JSON "handler" param
     * @param handler the effect consumer
     */
    public static void register(String name, Consumer<Context> handler) {
        HANDLERS.put(name, handler);
    }

    /** Looks up a handler by name.
     *
     * @param name the handler name
     * @return the handler, or null if not registered
     */
    public static @Nullable Consumer<Context> get(String name) {
        return HANDLERS.get(name);
    }
}
