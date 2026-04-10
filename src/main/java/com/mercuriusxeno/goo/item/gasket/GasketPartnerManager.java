package com.mercuriusxeno.goo.item.gasket;

import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.TunerLinkLogic.TunerAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Stateless helper for gasket partner lookups, link cleanup, and
 * denormalized partner-ref writes. Extracted from ChoralTunerItem
 * to stay under the TooManyMethods threshold.
 */
final class GasketPartnerManager {

    private GasketPartnerManager() {}

    /**
     * Looks up the existing partner for a gasket in the GasketRegistry.
     *
     * @param level    the current level
     * @param gasketId the gasket UUID to look up
     * @return the partner gasket UUID, or null if none
     */
    static @Nullable UUID lookupPartner(Level level, UUID gasketId) {
        if (!(level instanceof ServerLevel serverLevel)) { return null; }
        GasketRegistry registry = GasketRegistry.get(serverLevel);
        UUID target = registry.getTarget(gasketId);
        if (target != null) { return target; }
        return registry.getSource(gasketId);
    }

    /**
     * Clears denormalized partner refs on both endpoints displaced by re-linking.
     * Without this, the old pusher's cache stays live and creates ghost edges.
     *
     * @param level    the current level
     * @param registry the gasket registry
     * @param link     the link action containing output and input gasket UUIDs
     */
    static void clearDisplacedEndpoints(Level level, GasketRegistry registry,
            TunerAction.CompleteLink link) {
        clearDisplacedPartner(level, registry, registry.getTarget(link.outputGasket()));
        clearDisplacedPartner(level, registry, registry.getSource(link.inputGasket()));
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
    static void clearDisplacedPartner(Level level, GasketRegistry registry,
            @Nullable UUID displacedId) {
        if (displacedId == null) { return; }
        GasketLocation loc = registry.getLocation(displacedId);
        if (loc == null || loc.isEntityTarget()) { return; }
        if (!level.isLoaded(loc.pos())) { return; }

        clearPartnerByGasketId(level, loc.pos(), loc.slot(), displacedId);
    }

    /**
     * Clears the denormalized partner reference on the endpoint being severed,
     * matching the gasket UUID to the correct role before clearing.
     *
     * @param level    the current level
     * @param state    the tuner state containing the confirm target
     * @param gasketId the UUID of the gasket being severed
     */
    static void clearSeveredEndpoint(Level level, TunerState state, UUID gasketId) {
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
    static void clearPartnerWithRemote(Level level, IGasketHolder holder,
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
    static void clearRemotePartner(Level level, @Nullable GasketPartner partner) {
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
     * Finds the role matching the given gasket UUID at a position/slot and
     * clears its partner reference.
     *
     * @param level      the current level
     * @param pos        the block position
     * @param slot       the slot index
     * @param gasketId   the gasket UUID to match
     */
    static void clearPartnerByGasketId(Level level, BlockPos pos, int slot, UUID gasketId) {
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
    static void writePartnerInfo(
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
    static void setPartnerOnSide(
            Level level, BlockPos pos, int slot, GasketRole role,
            @Nullable GasketPartner partner) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IGasketHolder holder) {
            holder.setPartner(role, slot, partner);
        }
    }
}
