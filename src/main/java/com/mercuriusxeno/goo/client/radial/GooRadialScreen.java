package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * 15-wedge radial menu for selecting a goo type on the glove.
 * Opens when the player holds right-click past the radial threshold.
 * Mouse angle selects a wedge; releasing confirms the selection.
 * Center circle acts as a cancel/deselect zone.
 *
 * Wedges are rendered via pre-generated anti-aliased mask textures
 * (see {@link RadialTextures}) with per-wedge color tinting, replacing
 * the old scanline rasterizer.
 */
public final class GooRadialScreen extends Screen {

    /** Number of wedges (one per GooType). */
    private static final int WEDGE_COUNT = GooType.values().length;

    /** Arc span per wedge in radians. */
    private static final double WEDGE_ARC = 2.0 * Math.PI / WEDGE_COUNT;

    /** Inner radius of the wedge ring in GUI-scaled pixels. */
    private static final int INNER_RADIUS = 30;

    /** Outer radius of the wedge ring in GUI-scaled pixels. */
    private static final int OUTER_RADIUS = 100;

    /** Alpha for normal (unhovered) wedges. */
    private static final int NORMAL_ALPHA = 0xAA;

    /** Alpha for hovered wedges. */
    private static final int HOVER_ALPHA = 0xDD;

    /** Alpha for unavailable (zero quantity) wedges. */
    private static final int DISABLED_ALPHA = 0x55;

    /** Darkening factor for unavailable wedges (multiplied per channel). */
    private static final float DISABLED_DIM = 0.4f;

    /** Brightness boost for hovered wedges (added per channel, clamped). */
    private static final int HOVER_BOOST = 40;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Sentinel value for no wedge hovered (cancel zone or out of range). */
    private static final int NO_SELECTION = -1;

    /** Full circle in radians. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /** Semi-transparent white for cancel zone when hovered. */
    private static final int CANCEL_HOVER_COLOR = 0x88FFFFFF;

    /** Semi-transparent white for cancel zone when idle. */
    private static final int CANCEL_IDLE_COLOR = 0x44FFFFFF;

    /** Fully opaque white for untinted rendering. */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    /** Cancel symbol rendered in the center zone. */
    private static final String CANCEL_SYMBOL = "\u2715";

    /** Icon size in pixels for goo type bordered icons. */
    private static final int ICON_SIZE = 11;

    /** Half-icon offset for centering icons on their anchor. */
    private static final int ICON_OFFSET = 5;

    /** Gray text color for disabled (zero quantity) goo types. */
    private static final int DISABLED_TEXT_COLOR = 0xFF888888;

    /** Bit shift for red channel in ARGB packing. */
    private static final int RED_SHIFT = 16;

    /** Bit shift for green channel in ARGB packing. */
    private static final int GREEN_SHIFT = 8;

    /** Mask for extracting a single color channel (0-255). */
    private static final int CHANNEL_MASK = 0xFF;

    /** Bit shift for alpha channel in ARGB packing. */
    private static final int ALPHA_SHIFT = 24;

    /** Maximum channel value for clamping. */
    private static final int MAX_CHANNEL = 255;

    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/item/";

    /** Texture path suffix for bordered goo type icons. */
    private static final String ICON_PATH_SUFFIX = "_icon_bordered.png";

    /** Zero quantity display string. */
    private static final String ZERO_LABEL = "0";

    /** Threshold below which volume is displayed in raw mB. */
    private static final long KILO_THRESHOLD = 1_000;

    /** Threshold below which volume is displayed in k (thousands). */
    private static final long MEGA_THRESHOLD = 1_000_000;

    /** Divisor for kilo-scale volume formatting. */
    private static final double KILO_DIVISOR = 1_000.0;

    /** Divisor for mega-scale volume formatting. */
    private static final double MEGA_DIVISOR = 1_000_000.0;

    /** mB suffix for raw volume display. */
    private static final String MB_SUFFIX = " mB";

    /** Format string for kilo-scale volume display. */
    private static final String KILO_FORMAT = "%.1fk";

    /** Format string for mega-scale volume display. */
    private static final String MEGA_FORMAT = "%.1fM";

    /** Empty string for deselection packets. */
    private static final String DESELECT_ID = "";

    /** Available mB per goo type, snapshot taken on open. */
    private final Map<GooType, Long> available;

    /** Pre-computed ARGB color per wedge, updated each frame. */
    private final int[] wedgeColors = new int[WEDGE_COUNT];

    /** Currently hovered wedge index, or -1 for cancel zone / out of range. */
    private int hoveredIndex = -1;

    /**
     * Creates the radial screen, snapshotting available goo from the player's inventory.
     *
     * @param available map of goo types to available volumes in microblobs
     */
    private GooRadialScreen(Map<GooType, Long> available) {
        super(Component.empty());
        this.available = available;
    }

    /**
     * Opens the radial menu. Called by GloveUseTracker when the hold threshold is reached.
     * Snapshots available goo quantities at the moment of opening.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) { return; }

        Map<GooType, Long> snapshot = GooSourceScanner.aggregateAvailable(player);
        mc.setScreen(new GooRadialScreen(snapshot));
    }

    /**
     * Returns false so the game continues running while the radial is open.
     *
     * @return always false
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Renders the wedge ring, cancel zone, and labels behind the foreground layer.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param mouseX the current mouse x position
     * @param mouseY the current mouse y position
     * @param partialTick the partial tick for interpolation
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        RadialTextures.ensureInitialized();

        int centerX = width / HALF;
        int centerY = height / HALF;

        updateHoveredIndex(mouseX, mouseY, centerX, centerY);
        computeWedgeColors();
        renderWedges(graphics, centerX, centerY);
        renderCancelZone(graphics, centerX, centerY);
        renderLabels(graphics, centerX, centerY);
    }

    /**
     * Determines which wedge the mouse hovers based on angle and distance from center.
     *
     * @param mouseX the current mouse x position
     * @param mouseY the current mouse y position
     * @param centerX the screen center x coordinate
     * @param centerY the screen center y coordinate
     */
    private void updateHoveredIndex(int mouseX, int mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < INNER_RADIUS) {
            hoveredIndex = NO_SELECTION; // cancel zone
            return;
        }

        // atan2 gives angle from positive-X axis, CCW. We want angle from top (negative Y), CW.
        double angle = Math.atan2(dx, -dy); // 0 = up, positive = clockwise
        if (angle < 0) { angle += TWO_PI; }

        hoveredIndex = (int) (angle / WEDGE_ARC) % WEDGE_COUNT;
    }

    /** Pre-computes ARGB colors for all wedges based on hover/disabled state. */
    private void computeWedgeColors() {
        GooType[] types = GooType.values();
        for (int i = 0; i < WEDGE_COUNT; i++) {
            long qty = available.getOrDefault(types[i], 0L);
            boolean hovered = i == hoveredIndex;
            boolean disabled = qty <= 0;
            wedgeColors[i] = computeWedgeColor(types[i].getColor(), hovered, disabled);
        }
    }

    /**
     * Renders each wedge as a tinted blit of its pre-generated AA mask texture.
     * The mask is white-on-transparent; the blit color parameter applies
     * multiplicative tinting in the fragment shader.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param centerX the screen center x coordinate
     * @param centerY the screen center y coordinate
     */
    private void renderWedges(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        int texSize = RadialTextures.TEX_SIZE;
        // Blit covers the full outer radius; texture maps 1:1 to the render area
        int renderSize = OUTER_RADIUS * HALF;
        int x = centerX - OUTER_RADIUS;
        int y = centerY - OUTER_RADIUS;

        for (int i = 0; i < WEDGE_COUNT; i++) {
            Identifier tex = RadialTextures.getWedgeTexture(i);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    x, y, 0.0f, 0.0f, renderSize, renderSize, texSize, texSize,
                    wedgeColors[i]);
        }
    }

    /**
     * Renders the cancel zone as a tinted blit of the pre-generated circle mask.
     * Shows the deselect "X" symbol when hovered.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param centerX the screen center x coordinate
     * @param centerY the screen center y coordinate
     */
    private void renderCancelZone(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        boolean cancelHovered = hoveredIndex == NO_SELECTION;
        int color = cancelHovered ? CANCEL_HOVER_COLOR : CANCEL_IDLE_COLOR;

        int texSize = RadialTextures.TEX_SIZE;
        int renderSize = OUTER_RADIUS * HALF;
        int x = centerX - OUTER_RADIUS;
        int y = centerY - OUTER_RADIUS;

        Identifier tex = RadialTextures.getCancelTexture();
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0.0f, 0.0f, renderSize, renderSize, texSize, texSize,
                color);

        // "X" label in center only when cancel zone is hovered
        if (cancelHovered) {
            graphics.centeredText(font, Component.literal(CANCEL_SYMBOL),
                    centerX, centerY - font.lineHeight / HALF, COLOR_WHITE);
        }
    }

    /**
     * Renders blob item icons on each wedge and hovered type info in the center.
     *
     * @param graphics the GUI graphics extractor for rendering
     * @param centerX the screen center x coordinate
     * @param centerY the screen center y coordinate
     */
    private void renderLabels(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        GooType[] types = GooType.values();

        // Bordered goo-type icons on each wedge bisector
        for (int i = 0; i < WEDGE_COUNT; i++) {
            GooType type = types[i];
            double bisectorAngle = WEDGE_ARC * i + WEDGE_ARC / HALF;
            double iconRadius = (INNER_RADIUS + OUTER_RADIUS) / HALF;
            int iconX = centerX + (int) (Math.sin(bisectorAngle) * iconRadius) - ICON_OFFSET;
            int iconY = centerY - (int) (Math.cos(bisectorAngle) * iconRadius) - ICON_OFFSET;

            Identifier tex = Identifier.fromNamespaceAndPath(Goo.MODID,
                ICON_PATH_PREFIX + type.getId() + ICON_PATH_SUFFIX);
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    iconX, iconY, 0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }

        // Hovered type name + quantity in the center
        if (hoveredIndex >= 0 && hoveredIndex < WEDGE_COUNT) {
            GooType hovered = types[hoveredIndex];
            long qty = available.getOrDefault(hovered, 0L);
            boolean disabled = qty <= 0;
            int textColor = disabled ? DISABLED_TEXT_COLOR : COLOR_WHITE;

            Component name = Component.translatable(hovered.getTranslationKey());
            graphics.centeredText(font, name, centerX,
                    centerY - font.lineHeight - 1, textColor);

            String qtyText = formatQuantity(qty);
            graphics.centeredText(font, Component.literal(qtyText), centerX,
                    centerY + HALF, textColor);
        }
    }

    /**
     * Computes the final ARGB color for a wedge, applying hover brightening
     * or disabled dimming as appropriate.
     *
     * @param baseColor the base RGB color from the goo type
     * @param hovered true if this wedge is currently hovered
     * @param disabled true if the goo type has zero available quantity
     * @return the packed ARGB color with alpha and brightness adjustments
     */
    private static int computeWedgeColor(int baseColor, boolean hovered, boolean disabled) {
        int r = (baseColor >> RED_SHIFT) & CHANNEL_MASK;
        int g = (baseColor >> GREEN_SHIFT) & CHANNEL_MASK;
        int b = baseColor & CHANNEL_MASK;
        int alpha;

        if (disabled) {
            r = (int) (r * DISABLED_DIM);
            g = (int) (g * DISABLED_DIM);
            b = (int) (b * DISABLED_DIM);
            alpha = DISABLED_ALPHA;
        } else if (hovered) {
            r = Math.min(MAX_CHANNEL, r + HOVER_BOOST);
            g = Math.min(MAX_CHANNEL, g + HOVER_BOOST);
            b = Math.min(MAX_CHANNEL, b + HOVER_BOOST);
            alpha = HOVER_ALPHA;
        } else {
            alpha = NORMAL_ALPHA;
        }

        return (alpha << ALPHA_SHIFT) | (r << RED_SHIFT) | (g << GREEN_SHIFT) | b;
    }

    /**
     * Formats a mB quantity for display. Shows "0" for zero,
     * abbreviated "1.2k" for thousands, "1.2M" for millions.
     *
     * @param mB the quantity in microblobs
     * @return the human-readable formatted string
     */
    private static String formatQuantity(long mB) {
        if (mB <= 0) { return ZERO_LABEL; }
        if (mB < KILO_THRESHOLD) { return mB + MB_SUFFIX; }
        if (mB < MEGA_THRESHOLD) {
            double k = mB / KILO_DIVISOR;
            return String.format(KILO_FORMAT, k);
        }
        double m = mB / MEGA_DIVISOR;
        return String.format(MEGA_FORMAT, m);
    }

    // --- Input handling ---

    /**
     * Confirms the hovered selection on left or right mouse button release.
     *
     * @param event the mouse button release event
     * @return true if the event was handled
     */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Right-click release (button 1) or left-click release (button 0) confirms
        int button = event.button();
        if (button == 0 || button == 1) {
            confirmSelection();
            return true;
        }
        return super.mouseReleased(event);
    }

    /**
     * Delegates to default key handling; ESC closes without selecting.
     *
     * @param event the key press event
     * @return true if the event was handled
     */

    /** Applies the hovered selection to the glove and closes the screen. */
    private void confirmSelection() {
        if (hoveredIndex >= 0 && hoveredIndex < WEDGE_COUNT) {
            trySelectType(GooType.values()[hoveredIndex]);
        } else if (hoveredIndex == NO_SELECTION) {
            tryDeselectType();
        }
        onClose();
    }

    /** Selects the given goo type on the glove if the player has any available.
     *
     * @param type the goo type to select
     */
    private void trySelectType(GooType type) {
        if (available.getOrDefault(type, 0L) <= 0) { return; }
        ItemStack glove = findGloveStack();
        if (glove == null) { return; }
        GooGloveItem.setSelectedType(glove, type);
        sendSelectionToServer(type.getId());
    }

    /** Clears the glove selection (cancel/deselect zone). */
    private static void tryDeselectType() {
        ItemStack glove = findGloveStack();
        if (glove == null) { return; }
        GooGloveItem.setSelectedType(glove, null);
        sendSelectionToServer(DESELECT_ID);
    }

    /**
     * Finds the glove ItemStack the player is holding. Checks main hand first,
     * then offhand.
     *
     * @return the glove stack, or null if not held
     */
    private static @Nullable ItemStack findGloveStack() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) { return null; }

        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof GooGloveItem) { return main; }

        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof GooGloveItem) { return off; }

        return null;
    }

    /**
     * Sends the goo type selection to the server for persistence.
     *
     * @param gooTypeId the selected goo type ID, or empty string for deselect
     */
    private static void sendSelectionToServer(String gooTypeId) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    new GloveSelectPayload(gooTypeId)));
        }
    }
}
