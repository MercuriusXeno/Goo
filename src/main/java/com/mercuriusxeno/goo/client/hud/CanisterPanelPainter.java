package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Measures and draws the canister HUD panel: label, upgrade level, and
 * goo type/amount rows with face-aware billboard rotation.
 */
final class CanisterPanelPainter {
    /** Label color (gold). */
    private static final int LABEL_COLOR = 0xFFFFD700;

    /** Upgrade level color (aqua). */
    private static final int UPGRADE_COLOR = 0xFF55FFFF;

    /** Z-nudge for panels on upward/downward faces to prevent z-fighting. */
    private static final float Z_NUDGE_POS = 0.01f;

    /** Z-nudge for panels on side faces. */
    private static final float Z_NUDGE_NEG = -0.01f;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Upgrade level display prefix. */
    private static final String UPGRADE_PREFIX = "Lv ";

    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    /** Empty string for absent upgrade text. */
    private static final String EMPTY_UPGRADE = "";

    private CanisterPanelPainter() {}

    /**
     * Renders the HUD panel at the tracked position, using face-aware rotation.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param data the extracted render data
     * @param pos the block position
     * @param slot the slot index
     * @param anchor camera-relative positioning data for the panel
     */
    static void renderPanel(PoseStack poseStack, Camera camera,
            CanisterHudRenderer.SlotData data, BlockPos pos, int slot,
            PanelAnchor anchor) {
        poseStack.pushPose();
        applyPanelTransform(poseStack, camera, pos, anchor);
        renderContent(poseStack, data, anchor.face());
        poseStack.popPose();
    }

    /**
     * Positions, rotates, and scales the pose stack for panel rendering.
     * Translates to the camera-relative anchor, applies face/billboard rotation,
     * nudges to prevent z-fighting, and scales to pixel units.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pos the block position
     * @param anchor camera-relative positioning data for the panel
     */
    private static void applyPanelTransform(PoseStack poseStack,
            Camera camera, BlockPos pos, PanelAnchor anchor) {
        translateToAnchor(poseStack, camera, pos, anchor);
        applyRotation(poseStack, camera, anchor.face(), anchor.blockAbove(), anchor.pitch());
        float zNudge = isVerticalFace(anchor.face()) ? Z_NUDGE_POS : Z_NUDGE_NEG;
        poseStack.translate(0, 0, zNudge);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }

    /**
     * Translates the pose stack to the camera-relative anchor position.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pos the block position
     * @param anchor camera-relative positioning data for the panel
     */
    private static void translateToAnchor(PoseStack poseStack, Camera camera,
            BlockPos pos, PanelAnchor anchor) {
        Vec3 cam = camera.position();
        poseStack.translate(
            pos.getX() + anchor.cx() - cam.x,
            pos.getY() + anchor.lift() - cam.y,
            pos.getZ() + anchor.cz() - cam.z);
    }

    /**
     * Returns true if the face is vertical (UP or DOWN).
     *
     * @param face the direction to check
     * @return true for vertical faces
     */
    private static boolean isVerticalFace(Direction face) {
        return face == Direction.UP || face == Direction.DOWN;
    }

    /**
     * Applies the appropriate rotation based on face and block-above state.
     * Side faces use face rotation; UP with block above uses flat rotation;
     * UP without block above uses billboard rotation.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param face the face direction
     * @param blockAbove whether a block is above
     * @param pitch the current pitch
     */
    private static void applyRotation(PoseStack poseStack, Camera camera,
            Direction face, boolean blockAbove, float pitch) {
        if (isVerticalFace(face)) {
            if (blockAbove) {
                InWorldHud.applyFlatRotation(poseStack, camera);
            } else {
                InWorldHud.applyBillboardRotation(poseStack, camera, pitch);
            }
        } else {
            InWorldHud.applyFaceRotation(poseStack, face);
        }
    }

    /**
     * Renders the panel content: label, upgrade level, and goo type/amount rows.
     *
     * @param poseStack the pose stack for rendering
     * @param data the extracted render data
     * @param face the tracked face direction
     */
    private static void renderContent(PoseStack poseStack,
            CanisterHudRenderer.SlotData data, Direction face) {
        Font font = Minecraft.getInstance().font;
        PanelMetrics metrics = measurePanel(font, data);
        if (face == Direction.DOWN) {
            poseStack.translate(0, metrics.height, 0);
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        drawPanel(poseStack, font, buffers, data, metrics);
        buffers.endBatch();
    }

    /**
     * Measures panel dimensions based on label, upgrade text, and goo row content.
     *
     * @param font the font renderer for width measurement
     * @param data the slot data containing label, compression, and goo contents
     * @return the computed panel metrics
     */
    private static PanelMetrics measurePanel(Font font, CanisterHudRenderer.SlotData data) {
        String label = data.label();
        boolean hasLabel = label != null && !label.isEmpty();
        boolean hasUpgrade = data.compression() > 0;
        String upgradeText = hasUpgrade ? UPGRADE_PREFIX + data.compression() : EMPTY_UPGRADE;
        GooContents goo = toGooContents(data.content());
        int fluidRows = goo.isEmpty() && !data.content().isEmpty() ? 1 : goo.typeCount();
        float contentWidth = goo.isEmpty() && !data.content().isEmpty()
                ? maxWidth(font, InWorldHud.computeFluidRowWidth(font, data.content().amount()),
                        label, upgradeText, hasLabel, hasUpgrade)
                : measureContentWidth(font, goo, label, upgradeText, hasLabel, hasUpgrade);
        int rowCount = countRows(fluidRows, hasLabel, hasUpgrade);
        return buildMetrics(contentWidth, rowCount, label, upgradeText, hasLabel, hasUpgrade);
    }

    private static float maxWidth(Font font, float rowWidth,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
        float max = rowWidth;
        if (hasLabel) { max = Math.max(max, font.width(label)); }
        if (hasUpgrade) { max = Math.max(max, font.width(upgradeText)); }
        return max;
    }

    /**
     * Computes final panel width/height and assembles metrics.
     *
     * @param contentWidth the widest content row width
     * @param rowCount the total number of rows
     * @param label the label text
     * @param upgradeText the upgrade text
     * @param hasLabel whether a label is present
     * @param hasUpgrade whether an upgrade is present
     * @return the assembled panel metrics
     */
    private static PanelMetrics buildMetrics(float contentWidth, int rowCount,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
        float width = contentWidth + InWorldHud.BORDER * HALF;
        float height = InWorldHud.BORDER * HALF + rowCount * InWorldHud.ROW_HEIGHT;
        return new PanelMetrics(width, height, label, upgradeText, hasLabel, hasUpgrade);
    }

    /**
     * Computes the widest content row across goo rows, label, and upgrade text.
     *
     * @param font the font renderer
     * @param contents the goo contents for row width measurement
     * @param label the label text, or null
     * @param upgradeText the upgrade text
     * @param hasLabel whether a label is present
     * @param hasUpgrade whether an upgrade line is present
     * @return the maximum content width in pixels
     */
    private static float measureContentWidth(Font font, GooContents contents,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
        float maxRowWidth = InWorldHud.computeMaxRowWidth(font, contents);
        float labelWidth = hasLabel ? font.width(label) : 0;
        float upgradeWidth = hasUpgrade ? font.width(upgradeText) : 0;
        return Math.max(maxRowWidth, Math.max(labelWidth, upgradeWidth));
    }

    /**
     * Counts the total number of panel rows (headers + goo types).
     *
     * @param gooRows the number of goo type rows
     * @param hasLabel whether a label header is present
     * @param hasUpgrade whether an upgrade header is present
     * @return the total row count
     */
    private static int countRows(int gooRows, boolean hasLabel, boolean hasUpgrade) {
        int headerRows = (hasLabel ? 1 : 0) + (hasUpgrade ? 1 : 0);
        return headerRows + gooRows;
    }

    /**
     * Draws the background, header rows, and goo rows onto the panel.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source
     * @param data the slot data containing goo contents
     * @param metrics the pre-computed panel metrics
     */
    private static void drawPanel(PoseStack poseStack, Font font,
            MultiBufferSource.BufferSource buffers, CanisterHudRenderer.SlotData data,
            PanelMetrics metrics) {
        float halfW = metrics.width / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers,
                new PanelRectangle(-halfW, -metrics.height, metrics.width, metrics.height));
        float contentX = -halfW + InWorldHud.BORDER;
        float baseY = -metrics.height + InWorldHud.BORDER;
        int row = drawHeaders(font, buffers, poseStack, metrics, contentX, baseY);
        GooContents goo = toGooContents(data.content());
        if (!goo.isEmpty()) {
            InWorldHud.renderGooRows(poseStack, font, buffers, goo, contentX, baseY, row);
        } else if (!data.content().isEmpty()) {
            InWorldHud.renderFluidRow(poseStack, font, buffers,
                    data.content().fluid(), data.content().amount(), contentX,
                    baseY + row * InWorldHud.ROW_HEIGHT);
        }
    }

    /**
     * Draws the optional label and upgrade header rows, returning the next row index.
     *
     * @param font the font renderer
     * @param buffers the buffer source
     * @param poseStack the pose stack
     * @param metrics the panel metrics with header text
     * @param x the left X
     * @param baseY the panel content top Y
     * @return the row index after all headers
     */
    private static int drawHeaders(Font font, MultiBufferSource.BufferSource buffers,
            PoseStack poseStack, PanelMetrics metrics, float x, float baseY) {
        int row = 0;
        if (metrics.hasLabel) {
            row += renderHeaderRow(font, buffers, poseStack, metrics.label, x, baseY, row, LABEL_COLOR);
        }
        if (metrics.hasUpgrade) {
            row += renderHeaderRow(font, buffers, poseStack, metrics.upgradeText, x, baseY, row,
                    UPGRADE_COLOR);
        }
        return row;
    }

    /**
     * Renders a single header text row (label or upgrade), vertically centered.
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
    private static int renderHeaderRow(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float baseY, int row, int color) {
        float textY = baseY + row * InWorldHud.ROW_HEIGHT
            + (InWorldHud.ROW_HEIGHT - font.lineHeight) / HALF_F;
        InWorldHud.drawText(font, buffers, poseStack, text, x, textY, color);
        return 1;
    }

    /**
     * Converts single-fluid canister content to GooContents for HUD rendering.
     * @param content the single-fluid canister content to convert
     * @return GooContents wrapping the content, or EMPTY if none
     */
    private static GooContents toGooContents(CanisterFluidContent content) {
        if (content.isEmpty()) { return GooContents.EMPTY; }
        GooType type = content.getGooType();
        if (type == null) { return GooContents.EMPTY; }
        return new GooContents(Map.of(type, content.amount()));
    }

    /** Pre-computed panel dimensions and resolved header strings. */
    private record PanelMetrics(float width, float height,
            @Nullable String label, String upgradeText,
            boolean hasLabel, boolean hasUpgrade) {
    }
}
