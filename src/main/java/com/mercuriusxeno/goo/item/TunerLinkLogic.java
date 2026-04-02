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

    /** Carried opposite role: complete the link. */
    private static TunerAction resolveOppositeRole(
            GasketRole clickedRole, UUID clickedGasketId,
            UUID carriedGasketId) {
        if (clickedRole == GasketRole.RECEIVER) {
            return new TunerAction.CompleteLink(carriedGasketId, clickedGasketId);
        }
        return new TunerAction.CompleteLink(clickedGasketId, carriedGasketId);
    }

    /** Carried same role: prompt or confirm replacement. */
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
            "Already tuning a " + clickedRole.getSerializedName()
                + ". Tune again to switch.");
    }

    /** No carried selection: sever, prompt sever, or start awaiting. */
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
                "Sever this " + clickedRole.getSerializedName()
                    + "'s link? Tune again to confirm.");
        }
        return new TunerAction.StartAwaiting(clickedRole, clickedGasketId,
            clickedPos, clickedSlot, clickedFaceLabel);
    }

    /** Returns true if the confirm target matches the clicked position and slot. */
    private static boolean isSameTarget(
            @Nullable BlockPos confirmTarget, int confirmSlot,
            BlockPos clickedPos, int clickedSlot) {
        if (confirmTarget == null) return false;
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
         */
        record CompleteLink(UUID outputGasket, UUID inputGasket)
            implements TunerAction {}

        /** Start awaiting a partner for this gasket. */
        record StartAwaiting(GasketRole role, UUID gasketId, BlockPos pos,
                int slot, @Nullable String faceLabel)
            implements TunerAction {}

        /** Prompt the player to confirm replacing their current carried selection. */
        record PromptReplace(String message) implements TunerAction {}

        /** Prompt the player to confirm severing an existing link. */
        record PromptSever(String message) implements TunerAction {}

        /** Confirmed: replace the carried selection with a new one. */
        record ConfirmReplace(UUID newGasketId, BlockPos newPos, int newSlot,
                GasketRole role, @Nullable String faceLabel)
            implements TunerAction {}

        /** Confirmed: sever the gasket's link. */
        record ConfirmSever(UUID gasketId) implements TunerAction {}

        /** Warning: no gasket present on this face. */
        record NoGasketWarning(String message) implements TunerAction {}
    }
}
