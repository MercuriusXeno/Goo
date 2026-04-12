package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.radial.GooRadialScreen;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side tracker for glove hold duration. Opens radial menu
 * when hold exceeds threshold. Auto-registered via EventBusSubscriber.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveUseTracker {
    private static int holdTicks;

    /** How often (in ticks) to re-check whether the selected goo type is in inventory. */
    private static final int AVAILABILITY_CHECK_INTERVAL = 10;
    private static int availabilityTick;

    /**
     * True when the glove's selected goo type exists in the player's inventory.
     * Updated every {@link #AVAILABILITY_CHECK_INTERVAL} ticks to avoid per-frame
     * inventory scans. Used by the renderer and target highlighter to suppress
     * visuals when the player has depleted their goo without opening the radial.
     */
    private static boolean selectedTypeAvailable;

    private GloveUseTracker() {}


    /**
     * Whether the player's glove has goo of the selected type in inventory.
     *
     * @return true if selectedTypeAvailable
     */
    public static boolean isSelectedTypeAvailable() {
        return selectedTypeAvailable;
    }

    /**
     * Tracks glove hold duration each client tick, opening radial on threshold.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            resetState();
            return;
        }

        trackGloveHold(player);
        tickAvailabilityCheck(player);
        BlobFlightManager.tick();
        GloveThrowSender.tick();
        ThrowFreezeState.tick();
    }

    /** Clears hold and availability state when no player is present. */
    private static void resetState() {
        holdTicks = 0;
        selectedTypeAvailable = false;
        ThrowFreezeState.clear();
    }

    /**
     * Tracks glove use-item hold and opens radial menu on threshold.
     * @param player the local player to check for glove use-item input
     */
    private static void trackGloveHold(LocalPlayer player) {
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof GooGloveItem) {
            holdTicks++;
            if (holdTicks >= GooGloveItem.RADIAL_THRESHOLD_TICKS) {
                player.releaseUsingItem();
                GooRadialScreen.open();
                holdTicks = 0;
            }
        } else {
            holdTicks = 0;
        }
    }

    /**
     * Periodically re-checks whether the selected goo type is in inventory.
     * @param player the local player whose inventory is checked for goo availability
     */
    private static void tickAvailabilityCheck(LocalPlayer player) {
        availabilityTick++;
        if (availabilityTick >= AVAILABILITY_CHECK_INTERVAL) {
            availabilityTick = 0;
            selectedTypeAvailable = checkSelectedTypeAvailable(player);
        }
    }

    /**
     * Returns true if the player holds a glove with a selected type they have in inventory.
     *
     * @param player the interacting player
     * @return true if the player has at least 1 mB of the selected goo type
     */
    private static boolean checkSelectedTypeAvailable(LocalPlayer player) {
        GooType type = readSelectedType(player);
        return type != null && GooSourceScanner.hasEnough(player, type, 1);
    }

    /**
     * Reads the selected goo type from whichever hand holds a glove.
     *
     * @param player the interacting player
     * @return the selected goo type, or null if no glove is held or no type is selected
     */
    private static @Nullable GooType readSelectedType(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(main);
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(off);
        }
        return null;
    }
}
