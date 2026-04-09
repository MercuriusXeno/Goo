package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Client-side state for tuner visual feedback. Manages the reminder HUD
 * visibility toggled by right-clicking air with a tuner.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TunerHudRenderer {
    /** Whether the reminder HUD is visible (toggled by right-clicking air). */
    @SuppressWarnings("PMD.MutableStaticState") // client-side singleton state
    static boolean reminderVisible;

    private TunerHudRenderer() {}


    /** Clears the reminder state. Called when selection clears. */
    public static void clearReminder() {
        reminderVisible = false;
    }

    /** Toggles the reminder visibility. */
    public static void toggleReminder() {
        reminderVisible = !reminderVisible;
    }
}
