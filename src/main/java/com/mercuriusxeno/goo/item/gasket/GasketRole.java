package com.mercuriusxeno.goo.item.gasket;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Role of a gasket in the transport network: transmitter (output/bottom/base)
 * or receiver (input/top/cap). Crucible basin is special: always transmitter
 * despite being the top of the block.
 */
public enum GasketRole implements StringRepresentable {

    /** Bottom/base gasket: sends goo outward. */
    TRANSMITTER("transmitter"),

    /** Top/cap gasket: receives goo inward. */
    RECEIVER("receiver");

    /** Persistent codec via StringRepresentable. */
    public static final Codec<GasketRole> CODEC =
        StringRepresentable.fromEnum(GasketRole::values);

    /** Network codec: encodes as a boolean (TRANSMITTER=false, RECEIVER=true). */
    public static final StreamCodec<ByteBuf, GasketRole> STREAM_CODEC =
        ByteBufCodecs.BOOL.map(
            b -> b ? RECEIVER : TRANSMITTER,
            r -> r == RECEIVER
        );

    private final String serializedName;

    GasketRole(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the serialized name for codec persistence.
     *
     * @return the serialized name string
     */
    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }

    /**
     * Returns the opposite role.
     *
     * @return RECEIVER if this is TRANSMITTER, and vice versa
     */
    public GasketRole opposite() {
        return this == TRANSMITTER ? RECEIVER : TRANSMITTER;
    }
}
