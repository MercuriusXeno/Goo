package com.mercuriusxeno.goo.ability.mob;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.MobAbilityRegistry;
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
public final class MobAbilities {

    /** Per-type effect handler map. */
    private static final Map<GooType, Consumer<EffectContext>> EFFECTS =
            new EnumMap<>(Map.ofEntries(
        Map.entry(GooType.METAL, ctx -> MetalJavelin.apply(ctx.target())),
        Map.entry(GooType.CRYSTAL, ctx -> CrystalFlechettes.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.LEAF, ctx -> LeafEntangle.apply(ctx.target())),
        Map.entry(GooType.VITAL, ctx -> VitalClone.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.SHROOM, ctx -> ShroomToxify.apply(ctx.target())),
        Map.entry(GooType.ROCK, ctx -> RockPetrify.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.BLAZE, ctx -> BlazeIgnite.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.FROST, ctx -> FrostSnap.apply(ctx.target())),
        Map.entry(GooType.TYPHOON, ctx -> TyphoonLevitate.apply(ctx.target())),
        Map.entry(GooType.GLOW, ctx -> GlowLaser.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.HEX, ctx -> HexCharm.apply(ctx.target(), ctx.thrower())),
        Map.entry(GooType.PULSE, ctx -> PulseShortCircuit.apply(ctx.target())),
        Map.entry(GooType.NETHER, ctx -> NetherWither.apply(ctx.target())),
        Map.entry(GooType.ENDER, ctx -> EnderTeleport.apply(ctx.level(), ctx.target())),
        Map.entry(GooType.AEON, ctx -> AeonTimeStop.apply(ctx.target())),
        Map.entry(GooType.UNSTABLE, ctx -> UnstableExplode.apply(ctx.level(), ctx.target()))));

    /** Dispatch context: all parameters an effect handler might need. */
    private record EffectContext(Level level, LivingEntity target, @Nullable Entity thrower) {}

    // ── Handler name constants ──
    public static final String METAL_JAVELIN = "metal_javelin";
    public static final String CRYSTAL_FLECHETTES = "crystal_flechettes";
    public static final String LEAF_ENTANGLE = "leaf_entangle";
    public static final String VITAL_CLONE = "vital_clone";
    public static final String SHROOM_TOXIFY = "shroom_debuff";
    public static final String ROCK_PETRIFY = "rock_petrify";
    public static final String BLAZE_IGNITE = "blaze_ignite";
    public static final String FROST_SNAP = "frost_snap";
    public static final String TYPHOON_LEVITATE = "typhoon_levitate";
    public static final String GLOW_LASER = "glow_laser";
    public static final String HEX_CHARM = "hex_charm";
    public static final String PULSE_SHORT_CIRCUIT = "pulse_short_circuit";
    public static final String NETHER_WITHER = "nether_wither";
    public static final String ENDER_TELEPORT = "ender_teleport";
    public static final String AEON_TIME_STOP = "aeon_time_stop";
    public static final String UNSTABLE_EXPLODE = "unstable_explode";

    /** String-keyed handler map for data-driven entity_effect dispatch. */
    private static final Map<String, Consumer<EffectContext>> NAMED = buildNamedMap();

    private MobAbilities() {}

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
    public static void applyNamed(String name, MobAbilityRegistry.Context ctx) {
        if (ctx.level().isClientSide()) { return; }
        var handler = NAMED.get(name);
        if (handler != null) {
            handler.accept(new EffectContext(ctx.level(), ctx.target(), ctx.thrower()));
        }
    }

    private static Map<String, Consumer<EffectContext>> buildNamedMap() {
        Map<String, Consumer<EffectContext>> map = new HashMap<>();
        map.put(METAL_JAVELIN, ctx -> MetalJavelin.apply(ctx.target()));
        map.put(CRYSTAL_FLECHETTES, ctx -> CrystalFlechettes.apply(ctx.level(), ctx.target()));
        map.put(LEAF_ENTANGLE, ctx -> LeafEntangle.apply(ctx.target()));
        map.put(VITAL_CLONE, ctx -> VitalClone.apply(ctx.level(), ctx.target()));
        map.put(SHROOM_TOXIFY, ctx -> ShroomToxify.apply(ctx.target()));
        map.put(ROCK_PETRIFY, ctx -> RockPetrify.apply(ctx.level(), ctx.target()));
        map.put(BLAZE_IGNITE, ctx -> BlazeIgnite.apply(ctx.level(), ctx.target()));
        map.put(FROST_SNAP, ctx -> FrostSnap.apply(ctx.target()));
        map.put(TYPHOON_LEVITATE, ctx -> TyphoonLevitate.apply(ctx.target()));
        map.put(GLOW_LASER, ctx -> GlowLaser.apply(ctx.level(), ctx.target()));
        map.put(HEX_CHARM, ctx -> HexCharm.apply(ctx.target(), ctx.thrower()));
        map.put(PULSE_SHORT_CIRCUIT, ctx -> PulseShortCircuit.apply(ctx.target()));
        map.put(NETHER_WITHER, ctx -> NetherWither.apply(ctx.target()));
        map.put(ENDER_TELEPORT, ctx -> EnderTeleport.apply(ctx.level(), ctx.target()));
        map.put(AEON_TIME_STOP, ctx -> AeonTimeStop.apply(ctx.target()));
        map.put(UNSTABLE_EXPLODE, ctx -> UnstableExplode.apply(ctx.level(), ctx.target()));
        return Map.copyOf(map);
    }
}
