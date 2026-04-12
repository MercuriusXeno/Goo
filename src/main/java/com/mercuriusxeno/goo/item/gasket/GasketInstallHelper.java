package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.HubBlock;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.ICanisterAttachable;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.UUID;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Shared helpers for gasket installation: blockstate flipping, registry updates,
 * player feedback, and intake availability checks. Extracted from
 * {@link ChoralGasketItem} to keep per-class method counts manageable.
 */
final class GasketInstallHelper {

    /** Block update flags: notify clients + update neighbors. */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** Feedback: gasket installed successfully. */
    private static final String MSG_GASKET_INSTALLED = "Gasket installed";

    private GasketInstallHelper() {}

    /**
     * Returns true if the intake is blocked by a canister attachment above.
     *
     * @param level the level
     * @param pos   the block position
     * @return true if a canister is attached on top
     */
    static boolean isIntakeBlocked(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ICanisterAttachable att
                && att.currentTopAttachments() > 0;
    }

    /**
     * Flips HAS_GASKET blockstate on a hub for intake visual. No-op for non-hub entities.
     *
     * @param level the level
     * @param pos   the block position
     */
    static void flipHubIntakeBlockstate(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HubBlockEntity) {
            flipBlockstate(level, pos, level.getBlockState(pos), HubBlock.HAS_GASKET);
        }
    }

    /**
     * Sets a boolean blockstate property to true and sends block updates.
     *
     * @param level    the level
     * @param pos      the block position
     * @param state    the current blockstate
     * @param property the boolean property to flip
     */
    static void flipBlockstate(Level level, BlockPos pos, BlockState state, BooleanProperty property) {
        level.setBlock(pos, state.setValue(property, true), BLOCK_UPDATE_FLAGS);
    }

    /**
     * Ensures a gasket UUID on a blockstate-based machine and registers it.
     *
     * @param level the level
     * @param pos   the block position
     * @param role  the gasket role to assign
     * @param isTop whether this is a top-face gasket
     */
    static void registerBlockstateGasket(Level level, BlockPos pos, GasketRole role, boolean isTop) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IGasketHolder holder) {
            UUID newId = holder.ensureGasketId(role);
            if (newId != null) {
                registerGasketLocation(level, pos, newId, isTop, NO_SLOT);
            }
        }
    }

    /**
     * Registers a gasket UUID in the server-side GasketRegistry. No-op on client.
     *
     * @param level the level
     * @param pos   the block position
     * @param id    the gasket UUID
     * @param isTop whether this is a top-face gasket
     * @param slot  the slot index, or NO_SLOT
     */
    static void registerGasketLocation(Level level, BlockPos pos, UUID id, boolean isTop, int slot) {
        if (level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            registry.updateLocation(id, new GasketLocation(serverLevel.dimension(), pos, isTop, slot));
        }
    }

    /**
     * Builds a BlockHitResult from the use-on context.
     *
     * @param context the use-on context
     * @param pos     the block position
     * @return the hit result
     */
    static BlockHitResult buildHit(UseOnContext context, BlockPos pos) {
        return new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside());
    }

    /**
     * Sends an overlay message to the player from the context.
     *
     * @param context the use-on context
     * @param message the message text
     */
    static void sendOverlay(UseOnContext context, String message) {
        var player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.literal(message));
        }
    }

    /**
     * Sends a rejection overlay and returns PASS.
     *
     * @param context the use-on context
     * @param message the rejection reason
     * @return PASS
     */
    static InteractionResult rejectWith(UseOnContext context, String message) {
        sendOverlay(context, message);
        return InteractionResult.PASS;
    }

    /**
     * Consumes the gasket item (unless creative), sends default feedback, and returns SUCCESS.
     *
     * @param context the use-on context
     * @return SUCCESS
     */
    static InteractionResult finishInstall(UseOnContext context) {
        return finishInstall(context, MSG_GASKET_INSTALLED);
    }

    /**
     * Consumes the gasket item (unless creative), sends feedback, and returns SUCCESS.
     *
     * @param context the use-on context
     * @param message the feedback message
     * @return SUCCESS
     */
    static InteractionResult finishInstall(UseOnContext context, String message) {
        Player player = context.getPlayer();
        if (player != null && !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        sendOverlay(context, message);
        return InteractionResult.SUCCESS;
    }
}
