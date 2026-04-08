package com.mercuriusxeno.goo.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Stores a gasket's source and destination block positions.
 *
 * @param source      the source (origin) block position
 * @param destination the destination block position, or null if unlinked
 */
public record GasketPairing(BlockPos source, @Nullable BlockPos destination) {

    public static final Codec<GasketPairing> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("source").forGetter(GasketPairing::source),
            BlockPos.CODEC.optionalFieldOf("destination").forGetter(p -> Optional.ofNullable(p.destination()))
        ).apply(instance, (src, dest) -> new GasketPairing(src, dest.orElse(null)))
    );

    public static final StreamCodec<ByteBuf, GasketPairing> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        GasketPairing::source,
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
        p -> Optional.ofNullable(p.destination()),
        (src, dest) -> new GasketPairing(src, dest.orElse(null))
    );

    /**
     * Returns true if the pairing has both source and destination set.
     *
     * @return true if destination is non-null
     */
    public boolean isComplete() {
        return destination != null;
    }
}
