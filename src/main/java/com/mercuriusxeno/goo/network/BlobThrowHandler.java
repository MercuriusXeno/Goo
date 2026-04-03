package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.effect.GooMobEffects;
import com.mercuriusxeno.goo.effect.WorldEffects;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-side handler for blob throw requests. Validates the client's claim,
 * depletes goo from the player's inventory, broadcasts the flight to nearby
 * players, and schedules the effect application after the blob "arrives."
 */
public final class BlobThrowHandler {

    /** Cost of one throw in microblobs (1 blob = 1,000 mB). */
    public static final long THROW_COST = 1000;
    /** Maximum throw range in blocks. */
    public static final double MAX_RANGE = 32.0;
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;

    /** Pending effects waiting for their blob to arrive. */
    private static final List<PendingEffect> PENDING_EFFECTS = new ArrayList<>();

    private BlobThrowHandler() {}

    /** Handles the throw payload on the server thread. */
    public static void handle(BlobThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            execute(player, payload);
        });
    }

    /** Validates and executes the throw. */
    private static void execute(ServerPlayer player, BlobThrowPayload payload) {
        // 1. Player must be holding a goo glove
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean hasGlove = mainHand.getItem() instanceof GooGloveItem
                || offHand.getItem() instanceof GooGloveItem;
        if (!hasGlove) {
            Goo.LOGGER.debug("Throw rejected: player {} not holding glove", player.getName().getString());
            return;
        }

        // 2. Goo type must be valid
        GooType gooType = GooType.fromId(payload.gooTypeId());
        if (gooType == null) {
            Goo.LOGGER.debug("Throw rejected: unknown goo type '{}'", payload.gooTypeId());
            return;
        }

        // 3. Target must be in range
        double distSq = targetDistanceSquared(player, payload);
        if (distSq > MAX_RANGE_SQUARED) {
            Goo.LOGGER.debug("Throw rejected: target out of range ({} blocks)",
                    Math.sqrt(distSq));
            return;
        }

        // 4. Player must have enough goo
        if (!GooSourceScanner.hasEnough(player, gooType, THROW_COST)) {
            Goo.LOGGER.debug("Throw rejected: insufficient {} goo", gooType.getId());
            return;
        }

        // --- Execute ---

        // Deplete goo
        long depleted = GooSourceScanner.deplete(player, gooType, THROW_COST);
        if (depleted < THROW_COST) {
            // Race condition safeguard: refund is unnecessary since deplete is atomic per call
            Goo.LOGGER.warn("Partial depletion ({}/{} mB) for {} throw - proceeding anyway",
                    depleted, THROW_COST, gooType.getId());
        }

        // Calculate travel time
        double distance = Math.sqrt(distSq);
        int travelTicks = (int) ThrowArc.travelTicks(distance);

        // Build the flight payload - origin is the glove hand, not the eye
        Vec3 hand = getThrowHandPosition(player);
        BlobFlightPayload flight = new BlobFlightPayload(
                hand.x, hand.y, hand.z,
                payload.gooTypeId(),
                payload.targetEntityId(),
                payload.targetPos(),
                payload.targetFace(),
                travelTicks,
                payload.grannyArc()
        );

        // Broadcast to tracking players and the thrower
        PacketDistributor.sendToPlayersTrackingEntity(player, flight);
        PacketDistributor.sendToPlayer(player, flight);

        // Schedule delayed effect
        ServerLevel level = (ServerLevel) player.level();

        // Play throw sound at the player's position
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        Direction face = directionFromOrdinal(payload.targetFace());
        synchronized (PENDING_EFFECTS) {
            PENDING_EFFECTS.add(new PendingEffect(
                    arrivalTick, level, player, gooType,
                    payload.targetEntityId(), payload.targetPos(), face));
        }

        Goo.LOGGER.debug("Throw executed: {} by {} -> arrival in {} ticks",
                gooType.getId(), player.getName().getString(), travelTicks);
    }

    /**
     * Called every server tick to apply effects whose blobs have arrived.
     * Wire this to {@code ServerTickEvent.Post} in the mod event bus.
     */
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (PENDING_EFFECTS.isEmpty()) return;
        int currentTick = event.getServer().getTickCount();

        synchronized (PENDING_EFFECTS) {
            Iterator<PendingEffect> it = PENDING_EFFECTS.iterator();
            while (it.hasNext()) {
                PendingEffect pe = it.next();
                if (currentTick >= pe.arrivalTick) {
                    applyEffect(pe);
                    it.remove();
                }
            }
        }
    }

    /** Applies the goo effect at the target location or entity, with impact sound. */
    private static void applyEffect(PendingEffect pe) {
        if (pe.targetEntityId >= 0) {
            Entity target = pe.level.getEntity(pe.targetEntityId);
            if (target instanceof LivingEntity living) {
                pe.level.playSound(null, living.getX(), living.getY(), living.getZ(),
                        SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.9f + pe.level.getRandom().nextFloat() * 0.2f);
                GooMobEffects.apply(pe.level, living, pe.gooType, pe.thrower);
            } else {
                Goo.LOGGER.debug("Blob arrived but entity {} no longer exists", pe.targetEntityId);
            }
        } else {
            BlockPos pos = pe.targetPos;
            pe.level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.9f + pe.level.getRandom().nextFloat() * 0.2f);
            WorldEffects.apply(pe.level, pe.targetPos, pe.gooType, pe.targetFace);
        }
    }

    /** Returns squared distance from the player to the packet's target. */
    private static double targetDistanceSquared(ServerPlayer player, BlobThrowPayload payload) {
        if (payload.targetEntityId() >= 0) {
            Entity target = player.level().getEntity(payload.targetEntityId());
            if (target == null) return Double.MAX_VALUE;
            return player.distanceToSqr(target);
        }
        BlockPos pos = payload.targetPos();
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Converts a Direction ordinal (from the network payload) to a Direction.
     * Returns null for out-of-range values (e.g. -1 for entity targets).
     */
    private static Direction directionFromOrdinal(int ordinal) {
        Direction[] dirs = Direction.values();
        if (ordinal >= 0 && ordinal < dirs.length) {
            return dirs[ordinal];
        }
        return null;
    }

    /**
     * Computes the world-space glove hand position on the server using
     * yaw-derived basis vectors. Delegates pure offset to {@link ThrowArc}.
     */
    private static Vec3 getThrowHandPosition(ServerPlayer player) {
        float side = ThrowArc.gloveSide(player.getMainHandItem(), player.getMainArm());
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        // right = (-cos, 0, -sin), up = (0, 1, 0) - yaw-only, no pitch on server
        Vec3 offset = ThrowArc.handOffset(-cos, 0, -sin, 0, 1, 0,
                side, player.getScale());
        return player.getEyePosition().add(offset);
    }

    /** A goo effect waiting for its blob to finish travelling. */
    private record PendingEffect(int arrivalTick, ServerLevel level,
                                 ServerPlayer thrower, GooType gooType,
                                 int targetEntityId, BlockPos targetPos,
                                 Direction targetFace) {}
}
