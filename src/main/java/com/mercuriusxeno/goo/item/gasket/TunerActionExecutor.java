package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.ConfirmAction;
import com.mercuriusxeno.goo.item.gasket.TunerLinkLogic.TunerAction;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Stateless executor for resolved TunerActions. Each method maps a
 * {@link TunerAction} variant to its side-effects: registry writes,
 * partner updates, state transitions, and player feedback.
 */
final class TunerActionExecutor {

    /**
     * Feedback message for awaiting link state.
     */
    private static final String MSG_AWAITING = "Awaiting link...";
    /**
     * Feedback message for completed link.
     */
    private static final String MSG_LINKED = "Linked";
    /**
     * Feedback message for severed link.
     */
    private static final String MSG_SEVERED = "Link severed";
    /**
     * Default empty label for naming screen.
     */
    private static final String EMPTY_LABEL = "";

    private TunerActionExecutor() {
    }

    /**
     * Executes the resolved TunerAction via pattern matching.
     *
     * @param action  the resolved tuner action
     * @param state   the current tuner state
     * @param player  the interacting player
     * @param stack   the tuner item stack
     * @param level   the current level
     * @param clicked the gasket face that was targeted
     * @return the interaction result
     */
    static InteractionResult executeAction(
            TunerAction action, TunerState state, Player player,
            ItemStack stack, Level level, GasketClick clicked) {
        return switch (action) {
            case TunerAction.CompleteLink a -> executeCompleteLink(a, state, player, stack, level, clicked);
            case TunerAction.StartAwaiting a -> executeStartAwaiting(a, state, player, stack);
            case TunerAction.PromptReplace a ->
                    executePromptReplace(a, state, player, stack, clicked.pos(), clicked.slot());
            case TunerAction.PromptSever a ->
                    executePromptSever(a, state, player, stack, clicked.pos(), clicked.slot());
            default -> executeConfirmOrWarning(action, state, player, stack, level);
        };
    }

    /**
     * Executes confirm and warning actions that do not need positional context.
     *
     * @param action the resolved tuner action (ConfirmReplace, ConfirmSever, or NoGasketWarning)
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @return the interaction result
     */
    static InteractionResult executeConfirmOrWarning(
            TunerAction action, TunerState state, Player player,
            ItemStack stack, Level level) {
        return switch (action) {
            case TunerAction.ConfirmReplace a -> executeConfirmReplace(a, state, player, stack);
            case TunerAction.ConfirmSever a -> executeConfirmSever(a, player, stack, level);
            case TunerAction.NoGasketWarning a -> executeWarning(a, player);
            default -> InteractionResult.PASS;
        };
    }

    /**
     * Completes a link: registers in GasketRegistry, writes partners, clears selection.
     *
     * @param link    the complete link action data
     * @param state   the current tuner state
     * @param player  the interacting player
     * @param stack   the tuner item stack
     * @param level   the current level
     * @param clicked the gasket face that was targeted
     * @return the interaction result
     */
    private static InteractionResult executeCompleteLink(
            TunerAction.CompleteLink link, TunerState state,
            Player player, ItemStack stack, Level level,
            GasketClick clicked) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        GasketRegistry registry = GasketRegistry.get(serverLevel);
        GasketPartnerManager.clearDisplacedEndpoints(level, registry, link);
        registry.link(link.outputGasket(), link.inputGasket());

        writePartnerInfoAndClear(level, state, player, stack, clicked.pos(), clicked.slot());
        return InteractionResult.SUCCESS;
    }

    /**
     * Writes denormalized partner references for both endpoints and clears selection.
     *
     * @param level     the current level
     * @param state     the tuner state holding the output selection
     * @param player    the interacting player
     * @param stack     the tuner item stack
     * @param inputPos  the input endpoint block position
     * @param inputSlot the input endpoint slot index
     */
    private static void writePartnerInfoAndClear(Level level, TunerState state,
                                                 Player player, ItemStack stack, BlockPos inputPos, int inputSlot) {
        GasketRole outputRole = state.selectedRole();
        GasketRole inputRole = outputRole == GasketRole.TRANSMITTER
                ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        GasketPartnerManager.writePartnerInfo(level, state, inputPos, inputSlot, inputRole, outputRole);
        TunerStateHelper.setState(stack, state.clearSelection());
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.linkComplete(List.of(MSG_LINKED)));
    }

    /**
     * Stores the selection and sends AWAITING feedback.
     *
     * @param awaiting the start awaiting action data
     * @param state    the current tuner state
     * @param player   the interacting player
     * @param stack    the tuner item stack
     * @return the interaction result
     */
    private static InteractionResult executeStartAwaiting(
            TunerAction.StartAwaiting awaiting, TunerState state,
            Player player, ItemStack stack) {
        TunerState newState = state.withRoleSelection(
                awaiting.gasketId(), awaiting.pos(), awaiting.role(),
                awaiting.slot(), awaiting.faceLabel());
        TunerStateHelper.setState(stack, newState);

        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.awaiting(
                MSG_AWAITING, awaiting.pos(), awaiting.slot(), awaiting.role()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets pending REPLACE_LINK confirm and sends prompt.
     *
     * @param prompt the prompt replace action data
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param pos    the block position
     * @param slot   the slot index
     * @return the interaction result
     */
    private static InteractionResult executePromptReplace(
            TunerAction.PromptReplace prompt, TunerState state,
            Player player, ItemStack stack, BlockPos pos, int slot) {
        TunerState newState = state.withPendingConfirm(
                ConfirmAction.REPLACE_LINK, pos, slot);
        TunerStateHelper.setState(stack, newState);
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.confirmPrompt(prompt.message()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets pending SEVER_LINK confirm and sends prompt.
     *
     * @param prompt the prompt sever action data
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param pos    the block position
     * @param slot   the slot index
     * @return the interaction result
     */
    private static InteractionResult executePromptSever(
            TunerAction.PromptSever prompt, TunerState state,
            Player player, ItemStack stack, BlockPos pos, int slot) {
        TunerState newState = state.withPendingConfirm(
                ConfirmAction.SEVER_LINK, pos, slot);
        TunerStateHelper.setState(stack, newState);
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.confirmPrompt(prompt.message()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Confirmed replace: clears old selection and stores new one.
     *
     * @param confirm the confirm replace action data
     * @param state   the current tuner state
     * @param player  the interacting player
     * @param stack   the tuner item stack
     * @return the interaction result
     */
    private static InteractionResult executeConfirmReplace(
            TunerAction.ConfirmReplace confirm, TunerState state,
            Player player, ItemStack stack) {
        TunerState newState = state.withRoleSelection(
                confirm.newGasketId(), confirm.newPos(), confirm.role(),
                confirm.newSlot(), confirm.faceLabel());
        TunerStateHelper.setState(stack, newState);

        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.awaiting(
                MSG_AWAITING, confirm.newPos(), confirm.newSlot(), confirm.role()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Confirmed sever: unlinks both sides and clears partner info.
     *
     * @param sever  the confirm sever action data
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @return the interaction result
     */
    private static InteractionResult executeConfirmSever(
            TunerAction.ConfirmSever sever, Player player,
            ItemStack stack, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        TunerState state = TunerStateHelper.getState(stack);
        GasketPartnerManager.clearSeveredEndpoint(level, state, sever.gasketId());

        GasketRegistry registry = GasketRegistry.get(serverLevel);
        registry.unlink(sever.gasketId());

        TunerStateHelper.setState(stack, state.clearSelection());
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.cancel(MSG_SEVERED));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sends a no-gasket warning message.
     *
     * @param warning the warning action data
     * @param player  the interacting player
     * @return the interaction result
     */
    private static InteractionResult executeWarning(
            TunerAction.NoGasketWarning warning, Player player) {
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.brief(warning.message()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Opens the naming screen for the target machine.
     *
     * @param player the interacting player
     * @param holder the gasket holder interface
     * @param pos    the block position
     * @param slot   the slot index
     * @return the interaction result
     */
    static InteractionResult handleNaming(
            Player player, IGasketHolder holder, BlockPos pos, int slot) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        String currentLabel = holder.getMachineLabel(slot);
        boolean hasLink = hasActiveLink(player.level(), holder, slot);

        PacketDistributor.sendToPlayer(serverPlayer,
                new OpenNamingScreenPayload(pos, slot,
                        currentLabel != null ? currentLabel : EMPTY_LABEL, hasLink));
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true if the machine has any active gasket link.
     *
     * @param level  the current level
     * @param holder the gasket holder interface
     * @param slot   the slot index
     * @return true if any gasket link is active
     */
    private static boolean hasActiveLink(Level level, IGasketHolder holder, int slot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        GasketRegistry registry = GasketRegistry.get(serverLevel);
        return isLinked(registry, holder.getGasketId(GasketRole.RECEIVER, slot))
                || isLinked(registry, holder.getGasketId(GasketRole.TRANSMITTER, slot));
    }

    /**
     * Returns true if the given gasket has a pairing as source or target.
     *
     * @param registry the gasket registry
     * @param gasketId the gasket UUID, or null
     * @return true if the gasket is linked
     */
    private static boolean isLinked(GasketRegistry registry, @Nullable UUID gasketId) {
        if (gasketId == null) {
            return false;
        }
        return registry.getTarget(gasketId) != null
                || registry.getSource(gasketId) != null;
    }
}
