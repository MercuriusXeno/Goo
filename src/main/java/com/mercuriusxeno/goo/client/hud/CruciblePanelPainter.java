package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Measures and draws the crucible HUD panel: goo type rows with
 * reservoir/total volumes, and optional fuel row with blaze rod icon.
 */
final class CruciblePanelPainter {
    /** Height of one row (icon + text line). */
    private static final float ROW_HEIGHT = 11f;

    /** Volume text color (white). */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Separator color (dim gray). */
    private static final int SEP_COLOR = 0xFF888888;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    /** Separator between reservoir and total volumes. */
    private static final String VOLUME_SEPARATOR = " / ";

    private CruciblePanelPainter() {}

    /**
     * Renders the compact panel: one row per goo type plus an optional fuel row.
     * Goo rows: {icon} reservoir / total. Fuel row: {blaze rod icon} ##.#s
     *
     * @param poseStack the pose stack for rendering
     * @param be the block entity instance
     */
    static void renderPanel(PoseStack poseStack, CrucibleBlockEntity be) {
        GooContents reservoir = be.getReservoir();
        GooContents pool = getPoolContents(be);
        boolean hasFuel = !be.getFuelRod().isEmpty();
        boolean hasGoo = !reservoir.isEmpty() || !pool.isEmpty();
        if (!hasGoo && !hasFuel) { return; }
        PanelLayout layout = measurePanelLayout(reservoir, pool, be.getFuelRod(), hasGoo, hasFuel);
        drawPanelWithBackground(poseStack, reservoir, layout, be.getFuelRod(), hasGoo, hasFuel);
    }

    /**
     * Draws the panel background and content rows, then flushes the buffer.
     *
     * @param poseStack the pose stack for rendering
     * @param reservoir the reservoir goo contents
     * @param layout the pre-measured panel layout
     * @param fuelRod the fuel rod item stack
     * @param hasGoo whether goo is present
     * @param hasFuel whether fuel is present
     */
    private static void drawPanelWithBackground(PoseStack poseStack, GooContents reservoir,
            PanelLayout layout, ItemStack fuelRod, boolean hasGoo, boolean hasFuel) {
        MultiBufferSource.BufferSource buffers =
            Minecraft.getInstance().renderBuffers().bufferSource();
        float halfW = layout.panelWidth / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers, -halfW, -layout.panelHeight,
            layout.panelWidth, layout.panelHeight);
        renderContentAtOrigin(poseStack, buffers, reservoir, layout, halfW,
            fuelRod, hasGoo, hasFuel);
        buffers.endBatch();
    }

    /**
     * Computes the content origin from the layout and renders panel content there.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source
     * @param reservoir the reservoir goo contents
     * @param layout the pre-measured panel layout
     * @param halfW half the panel width
     * @param fuelRod the fuel rod item stack
     * @param hasGoo whether goo is present
     * @param hasFuel whether fuel is present
     */
    private static void renderContentAtOrigin(PoseStack poseStack, MultiBufferSource buffers,
            GooContents reservoir, PanelLayout layout, float halfW,
            ItemStack fuelRod, boolean hasGoo, boolean hasFuel) {
        float contentX = -halfW + InWorldHud.BORDER;
        float contentY = -layout.panelHeight + InWorldHud.BORDER;
        renderPanelContent(poseStack, buffers, reservoir, layout.total, layout.types,
            fuelRod, hasGoo, hasFuel, contentX, contentY);
    }

    /**
     * Measures panel dimensions from the crucible's goo and fuel state.
     *
     * @param reservoir the reservoir contents
     * @param pool the pool contents
     * @param fuelRod the fuel rod stack
     * @param hasGoo whether goo is present
     * @param hasFuel whether fuel is present
     * @return the computed layout
     */
    private static PanelLayout measurePanelLayout(GooContents reservoir, GooContents pool,
            ItemStack fuelRod, boolean hasGoo, boolean hasFuel) {
        GooContents total = hasGoo ? reservoir.mergeWith(pool) : GooContents.EMPTY;
        Set<GooType> types = hasGoo ? allTypes(reservoir, pool) : Set.of();
        float contentWidth = measureContentWidth(reservoir, total, types, fuelRod, hasGoo, hasFuel);
        int rowCount = types.size() + (hasFuel ? 1 : 0);
        return new PanelLayout(
            contentWidth + InWorldHud.BORDER * HALF,
            InWorldHud.BORDER * HALF + rowCount * ROW_HEIGHT,
            total, types);
    }

    /**
     * Measures the widest content row across goo and fuel rows.
     *
     * @param reservoir the reservoir goo contents
     * @param total the merged total goo contents
     * @param types the set of goo types present
     * @param fuelRod the fuel rod item stack
     * @param hasGoo whether goo is present
     * @param hasFuel whether fuel is present
     * @return the maximum content width in pixels
     */
    private static float measureContentWidth(GooContents reservoir, GooContents total,
            Set<GooType> types, ItemStack fuelRod, boolean hasGoo, boolean hasFuel) {
        Font font = Minecraft.getInstance().font;
        float gooWidth = hasGoo ? measureMaxRowWidth(font, reservoir, total, types) : 0;
        float fuelWidth = hasFuel ? CrucibleFuelDisplay.measureFuelRowWidth(font, fuelRod) : 0;
        return Math.max(gooWidth, fuelWidth);
    }

    /**
     * Renders goo rows and fuel row into the panel area.
     *
     * @param poseStack the pose stack
     * @param buffers the buffer source
     * @param reservoir the reservoir contents
     * @param total the merged total contents
     * @param types the goo types present
     * @param fuelRod the fuel rod stack
     * @param hasGoo whether goo is present
     * @param hasFuel whether fuel is present
     * @param contentX the left X
     * @param contentY the top Y
     */
    private static void renderPanelContent(PoseStack poseStack, MultiBufferSource buffers,
            GooContents reservoir, GooContents total, Set<GooType> types,
            ItemStack fuelRod, boolean hasGoo, boolean hasFuel,
            float contentX, float contentY) {
        Font font = Minecraft.getInstance().font;
        int gooRows = renderGooRowsIfPresent(poseStack, font, buffers,
            reservoir, total, types, hasGoo, contentX, contentY);
        if (hasFuel) {
            CrucibleFuelDisplay.renderFuelRow(poseStack, font, buffers,
                fuelRod, contentX, contentY + gooRows * ROW_HEIGHT);
        }
    }

    /**
     * Renders goo type rows if goo is present.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source
     * @param reservoir the reservoir goo contents
     * @param total the merged total goo contents
     * @param types the set of goo types present
     * @param hasGoo whether goo is present
     * @param x the left X coordinate
     * @param y the top Y coordinate
     * @return the number of goo rows rendered
     */
    private static int renderGooRowsIfPresent(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooContents reservoir, GooContents total,
            Set<GooType> types, boolean hasGoo, float x, float y) {
        if (!hasGoo) { return 0; }
        renderRows(poseStack, font, buffers, reservoir, total, types, x, y);
        return types.size();
    }

    /**
     * Extracts the PMI pool contents from the crucible.
     *
     * @param be the block entity instance
     * @return the poolContents
     */
    private static GooContents getPoolContents(CrucibleBlockEntity be) {
        if (be.getMeltingItem().isEmpty()) { return GooContents.EMPTY; }
        return PartiallyMeltedItem.getContents(be.getMeltingItem());
    }

    /**
     * Returns all goo types present in either the reservoir or pool.
     *
     * @param reservoir the reservoir goo contents
     * @param pool the pool goo contents
     * @return the complete set
     */
    private static Set<GooType> allTypes(GooContents reservoir, GooContents pool) {
        Set<GooType> types = new LinkedHashSet<>();
        types.addAll(reservoir.getAll().keySet());
        types.addAll(pool.getAll().keySet());
        return types;
    }

    /**
     * Measures the widest row across all types to determine panel width.
     *
     * @param font the font renderer
     * @param reservoir the reservoir goo contents
     * @param total the total merged goo contents
     * @param types the set of goo types present
     * @return the measured width in pixels
     */
    private static float measureMaxRowWidth(Font font, GooContents reservoir,
            GooContents total, Set<GooType> types) {
        float maxW = 0;
        for (GooType type : types) {
            String row = formatRow(volumeOf(reservoir, type), volumeOf(total, type));
            maxW = Math.max(maxW, font.width(row));
        }
        return CrucibleFuelDisplay.ICON_SIZE + CrucibleFuelDisplay.ICON_TEXT_GAP + maxW;
    }

    /**
     * Formats a single row: "### / ###" with compact volume notation.
     *
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol the total volume in mB
     * @return the formatted string
     */
    private static String formatRow(long reservoirVol, long totalVol) {
        return GooTooltipHandler.formatFluidDisplayCompact(reservoirVol)
            + VOLUME_SEPARATOR
            + GooTooltipHandler.formatFluidDisplayCompact(totalVol);
    }

    /**
     * Returns the volume of a specific type in a GooContents, or 0 if absent.
     *
     * @param contents the goo contents to measure
     * @param type the goo type
     * @return the result
     */
    private static long volumeOf(GooContents contents, GooType type) {
        return contents.getAll().getOrDefault(type, 0L);
    }

    /**
     * Renders all type rows vertically.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param reservoir the reservoir goo contents
     * @param total the total merged goo contents
     * @param types the set of goo types present
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private static void renderRows(PoseStack poseStack, Font font, MultiBufferSource buffers,
            GooContents reservoir, GooContents total,
            Set<GooType> types, float x, float y) {
        float rowY = y;
        for (GooType type : types) {
            renderTypeRow(poseStack, font, buffers, type,
                volumeOf(reservoir, type), volumeOf(total, type), x, rowY);
            rowY += ROW_HEIGHT;
        }
    }

    /**
     * Renders one row: goo type icon + "reservoir / total" text, both vertically centered.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol the total volume in mB
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private static void renderTypeRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooType type,
            long reservoirVol, long totalVol, float x, float y) {
        float iconY = y + (ROW_HEIGHT - CrucibleFuelDisplay.ICON_SIZE) / HALF_F;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF_F;
        CrucibleFuelDisplay.renderTexturedQuad(poseStack, buffers,
                CrucibleFuelDisplay.iconTexture(type), x, iconY);
        float textX = x + CrucibleFuelDisplay.ICON_SIZE + CrucibleFuelDisplay.ICON_TEXT_GAP;
        renderFractionText(font, buffers, poseStack, reservoirVol, totalVol, textX, textY);
    }

    /**
     * Renders "### / ###" with the separator in a dim color.
     *
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol the total volume in mB
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private static void renderFractionText(Font font, MultiBufferSource buffers,
            PoseStack poseStack, long reservoirVol, long totalVol,
            float x, float y) {
        String resText = GooTooltipHandler.formatFluidDisplayCompact(reservoirVol);
        String totText = GooTooltipHandler.formatFluidDisplayCompact(totalVol);
        float cx = x;
        cx = drawSegment(font, buffers, poseStack, resText, cx, y, TEXT_COLOR);
        cx = drawSegment(font, buffers, poseStack, VOLUME_SEPARATOR, cx, y, SEP_COLOR);
        drawSegment(font, buffers, poseStack, totText, cx, y, TEXT_COLOR);
    }

    /**
     * Draws one text segment and returns the X coordinate after it.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack for rendering
     * @param text the text to draw
     * @param x the left X coordinate
     * @param y the Y coordinate
     * @param color the ARGB color
     * @return the X coordinate after the drawn text
     */
    private static float drawSegment(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color) {
        InWorldHud.drawText(font, buffers, poseStack, text, x, y, color);
        return x + font.width(text);
    }

    /** Pre-computed panel dimensions and merged contents for rendering. */
    record PanelLayout(float panelWidth, float panelHeight,
            GooContents total, Set<GooType> types) {
    }
}
