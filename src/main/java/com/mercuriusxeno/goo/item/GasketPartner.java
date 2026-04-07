package com.mercuriusxeno.goo.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Denormalized reference to a gasket partner: the block position and slot
 * index of the machine on the other end of a gasket link. Stored on the
 * canister item (via CanisterMetadata) and on vat/crucible block entities
 * so the HUD can display "To: [name or coords]" / "From: [name or coords]".
 *
 * <p>Slot is -1 for single-gasket machines (vat, crucible) and 0-8 for
 * canister/hub sub-slots.</p>
 *
 * <p>For entity targets (e.g. player inventory), entityId is non-null and
 * pos becomes a stale hint for display. Use {@link #isEntityTarget()} to check.</p>
 *
 * @param pos      the block position (or stale hint for entity targets)
 * @param slot     the sub-slot index (-1 for single-gasket machines)
 * @param entityId the entity UUID for entity targets, or null
 */
public record GasketPartner(BlockPos pos, int slot, @Nullable UUID entityId) {

    /** Slot value indicating a single-gasket machine (vat, crucible). */
    public static final int NO_SLOT = -1;
    /** Coordinate label prefix for X axis. */
    private static final String COORD_X = "X: ";
    /** Coordinate label prefix for Y axis. */
    private static final String COORD_Y = "Y: ";
    /** Coordinate label prefix for Z axis. */
    private static final String COORD_Z = "Z: ";
    /** Separator between coordinate components. */
    private static final String COORD_SEP = ", ";

    /** Persistent codec. entityId is optional for backward compatibility. */
    public static final Codec<GasketPartner> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(GasketPartner::pos),
            Codec.INT.optionalFieldOf("slot", NO_SLOT).forGetter(GasketPartner::slot),
            UUIDUtil.STRING_CODEC.optionalFieldOf("entity_id")
                .forGetter(p -> Optional.ofNullable(p.entityId()))
        ).apply(instance, (pos, slot, entityOpt) ->
            new GasketPartner(pos, slot, entityOpt.orElse(null)))
    );

    /** Network codec. Sends entityId as optional. */
    public static final StreamCodec<ByteBuf, GasketPartner> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, GasketPartner::pos,
            ByteBufCodecs.VAR_INT, GasketPartner::slot,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                p -> Optional.ofNullable(p.entityId()),
            (pos, slot, entityOpt) -> new GasketPartner(pos, slot, entityOpt.orElse(null))
        );

    /**
     * Convenience constructor for block targets (no entity).
     *
     * @param pos  the block position
     * @param slot the slot index (-1 for single-gasket machines)
     */
    public GasketPartner(BlockPos pos, int slot) {
        this(pos, slot, null);
    }

    /**
     * Returns true if this partner targets an entity rather than a block.
     *
     * @return true if entity-targeted
     */
    public boolean isEntityTarget() {
        return entityId != null;
    }

    /**
     * Formats position as "X: x, Y: y, Z: z".
     *
     * @return the formatted coordinate string
     */
    public String formatCoords() {
        return COORD_X + pos.getX() + COORD_SEP + COORD_Y + pos.getY() + COORD_SEP + COORD_Z + pos.getZ();
    }

    /**
     * Formats X coordinate as "X: n".
     *
     * @return the formatted X coordinate
     */
    public String formatX() {
        return COORD_X + pos.getX();
    }

    /**
     * Formats Y coordinate as "Y: n".
     *
     * @return the formatted Y coordinate
     */
    public String formatY() {
        return COORD_Y + pos.getY();
    }

    /**
     * Formats Z coordinate as "Z: n".
     *
     * @return the formatted Z coordinate
     */
    public String formatZ() {
        return COORD_Z + pos.getZ();
    }
}
