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
    /** Whether debug adjustment is active. */
    public static boolean enabled = true;

    // Current transform values (applied additively by the renderer)
    public static float rotX;
    public static float rotY;
    public static float rotZ;

    public static float transZ;
    public static float scale = 1.0f;

    // Editing state
    private static int mode;       // 0=rotation, 1=translation, 2=scale
    private static int axis;       // 0=X, 1=Y, 2=Z
    private static boolean prevUp;

    private static boolean prevP;

    private static boolean prevR;

    private static boolean prevScaleKey;

    /** Scale mode index for the mode selector. */
    private static final int MODE_SCALE = 2;
    /** Rotation step size in degrees per adjustment. */
    private static final float ROTATION_STEP = 5f;
    /** Translation step size in block units per adjustment. */
    private static final float TRANSLATION_STEP = 0.5f;
    /** Scale step size per adjustment. */
    private static final float SCALE_STEP = 0.05f;
    /** Minimum allowed scale value. */
    private static final float MIN_SCALE = 0.05f;
    /** Y axis index for the axis selector. */
    private static final int AXIS_Y = 2;
    /** Overlay prefix for reset feedback. */
    private static final String MSG_RESET = "[Glove Debug] Reset";
    /** Overlay prefix for glove debug feedback. */
    private static final String MSG_PREFIX = "[Glove] ";
    /** Format for rotation debug output. */
    private static final String FMT_ROT = "Rot [%.0f, %.0f, %.0f]";
    /** Format for translation debug output. */
    private static final String FMT_TRANS = "Trans [%.1f, %.1f, %.1f]";
    /** Format for scale debug output. */
    private static final String FMT_SCALE = "Scale %.2f";
    /** JSON format for rotation debug print. */
    private static final String JSON_ROTATION = "\"rotation\": [%.0f, %.0f, %.0f],";
    /** JSON format for translation debug print. */
    private static final String JSON_TRANSLATION = "\"translation\": [%.1f, %.1f, %.1f],";
    /** JSON format for scale debug print. */
    private static final String JSON_SCALE = "\"scale\": [%.2f, %.2f, %.2f]";
    /** Negative adjustment direction. */
    private static final int DIR_DOWN = -1;

    public static float transX;
    public static float transY;
    private static boolean prevDown;
    private static boolean prevZero;
    private static boolean prevT;

    /** Default empty string for unmatched modes. */
    private static final String EMPTY = "";
    /** Space separator. */
    private static final String SPACE = " ";
    /** Colon-space separator. */
    private static final String COLON_SPACE = ": ";

    private static final String[] MODE_NAMES = {"Rotation", "Translation", "Scale"};
    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    private GloveTransformDebug() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!enabled) { return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) { return; }

        Window window = mc.getWindow();
        boolean alt = InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        if (!alt) { return; }

        pollModeKeys(window);
        pollAxisKeys(window);
        pollAdjustKeys(window);
        pollPrintKey(window, mc);
        pollResetKey(window, mc);
    }

    /**
     * Edge-detects mode selection keys (1/2/3) and updates the active mode.
     *
     * @param window the GLFW window handle for key polling
     */
    private static void pollModeKeys(Window window) {
        boolean k1 = InputConstants.isKeyDown(window, InputConstants.KEY_1);
        boolean k2 = InputConstants.isKeyDown(window, InputConstants.KEY_2);
        boolean k3 = InputConstants.isKeyDown(window, InputConstants.KEY_3);
        if (k1 && !prevR) { mode = 0; }
        if (k2 && !prevT) { mode = 1; }
        if (k3 && !prevScaleKey) { mode = MODE_SCALE; }
        prevR = k1;
        prevT = k2;
        prevScaleKey = k3;
    }

    /**
     * Polls axis selection keys (R/G/B) and updates the active axis.
     *
     * @param window the GLFW window handle for key polling
     */
    private static void pollAxisKeys(Window window) {
        boolean kR = InputConstants.isKeyDown(window, InputConstants.KEY_R);
        boolean kG = InputConstants.isKeyDown(window, InputConstants.KEY_G);
        boolean kB = InputConstants.isKeyDown(window, InputConstants.KEY_B);
        if (kR) { axis = 0; }
        if (kG) { axis = 1; }
        if (kB) { axis = AXIS_Y; }
    }

    /**
     * Edge-detects UP/DOWN arrows and applies one adjustment step.
     *
     * @param window the GLFW window handle for key polling
     */
    private static void pollAdjustKeys(Window window) {
        boolean up = InputConstants.isKeyDown(window, InputConstants.KEY_UP);
        boolean down = InputConstants.isKeyDown(window, InputConstants.KEY_DOWN);
        if (up && !prevUp) { adjust(1); }
        if (down && !prevDown) { adjust(DIR_DOWN); }
        prevUp = up;
        prevDown = down;
    }

    /**
     * Edge-detects P key and prints current transform values to chat.
     *
     * @param window the GLFW window handle for key polling
     * @param mc     the Minecraft client instance for chat output
     */
    private static void pollPrintKey(Window window, Minecraft mc) {
        boolean p = InputConstants.isKeyDown(window, InputConstants.KEY_P);
        if (p && !prevP) { printValues(mc); }
        prevP = p;
    }

    /**
     * Edge-detects 0 key and resets all transform values to defaults.
     *
     * @param window the GLFW window handle for key polling
     * @param mc     the Minecraft client instance for chat output
     */
    private static void pollResetKey(Window window, Minecraft mc) {
        boolean zero = InputConstants.isKeyDown(window, InputConstants.KEY_0);
        if (zero && !prevZero) { resetTransforms(mc); }
        prevZero = zero;
    }

    /**
     * Resets all rotation, translation, and scale values to their defaults.
     *
     * @param mc the Minecraft client instance for overlay feedback
     */
    private static void resetTransforms(Minecraft mc) {
        rotX = 0;
        rotY = 0;
        rotZ = 0;
        transX = 0;
        transY = 0;
        transZ = 0;
        scale = 1.0f;
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(MSG_RESET));
        }
    }

    /**
     * Adjusts the current mode/axis value by one step.
     *
     * @param direction the adjustment direction (+1 or -1)
     */
    private static void adjust(int direction) {
        float step;
        switch (mode) {
            case 0 -> { // rotation: 5 degree steps
                step = ROTATION_STEP * direction;
                switch (axis) {
                    case 0 -> rotX += step;
                    case 1 -> rotY += step;
                    case AXIS_Y -> rotZ += step;
                }
            }
            case 1 -> { // translation: 0.5 steps
                step = TRANSLATION_STEP * direction;
                switch (axis) {
                    case 0 -> transX += step;
                    case 1 -> transY += step;
                    case AXIS_Y -> transZ += step;
                }
            }
            case MODE_SCALE -> { // scale: 0.05 steps
                scale += SCALE_STEP * direction;
                scale = Math.max(MIN_SCALE, scale);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String val = switch (mode) {
                case 0 -> String.format(FMT_ROT, rotX, rotY, rotZ);
                case 1 -> String.format(FMT_TRANS, transX, transY, transZ);
                case MODE_SCALE -> String.format(FMT_SCALE, scale);
                default -> EMPTY;
            };
            mc.player.sendOverlayMessage(
                Component.literal(MSG_PREFIX + MODE_NAMES[mode] + SPACE + AXIS_NAMES[axis] + COLON_SPACE + val));
        }
    }

    /**
     * Prints all transform values to chat in JSON-ready format.
     *
     * @param mc the Minecraft client instance for chat output
     */
    private static void printValues(Minecraft mc) {
        if (mc.player == null) { return; }
        mc.player.sendSystemMessage(Component.literal(
            String.format(JSON_ROTATION, rotX, rotY, rotZ)));
        mc.player.sendSystemMessage(Component.literal(
            String.format(JSON_TRANSLATION, transX, transY, transZ)));
        mc.player.sendSystemMessage(Component.literal(
            String.format(JSON_SCALE, scale, scale, scale)));
    }
}
