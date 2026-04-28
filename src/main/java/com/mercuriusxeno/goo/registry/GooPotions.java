package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class GooPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Goo.MODID);
    public static final Map<GooType, DeferredHolder<Potion, Potion>> GOO_POTIONS = new EnumMap<>(GooType.class);
    /**
     * Standard long potion duration: 60 seconds at 20 tps.
     */
    private static final int DURATION_LONG = 1200;
    /**
     * Standard medium potion duration: 30 seconds at 20 tps.
     */
    private static final int DURATION_MEDIUM = 600;
    /**
     * Standard short potion duration: 10 seconds at 20 tps.
     */
    private static final int DURATION_SHORT = 200;
    /**
     * Very short potion duration: 5 seconds at 20 tps.
     */
    private static final int DURATION_BRIEF = 100;
    /**
     * Amplifier level 2 (third tier).
     */
    private static final int AMPLIFIER_II = 2;
    /**
     * Suffix appended to goo type id for potion names.
     */
    private static final String POTION_SUFFIX = "_goo";
    /**
     * Per-type potion factory: each entry produces a themed potion from its name.
     */
    private static final Map<GooType, Function<String, Potion>> POTION_FACTORIES =
            new EnumMap<>(Map.ofEntries(
                    Map.entry(GooType.METAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.STRENGTH, DURATION_LONG, 1))),
                    Map.entry(GooType.CRYSTAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_LONG, 0))),
                    Map.entry(GooType.LEAF, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.REGENERATION, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.VITAL, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1),
                            new MobEffectInstance(MobEffects.SATURATION, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.SHROOM, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.NAUSEA, DURATION_SHORT, 0))),
                    Map.entry(GooType.ROCK, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.ABSORPTION, DURATION_LONG, AMPLIFIER_II))),
                    Map.entry(GooType.BLAZE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.STRENGTH, DURATION_MEDIUM, 1))),
                    Map.entry(GooType.FROST, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.SLOWNESS, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.TYPHOON, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION_MEDIUM, 0),
                            new MobEffectInstance(MobEffects.JUMP_BOOST, DURATION_MEDIUM, AMPLIFIER_II))),
                    Map.entry(GooType.GLOW, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.GLOWING, DURATION_LONG, 0))),
                    Map.entry(GooType.HEX, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION_LONG, 0),
                            new MobEffectInstance(MobEffects.UNLUCK, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.PULSE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.LUCK, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.NETHER, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.WITHER, DURATION_BRIEF, 0),
                            new MobEffectInstance(MobEffects.RESISTANCE, DURATION_LONG, 1))),
                    Map.entry(GooType.ENDER, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INVISIBILITY, DURATION_MEDIUM, 0))),
                    Map.entry(GooType.AEON, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.SLOWNESS, DURATION_LONG, 0))),
                    Map.entry(GooType.UNSTABLE, (String n) -> new Potion(n,
                            new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, AMPLIFIER_II)))));

    static {
        for (GooType type : GooType.values()) {
            GOO_POTIONS.put(type, POTIONS.register(type.getId() + "_goo",
                    () -> createPotion(type)));
        }
    }

    /**
     * Creates a potion with mob effects themed to the given goo type.
     *
     * @param type the goo type
     * @return the configured potion
     */
    private static Potion createPotion(GooType type) {
        String name = type.getId() + POTION_SUFFIX;
        return POTION_FACTORIES.get(type).apply(name);
    }
}
