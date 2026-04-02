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

    private static final int ICON_SIZE = 11;
    private static final int ICON_GAP = 2;
    private static final int LINE_HEIGHT = 15;
    private static final int ICON_Y_OFFSET = -2;

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

    /** Builds the formatted display text: "1.8 Metal" with type color. */
    private static Component buildDisplayText(GooValueTooltipComponent data) {
        String formatted = GooTooltipHandler.formatFluidDisplay(data.amount());
        return Component.literal(formatted + " ")
                .append(Component.translatable(data.type().getTranslationKey())
                        .withStyle(Style.EMPTY.withColor(data.type().getColor())));
    }

    /** Returns the line height matching standard tooltip text. */
    @Override
    public int getHeight(@NonNull Font font) {
        return LINE_HEIGHT;
    }

    /** Returns total width: icon + gap + text. */
    @Override
    public int getWidth(Font font) {
        return ICON_SIZE + ICON_GAP + font.width(displayText);
    }

    /** Renders the goo icon and value text. */
    @Override
    public void extractImage(@NonNull Font font, int x, int y, int width, int height, @NonNull GuiGraphicsExtractor guiGraphics) {
        renderIcon(guiGraphics, x, y);
        renderText(font, x, y, guiGraphics);
    }

    /** Blits the 11x11 bordered goo type icon texture. */
    private void renderIcon(GuiGraphicsExtractor guiGraphics, int x, int y) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture,
                x, y + ICON_Y_OFFSET, 0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /** Draws the formatted value text to the right of the icon. */
    private void renderText(Font font, int x, int y, GuiGraphicsExtractor guiGraphics) {
        guiGraphics.text(font, displayText,
                x + ICON_SIZE + ICON_GAP, y, 0xFFFFFFFF);
    }
}
