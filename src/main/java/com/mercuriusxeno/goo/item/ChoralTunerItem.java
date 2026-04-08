package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.TunerLinkLogic.TunerAction;
import com.mercuriusxeno.goo.network.OpenNamingScreenPayload;
import com.mercuriusxeno.goo.network.TunerFeedbackPayload;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
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
    /** Feedback message for completed link. */
    private static final String MSG_LINKED = "Linked";
    /** Feedback message for severed link. */
    private static final String MSG_SEVERED = "Link severed";
    /** Feedback label for intake face. */
    private static final String LABEL_INTAKE = "intake";
    /** Custom model data key for receiver role. */
    private static final String MODEL_RECEIVER = "receiver";
    /** Custom model data key for transmitter role. */
    private static final String MODEL_TRANSMITTER = "transmitter";
    /** Default empty label for naming screen. */
    private static final String EMPTY_LABEL = "";

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
        TunerState state = getState(stack);

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
        setState(stack, state.clearSelection());
        sendFeedback(player, TunerFeedbackPayload.cancel(MSG_CANCELLED));
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
            sendFeedback(player, TunerFeedbackPayload.brief(MSG_AWAITING));
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
        TunerState state = ensureOwner(getState(stack), player, stack);

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
            sendFeedback(player, TunerFeedbackPayload.brief(MSG_NOT_YOUR_CANISTER));
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
        if (state.hasSelection()) { return handleCancelTuning(state, player, stack); }
        return handleNaming(player, holder, pos, slot);
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
            sendFeedback(player, TunerFeedbackPayload.brief(MSG_NO_GASKET));
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
        sendFeedback(player, TunerFeedbackPayload.brief(
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
        UUID existingPartnerId = lookupPartner(level, gasketId);

        TunerAction action = TunerLinkLogic.resolve(
            role, gasketId, existingPartnerId,
            state.selectedRole(), state.selectedGasketId(),
            state.pendingConfirm(), state.confirmTarget(), state.confirmSlot(),
            pos, slot, faceLabel);

        return executeAction(action, state, player, stack, level, pos, slot, faceLabel);
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
            if (state.hasSelection()) { return handleCancelTuning(state, player, stack); }
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
            sendFeedback(player, TunerFeedbackPayload.brief(MSG_NO_INTAKE_GASKET));
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
            setState(stack, state.clearSelection());
            sendFeedback(player, TunerFeedbackPayload.cancel(MSG_CANCELLED));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // --- Action execution ---

    /**
     * Executes the resolved TunerAction via pattern matching.
     *
     * @param action    the resolved tuner action
     * @param state     the current tuner state
     * @param player    the interacting player
     * @param stack     the tuner item stack
     * @param level     the current level
     * @param pos       the block position
     * @param slot      the slot index
     * @param faceLabel the face label, or null
     * @return the interaction result
     */
    private InteractionResult executeAction(
            TunerAction action, TunerState state, Player player,
            ItemStack stack, Level level, BlockPos pos, int slot,
            @Nullable String faceLabel) {
        return switch (action) {
            case TunerAction.CompleteLink a -> executeCompleteLink(a, state, player, stack, level, pos, slot, faceLabel);
            case TunerAction.StartAwaiting a -> executeStartAwaiting(a, state, player, stack);
            case TunerAction.PromptReplace a -> executePromptReplace(a, state, player, stack, pos, slot);
            case TunerAction.PromptSever a -> executePromptSever(a, state, player, stack, pos, slot);
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
    private InteractionResult executeConfirmOrWarning(
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
     * @param link           the complete link action data
     * @param state          the current tuner state
     * @param player         the interacting player
     * @param stack          the tuner item stack
     * @param level          the current level
     * @param inputPos       the input (receiver) block position
     * @param inputSlot      the input slot index
     * @param inputFaceLabel the input face label, or null
     * @return the interaction result
     */
    private InteractionResult executeCompleteLink(
            TunerAction.CompleteLink link, TunerState state,
            Player player, ItemStack stack, Level level,
            BlockPos inputPos, int inputSlot, @Nullable String inputFaceLabel) {
        if (!(level instanceof ServerLevel serverLevel)) { return InteractionResult.PASS; }

        GasketRegistry registry = GasketRegistry.get(serverLevel);
        clearDisplacedEndpoints(level, registry, link);
        registry.link(link.outputGasket(), link.inputGasket());

        writePartnerInfoAndClear(level, state, player, stack, inputPos, inputSlot);
        return InteractionResult.SUCCESS;
    }

    /**
     * Clears denormalized partner refs on both endpoints displaced by re-linking.
     * Without this, the old pusher's cache stays live and creates ghost edges.
     *
     * @param level    the current level
     * @param registry the gasket registry
     * @param link     the link action containing output and input gasket UUIDs
     */
    private void clearDisplacedEndpoints(Level level, GasketRegistry registry,
            TunerAction.CompleteLink link) {
        clearDisplacedPartner(level, registry, registry.getTarget(link.outputGasket()));
        clearDisplacedPartner(level, registry, registry.getSource(link.inputGasket()));
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
    private void writePartnerInfoAndClear(Level level, TunerState state,
            Player player, ItemStack stack, BlockPos inputPos, int inputSlot) {
        GasketRole outputRole = state.selectedRole();
        GasketRole inputRole = outputRole == GasketRole.TRANSMITTER
            ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        writePartnerInfo(level, state, inputPos, inputSlot, inputRole, outputRole);
        setState(stack, state.clearSelection());
        sendFeedback(player, TunerFeedbackPayload.linkComplete(List.of(MSG_LINKED)));
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
    private InteractionResult executeStartAwaiting(
            TunerAction.StartAwaiting awaiting, TunerState state,
            Player player, ItemStack stack) {
        TunerState newState = state.withRoleSelection(
            awaiting.gasketId(), awaiting.pos(), awaiting.role(),
            awaiting.slot(), awaiting.faceLabel());
        setState(stack, newState);

        sendFeedback(player, TunerFeedbackPayload.awaiting(
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
    private InteractionResult executePromptReplace(
            TunerAction.PromptReplace prompt, TunerState state,
            Player player, ItemStack stack, BlockPos pos, int slot) {
        TunerState newState = state.withPendingConfirm(
            ConfirmAction.REPLACE_LINK, pos, slot);
        setState(stack, newState);
        sendFeedback(player, TunerFeedbackPayload.confirmPrompt(prompt.message()));
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
    private InteractionResult executePromptSever(
            TunerAction.PromptSever prompt, TunerState state,
            Player player, ItemStack stack, BlockPos pos, int slot) {
        TunerState newState = state.withPendingConfirm(
            ConfirmAction.SEVER_LINK, pos, slot);
        setState(stack, newState);
        sendFeedback(player, TunerFeedbackPayload.confirmPrompt(prompt.message()));
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
    private InteractionResult executeConfirmReplace(
            TunerAction.ConfirmReplace confirm, TunerState state,
            Player player, ItemStack stack) {
        TunerState newState = state.withRoleSelection(
            confirm.newGasketId(), confirm.newPos(), confirm.role(),
            confirm.newSlot(), confirm.faceLabel());
        setState(stack, newState);

        sendFeedback(player, TunerFeedbackPayload.awaiting(
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
    private InteractionResult executeConfirmSever(
            TunerAction.ConfirmSever sever, Player player,
            ItemStack stack, Level level) {
        if (!(level instanceof ServerLevel serverLevel)) { return InteractionResult.PASS; }

        TunerState state = getState(stack);
        clearSeveredEndpoint(level, state, sever.gasketId());

        GasketRegistry registry = GasketRegistry.get(serverLevel);
        registry.unlink(sever.gasketId());

        setState(stack, state.clearSelection());
        sendFeedback(player, TunerFeedbackPayload.cancel(MSG_SEVERED));
        return InteractionResult.SUCCESS;
    }

    /**
     * Clears the denormalized partner reference on the endpoint being severed,
     * matching the gasket UUID to the correct role before clearing.
     *
     * @param level    the current level
     * @param state    the tuner state containing the confirm target
     * @param gasketId the UUID of the gasket being severed
     */
    private void clearSeveredEndpoint(Level level, TunerState state, UUID gasketId) {
        BlockPos pos = state.confirmTarget();
        if (pos == null) { return; }
        if (!(level.getBlockEntity(pos) instanceof IGasketHolder holder)) { return; }

        clearPartnerWithRemote(level, holder, state.confirmSlot(), gasketId);
    }

    /**
     * Finds the role matching the given gasket UUID, clears the remote partner,
     * and nulls the local partner reference.
     *
     * @param level    the current level
     * @param holder   the gasket holder on the severed endpoint
     * @param slot     the slot index
     * @param gasketId the UUID of the gasket being severed
     */
    private void clearPartnerWithRemote(Level level, IGasketHolder holder,
            int slot, UUID gasketId) {
        for (GasketRole role : GasketRole.values()) {
            if (gasketId.equals(holder.getGasketId(role, slot))) {
                clearRemotePartner(level, holder.getPartner(role, slot));
                holder.setPartner(role, slot, null);
                break;
            }
        }
    }

    /**
     * Clears the partner reference on the remote side of a link.
     * Mirrors the pattern in {@code CanisterUnlinkHandler.clearRemotePartner()}.
     *
     * @param level   the current level
     * @param partner the partner to clear, or null for no-op
     */
    private void clearRemotePartner(Level level, @Nullable GasketPartner partner) {
        if (partner == null) { return; }
        if (!level.isLoaded(partner.pos())) { return; }

        BlockEntity be = level.getBlockEntity(partner.pos());
        if (be instanceof IGasketHolder holder) {
            for (GasketRole role : GasketRole.values()) {
                holder.setPartner(role, partner.slot(), null);
            }
        }
    }

    /**
     * Clears the denormalized partner reference on a gasket being displaced
     * from a link. Uses the registry's location cache to find the block entity,
     * then matches by UUID to clear only the specific role. The setPartner(null)
     * call triggers rebuildCache on the pusher, killing any stale capability cache.
     *
     * @param level      the current level
     * @param registry   the gasket registry
     * @param displacedId the UUID of the displaced gasket, or null for no-op
     */
    private void clearDisplacedPartner(Level level, GasketRegistry registry,
            @Nullable UUID displacedId) {
        if (displacedId == null) { return; }
        GasketLocation loc = registry.getLocation(displacedId);
        if (loc == null || loc.isEntityTarget()) { return; }
        if (!level.isLoaded(loc.pos())) { return; }

        clearPartnerByGasketId(level, loc.pos(), loc.slot(), displacedId);
    }

    /**
     * Finds the role matching the given gasket UUID at a position/slot and
     * clears its partner reference.
     *
     * @param level      the current level
     * @param pos        the block position
     * @param slot       the slot index
     * @param gasketId   the gasket UUID to match
     */
    private void clearPartnerByGasketId(Level level, BlockPos pos, int slot, UUID gasketId) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IGasketHolder holder)) { return; }

        for (GasketRole role : GasketRole.values()) {
            if (gasketId.equals(holder.getGasketId(role, slot))) {
                holder.setPartner(role, slot, null);
                break;
            }
        }
    }

    /**
     * Sends a no-gasket warning message.
     *
     * @param warning the warning action data
     * @param player  the interacting player
     * @return the interaction result
     */
    private InteractionResult executeWarning(
            TunerAction.NoGasketWarning warning, Player player) {
        sendFeedback(player, TunerFeedbackPayload.brief(warning.message()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Looks up the existing partner for a gasket in the GasketRegistry.
     *
     * @param level    the current level
     * @param gasketId the gasket UUID to look up
     * @return the partner gasket UUID, or null if none
     */
    private @Nullable UUID lookupPartner(Level level, UUID gasketId) {
        if (!(level instanceof ServerLevel serverLevel)) { return null; }
        GasketRegistry registry = GasketRegistry.get(serverLevel);
        UUID target = registry.getTarget(gasketId);
        if (target != null) { return target; }
        return registry.getSource(gasketId);
    }

    // --- Partner info ---

    /**
     * Writes denormalized partner references to both endpoints after a link.
     * The output side (stored selection) gets a partner pointing to the input;
     * the input side gets a partner pointing to the output.
     *
     * @param level      the current level
     * @param state      the tuner state holding the output selection
     * @param inputPos   the input endpoint block position
     * @param inputSlot  the input endpoint slot index
     * @param inputRole  the role at the input endpoint
     * @param outputRole the role at the output endpoint
     */
    private void writePartnerInfo(
            Level level, TunerState state,
            BlockPos inputPos, int inputSlot,
            GasketRole inputRole, GasketRole outputRole) {
        BlockPos outputPos = state.selectedPos();
        int outputSlot = state.selectedSlot();

        GasketPartner toInput = new GasketPartner(inputPos, inputSlot);
        GasketPartner toOutput = new GasketPartner(outputPos, outputSlot);

        setPartnerOnSide(level, outputPos, outputSlot, outputRole, toInput);
        setPartnerOnSide(level, inputPos, inputSlot, inputRole, toOutput);
    }

    /**
     * Sets the partner reference on a specific gasket via the unified IGasketHolder interface.
     *
     * @param level   the current level
     * @param pos     the block position
     * @param slot    the slot index
     * @param role    the gasket role
     * @param partner the partner reference, or null to clear
     */
    private void setPartnerOnSide(
            Level level, BlockPos pos, int slot, GasketRole role,
            @Nullable GasketPartner partner) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IGasketHolder holder) {
            holder.setPartner(role, slot, partner);
        }
    }

    // --- Naming ---

    /**
     * Opens the naming screen for the target machine.
     *
     * @param player the interacting player
     * @param holder the gasket holder interface
     * @param pos    the block position
     * @param slot   the slot index
     * @return the interaction result
     */
    private InteractionResult handleNaming(
            Player player, IGasketHolder holder, BlockPos pos, int slot) {
        if (!(player instanceof ServerPlayer serverPlayer)) { return InteractionResult.PASS; }

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
    private boolean hasActiveLink(Level level, IGasketHolder holder, int slot) {
        if (!(level instanceof ServerLevel serverLevel)) { return false; }
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
    private boolean isLinked(GasketRegistry registry, @Nullable UUID gasketId) {
        if (gasketId == null) { return false; }
        return registry.getTarget(gasketId) != null
            || registry.getSource(gasketId) != null;
    }

    // --- Cancel ---

    /**
     * Cancels any in-progress tuning.
     *
     * @param state  the current tuner state
     * @param player the interacting player
     * @param stack  the tuner item stack
     * @return the interaction result
     */
    private InteractionResult handleCancelTuning(
            TunerState state, Player player, ItemStack stack) {
        setState(stack, state.clearSelection());
        sendFeedback(player, TunerFeedbackPayload.cancel(MSG_CANCELLED));
        return InteractionResult.SUCCESS;
    }

    // --- Feedback helpers ---

    /**
     * Sends a feedback payload to the player.
     *
     * @param player  the target player
     * @param payload the feedback payload to send
     */
    private void sendFeedback(Player player, TunerFeedbackPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    // --- State management ---

    /**
     * Returns the tuner state from the stack, defaulting to EMPTY.
     *
     * @param stack the tuner item stack
     * @return the tuner state, never null
     */
    private static TunerState getState(ItemStack stack) {
        TunerState state = stack.get(GooDataComponents.TUNER_STATE.get());
        return state != null ? state : TunerState.EMPTY;
    }

    /**
     * Writes the tuner state to the stack and syncs model data for visual feedback.
     *
     * @param stack the tuner item stack
     * @param state the tuner state to write
     */
    private static void setState(ItemStack stack, TunerState state) {
        stack.set(GooDataComponents.TUNER_STATE.get(), state);
        syncModelData(stack, state);
    }

    /**
     * Updates the CustomModelData string to drive item model selection.
     *
     * @param stack the tuner item stack
     * @param state the tuner state for model selection
     */
    private static void syncModelData(ItemStack stack, TunerState state) {
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
    private static CustomModelData buildRoleModelData(GasketRole role) {
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
    private TunerState ensureOwner(TunerState state, Player player, ItemStack stack) {
        if (!state.hasOwner()) {
            TunerState owned = state.withOwner(player.getUUID());
            setState(stack, owned);
            return owned;
        }
        return state;
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
