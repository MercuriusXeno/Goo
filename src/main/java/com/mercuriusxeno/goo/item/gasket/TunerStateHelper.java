package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

/**
 * Stateless helper for reading/writing TunerState on a choral tuner stack
 * and sending player feedback. Extracted from ChoralTunerItem to stay
 * under the TooManyMethods threshold.
 */
final class TunerStateHelper {

    /** Feedback message for cancel action. */
    private static final String MSG_CANCELLED = "Cancelled";
    /** Custom model data key for receiver role. */
    private static final String MODEL_RECEIVER = "receiver";
    /** Custom model data key for transmitter role. */
    private static final String MODEL_TRANSMITTER = "transmitter";

    private TunerStateHelper() {}

    /**
     * Returns the tuner state from the stack, defaulting to EMPTY.
     *
     * @param stack the tuner item stack
     * @return the tuner state, never null
     */
    static TunerState getState(ItemStack stack) {
        TunerState state = stack.get(GooDataComponents.TUNER_STATE.get());
        return state != null ? state : TunerState.EMPTY;
    }

    /**
     * Writes the tuner state to the stack and syncs model data for visual feedback.
     *
     * @param stack the tuner item stack
     * @param state the tuner state to write
     */
    static void setState(ItemStack stack, TunerState state) {
        stack.set(GooDataComponents.TUNER_STATE.get(), state);
        syncModelData(stack, state);
    }

    /**
     * Updates the CustomModelData string to drive item model selection.
     *
     * @param stack the tuner item stack
     * @param state the tuner state for model selection
     */
    static void syncModelData(ItemStack stack, TunerState state) {
        GasketRole role = state.selectedRole();
        if (role == null) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, buildRoleModelData(role));
        }
    }

    /**
     * Builds a CustomModelData with the string key for the given gasket role.
     *
     * @param role the gasket role to encode
     * @return the custom model data for item model selection
     */
    static CustomModelData buildRoleModelData(GasketRole role) {
        String modelKey = role == GasketRole.RECEIVER ? MODEL_RECEIVER : MODEL_TRANSMITTER;
        return new CustomModelData(
            List.of(), List.of(),
            List.of(modelKey), List.of());
    }

    /**
     * Stamps owner on first use if not already set, writing to stack.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @return the state with owner ensured
     */
    static TunerState ensureOwner(TunerState state, Player player, ItemStack stack) {
        if (!state.hasOwner()) {
            TunerState owned = state.withOwner(player.getUUID());
            setState(stack, owned);
            return owned;
        }
        return state;
    }

    /**
     * Cancels any in-progress tuning.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @return the interaction result
     */
    static InteractionResult handleCancelTuning(
            TunerState state, Player player, ItemStack stack) {
        setState(stack, state.clearSelection());
        sendFeedback(player, TunerFeedbackPayload.cancel(MSG_CANCELLED));
        return InteractionResult.SUCCESS;
    }

    /**
     * Sends a feedback payload to the player.
     *
     * @param player  the target player
     * @param payload the feedback payload to send
     */
    static void sendFeedback(Player player, TunerFeedbackPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }
}
