package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Measures and draws the crucible HUD panel: goo type rows with
 * reservoir/total volumes, and optional fuel row with blaze rod icon.
 */
final class CruciblePanelPainter {
    /**
     * Height of one row (icon + text line).
     */
    private static final float ROW_HEIGHT = 11f;

    /**
     * Volume text color (white).
     */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /**
     * Separator color (dim gray).
     */
    private static final int SEP_COLOR = 0xFF888888;

    /**
     * Divisor for centering calculations.
     */
    private static final int HALF = 2;

    /**
     * Half-width divisor for panel centering.
     */
    private static final float HALF_F = 2f;

    /**
     * Separator between reservoir and total volumes.
     */
    private static final String VOLUME_SEPARATOR = " / ";

    private CruciblePanelPainter() {
    }

    /**
     * Renders the compact panel: one row per goo type plus an optional fuel row.
     * Goo rows: {icon} reservoir / total. Fuel row: {blaze rod icon} ##.#s
     *
     * @param poseStack the pose stack for rendering
     * @param be        the block entity instance
     */
    static void renderPanel(PoseStack poseStack, CrucibleBlockEntity be) {
        CrucibleSnapshot snap = buildSnapshot(be);
        if (snap == null) {
            return;
        }
        PanelLayout layout = measurePanelLayout(snap);
        drawPanelWithBackground(poseStack, snap, layout);
    }

    /**
     * Builds the crucible snapshot, or returns null if there is nothing to display.
     *
     * @param be the crucible block entity
     * @return the snapshot, or null if the crucible has no displayable content
     */
    private static @Nullable CrucibleSnapshot buildSnapshot(CrucibleBlockEntity be) {
        GooContents reservoir = be.getReservoir();
        GooContents pool = getPoolContents(be);
        boolean hasFuel = !be.getFuelRod().isEmpty();
        boolean hasGoo = hasAnyGoo(reservoir, pool);
        if (!hasGoo && !hasFuel) {
            return null;
        }
        GooContents total = hasGoo ? reservoir.mergeWith(pool) : GooContents.EMPTY;
        Set<GooType> types = hasGoo ? allTypes(reservoir, pool) : Set.of();
        return new CrucibleSnapshot(
                reservoir, total, types, be.getFuelRod(), hasGoo, hasFuel);
    }

    /**
     * Returns true if either the reservoir or pool contains any goo.
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the pool goo contents
     * @return true if either is non-empty
     */
    private static boolean hasAnyGoo(GooContents reservoir, GooContents pool) {
        return !reservoir.isEmpty() || !pool.isEmpty();
    }

    /**
     * Draws the panel background and content rows, then flushes the buffer.
     *
     * @param poseStack the pose stack for rendering
     * @param snap      the crucible state snapshot
     * @param layout    the pre-measured panel layout
     */
    private static void drawPanelWithBackground(PoseStack poseStack, CrucibleSnapshot snap,
                                                PanelLayout layout) {
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        float halfW = layout.panelWidth / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers,
                new PanelRectangle(-halfW, -layout.panelHeight, layout.panelWidth, layout.panelHeight));
        renderContentAtOrigin(poseStack, buffers, snap, layout, halfW);
        buffers.endBatch();
    }

    /**
     * Computes the content origin from the layout and renders panel content there.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source
     * @param snap      the crucible state snapshot
     * @param layout    the pre-measured panel layout
     * @param halfW     half the panel width
     */
    private static void renderContentAtOrigin(PoseStack poseStack, MultiBufferSource buffers,
                                              CrucibleSnapshot snap, PanelLayout layout, float halfW) {
        float contentX = -halfW + InWorldHud.BORDER;
        float contentY = -layout.panelHeight + InWorldHud.BORDER;
        renderPanelContent(poseStack, buffers, snap, contentX, contentY);
    }

    /**
     * Measures panel dimensions from the crucible snapshot.
     *
     * @param snap the crucible state snapshot
     * @return the computed layout
     */
    private static PanelLayout measurePanelLayout(CrucibleSnapshot snap) {
        float contentWidth = measureContentWidth(snap);
        int rowCount = snap.types().size() + (snap.hasFuel() ? 1 : 0);
        return new PanelLayout(
                contentWidth + InWorldHud.BORDER * HALF,
                InWorldHud.BORDER * HALF + rowCount * ROW_HEIGHT);
    }

    /**
     * Measures the widest content row across goo and fuel rows.
     *
     * @param snap the crucible state snapshot
     * @return the maximum content width in pixels
     */
    private static float measureContentWidth(CrucibleSnapshot snap) {
        Font font = Minecraft.getInstance().font;
        float gooWidth = snap.hasGoo()
                ? measureMaxRowWidth(font, snap.reservoir(), snap.total(), snap.types()) : 0;
        float fuelWidth = snap.hasFuel()
                ? CrucibleFuelDisplay.measureFuelRowWidth(font, snap.fuelRod()) : 0;
        return Math.max(gooWidth, fuelWidth);
    }

    /**
     * Renders goo rows and fuel row into the panel area.
     *
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param snap      the crucible state snapshot
     * @param contentX  the left X
     * @param contentY  the top Y
     */
    private static void renderPanelContent(PoseStack poseStack, MultiBufferSource buffers,
                                           CrucibleSnapshot snap, float contentX, float contentY) {
        Font font = Minecraft.getInstance().font;
        int gooRows = renderGooRowsIfPresent(poseStack, font, buffers, snap, contentX, contentY);
        if (snap.hasFuel()) {
            CrucibleFuelDisplay.renderFuelRow(poseStack, font, buffers,
                    snap.fuelRod(), contentX, contentY + gooRows * ROW_HEIGHT);
        }
    }

    /**
     * Renders goo type rows if goo is present.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param snap      the crucible state snapshot
     * @param x         the left X coordinate
     * @param y         the top Y coordinate
     * @return the number of goo rows rendered
     */
    private static int renderGooRowsIfPresent(PoseStack poseStack, Font font,
                                              MultiBufferSource buffers, CrucibleSnapshot snap, float x, float y) {
        if (!snap.hasGoo()) {
            return 0;
        }
        renderRows(poseStack, font, buffers, snap.reservoir(), snap.total(), snap.types(), x, y);
        return snap.types().size();
    }

    /**
     * Extracts the PMI pool contents from the crucible.
     *
     * @param be the block entity instance
     * @return the poolContents
     */
    private static GooContents getPoolContents(CrucibleBlockEntity be) {
        if (be.getMeltingItem().isEmpty()) {
            return GooContents.EMPTY;
        }
        return PartiallyMeltedItem.getContents(be.getMeltingItem());
    }

    /**
     * Returns all goo types present in either the reservoir or pool.
     *
     * @param reservoir the reservoir goo contents
     * @param pool      the pool goo contents
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
     * @param font      the font renderer
     * @param reservoir the reservoir goo contents
     * @param total     the total merged goo contents
     * @param types     the set of goo types present
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
     * @param totalVol     the total volume in mB
     * @return the formatted string
     */
    private static String formatRow(int reservoirVol, int totalVol) {
        return GooTooltipHandler.formatFluidDisplayCompact(reservoirVol)
                + VOLUME_SEPARATOR
                + GooTooltipHandler.formatFluidDisplayCompact(totalVol);
    }

    /**
     * Returns the volume of a specific type in a GooContents, or 0 if absent.
     *
     * @param contents the goo contents to measure
     * @param type     the goo type
     * @return the result
     */
    private static int volumeOf(GooContents contents, GooType type) {
        return contents.getAll().getOrDefault(type, 0);
    }

    /**
     * Renders all type rows vertically.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source for rendering
     * @param reservoir the reservoir goo contents
     * @param total     the total merged goo contents
     * @param types     the set of goo types present
     * @param x         the X coordinate
     * @param y         the Y coordinate
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
     * @param poseStack    the pose stack for rendering
     * @param font         the font renderer
     * @param buffers      the buffer source for rendering
     * @param type         the goo type
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol     the total volume in mB
     * @param x            the X coordinate
     * @param y            the Y coordinate
     */
    private static void renderTypeRow(PoseStack poseStack, Font font,
                                      MultiBufferSource buffers, GooType type,
                                      int reservoirVol, int totalVol, float x, float y) {
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
     * @param font         the font renderer
     * @param buffers      the buffer source for rendering
     * @param poseStack    the pose stack for rendering
     * @param reservoirVol the reservoir volume in mB
     * @param totalVol     the total volume in mB
     * @param x            the X coordinate
     * @param y            the Y coordinate
     */
    private static void renderFractionText(Font font, MultiBufferSource buffers,
                                           PoseStack poseStack, int reservoirVol, int totalVol,
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
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param poseStack the pose stack for rendering
     * @param text      the text to draw
     * @param x         the left X coordinate
     * @param y         the Y coordinate
     * @param color     the ARGB color
     * @return the X coordinate after the drawn text
     */
    private static float drawSegment(Font font, MultiBufferSource buffers,
                                     PoseStack poseStack, String text, float x, float y, int color) {
        InWorldHud.drawText(font, buffers, poseStack, text, x, y, color);
        return x + font.width(text);
    }

    /**
     * Pre-computed panel dimensions for rendering.
     */
    record PanelLayout(float panelWidth, float panelHeight) {
    }
}
