package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders the fuel row and textured icon quads for the crucible HUD panel.
 * Handles blaze rod stage textures and goo type icon textures.
 */
final class CrucibleFuelDisplay {
    /** Icon render size in scaled pixels (matches the 10x10 tooltip icons). */
    static final float ICON_SIZE = 10f;
    /** Gap between icon and text. */
    static final float ICON_TEXT_GAP = 2f;
    /** Height of one row (icon + text line). */
    private static final float ROW_HEIGHT = 11f;
    /** Fuel text color (orange, matching the blaze rod bar). */
    private static final int FUEL_COLOR = 0xFFFF6600;
    /** Number of depleted blaze rod texture stages (0 = most depleted, 7 = fullest). */
    private static final int BLAZE_ROD_STAGES = 8;
    /** Half-width divisor for centering. */
    private static final float HALF_F = 2f;
    /** Ticks per second for fuel time conversion. */
    private static final float TICKS_PER_SECOND = 20f;
    /** Fuel display threshold: show integer seconds above this. */
    private static final float FUEL_INT_THRESHOLD = 10f;
    /** Format string for integer-second fuel display. */
    private static final String FUEL_INT_FORMAT = "%.0fs";
    /** Format string for decimal-second fuel display. */
    private static final String FUEL_DEC_FORMAT = "%.1fs";
    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/goo/type/";
    /** Texture path suffix for bordered goo type icons. */
    private static final String ICON_PATH_SUFFIX = ".png";
    /** Texture path prefix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_PREFIX = "textures/item/depleted_blaze_rod_";
    /** Texture path suffix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_SUFFIX = ".png";

    private CrucibleFuelDisplay() {}

    /**
     * Measures the width of the fuel row: blaze rod icon + seconds text.
     *
     * @param font the font renderer
     * @param fuelRod the fuel rod item stack
     * @return the measured width in pixels
     */
    static float measureFuelRowWidth(Font font, ItemStack fuelRod) {
        String text = formatFuelSeconds(fuelRod);
        return ICON_SIZE + ICON_TEXT_GAP + font.width(text);
    }

    /**
     * Renders the fuel row: depleted blaze rod icon + remaining seconds, both vertically centered.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param fuelRod the fuel rod item stack
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    static void renderFuelRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, ItemStack fuelRod, float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF_F;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF_F;
        Identifier tex = blazeRodTexture(fuelRod);
        renderTexturedQuad(poseStack, buffers, tex, x, iconY);
        String text = formatFuelSeconds(fuelRod);
        float textX = x + ICON_SIZE + ICON_TEXT_GAP;
        InWorldHud.drawText(font, buffers, poseStack, text, textX, textY, FUEL_COLOR);
    }

    /**
     * Formats fuel remaining as seconds with one decimal: "42.3s".
     *
     * @param fuelRod the fuel rod item stack
     * @return the formatted string
     */
    private static String formatFuelSeconds(ItemStack fuelRod) {
        int ticks = fuelTicksRemaining(fuelRod);
        float seconds = ticks / TICKS_PER_SECOND;
        if (seconds >= FUEL_INT_THRESHOLD) { return String.format(FUEL_INT_FORMAT, seconds); }
        return String.format(FUEL_DEC_FORMAT, seconds);
    }

    /**
     * Returns fuel ticks remaining: full 1200 for a vanilla blaze rod, else from data component.
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static int fuelTicksRemaining(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return DepletedBlazeRodItem.FULL_FUEL_TICKS; }
        return DepletedBlazeRodItem.getTicksRemaining(fuelRod);
    }

    /**
     * Returns the appropriate depleted blaze rod texture for the current fuel level.
     *
     * @param fuelRod the fuel rod item stack
     * @return the blaze rod texture identifier
     */
    private static Identifier blazeRodTexture(ItemStack fuelRod) {
        float fraction = fuelFraction(fuelRod);
        int stage = Math.min((int) (fraction * BLAZE_ROD_STAGES), BLAZE_ROD_STAGES - 1);
        return Identifier.fromNamespaceAndPath(Goo.MODID,
            BLAZE_ROD_PREFIX + stage + BLAZE_ROD_SUFFIX);
    }

    /**
     * Computes fuel fraction (0.0 = depleted, 1.0 = fresh).
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static float fuelFraction(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return 1f; }
        int remaining = DepletedBlazeRodItem.getTicksRemaining(fuelRod);
        return (float) remaining / DepletedBlazeRodItem.FULL_FUEL_TICKS;
    }

    /**
     * Returns the texture Identifier for a goo type's item icon.
     *
     * @param type the goo type
     * @return the icon texture identifier
     */
    static Identifier iconTexture(GooType type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID,
            ICON_PATH_PREFIX + type.getId() + ICON_PATH_SUFFIX);
    }

    /**
     * Renders a textured quad (ICON_SIZE x ICON_SIZE) in world space.
     * Uses textSeeThrough so icons respect the same depth as text and background.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param tex the texture identifier
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    static void renderTexturedQuad(PoseStack poseStack, MultiBufferSource buffers,
            Identifier tex, float x, float y) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.text(tex));
        PoseStack.Pose pose = poseStack.last();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        InWorldHud.iconVertex(vc, pose, x, y, InWorldHud.CONTENT_Z, 0f, 0f);
        InWorldHud.iconVertex(vc, pose, x, y2, InWorldHud.CONTENT_Z, 0f, 1f);
        InWorldHud.iconVertex(vc, pose, x2, y2, InWorldHud.CONTENT_Z, 1f, 1f);
        InWorldHud.iconVertex(vc, pose, x2, y, InWorldHud.CONTENT_Z, 1f, 0f);
    }
}
