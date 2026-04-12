package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom particle types for the goo mod.
 */
public class GooParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, Goo.MODID);

    /** Gravity-affected spark particle for crucible rod-contact effects. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GOO_SPARK =
        PARTICLE_TYPES.register("goo_spark", () -> new SimpleParticleType(false));

    /** Color-tinted bubble particle spawned during goo extraction. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_BUBBLE =
        PARTICLE_TYPES.register("goo_bubble", GooParticles::colorParticleType);

    /** Blocky slime drip shed by thrown goo blobs mid-flight. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_DRIP =
        PARTICLE_TYPES.register("goo_drip", GooParticles::colorParticleType);

    /** Brief splat when a goo drip hits the ground. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_DRIP_LAND =
        PARTICLE_TYPES.register("goo_drip_land", GooParticles::colorParticleType);

    /** Sonic-boom-style particle oriented along the blast axis. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ORIENTED_BOOM =
        PARTICLE_TYPES.register("oriented_boom", () -> new SimpleParticleType(false));

    /** Radial gradient fog puff for blob flight trails. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> GOO_FOG =
        PARTICLE_TYPES.register("goo_fog", GooParticles::colorParticleType);

    /**
     * Creates a non-syncing ParticleType that carries RGB color data.
     *
     * @return the configured color particle type
     */
    private static ParticleType<ColorParticleOption> colorParticleType() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }
}
