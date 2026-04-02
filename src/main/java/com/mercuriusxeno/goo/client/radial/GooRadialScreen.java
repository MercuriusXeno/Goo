package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;

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
public class GooRadialScreen extends Screen {

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

    /** Available mB per goo type, snapshot taken on open. */
    private final EnumMap<GooType, Long> available;

    /** Pre-computed ARGB color per wedge, updated each frame. */
    private final int[] wedgeColors = new int[WEDGE_COUNT];

    /** Currently hovered wedge index, or -1 for cancel zone / out of range. */
    private int hoveredIndex = -1;

    /** Creates the radial screen, snapshotting available goo from the player's inventory. */
    private GooRadialScreen(EnumMap<GooType, Long> available) {
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
        if (player == null) return;

        EnumMap<GooType, Long> snapshot = GooSourceScanner.aggregateAvailable(player);
        mc.setScreen(new GooRadialScreen(snapshot));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RadialTextures.ensureInitialized();

        int centerX = width / 2;
        int centerY = height / 2;

        updateHoveredIndex(mouseX, mouseY, centerX, centerY);
        computeWedgeColors();
        renderWedges(graphics, centerX, centerY);
        renderCancelZone(graphics, centerX, centerY);
        renderLabels(graphics, centerX, centerY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Determines which wedge the mouse hovers based on angle and distance from center. */
    private void updateHoveredIndex(int mouseX, int mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < INNER_RADIUS) {
            hoveredIndex = -1; // cancel zone
            return;
        }

        // atan2 gives angle from positive-X axis, CCW. We want angle from top (negative Y), CW.
        double angle = Math.atan2(dx, -dy); // 0 = up, positive = clockwise
        if (angle < 0) angle += 2.0 * Math.PI;

        hoveredIndex = (int) (angle / WEDGE_ARC) % WEDGE_COUNT;
    }

    /** Pre-computes ARGB colors for all wedges based on hover/disabled state. */
    private void computeWedgeColors() {
        GooType[] types = GooType.values();
        for (int i = 0; i < WEDGE_COUNT; i++) {
            long qty = available.getOrDefault(types[i], 0L);
            boolean hovered = (i == hoveredIndex);
            boolean disabled = (qty <= 0);
            wedgeColors[i] = computeWedgeColor(types[i].getColor(), hovered, disabled);
        }
    }

    /**
     * Renders each wedge as a tinted blit of its pre-generated AA mask texture.
     * The mask is white-on-transparent; the blit color parameter applies
     * multiplicative tinting in the fragment shader.
     */
    private void renderWedges(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        int texSize = RadialTextures.TEX_SIZE;
        // Blit covers the full outer radius; texture maps 1:1 to the render area
        int renderSize = OUTER_RADIUS * 2;
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
     */
    private void renderCancelZone(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        boolean cancelHovered = (hoveredIndex == -1);
        int color = cancelHovered ? 0x88FFFFFF : 0x44FFFFFF;

        int texSize = RadialTextures.TEX_SIZE;
        int renderSize = OUTER_RADIUS * 2;
        int x = centerX - OUTER_RADIUS;
        int y = centerY - OUTER_RADIUS;

        Identifier tex = RadialTextures.getCancelTexture();
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                x, y, 0.0f, 0.0f, renderSize, renderSize, texSize, texSize,
                color);

        // "X" label in center only when cancel zone is hovered
        if (cancelHovered) {
            graphics.centeredText(font, Component.literal("\u2715"),
                    centerX, centerY - font.lineHeight / 2, 0xFFFFFFFF);
        }
    }

    /** Renders blob item icons on each wedge and hovered type info in the center. */
    private void renderLabels(GuiGraphicsExtractor graphics, int centerX, int centerY) {
        GooType[] types = GooType.values();

        // Bordered goo-type icons on each wedge bisector
        for (int i = 0; i < WEDGE_COUNT; i++) {
            GooType type = types[i];
            double bisectorAngle = WEDGE_ARC * i + WEDGE_ARC / 2.0;
            double iconRadius = (INNER_RADIUS + OUTER_RADIUS) / 2.0;
            int iconX = centerX + (int) (Math.sin(bisectorAngle) * iconRadius) - 5;
            int iconY = centerY - (int) (Math.cos(bisectorAngle) * iconRadius) - 5;

            Identifier tex = Identifier.fromNamespaceAndPath(Goo.MODID,
                "textures/item/" + type.getId() + "_icon_bordered.png");
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    iconX, iconY, 0.0f, 0.0f, 11, 11, 11, 11);
        }

        // Hovered type name + quantity in the center
        if (hoveredIndex >= 0 && hoveredIndex < WEDGE_COUNT) {
            GooType hovered = types[hoveredIndex];
            long qty = available.getOrDefault(hovered, 0L);
            boolean disabled = (qty <= 0);
            int textColor = disabled ? 0xFF888888 : 0xFFFFFFFF;

            Component name = Component.translatable(hovered.getTranslationKey());
            graphics.centeredText(font, name, centerX,
                    centerY - font.lineHeight - 1, textColor);

            String qtyText = formatQuantity(qty);
            graphics.centeredText(font, Component.literal(qtyText), centerX,
                    centerY + 2, textColor);
        }
    }

    /**
     * Computes the final ARGB color for a wedge, applying hover brightening
     * or disabled dimming as appropriate.
     */
    private static int computeWedgeColor(int baseColor, boolean hovered, boolean disabled) {
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int alpha;

        if (disabled) {
            r = (int) (r * DISABLED_DIM);
            g = (int) (g * DISABLED_DIM);
            b = (int) (b * DISABLED_DIM);
            alpha = DISABLED_ALPHA;
        } else if (hovered) {
            r = Math.min(255, r + HOVER_BOOST);
            g = Math.min(255, g + HOVER_BOOST);
            b = Math.min(255, b + HOVER_BOOST);
            alpha = HOVER_ALPHA;
        } else {
            alpha = NORMAL_ALPHA;
        }

        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Formats a mB quantity for display. Shows "0" for zero,
     * abbreviated "1.2k" for thousands, "1.2M" for millions.
     */
    private static String formatQuantity(long mB) {
        if (mB <= 0) return "0";
        if (mB < 1_000) return mB + " mB";
        if (mB < 1_000_000) {
            double k = mB / 1_000.0;
            return String.format("%.1fk", k);
        }
        double m = mB / 1_000_000.0;
        return String.format("%.1fM", m);
    }

    // --- Input handling ---

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

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC closes without selection (handled by Screen.onClose default)
        return super.keyPressed(event);
    }

    /** Applies the hovered selection to the glove and closes the screen. */
    private void confirmSelection() {
        if (hoveredIndex >= 0 && hoveredIndex < WEDGE_COUNT) {
            GooType selected = GooType.values()[hoveredIndex];
            long qty = available.getOrDefault(selected, 0L);

            if (qty > 0) {
                ItemStack glove = findGloveStack();
                if (glove != null) {
                    GooGloveItem.setSelectedType(glove, selected);
                    // Phase 5: send C2S selection sync packet here
                }
            }
        } else if (hoveredIndex == -1) {
            // Cancel zone: clear selection - glove goes to neutral
            ItemStack glove = findGloveStack();
            if (glove != null) {
                GooGloveItem.setSelectedType(glove, null);
            }
        }
        onClose();
    }

    /**
     * Finds the glove ItemStack the player is holding. Checks main hand first,
     * then offhand. Returns null if neither hand holds a glove.
     */
    private static @Nullable ItemStack findGloveStack() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return null;

        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof GooGloveItem) return main;

        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof GooGloveItem) return off;

        return null;
    }
}
