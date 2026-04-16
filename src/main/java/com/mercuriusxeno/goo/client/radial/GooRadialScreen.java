package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
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
    static final int WEDGE_COUNT = GooType.values().length;

    /** Arc span per wedge in radians. */
    static final double WEDGE_ARC = 2.0 * Math.PI / WEDGE_COUNT;

    /** Inner radius of the wedge ring in GUI-scaled pixels. */
    static final int INNER_RADIUS = 30;

    /** Outer radius of the wedge ring in GUI-scaled pixels. */
    static final int OUTER_RADIUS = 100;

    /** Divisor for centering calculations. */
    static final int HALF = 2;

    /** Sentinel value for no wedge hovered (cancel zone or out of range). */
    static final int NO_SELECTION = -1;

    /** Full circle in radians. */
    static final double TWO_PI = 2.0 * Math.PI;

    /** Empty string for deselection packets. */
    private static final String DESELECT_ID = "";

    /** Available mB per goo type, snapshot taken on open. */
    private final Map<GooType, Integer> available;

    /** Pre-computed ARGB color per wedge, updated each frame. */
    private final int[] wedgeColors = new int[WEDGE_COUNT];

    /** Currently hovered wedge index, or -1 for cancel zone / out of range. */
    private int hoveredIndex = -1;

    /**
     * Creates the radial screen, snapshotting available goo from the player's inventory.
     *
     * @param available map of goo types to available volumes in microblobs
     */
    private GooRadialScreen(Map<GooType, Integer> available) {
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

        Map<GooType, Integer> snapshot = GooSourceScanner.aggregateAvailable(player);
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

        hoveredIndex = GooRadialRenderer.computeHoveredIndex(mouseX, mouseY, centerX, centerY);
        GooRadialRenderer.computeWedgeColors(wedgeColors, available, hoveredIndex);
        GooRadialRenderer.renderWedges(graphics, centerX, centerY, wedgeColors);
        GooRadialRenderer.renderCancelZone(graphics, centerX, centerY, hoveredIndex, font);
        GooRadialRenderer.renderLabels(graphics, centerX, centerY, hoveredIndex, available, font);
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
        if (available.getOrDefault(type, 0) <= 0) { return; }
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
