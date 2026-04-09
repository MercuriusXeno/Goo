package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.client.VatStackAggregator.VatStackData;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import java.util.Map;

/**
 * Renders the vat HUD panel content: background, header rows, and goo rows.
 * Extracted from VatHudRenderer to keep method counts under threshold.
 */
final class VatHudPanelPainter {

    /** Upgrade level color (aqua). */
    private static final int UPGRADE_COLOR = 0xFF55FFFF;
    /** Name label color (gold). */
    private static final int LABEL_COLOR = 0xFFFFAA00;
    /** Stack size color (gray). */
    private static final int STACK_COLOR = 0xFFAAAAAA;

    /** Upgrade level display prefix. */
    private static final String UPGRADE_PREFIX = "Lv ";
    /** Stack size display prefix. */
    private static final String STACK_PREFIX = "Stack: ";
    /** Empty string for absent text fields. */
    private static final String EMPTY_TEXT = "";

    /** Divisor for centering calculations. */
    private static final int HALF = 2;
    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    private VatHudPanelPainter() { }

    /**
     * Renders the panel content: label, upgrade level, stack size, goo rows.
     *
     * @param poseStack the pose stack for rendering
     * @param data the extracted render data
     * @param face the block face direction
     */
    static void renderContent(PoseStack poseStack,
            VatStackData data, Direction face) {
        Font font = Minecraft.getInstance().font;
        String upgradeText = data.compression() > 0 ? UPGRADE_PREFIX + data.compression() : EMPTY_TEXT;
        String stackText = data.stackSize() > 1 ? STACK_PREFIX + data.stackSize() : EMPTY_TEXT;
        float contentWidth = computeContentWidth(font, data, upgradeText, stackText);
        float[] dims = computePanelDimensions(contentWidth, data, upgradeText, stackText);

        if (face == Direction.DOWN) { poseStack.translate(0, dims[1], 0); }
        renderPanelBody(poseStack, font, data, upgradeText, stackText, dims[0], dims[1]);
    }

    /**
     * Computes the maximum content width across all row types.
     *
     * @param font the font renderer
     * @param data the vat stack data
     * @param upgradeText pre-formatted upgrade text (empty if none)
     * @param stackText pre-formatted stack text (empty if none)
     * @return the widest row width in pixels
     */
    static float computeContentWidth(Font font, VatStackData data,
            String upgradeText, String stackText) {
        float maxRowWidth = InWorldHud.computeMaxRowWidth(font, data.contents());
        float upgradeWidth = upgradeText.isEmpty() ? 0 : font.width(upgradeText);
        float stackWidth = stackText.isEmpty() ? 0 : font.width(stackText);
        float labelWidth = data.hasLabel() ? font.width(data.label()) : 0;
        return Math.max(maxRowWidth,
            Math.max(upgradeWidth, Math.max(labelWidth, stackWidth)));
    }

    /**
     * Returns panel width and height as a two-element array.
     *
     * @param contentWidth the widest row width
     * @param data the vat stack data
     * @param upgradeText pre-formatted upgrade text (empty if none)
     * @param stackText pre-formatted stack text (empty if none)
     * @return {panelWidth, panelHeight}
     */
    static float[] computePanelDimensions(float contentWidth, VatStackData data,
            String upgradeText, String stackText) {
        int headerRows = (data.hasLabel() ? 1 : 0)
            + (stackText.isEmpty() ? 0 : 1)
            + (upgradeText.isEmpty() ? 0 : 1);
        int rowCount = headerRows + data.contents().typeCount();
        float panelWidth = contentWidth + InWorldHud.BORDER * HALF;
        float panelHeight = InWorldHud.BORDER * HALF + rowCount * InWorldHud.ROW_HEIGHT;
        return new float[]{panelWidth, panelHeight};
    }

    /**
     * Renders background quad, header rows, and goo rows for the panel.
     *
     * @param poseStack the pose stack
     * @param font the font renderer
     * @param data the vat stack data
     * @param upgradeText pre-formatted upgrade text
     * @param stackText pre-formatted stack text
     * @param panelWidth the panel width
     * @param panelHeight the panel height
     */
    static void renderPanelBody(PoseStack poseStack, Font font, VatStackData data,
            String upgradeText, String stackText, float panelWidth, float panelHeight) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float halfW = panelWidth / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers, -halfW, -panelHeight, panelWidth, panelHeight);
        float contentX = -halfW + InWorldHud.BORDER;
        float baseY = -panelHeight + InWorldHud.BORDER;
        int row = renderHeaders(font, buffers, poseStack, data, upgradeText, stackText, contentX, baseY);
        renderGooRows(poseStack, font, buffers, data.contents(), contentX, baseY, row);
        buffers.endBatch();
    }

    /**
     * Renders label, stack size, and upgrade header rows as applicable.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack
     * @param data the vat stack data
     * @param upgradeText pre-formatted upgrade text
     * @param stackText pre-formatted stack text
     * @param x the left X
     * @param baseY the panel content top Y
     * @return the number of header rows rendered
     */
    static int renderHeaders(Font font, MultiBufferSource buffers,
            PoseStack poseStack, VatStackData data, String upgradeText,
            String stackText, float x, float baseY) {
        int row = 0;
        if (data.hasLabel()) { row += renderHeaderRow(font, buffers, poseStack, data.label(), x, baseY, row, LABEL_COLOR); }
        if (!stackText.isEmpty()) { row += renderHeaderRow(font, buffers, poseStack, stackText, x, baseY, row, STACK_COLOR); }
        if (!upgradeText.isEmpty()) { row += renderHeaderRow(font, buffers, poseStack, upgradeText, x, baseY, row, UPGRADE_COLOR); }
        return row;
    }

    /**
     * Renders a single header text row, vertically centered within its row slot.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack
     * @param text the header text
     * @param x the left X
     * @param baseY the panel content top Y
     * @param row the current row index
     * @param color the text color
     * @return 1, for row-counter advancement
     */
    static int renderHeaderRow(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float baseY, int row, int color) {
        float textY = baseY + row * InWorldHud.ROW_HEIGHT
            + (InWorldHud.ROW_HEIGHT - font.lineHeight) / HALF_F;
        InWorldHud.drawText(font, buffers, poseStack, text, x, textY, color);
        return 1;
    }

    /**
     * Renders all goo type rows starting at the given row offset.
     *
     * @param poseStack the pose stack
     * @param font the font renderer
     * @param buffers the buffer source
     * @param contents the goo contents to render
     * @param x the left X
     * @param baseY the panel content top Y
     * @param startRow the first row index for goo rows
     */
    static void renderGooRows(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooContents contents,
            float x, float baseY, int startRow) {
        int row = startRow;
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            float rowY = baseY + row * InWorldHud.ROW_HEIGHT;
            String amountText = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            InWorldHud.renderGooRow(poseStack, font, buffers, entry.getKey(), amountText, x, rowY);
            row++;
        }
    }
}
