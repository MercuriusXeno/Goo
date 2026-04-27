package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.item.ConfirmAction;
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

    /**
     * Prompt suffix for the replace-link confirmation.
     */
    private static final String PROMPT_REPLACE_PREFIX = "Already tuning a ";
    /**
     * Prompt suffix for the replace-link confirmation.
     */
    private static final String PROMPT_REPLACE_SUFFIX = ". Tune again to switch.";
    /**
     * Prompt prefix for the sever-link confirmation.
     */
    private static final String PROMPT_SEVER_PREFIX = "Sever this ";
    /**
     * Prompt suffix for the sever-link confirmation.
     */
    private static final String PROMPT_SEVER_SUFFIX = "'s link? Tune again to confirm.";

    private TunerLinkLogic() {
    }

    /**
     * Resolves the tuner action for a click on a gasket.
     *
     * @param clicked           the gasket face that was targeted
     * @param existingPartnerId the existing partner of the clicked gasket, or null
     * @param carriedRole       the role stored in the tuner's selection, or null if empty
     * @param carriedGasketId   the gasket ID stored in the tuner's selection, or null
     * @param confirm           the pending confirmation context
     * @return the action to take
     */
    public static TunerAction resolve(
            GasketClick clicked,
            @Nullable UUID existingPartnerId,
            @Nullable GasketRole carriedRole,
            @Nullable UUID carriedGasketId,
            ConfirmContext confirm) {
        boolean hasCarried = carriedRole != null && carriedGasketId != null;
        boolean sameTarget = isSameTarget(confirm, clicked);
        if (hasCarried) {
            return resolveWithCarried(clicked, carriedRole, carriedGasketId,
                    confirm.action(), sameTarget);
        }
        return resolveNoCarried(clicked, existingPartnerId,
                confirm.action(), sameTarget);
    }

    /**
     * Dispatches to opposite-role or same-role resolution when a carried selection exists.
     *
     * @param clicked         the gasket face that was targeted
     * @param carriedRole     the role stored in the tuner's selection
     * @param carriedGasketId the gasket ID stored in the tuner's selection
     * @param pendingConfirm  the current pending confirmation
     * @param sameTarget      whether the confirm target matches
     * @return the resolved action
     */
    private static TunerAction resolveWithCarried(
            GasketClick clicked,
            GasketRole carriedRole, UUID carriedGasketId,
            ConfirmAction pendingConfirm, boolean sameTarget) {
        if (carriedRole != clicked.role()) {
            return resolveOppositeRole(clicked.role(), clicked.gasketId(), carriedGasketId);
        }
        return resolveSameRole(clicked, pendingConfirm, sameTarget);
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
     * @param clicked        the gasket face that was targeted
     * @param pendingConfirm the current pending confirmation
     * @param sameTarget     whether the confirm target matches
     * @return a PromptReplace or ConfirmReplace action
     */
    private static TunerAction resolveSameRole(
            GasketClick clicked,
            ConfirmAction pendingConfirm, boolean sameTarget) {
        if (pendingConfirm == ConfirmAction.REPLACE_LINK && sameTarget) {
            return new TunerAction.ConfirmReplace(clicked.gasketId(),
                    clicked.pos(), clicked.slot(), clicked.role(), clicked.faceLabel());
        }
        return new TunerAction.PromptReplace(
                PROMPT_REPLACE_PREFIX + clicked.role().getSerializedName()
                        + PROMPT_REPLACE_SUFFIX);
    }

    /**
     * No carried selection: sever, prompt sever, or start awaiting.
     *
     * @param clicked           the gasket face that was targeted
     * @param existingPartnerId the existing partner UUID, or null
     * @param pendingConfirm    the current pending confirmation
     * @param sameTarget        whether the confirm target matches
     * @return the appropriate action
     */
    private static TunerAction resolveNoCarried(
            GasketClick clicked,
            @Nullable UUID existingPartnerId,
            ConfirmAction pendingConfirm, boolean sameTarget) {
        if (existingPartnerId != null) {
            if (pendingConfirm == ConfirmAction.SEVER_LINK && sameTarget) {
                return new TunerAction.ConfirmSever(clicked.gasketId());
            }
            return new TunerAction.PromptSever(
                    PROMPT_SEVER_PREFIX + clicked.role().getSerializedName()
                            + PROMPT_SEVER_SUFFIX);
        }
        return new TunerAction.StartAwaiting(clicked.role(), clicked.gasketId(),
                clicked.pos(), clicked.slot(), clicked.faceLabel());
    }

    /**
     * Returns true if the confirm target matches the clicked position and slot.
     *
     * @param confirm the pending confirmation context
     * @param clicked the gasket face that was targeted
     * @return true if both position and slot match
     */
    private static boolean isSameTarget(ConfirmContext confirm, GasketClick clicked) {
        if (confirm.target() == null) {
            return false;
        }
        return confirm.target().equals(clicked.pos()) && confirm.slot() == clicked.slot();
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
                implements TunerAction {
        }

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
                implements TunerAction {
        }

        /**
         * Prompt the player to confirm replacing their current carried selection.
         *
         * @param message the prompt message to display
         */
        record PromptReplace(String message) implements TunerAction {
        }

        /**
         * Prompt the player to confirm severing an existing link.
         *
         * @param message the prompt message to display
         */
        record PromptSever(String message) implements TunerAction {
        }

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
                implements TunerAction {
        }

        /**
         * Confirmed: sever the gasket's link.
         *
         * @param gasketId the gasket UUID to sever
         */
        record ConfirmSever(UUID gasketId) implements TunerAction {
        }

        /**
         * Warning: no gasket present on this face.
         *
         * @param message the warning message to display
         */
        record NoGasketWarning(String message) implements TunerAction {
        }
    }
}
