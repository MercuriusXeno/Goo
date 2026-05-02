package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Typed particle options carrying the blast direction for the
 * sonic-boom particle. Replaces the prior pattern of casting
 * {@code (int) xAux} back into a {@link Direction} ordinal on the
 * client provider; the direction now travels as a real field with a
 * proper codec.
 *
 * @param direction the blast direction (the quad faces this way)
 */
public record OrientedBoomParticleOptions(Direction direction) implements ParticleOptions {

    public static final MapCodec<OrientedBoomParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                    Direction.CODEC.fieldOf("direction").forGetter(OrientedBoomParticleOptions::direction)
            ).apply(i, OrientedBoomParticleOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrientedBoomParticleOptions> STREAM_CODEC =
            StreamCodec.composite(
                    Direction.STREAM_CODEC,
                    OrientedBoomParticleOptions::direction,
                    OrientedBoomParticleOptions::new
            );

    @Override
    public ParticleType<OrientedBoomParticleOptions> getType() {
        return GooParticles.ORIENTED_BOOM.get();
    }
}
