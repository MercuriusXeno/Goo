package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.IGooItemInteraction;
import com.mercuriusxeno.goo.item.gasket.TunerLinkLogic.TunerAction;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * Choral Tuner: player-keyed tool for linking gaskets and naming machines.
 * Owner UUID is stamped on first use. Only the tuner's owner can tune their
 * machines. Role-aware: each click resolves a transmitter or receiver role
 * from the hit location, enabling bidirectional gasket linking.
 *
 * <p>Right-click gasket machine: tuning (role-aware link flow).</p>
 * <p>Shift-right-click gasket machine: naming modal (or cancel if selection active).</p>
 * <p>Shift-right-click air: cancel selection.</p>
 * <p>Right-click air (with selection): toggle reminder HUD.</p>
 *
 * <p>Action execution delegates to {@link TunerActionExecutor}, partner
 * management to {@link GasketPartnerManager}, and state access to
 * {@link TunerStateHelper}.</p>
 */
public class ChoralTunerItem extends Item implements IGooItemInteraction {

    /** Maximum length for machine labels. */
    public static final int MAX_LABEL_LENGTH = 32;

    /** Feedback message for cancel action. */
    private static final String MSG_CANCELLED = "Cancelled";
    /** Feedback message for awaiting link state. */
    private static final String MSG_AWAITING = "Awaiting link...";
    /** Feedback message for non-owner canister interaction. */
    private static final String MSG_NOT_YOUR_CANISTER = "Not your canister";
    /** Feedback message for unsupported role on a face. */
    private static final String MSG_CANT_BE_PREFIX = " can't be a ";
    /** Fallback face label when none is provided. */
    private static final String LABEL_MACHINE = "Machine";
    /** Feedback message for missing gasket. */
    private static final String MSG_NO_GASKET = "No gasket on this face";
    /** Feedback message for missing intake gasket. */
    private static final String MSG_NO_INTAKE_GASKET = "No gasket on intake";
    /** Feedback label for intake face. */
    private static final String LABEL_INTAKE = "intake";

    /**
     * Creates a new choral tuner item.
     *
     * @param properties the item properties
     */
    public ChoralTunerItem(Properties properties) {
        super(properties.stacksTo(1)
            .component(GooDataComponents.TUNER_STATE.get(), TunerState.EMPTY));
    }

    /**
     * Non-block interactions: shift + selection = cancel, shift + no selection = pass,
     * no shift + selection = toggle reminder, no shift + no selection = pass.
     *
     * @param level  the current level
     * @param player the interacting player
     * @param hand   the hand used
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player,
            @NonNull InteractionHand hand) {
        if (level.isClientSide()) { return InteractionResult.PASS; }

        ItemStack stack = player.getItemInHand(hand);
        TunerState state = TunerStateHelper.getState(stack);

        if (player.isShiftKeyDown()) {
            return handleShiftUseInAir(state, player, stack);
        }

        return handleUseInAir(state, player);
    }

    /**
     * Shift-click in air: cancel if selection active, otherwise pass.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @return the interaction result
     */
    private InteractionResult handleShiftUseInAir(TunerState state, Player player,
            ItemStack stack) {
        if (!state.hasSelection()) { return InteractionResult.PASS; }
        TunerStateHelper.setState(stack, state.clearSelection());
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.cancel(MSG_CANCELLED));
        return InteractionResult.SUCCESS;
    }

    /**
     * Non-shift click in air: show awaiting reminder if selection active.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @return the interaction result
     */
    private InteractionResult handleUseInAir(TunerState state, Player player) {
        if (state.hasSelection()) {
            TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.brief(MSG_AWAITING));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Handles tuner clicks on blocks: routes to gasket tuning, naming, or pass-through.
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        Player player = context.getPlayer();
        if (player == null) { return InteractionResult.PASS; }

        return dispatchUseOn(context, player, level);
    }

    /**
     * Unpacks context, ensures owner, and dispatches to the appropriate handler.
     *
     * @param context the use-on context
     * @param player  the interacting player
     * @param level   the current level
     * @return the interaction result
     */
    private InteractionResult dispatchUseOn(UseOnContext context, Player player, Level level) {
        ItemStack stack = context.getItemInHand();
        TunerState state = TunerStateHelper.ensureOwner(
            TunerStateHelper.getState(stack), player, stack);

        BlockPos pos = context.getClickedPos();
        BlockHitResult hit = new BlockHitResult(
            context.getClickLocation(), context.getClickedFace(), pos, context.isInside());

        return handleUseOn(state, player, stack, level, pos, hit);
    }

    /**
     * Dispatches to the appropriate handler based on target block entity type.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param pos    the block position
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult handleUseOn(
            TunerState state, Player player, ItemStack stack,
            Level level, BlockPos pos, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof IGasketHolder holder)) {
            return handleNonGasketClick(state, player, stack);
        }
        return dispatchToHolder(state, player, stack, level, holder, pos, hit);
    }

    /**
     * Routes a gasket holder click to the correct handler based on resolved slot.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult dispatchToHolder(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos, BlockHitResult hit) {
        int slot = holder.resolveSlot(hit);
        if (slot == IGasketHolder.SLOT_MISS) {
            return handleSlotMiss(state, player, stack, level, holder, pos, hit);
        }
        return handleGasketMachineClick(state, player, stack, level,
            holder, pos, slot, hit);
    }

    /**
     * Handles a slot miss on a gasket holder: falls through to intake if available.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult handleSlotMiss(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos, BlockHitResult hit) {
        if (holder.hasIntake()) {
            return handleIntakeClick(state, player, stack, level, holder, pos, hit);
        }
        return InteractionResult.PASS;
    }

    /**
     * Unified handler for all gasket machine clicks. Resolves the role from
     * the hit location, checks gasket presence, looks up existing partner,
     * and delegates to TunerLinkLogic.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param slot   the resolved slot index
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult handleGasketMachineClick(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos, int slot,
            BlockHitResult hit) {

        if (!holder.allowsTuning(state.ownerUuid())) {
            TunerStateHelper.sendFeedback(player,
                TunerFeedbackPayload.brief(MSG_NOT_YOUR_CANISTER));
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            return handleGasketShiftClick(state, player, stack, holder, pos, slot);
        }

        return resolveAndExecuteGasketAction(state, player, stack, level, holder, pos, slot, hit);
    }

    /**
     * Shift-click on a gasket machine: cancel selection if active, otherwise open naming.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param slot   the resolved slot index
     * @return the interaction result
     */
    private InteractionResult handleGasketShiftClick(
            TunerState state, Player player, ItemStack stack,
            IGasketHolder holder, BlockPos pos, int slot) {
        if (state.hasSelection()) {
            return TunerStateHelper.handleCancelTuning(state, player, stack);
        }
        return TunerActionExecutor.handleNaming(player, holder, pos, slot);
    }

    /**
     * Resolves the gasket role from the hit, validates it, then delegates
     * to TunerLinkLogic and executes the resulting action.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param slot   the resolved slot index
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult resolveAndExecuteGasketAction(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos, int slot,
            BlockHitResult hit) {
        GasketRole role = holder.resolveRole(hit);
        if (!holder.supportsRole(role)) {
            return sendUnsupportedRoleFeedback(player, holder, role);
        }

        return resolveGasketOrWarn(state, player, stack, level, holder, role, pos, slot);
    }

    /**
     * Checks gasket presence on the resolved role and delegates to link resolution,
     * or warns if no gasket is installed.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param role   the resolved gasket role
     * @param pos    the block position
     * @param slot   the slot index
     * @return the interaction result
     */
    private InteractionResult resolveGasketOrWarn(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, GasketRole role,
            BlockPos pos, int slot) {
        UUID gasketId = holder.getGasketId(role, slot);
        if (gasketId == null) {
            TunerStateHelper.sendFeedback(player,
                TunerFeedbackPayload.brief(MSG_NO_GASKET));
            return InteractionResult.SUCCESS;
        }

        return resolveAndExecuteAction(state, player, stack, level,
            role, gasketId, pos, slot, holder.getFaceLabel(role));
    }

    /**
     * Sends feedback when the clicked face does not support the resolved role.
     *
     * @param player the interacting player
     * @param holder the gasket holder on the target block
     * @param role   the unsupported gasket role
     * @return SUCCESS after sending feedback
     */
    private InteractionResult sendUnsupportedRoleFeedback(
            Player player, IGasketHolder holder, GasketRole role) {
        String msg = holder.getFaceLabel(role);
        String label = msg != null ? msg : LABEL_MACHINE;
        TunerStateHelper.sendFeedback(player, TunerFeedbackPayload.brief(
            label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1)
            + MSG_CANT_BE_PREFIX + role.name().toLowerCase(Locale.ROOT)));
        return InteractionResult.SUCCESS;
    }

    /**
     * Looks up the partner, resolves a TunerAction, and executes it.
     *
     * @param state     the current tuner state
     * @param player    the interacting player
     * @param stack     the tuner item stack
     * @param level     the current level
     * @param role      the gasket role
     * @param gasketId  the gasket UUID
     * @param pos       the block position
     * @param slot      the slot index
     * @param faceLabel the face label, or null
     * @return the interaction result
     */
    private InteractionResult resolveAndExecuteAction(
            TunerState state, Player player, ItemStack stack, Level level,
            GasketRole role, UUID gasketId, BlockPos pos, int slot,
            @Nullable String faceLabel) {
        UUID existingPartnerId = GasketPartnerManager.lookupPartner(level, gasketId);

        TunerAction action = TunerLinkLogic.resolve(
            role, gasketId, existingPartnerId,
            state.selectedRole(), state.selectedGasketId(),
            state.pendingConfirm(), state.confirmTarget(), state.confirmSlot(),
            pos, slot, faceLabel);

        return TunerActionExecutor.executeAction(
            action, state, player, stack, level, pos, slot, faceLabel);
    }

    /**
     * Handles clicks on the intake region (e.g. hub frame). The intake is always
     * a RECEIVER. Shift-click cancels selection; naming is not applicable.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @param hit    the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult handleIntakeClick(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos,
            BlockHitResult hit) {

        if (player.isShiftKeyDown()) {
            if (state.hasSelection()) {
                return TunerStateHelper.handleCancelTuning(state, player, stack);
            }
            return InteractionResult.PASS;
        }

        return resolveIntakeGasket(state, player, stack, level, holder, pos);
    }

    /**
     * Resolves the intake gasket and delegates to link resolution, or warns if absent.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @param level  the current level
     * @param holder the gasket holder on the target block
     * @param pos    the block position
     * @return the interaction result
     */
    private InteractionResult resolveIntakeGasket(
            TunerState state, Player player, ItemStack stack,
            Level level, IGasketHolder holder, BlockPos pos) {
        UUID gasketId = holder.getGasketId(GasketRole.RECEIVER);
        if (gasketId == null) {
            TunerStateHelper.sendFeedback(player,
                TunerFeedbackPayload.brief(MSG_NO_INTAKE_GASKET));
            return InteractionResult.SUCCESS;
        }

        return resolveAndExecuteAction(state, player, stack, level,
            GasketRole.RECEIVER, gasketId, pos, GasketPartner.NO_SLOT, LABEL_INTAKE);
    }

    /**
     * Handles clicks on non-gasket blocks: cancel selection if shift held.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @return the interaction result
     */
    private InteractionResult handleNonGasketClick(
            TunerState state, Player player, ItemStack stack) {
        if (player.isShiftKeyDown() && state.hasSelection()) {
            TunerStateHelper.setState(stack, state.clearSelection());
            TunerStateHelper.sendFeedback(player,
                TunerFeedbackPayload.cancel(MSG_CANCELLED));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * Returns TUNER_PASS so canister blocks defer to the tuner's own useOn logic.
     *
     * @return the tuner pass interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.TUNER_PASS;
    }
}
