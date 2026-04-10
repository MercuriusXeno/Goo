package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.ICanisterHolder;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GooInteractionType;
import com.mercuriusxeno.goo.item.IGooItemInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import java.util.UUID;

/**
 * Choral Gasket: attaches to machine faces to enable remote goo transfer.
 * Right-click a machine to install a gasket on the targeted face.
 * Per-face install: one gasket item enables one face (top or bottom).
 * Pairing (source/destination linking) is handled by the Choral Tuner.
 */
public class ChoralGasketItem extends Item implements IGooItemInteraction {

    /** Feedback: machine already has a gasket on the targeted face. */
    private static final String MSG_ALREADY_HAS_GASKET = "Already has a gasket";
    /** Feedback: crucible already has a gasket. */
    private static final String MSG_CRUCIBLE_HAS_GASKET = "This crucible already has a gasket";
    /** Feedback prefix: machine already has a gasket. */
    private static final String MSG_THIS_PREFIX = "This ";
    /** Feedback suffix: machine already has a gasket. */
    private static final String MSG_ALREADY_SUFFIX = " already has a gasket";
    /** Feedback prefix: gasket installed on a machine. */
    private static final String MSG_INSTALLED_ON = "Gasket installed on ";
    /** Feedback: remove the canister first (mutual exclusivity). */
    private static final String MSG_REMOVE_CANISTER = "Remove the canister first";
    /** Feedback: intake already has a gasket. */
    private static final String MSG_INTAKE_HAS_GASKET = "Intake already has a gasket";
    /** Feedback: intake gasket installed. */
    private static final String MSG_INTAKE_INSTALLED = "Intake gasket installed";
    /** Feedback: no canister in this slot. */
    private static final String MSG_NO_CANISTER = "No canister in this slot";
    /** Feedback label for top face. */
    private static final String FACE_TOP = "top";
    /** Feedback label for bottom face. */
    private static final String FACE_BOTTOM = "bottom";
    /** Feedback prefix for face-specific installation. */
    private static final String MSG_INSTALLED_FACE_PREFIX = "Gasket installed (";
    /** Feedback suffix for face-specific installation. */
    private static final String MSG_INSTALLED_FACE_SUFFIX = ")";
    /** Machine name: tap. */
    private static final String MACHINE_TAP = "tap";

    /**
     * Creates a choral gasket item with the given properties.
     *
     * @param properties the item properties
     */
    public ChoralGasketItem(Properties properties) {
        super(properties);
    }

    /**
     * Tells goo machine blocks to pass so the gasket's own useOn handles it.
     *
     * @return the tuner pass interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.TUNER_PASS;
    }

    /**
     * Handles gasket installation on machines. Dispatch order:
     * 1. Crucible (blockstate HAS_GASKET)
     * 2. Tap (blockstate HAS_GASKET)
     * 3. Slotted machines (canister/hub - per-face UUID on CanisterMetadata)
     * Vat gasket handling is in VatBlock.useItemOn.
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }

        var player = context.getPlayer();
        if (player == null) { return InteractionResult.PASS; }

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        return dispatchByEntityType(be, context);
    }

    /**
     * Routes to the correct install path based on block entity type.
     *
     * @param be      the block entity at the clicked position
     * @param context the use-on context carrying pos, stack, player, hit
     * @return the interaction result
     */
    private InteractionResult dispatchByEntityType(BlockEntity be, UseOnContext context) {
        if (be instanceof CrucibleBlockEntity) {
            return installOnCrucible(context);
        }
        if (be instanceof TapBlockEntity) {
            return installViaBlockstate(context, TapBlock.HAS_GASKET, MACHINE_TAP);
        }
        return dispatchSlottedIfApplicable(be, context);
    }

    /**
     * Attempts slotted-machine gasket install if the entity supports it, else PASS.
     *
     * @param be      the block entity
     * @param context the use-on context
     * @return the interaction result
     */
    private InteractionResult dispatchSlottedIfApplicable(BlockEntity be, UseOnContext context) {
        if (be instanceof ICanisterHolder container && be instanceof IGasketHolder holder) {
            return dispatchSlotted(context, container, holder);
        }
        return InteractionResult.PASS;
    }

    /**
     * Routes slotted machines to intake or per-face install based on hit region.
     *
     * @param context   the use-on context
     * @param container the slotted goo container
     * @param holder    the gasket holder interface
     * @return the interaction result
     */
    private InteractionResult dispatchSlotted(UseOnContext context,
            ICanisterHolder container, IGasketHolder holder) {
        BlockPos pos = context.getClickedPos();
        BlockHitResult hit = GasketInstallHelper.buildHit(context, pos);
        int slot = holder.resolveSlot(hit);

        if (slot == IGasketHolder.SLOT_MISS && holder.hasIntake()) {
            return installOnIntake(context, holder);
        }
        return installOnSlottedMachine(context, container, holder, hit);
    }

    /**
     * Installs a gasket on a crucible via blockstate property.
     *
     * @param context the use-on context
     * @return the interaction result
     */
    private InteractionResult installOnCrucible(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.getValue(CrucibleBlock.HAS_GASKET)) {
            return GasketInstallHelper.rejectWith(context, MSG_CRUCIBLE_HAS_GASKET);
        }

        GasketInstallHelper.flipBlockstate(context.getLevel(), context.getClickedPos(), state, CrucibleBlock.HAS_GASKET);
        GasketInstallHelper.registerBlockstateGasket(context.getLevel(), context.getClickedPos(), GasketRole.TRANSMITTER, false);
        return GasketInstallHelper.finishInstall(context);
    }

    /**
     * Generic blockstate-based gasket installation for tap and plexer.
     * Same pattern as crucible: flips a HAS_GASKET boolean property.
     *
     * @param context        the use-on context
     * @param gasketProperty the blockstate boolean property to flip
     * @param machineName    display name for feedback messages
     * @return the interaction result
     */
    private InteractionResult installViaBlockstate(UseOnContext context,
            net.minecraft.world.level.block.state.properties.BooleanProperty gasketProperty, String machineName) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.getValue(gasketProperty)) {
            return GasketInstallHelper.rejectWith(context, MSG_THIS_PREFIX + machineName + MSG_ALREADY_SUFFIX);
        }

        GasketInstallHelper.flipBlockstate(context.getLevel(), context.getClickedPos(), state, gasketProperty);
        GasketInstallHelper.registerBlockstateGasket(context.getLevel(), context.getClickedPos(), GasketRole.RECEIVER, true);
        return GasketInstallHelper.finishInstall(context, MSG_INSTALLED_ON + machineName);
    }

    /**
     * Installs a gasket on a hub's central intake. Refuses if a canister
     * is copper-fitted above (mutual exclusivity).
     *
     * @param context the use-on context
     * @param holder  the gasket holder interface
     * @return the interaction result
     */
    private InteractionResult installOnIntake(UseOnContext context, IGasketHolder holder) {
        InteractionResult guard = rejectIfIntakeUnavailable(context, holder);
        if (guard != null) { return guard; }
        return commitIntakeGasket(context, holder);
    }

    /**
     * Returns PASS with feedback if the intake is blocked or already gasketed, null otherwise.
     *
     * @param context the use-on context
     * @param holder  the gasket holder
     * @return PASS if blocked, null if available
     */
    private InteractionResult rejectIfIntakeUnavailable(UseOnContext context, IGasketHolder holder) {
        if (GasketInstallHelper.isIntakeBlocked(context.getLevel(), context.getClickedPos())) {
            return GasketInstallHelper.rejectWith(context, MSG_REMOVE_CANISTER);
        }
        if (holder.getGasketId(GasketRole.RECEIVER) != null) {
            return GasketInstallHelper.rejectWith(context, MSG_INTAKE_HAS_GASKET);
        }
        return null;
    }

    /**
     * Completes the intake gasket install after preconditions pass.
     *
     * @param context the use-on context
     * @param holder  the gasket holder
     * @return the interaction result
     */
    private InteractionResult commitIntakeGasket(UseOnContext context, IGasketHolder holder) {
        UUID newId = holder.ensureGasketId(GasketRole.RECEIVER);
        if (newId == null) { return InteractionResult.PASS; }

        GasketInstallHelper.flipHubIntakeBlockstate(context.getLevel(), context.getClickedPos());
        GasketInstallHelper.registerGasketLocation(context.getLevel(), context.getClickedPos(), newId, true, GasketPartner.NO_SLOT);
        return GasketInstallHelper.finishInstall(context, MSG_INTAKE_INSTALLED);
    }

    /**
     * Installs a gasket on one face of a canister or hub slot.
     * Click height determines face: upper half = RECEIVER (top),
     * lower half = TRANSMITTER (bottom).
     *
     * @param context   the use-on context
     * @param container the slotted goo container
     * @param holder    the gasket holder interface
     * @param hit       the ray trace hit result
     * @return the interaction result
     */
    private InteractionResult installOnSlottedMachine(UseOnContext context,
            ICanisterHolder container, IGasketHolder holder, BlockHitResult hit) {
        int slot = holder.resolveSlot(hit);
        if (slot < 0) { return InteractionResult.PASS; }

        if (container.getCanister(slot).isEmpty()) {
            return GasketInstallHelper.rejectWith(context, MSG_NO_CANISTER);
        }
        return commitSlotGasket(context, container, holder.resolveRole(hit), slot);
    }

    /**
     * Checks for existing gasket on the face, then installs if clear.
     *
     * @param context   the use-on context
     * @param container the slotted goo container
     * @param role      the gasket role (top/bottom)
     * @param slot      the resolved slot index
     * @return the interaction result
     */
    private InteractionResult commitSlotGasket(UseOnContext context,
            ICanisterHolder container, GasketRole role, int slot) {
        if (slotAlreadyHasGasket(container, slot, role)) {
            return GasketInstallHelper.rejectWith(context, MSG_ALREADY_HAS_GASKET);
        }

        UUID newId = applySlotGasket(container, slot, role);
        GasketInstallHelper.registerGasketLocation(context.getLevel(), context.getClickedPos(), newId, role == GasketRole.RECEIVER, slot);
        String face = role == GasketRole.RECEIVER ? FACE_TOP : FACE_BOTTOM;
        return GasketInstallHelper.finishInstall(context, MSG_INSTALLED_FACE_PREFIX + face + MSG_INSTALLED_FACE_SUFFIX);
    }

    /**
     * Returns true if the given slot already has a gasket on the targeted face.
     *
     * @param container the slotted container
     * @param slot      the slot index
     * @param role      the gasket role (determines which face to check)
     * @return true if a gasket UUID already exists on that face
     */
    private boolean slotAlreadyHasGasket(ICanisterHolder container, int slot, GasketRole role) {
        CanisterMetadata meta = container.getSlotMetadata(slot);
        UUID existing = role == GasketRole.RECEIVER ? meta.topGasketId() : meta.bottomGasketId();
        return existing != null;
    }

    /**
     * Creates a new gasket UUID and writes it to the slot metadata for the given face.
     *
     * @param container the slotted container
     * @param slot      the slot index
     * @param role      the gasket role
     * @return the newly generated UUID
     */
    private UUID applySlotGasket(ICanisterHolder container, int slot, GasketRole role) {
        UUID newId = UUID.randomUUID();
        CanisterMetadata meta = container.getSlotMetadata(slot);
        CanisterMetadata updated = role == GasketRole.RECEIVER
            ? meta.withTopGasketId(newId) : meta.withBottomGasketId(newId);
        container.setSlotMetadata(slot, updated);
        return newId;
    }
}
