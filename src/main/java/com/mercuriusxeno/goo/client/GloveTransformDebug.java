package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Temporary debug tool for tuning glove display transforms at runtime.
 * Hotkeys adjust rotation/translation/scale; current values print to chat
 * in JSON-ready format so they can be pasted into the model file.
 *
 * <p>Controls (while holding LEFT_ALT):
 * <ul>
 *   <li>R/T/Y = cycle which axis to adjust (X/Y/Z)</li>
 *   <li>1/2 = mode: rotation / translation / scale</li>
 *   <li>UP/DOWN = increase/decrease current value</li>
 *   <li>P = print current values to chat as JSON</li>
 *   <li>0 = reset all to zero</li>
 * </ul>
 * Delete this class when tuning is done.</p>
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveTransformDebug {

    private GloveTransformDebug() {}

    /** Whether debug adjustment is active. */
    public static boolean enabled = true;

    // Current transform values (applied additively by the renderer)
    public static float rotX, rotY, rotZ;
    public static float transX, transY, transZ;
    public static float scale = 1.0f;

    // Editing state
    private static int mode = 0;       // 0=rotation, 1=translation, 2=scale
    private static int axis = 0;       // 0=X, 1=Y, 2=Z
    private static boolean prevUp, prevDown, prevP, prevZero;
    private static boolean prevR, prevT, prevScaleKey;

    private static final String[] MODE_NAMES = {"Rotation", "Translation", "Scale"};
    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        Window window = mc.getWindow();
        boolean alt = InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        if (!alt) return;

        // Mode: 1=rotation, 2=translation, 3=scale
        boolean k1 = InputConstants.isKeyDown(window, InputConstants.KEY_1);
        boolean k2 = InputConstants.isKeyDown(window, InputConstants.KEY_2);
        boolean k3 = InputConstants.isKeyDown(window, InputConstants.KEY_3);
        if (k1 && !prevR) mode = 0;
        if (k2 && !prevT) mode = 1;
        if (k3 && !prevScaleKey) mode = 2;
        prevR = k1; prevT = k2; prevScaleKey = k3;

        // Axis: R=X, T=Y, Y=Z (chosen to avoid conflict with movement)
        boolean kR = InputConstants.isKeyDown(window, InputConstants.KEY_R);
        boolean kTy = InputConstants.isKeyDown(window, InputConstants.KEY_G);
        boolean kY = InputConstants.isKeyDown(window, InputConstants.KEY_B);
        if (kR) axis = 0;
        if (kTy) axis = 1;
        if (kY) axis = 2;

        // Up/Down adjust
        boolean up = InputConstants.isKeyDown(window, InputConstants.KEY_UP);
        boolean down = InputConstants.isKeyDown(window, InputConstants.KEY_DOWN);
        if (up && !prevUp) adjust(1);
        if (down && !prevDown) adjust(-1);
        prevUp = up; prevDown = down;

        // P = print
        boolean p = InputConstants.isKeyDown(window, InputConstants.KEY_P);
        if (p && !prevP) printValues(mc);
        prevP = p;

        // 0 = reset
        boolean zero = InputConstants.isKeyDown(window, InputConstants.KEY_0);
        if (zero && !prevZero) {
            rotX = rotY = rotZ = transX = transY = transZ = 0;
            scale = 1.0f;
            mc.player.sendOverlayMessage(Component.literal("[Glove Debug] Reset"));
        }
        prevZero = zero;
    }

    /** Adjusts the current mode/axis value by one step. */
    private static void adjust(int direction) {
        float step;
        switch (mode) {
            case 0 -> { // rotation: 5 degree steps
                step = 5f * direction;
                switch (axis) {
                    case 0 -> rotX += step;
                    case 1 -> rotY += step;
                    case 2 -> rotZ += step;
                }
            }
            case 1 -> { // translation: 0.5 steps
                step = 0.5f * direction;
                switch (axis) {
                    case 0 -> transX += step;
                    case 1 -> transY += step;
                    case 2 -> transZ += step;
                }
            }
            case 2 -> { // scale: 0.05 steps
                scale += 0.05f * direction;
                scale = Math.max(0.05f, scale);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String val = switch (mode) {
                case 0 -> String.format("Rot [%.0f, %.0f, %.0f]", rotX, rotY, rotZ);
                case 1 -> String.format("Trans [%.1f, %.1f, %.1f]", transX, transY, transZ);
                case 2 -> String.format("Scale %.2f", scale);
                default -> "";
            };
            mc.player.sendOverlayMessage(
                Component.literal("[Glove] " + MODE_NAMES[mode] + " " + AXIS_NAMES[axis] + ": " + val));
        }
    }

    /** Prints all transform values to chat in JSON-ready format. */
    private static void printValues(Minecraft mc) {
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
            String.format("\"rotation\": [%.0f, %.0f, %.0f],", rotX, rotY, rotZ)));
        mc.player.sendSystemMessage(Component.literal(
            String.format("\"translation\": [%.1f, %.1f, %.1f],", transX, transY, transZ)));
        mc.player.sendSystemMessage(Component.literal(
            String.format("\"scale\": [%.2f, %.2f, %.2f]", scale, scale, scale)));
    }
}
