package com.mercuriusxeno.goo.item;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Pure decision matrix for choral tuner interactions. No Minecraft dependencies.
 * Given the clicked gasket's role, IDs, and the tuner's carried state, returns
 * a {@link TunerAction} describing what should happen next.
 *
 * <p>Decision table (for any click):</p>
 * <ul>
 *   <li>Carried opposite role: CompleteLink</li>
 *   <li>Carried same role + REPLACE pending on same target: ConfirmReplace</li>
 *   <li>Carried same role + no confirm: PromptReplace</li>
 *   <li>No carried + partner exists + SEVER pending on same target: ConfirmSever</li>
 *   <li>No carried + partner exists + no confirm: PromptSever</li>
 *   <li>No carried + no partner: StartAwaiting</li>
 * </ul>
 */
public final class TunerLinkLogic {

    /** Prompt suffix for the replace-link confirmation. */
    private static final String PROMPT_REPLACE_PREFIX = "Already tuning a ";
    /** Prompt suffix for the replace-link confirmation. */
    private static final String PROMPT_REPLACE_SUFFIX = ". Tune again to switch.";
    /** Prompt prefix for the sever-link confirmation. */
    private static final String PROMPT_SEVER_PREFIX = "Sever this ";
    /** Prompt suffix for the sever-link confirmation. */
    private static final String PROMPT_SEVER_SUFFIX = "'s link? Tune again to confirm.";

    private TunerLinkLogic() {}

    /**
     * Resolves the tuner action for a click on a gasket.
     *
     * @param clickedRole the role of the gasket that was clicked
     * @param clickedGasketId the UUID of the clicked gasket
     * @param existingPartnerId the existing partner of the clicked gasket, or null
     * @param carriedRole the role stored in the tuner's selection, or null if empty
     * @param carriedGasketId the gasket ID stored in the tuner's selection, or null
     * @param pendingConfirm the current pending confirmation state
     * @param confirmTarget the position being confirmed, or null
     * @param confirmSlot the slot being confirmed
     * @param clickedPos the position of the clicked block
     * @param clickedSlot the slot of the clicked block
     * @param clickedFaceLabel the face label of the clicked gasket
     * @return the action to take
     */
    public static TunerAction resolve(
            GasketRole clickedRole,
            UUID clickedGasketId,
            @Nullable UUID existingPartnerId,
            @Nullable GasketRole carriedRole,
            @Nullable UUID carriedGasketId,
            ConfirmAction pendingConfirm,
            @Nullable BlockPos confirmTarget,
            int confirmSlot,
            BlockPos clickedPos,
            int clickedSlot,
            @Nullable String clickedFaceLabel) {

        boolean hasCarried = carriedRole != null && carriedGasketId != null;
        boolean sameTarget = isSameTarget(confirmTarget, confirmSlot,
            clickedPos, clickedSlot);

        if (hasCarried && carriedRole != clickedRole) {
            return resolveOppositeRole(clickedRole, clickedGasketId,
                carriedGasketId);
        }

        if (hasCarried && carriedRole == clickedRole) {
            return resolveSameRole(clickedRole, clickedGasketId,
                clickedPos, clickedSlot, clickedFaceLabel,
                pendingConfirm, sameTarget);
        }

        return resolveNoCarried(clickedRole, clickedGasketId,
            existingPartnerId, clickedPos, clickedSlot, clickedFaceLabel,
            pendingConfirm, sameTarget);
    }

    /**
     * Carried opposite role: complete the link.
     *
     * @param clickedRole     the role of the clicked gasket
     * @param clickedGasketId the UUID of the clicked gasket
     * @param carriedGasketId the UUID of the carried gasket
     * @return a CompleteLink action
     */
    private static TunerAction resolveOppositeRole(
            GasketRole clickedRole, UUID clickedGasketId,
            UUID carriedGasketId) {
        if (clickedRole == GasketRole.RECEIVER) {
            return new TunerAction.CompleteLink(carriedGasketId, clickedGasketId);
        }
        return new TunerAction.CompleteLink(clickedGasketId, carriedGasketId);
    }

    /**
     * Carried same role: prompt or confirm replacement.
     *
     * @param clickedRole      the role of the clicked gasket
     * @param clickedGasketId  the UUID of the clicked gasket
     * @param clickedPos       the block position of the click
     * @param clickedSlot      the slot of the click
     * @param clickedFaceLabel the face label, or null
     * @param pendingConfirm   the current pending confirmation
     * @param sameTarget       whether the confirm target matches
     * @return a PromptReplace or ConfirmReplace action
     */
    private static TunerAction resolveSameRole(
            GasketRole clickedRole, UUID clickedGasketId,
            BlockPos clickedPos, int clickedSlot,
            @Nullable String clickedFaceLabel,
            ConfirmAction pendingConfirm, boolean sameTarget) {
        if (pendingConfirm == ConfirmAction.REPLACE_LINK && sameTarget) {
            return new TunerAction.ConfirmReplace(clickedGasketId,
                clickedPos, clickedSlot, clickedRole, clickedFaceLabel);
        }
        return new TunerAction.PromptReplace(
            PROMPT_REPLACE_PREFIX + clickedRole.getSerializedName()
                + PROMPT_REPLACE_SUFFIX);
    }

    /**
     * No carried selection: sever, prompt sever, or start awaiting.
     *
     * @param clickedRole      the role of the clicked gasket
     * @param clickedGasketId  the UUID of the clicked gasket
     * @param existingPartnerId the existing partner UUID, or null
     * @param clickedPos       the block position of the click
     * @param clickedSlot      the slot of the click
     * @param clickedFaceLabel the face label, or null
     * @param pendingConfirm   the current pending confirmation
     * @param sameTarget       whether the confirm target matches
     * @return the appropriate action
     */
    private static TunerAction resolveNoCarried(
            GasketRole clickedRole, UUID clickedGasketId,
            @Nullable UUID existingPartnerId,
            BlockPos clickedPos, int clickedSlot,
            @Nullable String clickedFaceLabel,
            ConfirmAction pendingConfirm, boolean sameTarget) {
        if (existingPartnerId != null) {
            if (pendingConfirm == ConfirmAction.SEVER_LINK && sameTarget) {
                return new TunerAction.ConfirmSever(clickedGasketId);
            }
            return new TunerAction.PromptSever(
                PROMPT_SEVER_PREFIX + clickedRole.getSerializedName()
                    + PROMPT_SEVER_SUFFIX);
        }
        return new TunerAction.StartAwaiting(clickedRole, clickedGasketId,
            clickedPos, clickedSlot, clickedFaceLabel);
    }

    /**
     * Returns true if the confirm target matches the clicked position and slot.
     *
     * @param confirmTarget the position being confirmed, or null
     * @param confirmSlot   the slot being confirmed
     * @param clickedPos    the clicked block position
     * @param clickedSlot   the clicked slot
     * @return true if both position and slot match
     */
    private static boolean isSameTarget(
            @Nullable BlockPos confirmTarget, int confirmSlot,
            BlockPos clickedPos, int clickedSlot) {
        if (confirmTarget == null) { return false; }
        return confirmTarget.equals(clickedPos) && confirmSlot == clickedSlot;
    }

    /**
     * Sealed interface for tuner action outcomes. Each variant carries the
     * data needed by the executor in ChoralTunerItem.
     */
    public sealed interface TunerAction {

        /**
         * Complete a link between two gaskets. Output (transmitter) is always
         * first, input (receiver) second.
         *
         * @param outputGasket the transmitter gasket UUID
         * @param inputGasket  the receiver gasket UUID
         */
        record CompleteLink(UUID outputGasket, UUID inputGasket)
            implements TunerAction {}

        /**
         * Start awaiting a partner for this gasket.
         *
         * @param role      the gasket role (transmitter or receiver)
         * @param gasketId  the gasket UUID
         * @param pos       the block position
         * @param slot      the sub-slot index
         * @param faceLabel the face label for display, or null
         */
        record StartAwaiting(GasketRole role, UUID gasketId, BlockPos pos,
                int slot, @Nullable String faceLabel)
            implements TunerAction {}

        /**
         * Prompt the player to confirm replacing their current carried selection.
         *
         * @param message the prompt message to display
         */
        record PromptReplace(String message) implements TunerAction {}

        /**
         * Prompt the player to confirm severing an existing link.
         *
         * @param message the prompt message to display
         */
        record PromptSever(String message) implements TunerAction {}

        /**
         * Confirmed: replace the carried selection with a new one.
         *
         * @param newGasketId the replacement gasket UUID
         * @param newPos      the new block position
         * @param newSlot     the new sub-slot index
         * @param role        the gasket role
         * @param faceLabel   the face label for display, or null
         */
        record ConfirmReplace(UUID newGasketId, BlockPos newPos, int newSlot,
                GasketRole role, @Nullable String faceLabel)
            implements TunerAction {}

        /**
         * Confirmed: sever the gasket's link.
         *
         * @param gasketId the gasket UUID to sever
         */
        record ConfirmSever(UUID gasketId) implements TunerAction {}

        /**
         * Warning: no gasket present on this face.
         *
         * @param message the warning message to display
         */
        record NoGasketWarning(String message) implements TunerAction {}
    }
}
