package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom sound events for the goo mod. Sound assets live under
 * {@code src/main/resources/assets/goo/sounds/} and are mapped to event
 * ids via {@code assets/goo/sounds.json}.
 */
public final class GooSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, Goo.MODID);

    /** Nether chain effect: sphere expand / destroy / contract "boom". */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLACK_HOLE =
        SOUND_EVENTS.register("effects.black_hole",
            () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Goo.MODID, "effects.black_hole")));

    /** Glow blob throw: laser beam fire sound. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOW_THROW =
        SOUND_EVENTS.register("effects.glow_throw",
            () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Goo.MODID, "effects.glow_throw")));

    private GooSounds() {}
}
