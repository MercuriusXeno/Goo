package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies goo-type-specific mob effects when blobs hit living entities.
 * Each goo type has a unique effect dispatched through a type-indexed map.
 */
public final class GooMobEffects {

    /** Per-type effect handler map. */
    private static final Map<GooType, Consumer<EffectContext>> EFFECTS =
            new EnumMap<>(Map.ofEntries(
        Map.entry(GooType.METAL, ctx -> MetalMobEffect.apply(ctx.target())),
        Map.entry(GooType.CRYSTAL, ctx -> CrystalMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.LEAF, ctx -> LeafMobEffect.apply(ctx.target())),
        Map.entry(GooType.VITAL, ctx -> VitalMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.SHROOM, ctx -> ShroomMobEffect.apply(ctx.target())),
        Map.entry(GooType.ROCK, ctx -> RockMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.BLAZE, ctx -> BlazeMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.FROST, ctx -> FrostMobEffect.apply(ctx.target())),
        Map.entry(GooType.TYPHOON, ctx -> TyphoonMobEffect.apply(ctx.target())),
        Map.entry(GooType.GLOW, ctx -> GlowMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.HEX, ctx -> HexMobEffect.apply(ctx.target(), ctx.thrower())),
        Map.entry(GooType.PULSE, ctx -> PulseMobEffect.apply(ctx.target())),
        Map.entry(GooType.NETHER, ctx -> NetherMobEffect.apply(ctx.target())),
        Map.entry(GooType.ENDER, ctx -> EnderMobEffect.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.AEON, ctx -> AeonMobEffect.apply(ctx.target())),
        Map.entry(GooType.UNSTABLE, ctx -> UnstableMobEffect.apply(ctx.level(), ctx.target()))));

    /** Dispatch context: all parameters an effect handler might need. */
    private record EffectContext(Level level, LivingEntity target, @Nullable Entity thrower) {}

    // ── Handler name constants ──
    static final String H_METAL_JAVELIN = "metal_javelin";
    static final String H_CRYSTAL_FLECHETTES = "crystal_flechettes";
    static final String H_LEAF_ENTANGLE = "leaf_entangle";
    static final String H_VITAL_CLONE = "vital_clone";
    static final String H_SHROOM_DEBUFF = "shroom_debuff";
    static final String H_ROCK_PETRIFY = "rock_petrify";
    static final String H_BLAZE_IGNITE = "blaze_ignite";
    static final String H_FROST_SNAP = "frost_snap";
    static final String H_TYPHOON_LEVITATE = "typhoon_levitate";
    static final String H_GLOW_LASER = "glow_laser";
    static final String H_HEX_CHARM = "hex_charm";
    static final String H_PULSE_SHORT_CIRCUIT = "pulse_short_circuit";
    static final String H_NETHER_WITHER = "nether_wither";
    static final String H_ENDER_TELEPORT = "ender_teleport";
    static final String H_AEON_TIME_STOP = "aeon_time_stop";
    static final String H_UNSTABLE_EXPLODE = "unstable_explode";

    /** String-keyed handler map for data-driven entity_effect dispatch. */
    private static final Map<String, Consumer<EffectContext>> NAMED = buildNamedMap();

    private GooMobEffects() {}

    /**
     * Applies the mob effect for the given goo type to a living entity.
     *
     * @param level   the world
     * @param target  the entity to affect
     * @param type    the goo type whose effect to apply
     * @param thrower the entity that threw the blob, or null if unknown
     */
    public static void apply(Level level, LivingEntity target, GooType type, @Nullable Entity thrower) {
        if (level.isClientSide()) { return; }
        var handler = EFFECTS.get(type);
        if (handler != null) { handler.accept(new EffectContext(level, target, thrower)); }
    }

    /**
     * Applies a named entity effect handler. Used by EntityEffectRegistry
     * for data-driven ability dispatch.
     *
     * @param name the handler name (e.g., "metal_javelin")
     * @param ctx  the entity effect context
     */
    public static void applyNamed(String name, EntityEffectRegistry.Context ctx) {
        if (ctx.level().isClientSide()) { return; }
        var handler = NAMED.get(name);
        if (handler != null) {
            handler.accept(new EffectContext(ctx.level(), ctx.target(), ctx.thrower()));
        }
    }

    private static Map<String, Consumer<EffectContext>> buildNamedMap() {
        Map<String, Consumer<EffectContext>> map = new HashMap<>();
        map.put(H_METAL_JAVELIN, ctx -> MetalMobEffect.apply(ctx.target()));
        map.put(H_CRYSTAL_FLECHETTES, ctx -> CrystalMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_LEAF_ENTANGLE, ctx -> LeafMobEffect.apply(ctx.target()));
        map.put(H_VITAL_CLONE, ctx -> VitalMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_SHROOM_DEBUFF, ctx -> ShroomMobEffect.apply(ctx.target()));
        map.put(H_ROCK_PETRIFY, ctx -> RockMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_BLAZE_IGNITE, ctx -> BlazeMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_FROST_SNAP, ctx -> FrostMobEffect.apply(ctx.target()));
        map.put(H_TYPHOON_LEVITATE, ctx -> TyphoonMobEffect.apply(ctx.target()));
        map.put(H_GLOW_LASER, ctx -> GlowMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_HEX_CHARM, ctx -> HexMobEffect.apply(ctx.target(), ctx.thrower()));
        map.put(H_PULSE_SHORT_CIRCUIT, ctx -> PulseMobEffect.apply(ctx.target()));
        map.put(H_NETHER_WITHER, ctx -> NetherMobEffect.apply(ctx.target()));
        map.put(H_ENDER_TELEPORT, ctx -> EnderMobEffect.apply(ctx.level(), ctx.target()));
        map.put(H_AEON_TIME_STOP, ctx -> AeonMobEffect.apply(ctx.target()));
        map.put(H_UNSTABLE_EXPLODE, ctx -> UnstableMobEffect.apply(ctx.level(), ctx.target()));
        return Map.copyOf(map);
    }
}
