package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.client.GooTooltipHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Renders a goo value tooltip line: an 11x11 type icon followed by "1.8 Metal" text.
 */
public class GooValueClientTooltipComponent implements ClientTooltipComponent {

    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 2;
    private static final int LINE_HEIGHT = 11;
    private static final int ICON_Y_OFFSET = -1;
    /** Scale factor for text to match smaller icons. */
    private static final float TEXT_SCALE = 0.8f;
    /** Separator between amount and type name in tooltip text. */
    private static final String AMOUNT_TYPE_SEPARATOR = " ";
    /** Fully opaque white in ARGB for tooltip text rendering. */
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Identifier iconTexture;
    private final Component displayText;

    /**
     * Constructs a client tooltip component from the data model.
     *
     * @param data the goo value data to render
     */
    public GooValueClientTooltipComponent(GooValueTooltipComponent data) {
        this.iconTexture = Identifier.fromNamespaceAndPath(
                "goo", "textures/item/" + data.type().getId() + "_icon_bordered.png");
        this.displayText = buildDisplayText(data);
    }

    /**
     * Builds the formatted display text: "1.8 Metal" with type color.
     *
     * @param data the goo value data to format
     * @return the styled text component for display
     */
    private static Component buildDisplayText(GooValueTooltipComponent data) {
        String formatted = GooTooltipHandler.formatFluidDisplay(data.amount());
        return Component.literal(formatted + AMOUNT_TYPE_SEPARATOR)
                .append(Component.translatable(data.type().getTranslationKey())
                        .withStyle(Style.EMPTY.withColor(data.type().getColor())));
    }

    /**
     * Returns the line height matching standard tooltip text.
     *
     * @param font the font used for measurement
     * @return the line height in pixels
     */
    @Override
    public int getHeight(@NonNull Font font) {
        return LINE_HEIGHT;
    }

    /**
     * Returns total width: icon + gap + text.
     *
     * @param font the font used for text width measurement
     * @return the total width in pixels
     */
    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + ICON_GAP + Math.round(font.width(displayText) * TEXT_SCALE);
    }

    /**
     * Renders the goo icon and value text.
     *
     * @param font the font for text rendering
     * @param x the left x position of the tooltip area
     * @param y the top y position of the tooltip area
     * @param width the available width for rendering
     * @param height the available height for rendering
     * @param guiGraphics the GUI graphics extractor for drawing
     */
    @Override
    public void extractImage(@NonNull Font font, int x, int y, int width, int height, @NonNull GuiGraphicsExtractor guiGraphics) {
        renderIcon(guiGraphics, x, y);
        renderText(font, x, y, guiGraphics);
    }

    /**
     * Blits the 11x11 bordered goo type icon texture.
     *
     * @param guiGraphics the GUI graphics extractor for drawing
     * @param x the left x position
     * @param y the top y position
     */
    private void renderIcon(GuiGraphicsExtractor guiGraphics, int x, int y) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture,
                x, y + ICON_Y_OFFSET, 0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Draws the formatted value text to the right of the icon.
     *
     * @param font the font for text rendering
     * @param x the left x position
     * @param y the top y position
     * @param guiGraphics the GUI graphics extractor for drawing
     */
    private void renderText(Font font, int x, int y, GuiGraphicsExtractor guiGraphics) {
        guiGraphics.pose().pushMatrix();
        float textX = x + ICON_SIZE + ICON_GAP;
        guiGraphics.pose().translate(textX, y);
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        guiGraphics.text(font, displayText, 0, 0, TEXT_COLOR);
        guiGraphics.pose().popMatrix();
    }
}
