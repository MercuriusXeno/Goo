package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
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
 *
 * @param topGasketId    UUID of the top (input) gasket, or null
 * @param bottomGasketId UUID of the bottom (output) gasket, or null
 * @param label          the user-assigned label, or null
 * @param topPartner     the top gasket's linked partner, or null
 * @param bottomPartner  the bottom gasket's linked partner, or null
 */
public record CanisterMetadata(
        @Nullable UUID topGasketId,
        @Nullable UUID bottomGasketId,
        @Nullable String label,
        @Nullable GasketPartner topPartner,
        @Nullable GasketPartner bottomPartner) {

    /**
     * Empty metadata with no gaskets, label, or partners.
     */
    public static final CanisterMetadata EMPTY =
            new CanisterMetadata(null, null, null, null, null);

    /**
     * Persistent codec for saving/loading canister metadata.
     */
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

    /**
     * Network codec for client-server sync.
     */
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

    /**
     * Constructs from codec output, unwrapping optionals.
     *
     * @param topGasketId    optional top gasket UUID
     * @param bottomGasketId optional bottom gasket UUID
     * @param label          optional player-assigned label
     * @param topPartner     optional top gasket partner
     * @param bottomPartner  optional bottom gasket partner
     * @return the constructed metadata
     */
    private static CanisterMetadata fromCodec(
            Optional<UUID> topGasketId, Optional<UUID> bottomGasketId,
            Optional<String> label,
            Optional<GasketPartner> topPartner, Optional<GasketPartner> bottomPartner) {
        return new CanisterMetadata(
                topGasketId.orElse(null), bottomGasketId.orElse(null),
                label.orElse(null),
                topPartner.orElse(null), bottomPartner.orElse(null));
    }

    /**
     * Constructs from stream codec output, unwrapping optionals.
     *
     * @param topGasketId    optional top gasket UUID
     * @param bottomGasketId optional bottom gasket UUID
     * @param label          optional player-assigned label
     * @param topPartner     optional top gasket partner
     * @param bottomPartner  optional bottom gasket partner
     * @return the constructed metadata
     */
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
     *
     * @return true if any metadata field is set
     */
    public boolean hasData() {
        return hasAnyGasket() || label != null || hasAnyPartner();
    }

    /**
     * Returns true if either the top or bottom gasket ID is present.
     *
     * @return true if any gasket is installed
     */
    private boolean hasAnyGasket() {
        return topGasketId != null || bottomGasketId != null;
    }

    /**
     * Returns true if either the top or bottom partner is linked.
     *
     * @return true if any partner is linked
     */
    private boolean hasAnyPartner() {
        return topPartner != null || bottomPartner != null;
    }

    /**
     * Returns metadata with gasket UUIDs and partners cleared, preserving label.
     *
     * @return new metadata with gaskets removed
     */
    public CanisterMetadata withoutGaskets() {
        return new CanisterMetadata(null, null, label, null, null);
    }

    /**
     * Returns metadata with fresh UUIDs for each installed gasket and partners cleared.
     * Preserves gasket presence (non-null UUID) while avoiding UUID duplication
     * across creative-mode copies.
     *
     * @return new metadata with regenerated gasket UUIDs
     */
    public CanisterMetadata withFreshGasketIds() {
        return new CanisterMetadata(
                topGasketId != null ? UUID.randomUUID() : null,
                bottomGasketId != null ? UUID.randomUUID() : null,
                label, null, null);
    }

    /**
     * Returns a new metadata with the given label (null to clear).
     *
     * @param newLabel the label to set, or null to clear
     * @return new metadata with the label applied
     */
    public CanisterMetadata withLabel(@Nullable String newLabel) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
                newLabel, topPartner, bottomPartner);
    }

    /**
     * Returns a new metadata with both gasket UUIDs assigned if missing.
     *
     * @return new metadata with gasket UUIDs ensured
     */
    public CanisterMetadata withGasketIds() {
        if (topGasketId != null && bottomGasketId != null) {
            return this;
        }
        return new CanisterMetadata(
                getOrCreateTopGasketId(), getOrCreateBottomGasketId(),
                label, topPartner, bottomPartner);
    }

    /**
     * Returns a new metadata with the given top gasket UUID.
     *
     * @param id the top gasket UUID
     * @return new metadata with the top gasket ID set
     */
    public CanisterMetadata withTopGasketId(UUID id) {
        return new CanisterMetadata(id, bottomGasketId,
                label, topPartner, bottomPartner);
    }

    /**
     * Returns a new metadata with the given bottom gasket UUID.
     *
     * @param id the bottom gasket UUID
     * @return new metadata with the bottom gasket ID set
     */
    public CanisterMetadata withBottomGasketId(UUID id) {
        return new CanisterMetadata(topGasketId, id,
                label, topPartner, bottomPartner);
    }

    /**
     * Returns a new metadata with the top gasket UUID and partner cleared.
     *
     * @return new metadata without the top gasket
     */
    public CanisterMetadata withoutTopGasket() {
        return new CanisterMetadata(null, bottomGasketId,
                label, null, bottomPartner);
    }

    /**
     * Returns a new metadata with the bottom gasket UUID and partner cleared.
     *
     * @return new metadata without the bottom gasket
     */
    public CanisterMetadata withoutBottomGasket() {
        return new CanisterMetadata(topGasketId, null,
                label, topPartner, null);
    }

    /**
     * Returns a new metadata with the given top gasket partner (null to clear).
     *
     * @param partner the top gasket partner, or null to clear
     * @return new metadata with the top partner set
     */
    public CanisterMetadata withTopPartner(@Nullable GasketPartner partner) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
                label, partner, bottomPartner);
    }

    /**
     * Returns a new metadata with the given bottom gasket partner (null to clear).
     *
     * @param partner the bottom gasket partner, or null to clear
     * @return new metadata with the bottom partner set
     */
    public CanisterMetadata withBottomPartner(@Nullable GasketPartner partner) {
        return new CanisterMetadata(topGasketId, bottomGasketId,
                label, topPartner, partner);
    }

    /**
     * Returns the top gasket UUID, generating one if absent.
     *
     * @return the existing or newly generated top gasket UUID
     */
    public UUID getOrCreateTopGasketId() {
        return topGasketId != null ? topGasketId : UUID.randomUUID();
    }

    /**
     * Returns the bottom gasket UUID, generating one if absent.
     *
     * @return the existing or newly generated bottom gasket UUID
     */
    public UUID getOrCreateBottomGasketId() {
        return bottomGasketId != null ? bottomGasketId : UUID.randomUUID();
    }
}
