package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooBlobItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Stateless dispatch and handler methods for vat block interactions.
 * Keeps VatBlock under the TooManyMethods threshold by extracting
 * item-use and empty-hand logic into a utility class.
 */
final class VatInteractionHandler {

    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private VatInteractionHandler() { }

    // --- Dispatch ---

    /** Server-side instanceof dispatch chain for item interactions.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hand      the hand used
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    static InteractionResult dispatchInteraction(
            VatBlockEntity vat, ItemStack stack,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = dispatchGasketOrBlob(vat, stack, player, hitResult);
        if (result != null) { return result; }
        result = VatFluidInteraction.dispatchFluidContainers(vat, stack, player, hand);
        if (result != null) { return result; }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Dispatches gasket apply and blob insert interactions.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result, or null if no match
     */
    @Nullable
    private static InteractionResult dispatchGasketOrBlob(
            VatBlockEntity vat, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        if (stack.getItem() instanceof ChoralGasketItem) {
            return handleGasketApply(vat, stack, player, hitResult);
        }
        if (stack.getItem() instanceof GooBlobItem || stack.getItem() instanceof GooOmniblobItem) {
            return handleBlobInsert(vat, stack, player);
        }
        return null;
    }

    // --- Gasket handlers ---

    /**
     * Applies a gasket to the cap or base face depending on where the player clicked.
     * Click on upper half or UP face -> cap gasket. Lower half or DOWN face -> base gasket.
     * Cannot apply to a face that is occluded by another vat or already has a gasket.
     *
     * @param vat       the vat block entity
     * @param stack     the item stack
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    static InteractionResult handleGasketApply(
            VatBlockEntity vat, ItemStack stack,
            Player player, BlockHitResult hitResult) {
        var state = vat.getBlockState();
        BooleanProperty target = VatGasketOps.resolveGasketFace(hitResult, vat.getBlockPos());
        if (state.getValue(target)) { return InteractionResult.PASS; }
        if (VatGasketOps.isFaceOccluded(state, target)) { return InteractionResult.PASS; }
        applyGasketToFace(vat, target);
        consumeIfSurvival(stack, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Shrinks the stack by one unless the player is in creative mode.
     *
     * @param stack  the item stack to consume from
     * @param player the interacting player
     */
    private static void consumeIfSurvival(ItemStack stack, Player player) {
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    /**
     * Sets the gasket blockstate and registers the gasket in the registry.
     *
     * @param vat    the vat block entity
     * @param target the gasket property to set
     */
    private static void applyGasketToFace(VatBlockEntity vat, BooleanProperty target) {
        var level = vat.getLevel();
        var pos = vat.getBlockPos();
        level.setBlock(pos, vat.getBlockState().setValue(target, true), BLOCK_UPDATE_FLAGS);
        GasketRole role = target == VatBlock.GASKET_CAP ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        registerNewGasket(vat, role, level, pos);
    }

    /**
     * Ensures a gasket UUID and publishes its location to the registry.
     *
     * @param vat   the vat block entity
     * @param role  the gasket role
     * @param level the current level
     * @param pos   the block position
     */
    private static void registerNewGasket(
            VatBlockEntity vat, GasketRole role, Level level, BlockPos pos) {
        UUID newId = vat.ensureGasketId(role);
        if (newId != null && level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            registry.updateLocation(newId,
                new GasketLocation(serverLevel.dimension(), pos,
                    role == GasketRole.RECEIVER, GasketPartner.NO_SLOT));
        }
    }

    /**
     * Removes the gasket on the targeted face if one is installed.
     * Flips the blockstate, drops the gasket item, and clears the gasket UUID.
     *
     * @param vat       the vat block entity
     * @param hitResult the ray trace hit result
     * @return the interaction result
     */
    static InteractionResult handleGasketRemove(
            VatBlockEntity vat, BlockHitResult hitResult) {
        var state = vat.getBlockState();
        BooleanProperty target = VatGasketOps.resolveGasketFace(hitResult, vat.getBlockPos());
        if (!state.getValue(target)) { return InteractionResult.PASS; }
        removeGasketFromFace(vat, state, target);
        return InteractionResult.SUCCESS;
    }

    /**
     * Pops the gasket item, clears the UUID, and flips the blockstate flag.
     *
     * @param vat    the vat block entity
     * @param state  the current block state
     * @param target the gasket property to clear
     */
    private static void removeGasketFromFace(
            VatBlockEntity vat, net.minecraft.world.level.block.state.BlockState state,
            BooleanProperty target) {
        var level = vat.getLevel();
        var pos = vat.getBlockPos();
        GasketRole role = target == VatBlock.GASKET_CAP ? GasketRole.RECEIVER : GasketRole.TRANSMITTER;
        GasketInstallation.popGasket(level, pos, vat.getGasketId(role));
        vat.clearGasket(role);
        level.setBlock(pos, state.setValue(target, false), BLOCK_UPDATE_FLAGS);
    }

    // --- Blob handlers ---

    /** Inserts a blob or omniblob's volume into the vat.
     *
     * @param vat    the vat block entity
     * @param stack  the item stack
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleBlobInsert(
            VatBlockEntity vat, ItemStack stack, Player player) {
        GooType blobType = BlobStacks.gooTypeOf(stack);
        if (blobType == null) { return InteractionResult.PASS; }
        long volume = BlobStacks.volumeOf(stack);
        if (volume <= 0 || !vat.canAccept()) { return InteractionResult.PASS; }

        long accepted = vat.insertGoo(blobType, volume);
        if (accepted <= 0) { return InteractionResult.PASS; }

        BlobStacks.deplete(stack, accepted, player);
        return InteractionResult.SUCCESS;
    }

    /** Extracts a full stack (64,000 mB) of the dominant type from the vat into the player's inventory.
     *
     * @param vat    the vat block entity
     * @param player the interacting player
     * @return the interaction result
     */
    static InteractionResult handleBlobExtract(VatBlockEntity vat, Player player) {
        GooType dominant = VatFluidInteraction.extractableDominant(vat);
        if (dominant == null) { return InteractionResult.PASS; }
        long extractAmount = Math.min(vat.getContents().getVolume(dominant), BlobStacks.MAX_BLOB_STACK_VOLUME);
        long extracted = vat.extractGoo(dominant, extractAmount);
        if (extracted <= 0) { return InteractionResult.PASS; }

        ItemStack output = BlobStacks.createForOutput(dominant, extracted);
        PlayerUtils.addOrDrop(player, output);
        return InteractionResult.SUCCESS;
    }

}
