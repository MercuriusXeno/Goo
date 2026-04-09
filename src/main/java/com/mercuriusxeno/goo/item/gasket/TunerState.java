package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.item.ConfirmAction;
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
 * Data component storing a choral tuner's owner, in-progress role-aware gasket
 * selection, and pending confirmation state. Player-keyed: the owner UUID is
 * stamped on first use.
 *
 * <p>The {@code selectedRole} field replaces the old {@code selectedIsTop}
 * boolean, encoding whether the selection is a transmitter (output/base) or
 * receiver (input/cap) endpoint.</p>
 *
 * @param ownerUuid         the UUID of the player who owns this tuner
 * @param selectedGasketId  the UUID of the currently selected gasket, or null
 * @param selectedPos       the block position of the selection, or null
 * @param selectedRole      the role of the selection (transmitter or receiver), or null
 * @param selectedSlot      the sub-slot index of the selection
 * @param selectedFaceLabel the face label of the selection, or null
 * @param pendingConfirm    the pending confirmation action
 * @param confirmTarget     the position being confirmed, or null
 * @param confirmSlot       the slot being confirmed
 */
public record TunerState(
        @Nullable UUID ownerUuid,
        @Nullable UUID selectedGasketId,
        @Nullable BlockPos selectedPos,
        @Nullable GasketRole selectedRole,
        int selectedSlot,
        @Nullable String selectedFaceLabel,
        ConfirmAction pendingConfirm,
        @Nullable BlockPos confirmTarget,
        int confirmSlot) {

    /** Empty state: no owner, no selection, no pending confirmation. */
    public static final TunerState EMPTY =
        new TunerState(null, null, null, null, -1, null,
            ConfirmAction.NONE, null, -1);

    /** Persistent codec for saving/loading tuner state. */
    public static final Codec<TunerState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner_uuid")
                .forGetter(s -> Optional.ofNullable(s.ownerUuid())),
            UUIDUtil.STRING_CODEC.optionalFieldOf("selected_gasket_id")
                .forGetter(s -> Optional.ofNullable(s.selectedGasketId())),
            BlockPos.CODEC.optionalFieldOf("selected_pos")
                .forGetter(s -> Optional.ofNullable(s.selectedPos())),
            GasketRole.CODEC.optionalFieldOf("selected_role")
                .forGetter(s -> Optional.ofNullable(s.selectedRole())),
            Codec.INT.optionalFieldOf("selected_slot", -1)
                .forGetter(TunerState::selectedSlot),
            Codec.STRING.optionalFieldOf("selected_face_label")
                .forGetter(s -> Optional.ofNullable(s.selectedFaceLabel())),
            ConfirmAction.CODEC.optionalFieldOf("pending_confirm", ConfirmAction.NONE)
                .forGetter(TunerState::pendingConfirm),
            BlockPos.CODEC.optionalFieldOf("confirm_target")
                .forGetter(s -> Optional.ofNullable(s.confirmTarget())),
            Codec.INT.optionalFieldOf("confirm_slot", -1)
                .forGetter(TunerState::confirmSlot)
        ).apply(instance, TunerState::fromCodec)
    );

    /** Network codec for client-server sync. Manual encode/decode. */
    public static final StreamCodec<ByteBuf, TunerState> STREAM_CODEC =
        StreamCodec.of(TunerState::encode, TunerState::decode);

    /** Canister slot center hundredths: 3/16=19, 8/16=50, 13/16=81. */
    private static final int[] SLOT_HUNDREDTHS = {19, 50, 81};
    /** Total canister slots in a 3x3 grid. */
    private static final int GRID_SLOT_COUNT = 9;
    /** Grid columns (or rows) in a 3x3 canister layout. */
    private static final int GRID_COLUMNS = 3;
    /** Format pattern for sub-block hundredths (zero-padded to 2 digits). */
    private static final String HUNDREDTHS_FORMAT = "%02d";
    /** Sentinel slot value meaning no sub-slot selection. */
    private static final int NO_SLOT = -1;
    /** Coordinate separator in formatted strings. */
    private static final String COORD_SEP = ", ";
    /** Decimal point for sub-block coordinate formatting. */
    private static final String DECIMAL_POINT = ".";

    /**
     * Constructs from codec output, unwrapping optionals.
     *
     * @param ownerUuid          optional owner UUID
     * @param selectedGasketId   optional selected gasket UUID
     * @param selectedPos        optional selected block position
     * @param selectedRole       optional selected gasket role
     * @param selectedSlot       the selected slot index
     * @param selectedFaceLabel  optional selected face label
     * @param pendingConfirm     the pending confirmation action
     * @param confirmTarget      optional confirmation target position
     * @param confirmSlot        the confirmation slot index
     * @return the constructed tuner state
     */
    private static TunerState fromCodec(
            Optional<UUID> ownerUuid, Optional<UUID> selectedGasketId,
            Optional<BlockPos> selectedPos, Optional<GasketRole> selectedRole,
            int selectedSlot, Optional<String> selectedFaceLabel,
            ConfirmAction pendingConfirm, Optional<BlockPos> confirmTarget,
            int confirmSlot) {
        return new TunerState(
            ownerUuid.orElse(null), selectedGasketId.orElse(null),
            selectedPos.orElse(null), selectedRole.orElse(null),
            selectedSlot, selectedFaceLabel.orElse(null),
            pendingConfirm, confirmTarget.orElse(null), confirmSlot);
    }

    /**
     * Writes to the network buffer.
     *
     * @param buf   the byte buffer
     * @param state the tuner state to encode
     */
    private static void encode(ByteBuf buf, TunerState state) {
        encodeOptionalUuid(buf, state.ownerUuid());
        encodeOptionalUuid(buf, state.selectedGasketId());
        encodeOptionalBlockPos(buf, state.selectedPos());
        encodeOptionalRole(buf, state.selectedRole());
        ByteBufCodecs.VAR_INT.encode(buf, state.selectedSlot());
        encodeOptionalString(buf, state.selectedFaceLabel());
        ConfirmAction.STREAM_CODEC.encode(buf, state.pendingConfirm());
        encodeOptionalBlockPos(buf, state.confirmTarget());
        ByteBufCodecs.VAR_INT.encode(buf, state.confirmSlot());
    }

    /**
     * Reads from the network buffer.
     *
     * @param buf the byte buffer
     * @return the decoded tuner state
     */
    private static TunerState decode(ByteBuf buf) {
        UUID ownerUuid = decodeOptionalUuid(buf);
        UUID selectedGasketId = decodeOptionalUuid(buf);
        BlockPos selectedPos = decodeOptionalBlockPos(buf);
        GasketRole selectedRole = decodeOptionalRole(buf);
        int selectedSlot = ByteBufCodecs.VAR_INT.decode(buf);
        String selectedFaceLabel = decodeOptionalString(buf);
        ConfirmAction pendingConfirm = ConfirmAction.STREAM_CODEC.decode(buf);
        BlockPos confirmTarget = decodeOptionalBlockPos(buf);
        int confirmSlot = ByteBufCodecs.VAR_INT.decode(buf);
        return new TunerState(ownerUuid, selectedGasketId, selectedPos,
            selectedRole, selectedSlot, selectedFaceLabel,
            pendingConfirm, confirmTarget, confirmSlot);
    }

    // --- Optional encoding helpers ---

    /**
     * Encodes a nullable UUID as a presence boolean + UUID bytes.
     *
     * @param buf  the byte buffer
     * @param uuid the UUID to encode, or null
     */
    private static void encodeOptionalUuid(ByteBuf buf, @Nullable UUID uuid) {
        ByteBufCodecs.BOOL.encode(buf, uuid != null);
        if (uuid != null) { UUIDUtil.STREAM_CODEC.encode(buf, uuid); }
    }

    /**
     * Decodes a nullable UUID.
     *
     * @param buf the byte buffer
     * @return the UUID, or null if absent
     */
    private static @Nullable UUID decodeOptionalUuid(ByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf) ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
    }

    /**
     * Encodes a nullable BlockPos as a presence boolean + BlockPos.
     *
     * @param buf the byte buffer
     * @param pos the block position, or null
     */
    private static void encodeOptionalBlockPos(ByteBuf buf, @Nullable BlockPos pos) {
        ByteBufCodecs.BOOL.encode(buf, pos != null);
        if (pos != null) { BlockPos.STREAM_CODEC.encode(buf, pos); }
    }

    /**
     * Decodes a nullable BlockPos.
     *
     * @param buf the byte buffer
     * @return the block position, or null if absent
     */
    private static @Nullable BlockPos decodeOptionalBlockPos(ByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf) ? BlockPos.STREAM_CODEC.decode(buf) : null;
    }

    /**
     * Encodes a nullable GasketRole as a presence boolean + role.
     *
     * @param buf  the byte buffer
     * @param role the gasket role, or null
     */
    private static void encodeOptionalRole(ByteBuf buf, @Nullable GasketRole role) {
        ByteBufCodecs.BOOL.encode(buf, role != null);
        if (role != null) { GasketRole.STREAM_CODEC.encode(buf, role); }
    }

    /**
     * Decodes a nullable GasketRole.
     *
     * @param buf the byte buffer
     * @return the gasket role, or null if absent
     */
    private static @Nullable GasketRole decodeOptionalRole(ByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf) ? GasketRole.STREAM_CODEC.decode(buf) : null;
    }

    /**
     * Encodes a nullable String as a presence boolean + UTF string.
     *
     * @param buf the byte buffer
     * @param str the string, or null
     */
    private static void encodeOptionalString(ByteBuf buf, @Nullable String str) {
        ByteBufCodecs.BOOL.encode(buf, str != null);
        if (str != null) { ByteBufCodecs.STRING_UTF8.encode(buf, str); }
    }

    /**
     * Decodes a nullable String.
     *
     * @param buf the byte buffer
     * @return the string, or null if absent
     */
    private static @Nullable String decodeOptionalString(ByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf) ? ByteBufCodecs.STRING_UTF8.decode(buf) : null;
    }

    // --- Query methods ---

    /**
     * Returns true if this tuner has an owner.
     *
     * @return true if owner UUID is set
     */
    public boolean hasOwner() {
        return ownerUuid != null;
    }

    /**
     * Returns true if a gasket selection is in progress.
     *
     * @return true if a gasket is selected
     */
    public boolean hasSelection() {
        return selectedGasketId != null && selectedPos != null;
    }

    // --- Factory methods ---

    /**
     * Returns a new state with the given owner.
     *
     * @param owner the owner UUID to set
     * @return new state with owner assigned
     */
    public TunerState withOwner(UUID owner) {
        return new TunerState(owner, selectedGasketId, selectedPos,
            selectedRole, selectedSlot, selectedFaceLabel,
            pendingConfirm, confirmTarget, confirmSlot);
    }

    /**
     * Returns a new state with a role-aware selection stored.
     *
     * @param gasketId the gasket UUID being selected
     * @param pos the block position
     * @param role the role of the selected gasket
     * @param slot slot index (-1 for vat/crucible)
     * @param faceLabel "cap", "base", "crucible", or null for canister/hub
     */
    public TunerState withRoleSelection(UUID gasketId, BlockPos pos,
            GasketRole role, int slot, @Nullable String faceLabel) {
        return new TunerState(ownerUuid, gasketId, pos, role, slot,
            faceLabel, ConfirmAction.NONE, null, NO_SLOT);
    }

    /**
     * Returns a new state with a pending confirmation action.
     *
     * @param action the confirmation type
     * @param pos the block position being confirmed
     * @param slot the slot being confirmed
     */
    public TunerState withPendingConfirm(ConfirmAction action,
            BlockPos pos, int slot) {
        return new TunerState(ownerUuid, selectedGasketId, selectedPos,
            selectedRole, selectedSlot, selectedFaceLabel,
            action, pos, slot);
    }

    /**
     * Returns a new state with the pending confirmation cleared.
     *
     * @return new state with no pending confirm
     */
    public TunerState clearConfirm() {
        return new TunerState(ownerUuid, selectedGasketId, selectedPos,
            selectedRole, selectedSlot, selectedFaceLabel,
            ConfirmAction.NONE, null, NO_SLOT);
    }

    /**
     * Returns a new state with the selection and confirmation cleared.
     *
     * @return new state with no selection or confirmation
     */
    public TunerState clearSelection() {
        return new TunerState(ownerUuid, null, null, null, NO_SLOT, null,
            ConfirmAction.NONE, null, NO_SLOT);
    }

    // --- Coordinate formatting (kept for backward compatibility) ---

    /**
     * Formats block position with sub-block precision based on slot center.
     * Returns "X.33, Y, Z.66" style coordinates where the decimal indicates
     * the slot's third within the block (0.33 / 0.66 / 0.99).
     *
     * @param pos  the block position
     * @param slot the slot index for sub-block offset
     * @return the formatted coordinate string
     */
    public static String formatSubBlockCoords(BlockPos pos, int slot) {
        int[] subBlock = slotSubBlockOffset(slot);
        return formatCoord(pos.getX(), subBlock[0]) + COORD_SEP
            + pos.getY() + COORD_SEP
            + formatCoord(pos.getZ(), subBlock[1]);
    }

    /**
     * Formats a single coordinate with sub-block offset.
     *
     * @param blockCoord the integer block coordinate
     * @param hundredths the sub-block offset in hundredths
     * @return the formatted coordinate string
     */
    private static String formatCoord(int blockCoord, int hundredths) {
        if (hundredths <= 0) { return String.valueOf(blockCoord); }
        return blockCoord + DECIMAL_POINT + String.format(HUNDREDTHS_FORMAT, hundredths);
    }

    /**
     * Returns the sub-block XZ offset as hundredths for a slot, using
     * actual canister XZ centers: 3/16, 8/16, 13/16.
     * Returns {0, 0} for unknown slots (hub or invalid).
     *
     * @param slot the slot index (0-8)
     * @return two-element array {x hundredths, z hundredths}
     */
    private static int[] slotSubBlockOffset(int slot) {
        if (slot < 0 || slot >= GRID_SLOT_COUNT) { return new int[]{0, 0}; }
        int col = slot % GRID_COLUMNS;
        int row = slot / GRID_COLUMNS;
        return new int[]{SLOT_HUNDREDTHS[col], SLOT_HUNDREDTHS[row]};
    }
}
