package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pushes goo from a reservoir to a gasket partner on a fixed interval.
 * Owns the push timer and endpoint cache lifecycle.
 */
public class GasketPusher implements IGasketPusher {

    /** Release the forced chunk ticket after this many ticks with no transfer. */
    static final int IDLE_THRESHOLD = 200;

    private final IGooSource reservoir;
    private final Supplier<@Nullable UUID> gasketId;
    private final Supplier<@Nullable GasketPartner> partner;
    private final Supplier<@Nullable Level> level;
    private final Supplier<BlockPos> ownerPos;
    private final Runnable sync;
    private final IGasketRegistryAccess registryAccess;

    private int idleTicks = 0;
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, UUID> endpointCache;
    private @Nullable ChunkPos forcedChunk;

    /**
     * Creates a gasket pusher wired to the host entity's state.
     *
     * @param reservoir      the goo source to push from
     * @param gasketId       supplier for the host's gasket UUID
     * @param partner        supplier for the host's gasket partner
     * @param level          supplier for the host's level (null before setLevel)
     * @param ownerPos       supplier for the host block entity's position (ticket owner)
     * @param sync           callback to sync the host to clients after a push
     * @param registryAccess decoupled access to the gasket registry (avoids direct GasketRegistry.get calls)
     */
    public GasketPusher(IGooSource reservoir,
                        Supplier<@Nullable UUID> gasketId,
                        Supplier<@Nullable GasketPartner> partner,
                        Supplier<@Nullable Level> level,
                        Supplier<BlockPos> ownerPos,
                        Runnable sync,
                        IGasketRegistryAccess registryAccess) {
        this.reservoir = reservoir;
        this.gasketId = gasketId;
        this.partner = partner;
        this.level = level;
        this.ownerPos = ownerPos;
        this.sync = sync;
        this.registryAccess = registryAccess;
    }

    @Override
    public void tick() {
        if (!hasPushableTarget()) {
            trackIdle();
            return;
        }
        ensureChunkForced();
        pushToDestinations();
    }

    @Override
    public void dispose() {
        unforceChunk();
        endpointCache = null;
    }

    @Override
    public void rebuildCache() {
        unforceChunk();
        endpointCache = null;
        if (!canBuildCache()) return;
        GasketPartner p = partner.get();
        if (p == null || p.isEntityTarget()) return;
        UUID targetGasketId = resolveTargetGasketId();
        if (targetGasketId == null) return;
        ServerLevel serverLevel = (ServerLevel) level.get();
        ChunkPos cp = ChunkPos.containing(p.pos());
        GooTickets.GASKET_CHUNKS.forceChunk(
            serverLevel, ownerPos.get(), cp.x(), cp.z(), true, false);
        forcedChunk = cp;
        endpointCache = BlockCapabilityCache.create(
            GooCapabilities.GASKET_BLOCK,
            serverLevel,
            p.pos(),
            targetGasketId);
    }

    /** Increments idle counter and releases the chunk ticket when the threshold is reached. */
    private void trackIdle() {
        idleTicks++;
        if (idleTicks >= IDLE_THRESHOLD && forcedChunk != null) {
            unforceChunk();
        }
    }

    /** Re-forces the target chunk if the ticket was released due to idle. */
    private void ensureChunkForced() {
        if (forcedChunk != null) return;
        GasketPartner p = partner.get();
        if (p == null || p.isEntityTarget()) return;
        Level lvl = level.get();
        if (!(lvl instanceof ServerLevel serverLevel)) return;
        ChunkPos cp = ChunkPos.containing(p.pos());
        GooTickets.GASKET_CHUNKS.forceChunk(
            serverLevel, ownerPos.get(), cp.x(), cp.z(), true, false);
        forcedChunk = cp;
    }

    /** Returns true when the reservoir has goo and a valid push target is configured. */
    private boolean hasPushableTarget() {
        GasketPartner p = partner.get();
        if (reservoir.isEmpty() || p == null) return false;
        return p.isEntityTarget() || endpointCache != null;
    }

    /** Returns true when the cache can be built (server-side with a partner). */
    private boolean canBuildCache() {
        Level lvl = level.get();
        return lvl != null && !lvl.isClientSide() && partner.get() != null;
    }

    /** Releases the forced chunk ticket, if one is held. */
    private void unforceChunk() {
        if (forcedChunk == null) return;
        Level lvl = level.get();
        if (lvl instanceof ServerLevel serverLevel) {
            GooTickets.GASKET_CHUNKS.forceChunk(
                serverLevel, ownerPos.get(), forcedChunk.x(), forcedChunk.z(), false, false);
        }
        forcedChunk = null;
    }

    /** Dispatches to block or entity push path based on partner type. */
    private void pushToDestinations() {
        GasketPartner p = partner.get();
        if (reservoir.isEmpty() || p == null) return;
        if (p.isEntityTarget()) {
            pushToEntityTarget(p);
        } else {
            pushToBlockTarget();
        }
    }

    /** Pushes via BlockCapabilityCache for block-based gasket partners. */
    private void pushToBlockTarget() {
        if (endpointCache == null) return;
        ResourceHandler<FluidResource> handler = endpointCache.getCapability();
        if (handler == null) return;
        pushViaHandler(handler);
    }

    /** Looks up the player entity by UUID and queries GASKET_ENTITY. */
    private void pushToEntityTarget(GasketPartner p) {
        Level lvl = level.get();
        if (!(lvl instanceof ServerLevel serverLevel)) return;
        UUID targetEntityId = p.entityId();
        if (targetEntityId == null) return;
        UUID targetGasketId = resolveTargetGasketId();
        if (targetGasketId == null) return;
        Player player = serverLevel.getPlayerByUUID(targetEntityId);
        if (player == null) return;
        ResourceHandler<FluidResource> handler =
            player.getCapability(GooCapabilities.GASKET_ENTITY, targetGasketId);
        if (handler == null) return;
        pushViaHandler(handler);
    }

    /**
     * Transfers goo from the reservoir to the given fluid handler via
     * {@link GasketPushMath#computePush}. Syncs to clients if anything moved.
     */
    private void pushViaHandler(ResourceHandler<FluidResource> handler) {
        GooContents before = reservoir.toGooContents();
        GasketPushMath.PushResult result = GasketPushMath.computeTaperedPush(
            before, (type, volume) -> GooFluidTransfer.insert(handler, type, volume));
        if (!result.remaining().equals(before)) {
            reservoir.loadFrom(result.remaining());
            idleTicks = 0;
            sync.run();
        }
    }

    /**
     * Resolves the gasket UUID on the partner side via the injected registry access.
     * Returns the partner's gasket UUID, or null if unlinked.
     */
    private @Nullable UUID resolveTargetGasketId() {
        UUID id = gasketId.get();
        if (id == null) return null;
        return registryAccess.get().getTarget(id);
    }

    /**
     * Forces the chunk of a receiver gasket's transmitter partner.
     * Called on block entity load so the transmitter wakes up and can push.
     * Keeps ServerLevel for dimension checks and chunk forcing, but registry
     * access is decoupled through the interface.
     *
     * @param receiverGasketId the receiver gasket UUID
     * @param registryAccess   decoupled access to the gasket registry
     * @param serverLevel      the server level (for dimension + chunk forcing)
     * @param ownerPos         the receiver block entity's position (ticket owner)
     */
    public static void forceTransmitterChunk(
            @Nullable UUID receiverGasketId,
            IGasketRegistryAccess registryAccess,
            ServerLevel serverLevel,
            BlockPos ownerPos) {
        if (receiverGasketId == null) return;
        GasketRegistry registry = registryAccess.get();
        UUID sourceId = registry.getSource(receiverGasketId);
        if (sourceId == null) return;
        GasketLocation loc = registry.getLocation(sourceId);
        if (loc == null || loc.isEntityTarget()) return;
        if (!loc.dimension().equals(serverLevel.dimension())) return;
        ChunkPos cp = ChunkPos.containing(loc.pos());
        GooTickets.GASKET_CHUNKS.forceChunk(
            serverLevel, ownerPos, cp.x(), cp.z(), true, false);
    }
}
