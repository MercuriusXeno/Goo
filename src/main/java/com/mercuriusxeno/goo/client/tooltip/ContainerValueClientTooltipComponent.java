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
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a two-column goo tooltip: fluid contents on the left, container
 * decomposition value on the right, with a white "+" in between.
 */
public class ContainerValueClientTooltipComponent implements ClientTooltipComponent {

    private static final int ICON_SIZE = 11;
    private static final int ICON_GAP = 2;
    private static final int LINE_HEIGHT = 15;
    private static final int ICON_Y_OFFSET = -2;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PLUS_COLOR = 0xFFFFFFFF;
    private static final String PLUS = "+";
    private static final int PLUS_PAD = 6;
    /** Total width of the plus cell: one pad on each side. */
    private static final int PLUS_CELL_WIDTH = PLUS_PAD * 2;
    /** Divisor for finding the center row of the column. */
    private static final int ROW_HALVE = 2;
    private static final String AMOUNT_TYPE_SEP = " ";
    /** Namespace for goo textures. */
    private static final String GOO_NAMESPACE = "goo";
    /** Texture path prefix for goo type icons. */
    private static final String ICON_TEXTURE_PREFIX = "textures/goo/type/";
    /** Texture path suffix for bordered goo type icons. */
    private static final String ICON_TEXTURE_SUFFIX = ".png";

    private final List<ColumnEntry> leftEntries;
    private final List<ColumnEntry> rightEntries;

    /** Pre-rendered column entry with icon texture and display text. */
    private record ColumnEntry(Identifier icon, Component text) {

        int width(Font font) {
            return ICON_SIZE + ICON_GAP + font.width(text);
        }
    }

    /**
     * Constructs the client tooltip from the data model.
     *
     * @param data the container value tooltip data
     */
    public ContainerValueClientTooltipComponent(ContainerValueTooltipComponent data) {
        this.leftEntries = buildEntries(data.contents());
        this.rightEntries = buildEntries(data.container());
    }

    private static List<ColumnEntry> buildEntries(List<ContainerValueTooltipComponent.Entry> entries) {
        List<ColumnEntry> result = new ArrayList<>(entries.size());
        for (var e : entries) {
            Identifier icon = Identifier.fromNamespaceAndPath(
                    GOO_NAMESPACE, ICON_TEXTURE_PREFIX + e.type().getId() + ICON_TEXTURE_SUFFIX);
            String formatted = GooTooltipHandler.formatFluidDisplay(e.amount());
            Component text = Component.literal(formatted + AMOUNT_TYPE_SEP)
                    .append(Component.translatable(e.type().getTranslationKey())
                            .withStyle(Style.EMPTY.withColor(e.type().getColor())));
            result.add(new ColumnEntry(icon, text));
        }
        return result;
    }

    @Override
    public int getHeight(@NonNull Font font) {
        return Math.max(leftEntries.size(), rightEntries.size()) * LINE_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int left = maxWidth(font, leftEntries);
        int right = maxWidth(font, rightEntries);
        int plus = font.width(PLUS) + PLUS_CELL_WIDTH;
        return left + plus + right;
    }

    private static int maxWidth(Font font, List<ColumnEntry> entries) {
        int max = 0;
        for (ColumnEntry e : entries) {
            max = Math.max(max, e.width(font));
        }
        return max;
    }

    @Override
    public void extractImage(@NonNull Font font, int x, int y,
            int width, int height, @NonNull GuiGraphicsExtractor g) {
        int leftWidth = maxWidth(font, leftEntries);
        int plusWidth = font.width(PLUS) + PLUS_CELL_WIDTH;
        int rightX = x + leftWidth + plusWidth;
        int rows = Math.max(leftEntries.size(), rightEntries.size());
        int midRow = rows / ROW_HALVE;

        for (int row = 0; row < rows; row++) {
            int rowY = y + row * LINE_HEIGHT;
            if (row < leftEntries.size()) {
                renderEntry(g, font, leftEntries.get(row), x, rowY);
            }
            if (row < rightEntries.size()) {
                renderEntry(g, font, rightEntries.get(row), rightX, rowY);
            }
            if (row == midRow) {
                int plusX = x + leftWidth + PLUS_PAD;
                g.text(font, Component.literal(PLUS), plusX, rowY, PLUS_COLOR);
            }
        }
    }

    private static void renderEntry(GuiGraphicsExtractor g, Font font,
            ColumnEntry entry, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, entry.icon,
                x, y + ICON_Y_OFFSET, 0.0f, 0.0f,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        g.text(font, entry.text, x + ICON_SIZE + ICON_GAP, y, TEXT_COLOR);
    }
}
