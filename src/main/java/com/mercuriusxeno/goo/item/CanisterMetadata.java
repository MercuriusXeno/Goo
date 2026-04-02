package com.mercuriusxeno.goo.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Canister-specific metadata: gasket UUIDs for the transport network,
 * optional player-assigned label, and denormalized gasket partner references
 * for HUD display. Separated from goo volume storage (GooContents)
 * so that machine-specific concerns don't pollute the shared storage record.
 *
 * <p>Partner fields store the position and slot of the machine on the other
 * end of each gasket link, enabling the canister HUD to display
 * "To: [name or coords]" / "From: [name or coords]" without querying
 * the server-only GasketRegistry.</p>
 */
public record CanisterMetadata(
        @Nullable UUID topGasketId,
        @Nullable UUID bottomGasketId,
        @Nullable String label,
        @Nullable GasketPartner topPartner,
        @Nullable GasketPartner bottomPartner) {

    /** Empty metadata with no gaskets, label, or partners. */
    public static final CanisterMetadata EMPTY =
        new CanisterMetadata(null, null, null, null, null);

    /** Persistent codec for saving/loading canister metadata. */
    public static final Codec<CanisterMetadata> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.STRING_CODEC.optionalFieldOf("top_gasket_id")
                .forGetter(c -> Optional.ofNullable(c.topGasketId())),
            UUIDUtil.STRING_CODEC.optionalFieldOf("bottom_gasket_id")
                .forGetter(c -> Optional.ofNullable(c.bottomGasketId())),
            Codec.STRING.optionalFieldOf("label")
                .forGetter(c -> Optional.ofNullable(c.label())),
            GasketPartner.CODEC.optionalFieldOf("top_partner")
                .forGetter(c -> Optional.ofNullable(c.topPartner())),
            GasketPartner.CODEC.optionalFieldOf("bottom_partner")
                .forGetter(c -> Optional.ofNullable(c.bottomPartner()))
        ).apply(instance, CanisterMetadata::fromCodec)
    );

    /** Constructs from codec output, unwrapping optionals. */
    private static CanisterMetadata fromCodec(
            Optional<UUID> topGasketId, Optional<UUID> bottomGasketId,
            Optional<String> label,
            Optional<GasketPartner> topPartner, Optional<GasketPartner> bottomPartner) {
        return new CanisterMetadata(
            topGasketId.orElse(null), bottomGasketId.orElse(null),
            label.orElse(null),
            topPartner.orElse(null), bottomPartner.orElse(null));
    }

    /** Network codec for client-server sync. */
    public static final StreamCodec<ByteBuf, CanisterMetadata> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
        c -> Optional.ofNullable(c.topGasketId()),
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
        c -> Optional.ofNullable(c.bottomGasketId()),
        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
        c -> Optional.ofNullable(c.label()),
        ByteBufCodecs.optional(GasketPartner.STREAM_CODEC),
        c -> Optional.ofNullable(c.topPartner()),
        ByteBufCodecs.optional(GasketPartner.STREAM_CODEC),
        c -> Optional.ofNullable(c.bottomPartner()),
        CanisterMetadata::fromStreamCodec
    );

    /** Constructs from stream codec output, unwrapping optionals. */
    private static CanisterMetadata fromStreamCodec(
            Optional<UUID> topGasketId, Optional<UUID> bottomGasketId,
            Optional<String> label,
            Optional<GasketPartner> topPartner, Optional<GasketPartner> bottomPartner) {
        return new CanisterMetadata(
            topGasketId.orElse(null), bottomGasketId.orElse(null),
            label.orElse(null),
            topPartner.orElse(null), bottomPartner.orElse(null));
    }

    /**
     * Returns true if any field is non-default: any gasket ID present,
     * a label is set, or any partner is linked. Used to decide whether
     * to keep the component on an ItemStack.
     */
    public boolean hasData() {
        return topGasketId != null || bottomGasketId != null
            || label != null || topPartner != null || bottomPartner != null;
    }

    /** Returns metadata with gasket UUIDs and partners cleared, preserving label. */
    public CanisterMetadata withoutGaskets() {
        return new CanisterMetadata(null, null, label, null, null);
    }

    /**
     * Returns metadata with fresh UUIDs for each installed gasket and partners cleared.
     * Preserves gasket presence (non-null UUID) while avoiding UUID duplication
     * across creative-mode copies.
     */
    public CanisterMetadata withFreshGasketIds() {
        return new CanisterMetadata(
            topGasketId != null ? UUID.randomUUID() : null,
            bottomGasketId != null ? UUID.randomUUID() : null,
            label, null, null);
    }

    /** Returns a new metadata with the given label (null to clear). */
    public CanisterMetadata withLabel(@Nullable String newLabel) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
            newLabel, topPartner, bottomPartner);
    }

    /** Returns a new metadata with both gasket UUIDs assigned if missing. */
    public CanisterMetadata withGasketIds() {
        if (topGasketId != null && bottomGasketId != null) return this;
        return new CanisterMetadata(
            getOrCreateTopGasketId(), getOrCreateBottomGasketId(),
            label, topPartner, bottomPartner);
    }

    /** Returns a new metadata with the given top gasket UUID. */
    public CanisterMetadata withTopGasketId(UUID id) {
        return new CanisterMetadata(id, bottomGasketId,
            label, topPartner, bottomPartner);
    }

    /** Returns a new metadata with the given bottom gasket UUID. */
    public CanisterMetadata withBottomGasketId(UUID id) {
        return new CanisterMetadata(topGasketId, id,
            label, topPartner, bottomPartner);
    }

    /** Returns a new metadata with the top gasket UUID and partner cleared. */
    public CanisterMetadata withoutTopGasket() {
        return new CanisterMetadata(null, bottomGasketId,
            label, null, bottomPartner);
    }

    /** Returns a new metadata with the bottom gasket UUID and partner cleared. */
    public CanisterMetadata withoutBottomGasket() {
        return new CanisterMetadata(topGasketId, null,
            label, topPartner, null);
    }

    /** Returns a new metadata with the given top gasket partner (null to clear). */
    public CanisterMetadata withTopPartner(@Nullable GasketPartner partner) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
            label, partner, bottomPartner);
    }

    /** Returns a new metadata with the given bottom gasket partner (null to clear). */
    public CanisterMetadata withBottomPartner(@Nullable GasketPartner partner) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
            label, topPartner, partner);
    }

    /** Returns the top gasket UUID, generating one if absent. */
    public UUID getOrCreateTopGasketId() {
        return topGasketId != null ? topGasketId : UUID.randomUUID();
    }

    /** Returns the bottom gasket UUID, generating one if absent. */
    public UUID getOrCreateBottomGasketId() {
        return bottomGasketId != null ? bottomGasketId : UUID.randomUUID();
    }
}
