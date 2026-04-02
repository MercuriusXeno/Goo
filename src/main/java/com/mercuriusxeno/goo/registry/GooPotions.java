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

public class GooPotions {
    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(Registries.POTION, Goo.MODID);

    public static final Map<GooType, DeferredHolder<Potion, Potion>> GOO_POTIONS = new EnumMap<>(GooType.class);

    static {
        for (GooType type : GooType.values()) {
            GOO_POTIONS.put(type, POTIONS.register(type.getId() + "_goo",
                () -> createPotion(type)));
        }
    }

    private static Potion createPotion(GooType type) {
        String name = type.getId() + "_goo";
        return switch (type) {
            case METAL -> new Potion(name, new MobEffectInstance(MobEffects.SLOW_FALLING, 1200, 0),
                new MobEffectInstance(MobEffects.STRENGTH, 1200, 1));
            case CRYSTAL -> new Potion(name, new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0));
            case LEAF -> new Potion(name, new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
            case VITAL -> new Potion(name, new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1),
                new MobEffectInstance(MobEffects.SATURATION, 600, 0));
            case SHROOM -> new Potion(name, new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0),
                new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
            case ROCK -> new Potion(name, new MobEffectInstance(MobEffects.ABSORPTION, 1200, 2));
            case BLAZE -> new Potion(name, new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0),
                new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
            case FROST -> new Potion(name, new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0),
                new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
            case TYPHOON -> new Potion(name, new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0),
                new MobEffectInstance(MobEffects.JUMP_BOOST, 600, 2));
            case GLOW -> new Potion(name, new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
            case HEX -> new Potion(name, new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0),
                new MobEffectInstance(MobEffects.UNLUCK, 600, 0));
            case PULSE -> new Potion(name, new MobEffectInstance(MobEffects.LUCK, 600, 0));
            case NETHER -> new Potion(name, new MobEffectInstance(MobEffects.WITHER, 100, 0),
                new MobEffectInstance(MobEffects.RESISTANCE, 1200, 1));
            case ENDER -> new Potion(name, new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0));
            case AEON -> new Potion(name, new MobEffectInstance(MobEffects.SLOWNESS, 1200, 0));
        };
    }
}
