package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.ICanisterAttachable;
import com.mercuriusxeno.goo.block.IGasketHolder;
import com.mercuriusxeno.goo.block.ISlottedGooContainer;
import com.mercuriusxeno.goo.block.TapBlock;
import com.mercuriusxeno.goo.block.TapBlockEntity;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

    /** Creates a choral gasket item with the given properties. */
    public ChoralGasketItem(Properties properties) {
        super(properties);
    }

    /** Tells goo machine blocks to pass so the gasket's own useOn handles it. */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.TUNER_PASS;
    }

    /**
     * Handles gasket installation on machines. Dispatch order:
     * 1. Crucible (blockstate HAS_GASKET)
     * 2. Slotted machines (canister/hub - per-face UUID on CanisterMetadata)
     * Vat gasket handling is in VatBlock.useItemOn.
     */
    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);

        // --- Blockstate-based gasket: crucible, tap, plexer ---
        if (be instanceof CrucibleBlockEntity) {
            return installOnCrucible(level, pos, stack, player);
        }
        if (be instanceof TapBlockEntity) {
            return installViaBlockstate(level, pos, stack, player,
                TapBlock.HAS_GASKET, "tap");
        }
        // Plexer does not support gaskets - skip

        // --- Slotted machine path (canister/hub per-face UUID) ---
        if (be instanceof ISlottedGooContainer container && be instanceof IGasketHolder holder) {
            BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(), context.getClickedFace(), pos, context.isInside());

            int slot = holder.resolveSlot(hit);
            // If we hit the intake region (SLOT_MISS) and the machine has an intake, install there
            if (slot == IGasketHolder.SLOT_MISS && holder.hasIntake()) {
                return installOnIntake(level, pos, stack, player, holder);
            }
            return installOnSlottedMachine(level, pos, stack, player, container, holder, hit);
        }

        return InteractionResult.PASS;
    }

    /** Installs a gasket on a crucible via blockstate property. */
    private InteractionResult installOnCrucible(
            Level level, BlockPos pos, ItemStack stack,
            net.minecraft.world.entity.player.Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(CrucibleBlock.HAS_GASKET)) {
            player.sendOverlayMessage(
                Component.literal("This crucible already has a gasket"));
            return InteractionResult.PASS;
        }

        level.setBlock(pos, state.setValue(CrucibleBlock.HAS_GASKET, true), 3);

        // Generate UUID and register in GasketRegistry
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IGasketHolder holder) {
            UUID newId = holder.ensureGasketId(GasketRole.TRANSMITTER);
            if (newId != null && level instanceof ServerLevel serverLevel) {
                GasketRegistry registry = GasketRegistry.get(serverLevel);
                registry.updateLocation(newId,
                    new GasketLocation(serverLevel.dimension(), pos,
                        false, GasketPartner.NO_SLOT));
            }
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(
                Component.literal("Gasket installed"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Generic blockstate-based gasket installation for tap and plexer.
     * Same pattern as crucible: flips a HAS_GASKET boolean property.
     */
    private InteractionResult installViaBlockstate(
            Level level, BlockPos pos, ItemStack stack,
            net.minecraft.world.entity.player.Player player,
            BooleanProperty gasketProperty, String machineName) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(gasketProperty)) {
            player.sendOverlayMessage(
                Component.literal("This " + machineName + " already has a gasket"));
            return InteractionResult.PASS;
        }

        level.setBlock(pos, state.setValue(gasketProperty, true), 3);

        // Ensure the gasket UUID is generated
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IGasketHolder holder) {
            UUID newId = holder.ensureGasketId(GasketRole.RECEIVER);
            if (newId != null && level instanceof ServerLevel serverLevel) {
                GasketRegistry registry = GasketRegistry.get(serverLevel);
                ResourceKey<Level> dimension = serverLevel.dimension();
                registry.updateLocation(newId,
                    new GasketLocation(dimension, pos, true, GasketPartner.NO_SLOT));
            }
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(
                Component.literal("Gasket installed on " + machineName));
        return InteractionResult.SUCCESS;
    }

    /**
     * Installs a gasket on a hub's central intake. Refuses if a canister
     * is copper-fitted above (mutual exclusivity).
     */
    private InteractionResult installOnIntake(
            Level level, BlockPos pos, ItemStack stack,
            net.minecraft.world.entity.player.Player player,
            IGasketHolder holder) {

        // Mutual exclusivity: refuse if a canister is attached above
        if (level.getBlockEntity(pos) instanceof ICanisterAttachable att
                && att.currentTopAttachments() > 0) {
            player.sendOverlayMessage(
                Component.literal("Remove the canister first"));
            return InteractionResult.PASS;
        }

        UUID existing = holder.getGasketId(GasketRole.RECEIVER);
        if (existing != null) {
            player.sendOverlayMessage(
                Component.literal("Intake already has a gasket"));
            return InteractionResult.PASS;
        }

        UUID newId = holder.ensureGasketId(GasketRole.RECEIVER);
        if (newId == null) return InteractionResult.PASS;

        // Flip blockstate for hub intake visual
        if (level.getBlockEntity(pos) instanceof HubBlockEntity) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, state.setValue(HubBlock.HAS_GASKET, true), 3);
        }

        // Register in GasketRegistry
        if (level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            ResourceKey<Level> dimension = serverLevel.dimension();
            registry.updateLocation(newId,
                new GasketLocation(dimension, pos, true, GasketPartner.NO_SLOT));
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.sendOverlayMessage(
                Component.literal("Intake gasket installed"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Installs a gasket on one face of a canister or hub slot.
     * Click height determines face: upper half = RECEIVER (top),
     * lower half = TRANSMITTER (bottom).
     */
    private InteractionResult installOnSlottedMachine(
            Level level, BlockPos pos, ItemStack stack,
            net.minecraft.world.entity.player.Player player,
            ISlottedGooContainer container, IGasketHolder holder,
            BlockHitResult hit) {

        int slot = holder.resolveSlot(hit);
        if (slot < 0 || slot == IGasketHolder.SLOT_MISS) {
            return InteractionResult.PASS;
        }

        // Refuse if the slot is empty (no canister to gasket)
        if (container.getCanister(slot).isEmpty()) {
            player.sendOverlayMessage(
                Component.literal("No canister in this slot"));
            return InteractionResult.PASS;
        }

        GasketRole role = holder.resolveRole(hit);
        CanisterMetadata meta = container.getSlotMetadata(slot);

        // Check if this face already has a gasket
        UUID existing = role == GasketRole.RECEIVER
            ? meta.topGasketId() : meta.bottomGasketId();
        if (existing != null) {
            player.sendOverlayMessage(
                Component.literal("Already has a gasket"));
            return InteractionResult.PASS;
        }

        // Install gasket on this face only
        UUID newId = UUID.randomUUID();
        CanisterMetadata updated = role == GasketRole.RECEIVER
            ? meta.withTopGasketId(newId)
            : meta.withBottomGasketId(newId);
        container.setSlotMetadata(slot, updated);

        // Register the new gasket UUID in the GasketRegistry
        if (level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            ResourceKey<Level> dimension = serverLevel.dimension();
            boolean isTop = role == GasketRole.RECEIVER;
            registry.updateLocation(newId,
                new GasketLocation(dimension, pos, isTop, slot));
        }

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        String face = role == GasketRole.RECEIVER ? "top" : "bottom";
        player.sendOverlayMessage(
                Component.literal("Gasket installed (" + face + ")"));
        return InteractionResult.SUCCESS;
    }
}
