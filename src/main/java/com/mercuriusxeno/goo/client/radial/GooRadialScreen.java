package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.AbilitySyncHandler;
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
 * Left-click a wedge to drill into that type's ability radial.
 * Left-click center to deselect. Right-click to dismiss.
 *
 * Wedges are rendered via pre-generated anti-aliased mask textures
 * (see {@link RadialTextures}) with per-wedge color tinting.
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
     * Re-opens the type radial with a pre-existing availability snapshot.
     * Used when navigating back from the ability radial.
     *
     * @param snapshot the goo availability map to reuse
     */
    public static void openWithSnapshot(Map<GooType, Integer> snapshot) {
        Minecraft mc = Minecraft.getInstance();
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
     * Left-click on a wedge transitions to ability radial for that type.
     * Left-click on center deselects and closes.
     * Right-click dismisses without changing selection.
     *
     * @param event       the mouse button click event
     * @param doubleClick true if this is a double-click
     * @return true if the event was handled
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button == 1) {
            onClose();
            return true;
        }
        if (button == 0) {
            handleLeftClick();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Processes a left-click: drill into type or deselect from center. */
    private void handleLeftClick() {
        if (hoveredIndex >= 0 && hoveredIndex < WEDGE_COUNT) {
            tryTransitionToAbilities(GooType.values()[hoveredIndex]);
        } else if (hoveredIndex == NO_SELECTION) {
            tryDeselectType();
            onClose();
        }
    }

    /** Transitions to the ability radial for the given type if available.
     *
     * @param type the goo type to drill into
     */
    private void tryTransitionToAbilities(GooType type) {
        if (available.getOrDefault(type, 0) <= 0) { return; }
        if (AbilitySyncHandler.hasAbilities(type)) {
            AbilityRadialScreen.open(type, available);
        }
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
