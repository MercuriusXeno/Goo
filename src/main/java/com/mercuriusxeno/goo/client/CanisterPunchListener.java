package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.CanisterBlock;
import com.mercuriusxeno.goo.network.CanisterPunchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side hold-to-break state machine for canister blocks.
 * The player must hold left-click for {@link #HOLD_TICKS} ticks while
 * aiming at the same slot before the punch packet is sent to the server.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterPunchListener {

    private CanisterPunchListener() {}

    /** Number of ticks the player must hold left-click before the canister pops. */
    private static final int HOLD_TICKS = 10;

    /** Block position of the canister being targeted, or null if inactive. */
    private static @Nullable BlockPos activePos;

    /** Slot index within the canister being targeted. */
    private static int activeSlot;

    /** Ticks elapsed since the hold started. */
    private static int holdTicks;

    /** Whether a hold-to-break sequence is in progress. */
    private static boolean active;

    /**
     * Intercepts left-click on canister blocks to begin or abort a hold sequence.
     * START on a canister: begins the hold. ABORT: clears state.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            clearState();
            return;
        }
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;

        boolean isCanister = event.getLevel().getBlockState(event.getPos())
                .getBlock() instanceof CanisterBlock;

        if (!isCanister) {
            clearState();
            return;
        }

        int slot = resolveSlot(event.getPos());
        if (slot < 0) {
            clearState();
            event.setCanceled(true);
            return;
        }

        if (active && event.getPos().equals(activePos) && slot == activeSlot) {
            event.setCanceled(true);
            return;
        }

        activePos = event.getPos();
        activeSlot = slot;
        holdTicks = 0;
        active = true;
        event.setCanceled(true);
    }

    /**
     * Advances the hold timer each client tick.
     * Clears state if the player releases the attack key, looks away,
     * or switches to a different slot. Fires the punch packet on completion.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.keyAttack.isDown()) {
            clearState();
            return;
        }

        if (!isStillAimingAtTarget(mc)) {
            clearState();
            return;
        }

        holdTicks++;
        if (holdTicks >= HOLD_TICKS) {
            sendPunchPacket(activePos, activeSlot);
            clearState();
        }
    }

    /** Returns true if the player is still looking at the same canister slot. */
    private static boolean isStillAimingAtTarget(Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) return false;
        if (!blockHit.getBlockPos().equals(activePos)) return false;

        int slot = CanisterBlock.hitSlot(blockHit, activePos);
        return slot == activeSlot;
    }

    /** Resolves the targeted slot using the client's precise hit result. */
    private static int resolveSlot(BlockPos pos) {
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit)) return -1;
        return CanisterBlock.hitSlot(blockHit, pos);
    }

    /** Sends the punch payload to the server. */
    private static void sendPunchPacket(BlockPos pos, int slot) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                new CanisterPunchPayload(pos, slot)));
        }
    }

    /** Resets all hold-to-break state. */
    private static void clearState() {
        active = false;
        activePos = null;
        activeSlot = 0;
        holdTicks = 0;
    }

    /** Returns the break progress as a float from 0.0 (just started) to 1.0 (complete). */
    public static float getProgress() {
        return active ? (float) holdTicks / HOLD_TICKS : 0f;
    }

    /** Returns the block position of the active punch target, or null if inactive. */
    public static @Nullable BlockPos getActivePos() {
        return active ? activePos : null;
    }

    /** Returns the slot index of the active punch target. */
    public static int getActiveSlot() {
        return activeSlot;
    }

    /** Returns true if a hold-to-break sequence is in progress. */
    public static boolean isActive() {
        return active;
    }
}
