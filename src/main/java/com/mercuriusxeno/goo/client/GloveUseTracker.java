package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.radial.GooRadialScreen;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
public class GloveUseTracker {

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

    /** Whether the player's glove has goo of the selected type in inventory. */
    public static boolean isSelectedTypeAvailable() {
        return selectedTypeAvailable;
    }

    /** Tracks glove hold duration each client tick, opening radial on threshold. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            holdTicks = 0;
            selectedTypeAvailable = false;
            return;
        }

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

        // Periodically check whether the selected goo type is still in inventory
        if (++availabilityTick >= AVAILABILITY_CHECK_INTERVAL) {
            availabilityTick = 0;
            selectedTypeAvailable = checkSelectedTypeAvailable(player);
        }

        // Advance blob flight animations each client tick
        BlobFlightManager.tick();
    }

    /** Returns true if the player holds a glove with a selected type they have in inventory. */
    private static boolean checkSelectedTypeAvailable(LocalPlayer player) {
        GooType type = readSelectedType(player);
        if (type == null) return false;
        return GooSourceScanner.hasEnough(player, type, 1);
    }

    /** Reads the selected goo type from whichever hand holds a glove. */
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
