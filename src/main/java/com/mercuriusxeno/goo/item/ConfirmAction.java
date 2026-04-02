package com.mercuriusxeno.goo.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * Pending confirmation action for the choral tuner. Tracks whether the player
 * needs to confirm a destructive or role-switching operation.
 */
public enum ConfirmAction implements StringRepresentable {

    /** No pending confirmation. */
    NONE("none"),

    /** Confirm replacing the current carried link with a new one. */
    REPLACE_LINK("replace_link"),

    /** Confirm severing an existing gasket link. */
    SEVER_LINK("sever_link");

    /** Persistent codec via StringRepresentable. */
    public static final Codec<ConfirmAction> CODEC =
        StringRepresentable.fromEnum(ConfirmAction::values);

    /** Network codec: encodes as a varint ordinal. */
    public static final StreamCodec<ByteBuf, ConfirmAction> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.map(
            i -> values()[i],
            ConfirmAction::ordinal
        );

    private final String serializedName;

    ConfirmAction(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
