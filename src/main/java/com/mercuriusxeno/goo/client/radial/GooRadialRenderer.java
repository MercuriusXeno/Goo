package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.Map;

/**
 * Stateless rendering utilities for the goo radial menu, separating
 * pure rendering logic from input handling and selection state.
 */
final class GooRadialRenderer {

    // --- Color manipulation constants ---

    /**
     * Alpha for normal (unhovered) wedges.
     */
    private static final int NORMAL_ALPHA = 0xAA;

    /**
     * Alpha for hovered wedges.
     */
    private static final int HOVER_ALPHA = 0xDD;

    /**
     * Alpha for unavailable (zero quantity) wedges.
     */
    private static final int DISABLED_ALPHA = 0x55;

    /**
     * Darkening factor for unavailable wedges (multiplied per channel).
     */
    private static final float DISABLED_DIM = 0.4f;

    // --- Cancel zone constants ---

    /**
     * Semi-transparent white for cancel zone when hovered.
     */
    private static final int CANCEL_HOVER_COLOR = 0x88FFFFFF;

    /**
     * Semi-transparent white for cancel zone when idle.
     */
    private static final int CANCEL_IDLE_COLOR = 0x44FFFFFF;

    /**
     * Fully opaque white for untinted rendering.
     */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    /**
     * Cancel symbol rendered in the center zone.
     */
    private static final String CANCEL_SYMBOL = "\u2715";

    // --- Icon / label constants ---

    /**
     * Icon size in pixels for goo type bordered icons.
     */
    private static final int ICON_SIZE = 11;

    /**
     * Half-icon offset for centering icons on their anchor.
     */
    private static final int ICON_OFFSET = 5;

    /**
     * Gray text color for disabled (zero quantity) goo types.
     */
    private static final int DISABLED_TEXT_COLOR = 0xFF888888;

    /**
     * Texture path prefix for goo type icons.
     */
    private static final String ICON_PATH_PREFIX = "textures/goo/type/";

    /**
     * Texture path suffix for bordered goo type icons.
     */
    private static final String ICON_PATH_SUFFIX = ".png";

    // --- Quantity formatting constants ---

    /**
     * Zero quantity display string.
     */
    private static final String ZERO_LABEL = "0";

    /**
     * Threshold below which volume is displayed in raw mB.
     */
    private static final int KILO_THRESHOLD = 1_000;

    /**
     * Threshold below which volume is displayed in k (thousands).
     */
    private static final int MEGA_THRESHOLD = 1_000_000;

    /**
     * Divisor for kilo-scale volume formatting.
     */
    private static final double KILO_DIVISOR = 1_000.0;

    /**
     * Divisor for mega-scale volume formatting.
     */
    private static final double MEGA_DIVISOR = 1_000_000.0;

    /**
     * mB suffix for raw volume display.
     */
    private static final String MB_SUFFIX = " mB";

    /**
     * Format string for kilo-scale volume display.
     */
    private static final String KILO_FORMAT = "%.1fk";

    /**
     * Format string for mega-scale volume display.
     */
    private static final String MEGA_FORMAT = "%.1fM";

    private GooRadialRenderer() {
    }

    /**
     * Determines which wedge the mouse hovers based on angle and distance from center.
     * Returns {@link GooRadialScreen#NO_SELECTION} when the cursor is inside the cancel zone.
     *
     * @param mouseX  the current mouse x position
     * @param mouseY  the current mouse y position
     * @param centerX the screen center x coordinate
     * @param centerY the screen center y coordinate
     * @return the hovered wedge index, or -1 for cancel zone / out of range
     */
    static int computeHoveredIndex(int mouseX, int mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < GooRadialScreen.INNER_RADIUS) {
            return GooRadialScreen.NO_SELECTION;
        }
        double angle = Math.atan2(dx, -dy);
        if (angle < 0) {
            angle += GooRadialScreen.TWO_PI;
        }
        return (int) (angle / GooRadialScreen.WEDGE_ARC) % GooRadialScreen.WEDGE_COUNT;
    }

    /**
     * Pre-computes ARGB colors for all wedges based on hover/disabled state.
     *
     * @param wedgeColors  output array of length {@link GooRadialScreen#WEDGE_COUNT}
     * @param available    map of goo types to available volumes in microblobs
     * @param hoveredIndex the currently hovered wedge index, or -1 for none
     */
    static void computeWedgeColors(int[] wedgeColors, Map<GooType, Integer> available, int hoveredIndex) {
        GooType[] types = GooType.values();
        for (int i = 0; i < GooRadialScreen.WEDGE_COUNT; i++) {
            int qty = available.getOrDefault(types[i], 0);
            boolean hovered = i == hoveredIndex;
            boolean disabled = qty <= 0;
            int baseRgb = hovered ? GooColors.bright(types[i]) : GooColors.wheel(types[i]);
            wedgeColors[i] = computeWedgeColor(baseRgb, hovered, disabled);
        }
    }

    /**
     * Computes the final ARGB color for a wedge, applying hover brightening
     * or disabled dimming as appropriate.
     *
     * @param baseColor the base RGB color from the goo type
     * @param hovered   true if this wedge is currently hovered
     * @param disabled  true if the goo type has zero available quantity
     * @return the packed ARGB color with alpha and brightness adjustments
     */
    static int computeWedgeColor(int baseColor, boolean hovered, boolean disabled) {
        int r = ARGB.red(baseColor);
        int g = ARGB.green(baseColor);
        int b = ARGB.blue(baseColor);
        if (disabled) {
            return packDisabledColor(r, g, b);
        }
        if (hovered) {
            return packHoveredColor(r, g, b);
        }
        return ARGB.color(NORMAL_ALPHA, r, g, b);
    }

    /**
     * Dims RGB channels and applies disabled alpha.
     *
     * @param r red channel (0-255)
     * @param g green channel (0-255)
     * @param b blue channel (0-255)
     * @return the packed ARGB disabled color
     */
    private static int packDisabledColor(int r, int g, int b) {
        int dr = (int) (r * DISABLED_DIM);
        int dg = (int) (g * DISABLED_DIM);
        int db = (int) (b * DISABLED_DIM);
        return ARGB.color(DISABLED_ALPHA, dr, dg, db);
    }

    /**
     * Brightens RGB channels and applies hover alpha.
     *
     * @param r red channel (0-255)
     * @param g green channel (0-255)
     * @param b blue channel (0-255)
     * @return the packed ARGB hovered color
     */
    private static int packHoveredColor(int r, int g, int b) {
        return ARGB.color(HOVER_ALPHA, r, g, b);
    }

    /**
     * Renders each wedge as a tinted blit of its pre-generated AA mask texture.
     * The mask is white-on-transparent; the blit color parameter applies
     * multiplicative tinting in the fragment shader.
     *
     * @param graphics    the GUI graphics extractor for rendering
     * @param centerX     the screen center x coordinate
     * @param centerY     the screen center y coordinate
     * @param wedgeColors pre-computed ARGB color per wedge
     */
    static void renderWedges(GuiGraphicsExtractor graphics, int centerX, int centerY, int... wedgeColors) {
        int x = centerX - GooRadialScreen.OUTER_RADIUS;
        int y = centerY - GooRadialScreen.OUTER_RADIUS;
        int size = GooRadialScreen.OUTER_RADIUS * GooRadialScreen.HALF;
        for (int i = 0; i < GooRadialScreen.WEDGE_COUNT; i++) {
            blitWedge(graphics, x, y, size, i, wedgeColors[i]);
        }
    }

    /**
     * Blits a single wedge mask texture at the given screen position.
     *
     * @param graphics   the GUI graphics extractor for rendering
     * @param x          the top-left X screen coordinate
     * @param y          the top-left Y screen coordinate
     * @param size       the render size in pixels
     * @param wedgeIndex the wedge index
     * @param color      the ARGB tint color
     */
    private static void blitWedge(GuiGraphicsExtractor graphics, int x, int y, int size, int wedgeIndex, int color) {
        Identifier tex = RadialTextures.getWedgeTexture(wedgeIndex);
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0.0f, 0.0f, size, size, RadialTextures.TEX_SIZE, RadialTextures.TEX_SIZE, color);
    }

    /**
     * Renders the cancel zone as a tinted blit of the pre-generated circle mask.
     * Shows the deselect "X" symbol when hovered.
     *
     * @param graphics     the GUI graphics extractor for rendering
     * @param centerX      the screen center x coordinate
     * @param centerY      the screen center y coordinate
     * @param hoveredIndex the currently hovered wedge index, or -1 for cancel zone
     * @param font         the font for rendering the cancel symbol
     */
    static void renderCancelZone(GuiGraphicsExtractor graphics, int centerX, int centerY,
                                 int hoveredIndex, Font font) {
        boolean cancelHovered = hoveredIndex == GooRadialScreen.NO_SELECTION;
        int color = cancelHovered ? CANCEL_HOVER_COLOR : CANCEL_IDLE_COLOR;
        blitFullRadial(graphics, centerX, centerY, RadialTextures.getCancelTexture(), color);
        if (cancelHovered) {
            renderCancelSymbol(graphics, font, centerX, centerY);
        }
    }

    /**
     * Blits a full-radius mask texture centered on the given screen coordinates.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param centerX  the screen center x coordinate
     * @param centerY  the screen center y coordinate
     * @param tex      the texture identifier to blit
     * @param color    the ARGB tint color
     */
    private static void blitFullRadial(GuiGraphicsExtractor graphics,
                                       int centerX, int centerY, Identifier tex, int color) {
        int texSize = RadialTextures.TEX_SIZE;
        int renderSize = GooRadialScreen.OUTER_RADIUS * GooRadialScreen.HALF;
        int x = centerX - GooRadialScreen.OUTER_RADIUS;
        int y = centerY - GooRadialScreen.OUTER_RADIUS;
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0.0f, 0.0f, renderSize, renderSize, texSize, texSize, color);
    }

    /**
     * Draws the "X" cancel symbol centered on the radial menu.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param font     the font for rendering the cancel symbol
     * @param centerX  the screen center x coordinate
     * @param centerY  the screen center y coordinate
     */
    private static void renderCancelSymbol(GuiGraphicsExtractor graphics,
                                           Font font, int centerX, int centerY) {
        graphics.centeredText(font, Component.literal(CANCEL_SYMBOL),
                centerX, centerY - font.lineHeight / GooRadialScreen.HALF, COLOR_WHITE);
    }

    /**
     * Renders blob item icons on each wedge and hovered type info in the center.
     *
     * @param graphics     the GUI graphics extractor for rendering
     * @param centerX      the screen center x coordinate
     * @param centerY      the screen center y coordinate
     * @param hoveredIndex the currently hovered wedge index, or -1 for none
     * @param available    map of goo types to available volumes in microblobs
     * @param font         the font for rendering text labels
     */
    static void renderLabels(GuiGraphicsExtractor graphics, int centerX, int centerY,
                             int hoveredIndex, Map<GooType, Integer> available, Font font) {
        renderWedgeIcons(graphics, centerX, centerY);
        if (hoveredIndex >= 0 && hoveredIndex < GooRadialScreen.WEDGE_COUNT) {
            renderHoveredInfo(graphics, font, centerX, centerY, hoveredIndex, available);
        }
    }

    /**
     * Blits bordered goo-type icons on each wedge bisector.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param centerX  the screen center x coordinate
     * @param centerY  the screen center y coordinate
     */
    private static void renderWedgeIcons(GuiGraphicsExtractor graphics,
                                         int centerX, int centerY) {
        GooType[] types = GooType.values();
        for (int i = 0; i < GooRadialScreen.WEDGE_COUNT; i++) {
            renderSingleIcon(graphics, types[i], i, centerX, centerY);
        }
    }

    /**
     * Blits one goo-type icon at the bisector of the given wedge.
     *
     * @param graphics   the GUI graphics extractor for rendering
     * @param type       the goo type to render
     * @param wedgeIndex the wedge index
     * @param centerX    the screen center x coordinate
     * @param centerY    the screen center y coordinate
     */
    private static void renderSingleIcon(GuiGraphicsExtractor graphics,
                                         GooType type, int wedgeIndex, int centerX, int centerY) {
        double bisector = GooRadialScreen.WEDGE_ARC * wedgeIndex + GooRadialScreen.WEDGE_ARC / GooRadialScreen.HALF;
        double r = (GooRadialScreen.INNER_RADIUS + GooRadialScreen.OUTER_RADIUS) / (double) GooRadialScreen.HALF;
        int iconX = centerX + (int) (Math.sin(bisector) * r) - ICON_OFFSET;
        int iconY = centerY - (int) (Math.cos(bisector) * r) - ICON_OFFSET;
        Identifier tex = Identifier.fromNamespaceAndPath(Goo.MODID, ICON_PATH_PREFIX + type.getId() + ICON_PATH_SUFFIX);
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Renders the hovered goo type name and quantity in the center of the radial.
     *
     * @param graphics     the GUI graphics extractor for rendering
     * @param font         the font for rendering text labels
     * @param centerX      the screen center x coordinate
     * @param centerY      the screen center y coordinate
     * @param hoveredIndex the currently hovered wedge index
     * @param available    map of goo types to available volumes in microblobs
     */
    private static void renderHoveredInfo(GuiGraphicsExtractor graphics, Font font,
                                          int centerX, int centerY, int hoveredIndex, Map<GooType, Integer> available) {
        GooType hovered = GooType.values()[hoveredIndex];
        int qty = available.getOrDefault(hovered, 0);
        int textColor = qty <= 0 ? DISABLED_TEXT_COLOR : COLOR_WHITE;
        Component name = Component.translatable(hovered.getTranslationKey());
        graphics.centeredText(font, name, centerX, centerY - font.lineHeight - 1, textColor);
        graphics.centeredText(font, Component.literal(formatQuantity(qty)), centerX, centerY + GooRadialScreen.HALF, textColor);
    }

    /**
     * Formats a mB quantity for display. Shows "0" for zero,
     * abbreviated "1.2k" for thousands, "1.2M" for millions.
     *
     * @param mB the quantity in microblobs
     * @return the human-readable formatted string
     */
    static String formatQuantity(int mB) {
        if (mB <= 0) {
            return ZERO_LABEL;
        }
        if (mB < KILO_THRESHOLD) {
            return mB + MB_SUFFIX;
        }
        if (mB < MEGA_THRESHOLD) {
            double k = mB / KILO_DIVISOR;
            return String.format(KILO_FORMAT, k);
        }
        double m = mB / MEGA_DIVISOR;
        return String.format(MEGA_FORMAT, m);
    }
}
