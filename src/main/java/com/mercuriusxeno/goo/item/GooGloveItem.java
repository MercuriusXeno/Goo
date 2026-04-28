package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Goo glove held in main/offhand. Right-click is overloaded:
 * short press (<6 ticks) throws the selected goo type,
 * long press (>=6 ticks) opens the radial menu to change selection.
 */
public class GooGloveItem extends Item {

    /** Ticks of hold before radial menu opens instead of throwing. */
    public static final int RADIAL_THRESHOLD_TICKS = 6;
    /** Maximum use duration in ticks (same as bow: 1 hour at 20 tps). */
    private static final int MAX_USE_DURATION = 72_000;
    /** Recollect pickup sound volume. */
    private static final float PICKUP_VOLUME = 0.5f;
    /** Recollect pickup sound pitch. */
    private static final float PICKUP_PITCH = 1.2f;

    /**
     * Creates a goo glove item with the given properties.
     *
     * @param properties the item properties
     */
    public GooGloveItem(Properties properties) {
        super(properties);
    }

    /**
     * Shift+right-click on a chain marker recollects blobs. Returns the
     * stacked blobs to the player's inventory and removes the marker.
     *
     * @param context the use-on-block context
     * @return SUCCESS if recollected, PASS otherwise
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) { return InteractionResult.PASS; }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            recollectBlobs(level, pos, be, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Gives the marker's stacked blobs back to the player and removes the block.
     * @param level the world the marker exists in
     * @param pos the marker block position
     * @param be the chain marker block entity holding blob data
     * @param player the player receiving the recollected blobs
     */
    private static void recollectBlobs(Level level, BlockPos pos,
            ChainMarkerBlockEntity be, Player player) {
        GooType type = be.getGooType();
        int stacks = be.getStackCount();
        if (stacks > 0) {
            ItemStack blobs = BlobStacks.createBlobStack(type, stacks);
            PlayerUtils.addOrDrop(player, blobs);
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                PICKUP_VOLUME, PICKUP_PITCH);
    }

    /**
     * Starts using the glove. The hold duration determines throw vs. radial menu.
     *
     * @param level the world
     * @param player the player using the item
     * @param hand the hand holding the glove
     * @return CONSUME to begin the use-duration countdown
     */
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player,
            @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /**
     * Returns the maximum hold duration (same as bow).
     *
     * @param stack the glove stack
     * @param entity the entity holding the item
     * @return 72000 ticks
     */
    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    /**
     * No animation while holding the glove.
     *
     * @param stack the item stack
     * @return NONE (no animation)
     */
    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * Called when the player releases the use button. Short hold throws,
     * long hold is already handled by the client-side radial tracker.
     *
     * @param stack the glove stack
     * @param level the world
     * @param entity the entity that released
     * @param timeLeft remaining ticks from the use duration
     * @return true if the release was handled
     */
    @Override
    public boolean releaseUsing(@NonNull ItemStack stack, @NonNull Level level,
            @NonNull LivingEntity entity, int timeLeft) {
        int ticksUsed = getUseDuration(stack, entity) - timeLeft;
        return ticksUsed < RADIAL_THRESHOLD_TICKS && handleQuickThrow(stack, level, entity);
    }

    /** Sends a throw packet (client) and plays the arm swing (both sides).
     *
     * @param stack  the glove stack
     * @param level  the world
     * @param entity the entity throwing
     * @return true if a throw was initiated, false if no type selected
     */
    private boolean handleQuickThrow(ItemStack stack, Level level, LivingEntity entity) {
        GooType selected = getSelectedType(stack);
        if (selected == null) { return false; }
        if (level.isClientSide() && entity instanceof Player player) {
            com.mercuriusxeno.goo.client.throwing.GloveThrowSender.sendThrow(player, selected);
        }
        entity.swing(entity.getUsedItemHand());
        return true;
    }

    /**
     * Reads the selected goo type from this glove's data component.
     *
     * @param stack the glove stack
     * @return the selected GooType, or null if none selected
     */
    public static @Nullable GooType getSelectedType(ItemStack stack) {
        GloveSelection sel = getSelection(stack);
        if (sel != null && sel.hasType()) { return sel.getGooType(); }
        String id = stack.get(GooDataComponents.SELECTED_GOO_TYPE.get());
        if (id == null || id.isEmpty()) { return null; }
        return GooType.fromId(id);
    }

    /**
     * Writes the selected goo type to this glove's data component.
     * Also updates the legacy SELECTED_GOO_TYPE for backward compat.
     *
     * @param stack the glove stack
     * @param type the goo type to select, or null to clear
     */
    public static void setSelectedType(ItemStack stack, @Nullable GooType type) {
        if (type == null) {
            stack.remove(GooDataComponents.SELECTED_GOO_TYPE.get());
            stack.remove(GooDataComponents.SELECTED_ABILITY.get());
        } else {
            stack.set(GooDataComponents.SELECTED_GOO_TYPE.get(), type.getId());
            stack.set(GooDataComponents.SELECTED_ABILITY.get(), GloveSelection.ofType(type));
        }
    }

    /**
     * Reads the full selection (type + ability) from this glove.
     *
     * @param stack the glove stack
     * @return the selection, or null if none
     */
    public static @Nullable GloveSelection getSelection(ItemStack stack) {
        return stack.get(GooDataComponents.SELECTED_ABILITY.get());
    }

    /**
     * Writes a full selection (type + ability) to this glove.
     * Also updates the legacy SELECTED_GOO_TYPE for backward compat.
     *
     * @param stack     the glove stack
     * @param selection the selection to set
     */
    public static void setSelection(ItemStack stack, GloveSelection selection) {
        if (selection.isEmpty()) {
            stack.remove(GooDataComponents.SELECTED_ABILITY.get());
            stack.remove(GooDataComponents.SELECTED_GOO_TYPE.get());
        } else {
            stack.set(GooDataComponents.SELECTED_ABILITY.get(), selection);
            stack.set(GooDataComponents.SELECTED_GOO_TYPE.get(), selection.gooTypeId());
        }
    }
}
