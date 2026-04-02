package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.Map;

/**
 * Shared utilities for in-world HUD billboards rendered via nine-slice backgrounds.
 * Extracted from CrucibleHudRenderer for reuse by all machine HUD renderers.
 */
public final class InWorldHud {

    private InWorldHud() {}

    /** Scale factor: 1 pixel = 1/64 of a block. */
    public static final float PIXEL_SCALE = 1f / 64f;

    /** Border inset in scaled pixels for nine-slice rendering. */
    public static final float BORDER = 3f;

    /** Border inset as UV fraction (3px / 24px texture). */
    public static final float BORDER_UV = 3f / 24f;

    /** Z offset for content (icons, text) to sit in front of the background. */
    public static final float CONTENT_Z = 1f;

    /** Background texture: vanilla HUD effect background. */
    public static final Identifier BG_TEXTURE = Identifier.withDefaultNamespace(
        "textures/gui/sprites/hud/effect_background.png");

    /**
     * Renders a nine-slice background using the vanilla effect_background texture.
     * The 3px border is never stretched; edges stretch in one axis; center stretches freely.
     */
    public static void renderBackground(PoseStack poseStack, MultiBufferSource buffers,
            float x, float y, float w, float h) {
        renderBackgroundInternal(poseStack, buffers, x, y, w, h, false);
    }

    /** Renders a nine-slice background without depth testing (renders on top of world). */
    public static void renderBackgroundSeeThrough(PoseStack poseStack, MultiBufferSource buffers,
            float x, float y, float w, float h) {
        renderBackgroundInternal(poseStack, buffers, x, y, w, h, true);
    }

    /** Internal nine-slice background renderer with optional see-through mode. */
    private static void renderBackgroundInternal(PoseStack poseStack, MultiBufferSource buffers,
            float x, float y, float w, float h, boolean seeThrough) {
        VertexConsumer vc = buffers.getBuffer(
            seeThrough ? RenderTypes.textSeeThrough(BG_TEXTURE) : RenderTypes.text(BG_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float b = BORDER;
        float uB = BORDER_UV;
        float x1 = x + b;
        float x2 = x + w - b;
        float y1 = y + b;
        float y2 = y + h - b;
        nineSliceQuad(vc, pose, x,  y,  x1, y1, 0f,    0f,    uB,    uB);
        nineSliceQuad(vc, pose, x2, y,  x+w,y1, 1f-uB, 0f,    1f,    uB);
        nineSliceQuad(vc, pose, x,  y2, x1, y+h,0f,    1f-uB, uB,    1f);
        nineSliceQuad(vc, pose, x2, y2, x+w,y+h,1f-uB, 1f-uB, 1f,   1f);
        nineSliceQuad(vc, pose, x1, y,  x2, y1, uB,    0f,    1f-uB, uB);
        nineSliceQuad(vc, pose, x1, y2, x2, y+h,uB,    1f-uB, 1f-uB, 1f);
        nineSliceQuad(vc, pose, x,  y1, x1, y2, 0f,    uB,    uB,    1f-uB);
        nineSliceQuad(vc, pose, x2, y1, x+w,y2, 1f-uB, uB,    1f,    1f-uB);
        nineSliceQuad(vc, pose, x1, y1, x2, y2, uB,    uB,    1f-uB, 1f-uB);
    }

    /** Emits one quad of the nine-slice background at z=0. */
    public static void nineSliceQuad(VertexConsumer vc, PoseStack.Pose pose,
            float px0, float py0, float px1, float py1,
            float u0, float v0, float u1, float v1) {
        iconVertex(vc, pose, px0, py0, 0f, u0, v0);
        iconVertex(vc, pose, px0, py1, 0f, u0, v1);
        iconVertex(vc, pose, px1, py1, 0f, u1, v1);
        iconVertex(vc, pose, px1, py0, 0f, u1, v0);
    }

    /** Adds a vertex with full-bright lighting at the given depth. */
    public static void iconVertex(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
            .setColor(0xFFFFFFFF)
            .setUv(u, v)
            .setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    /** Draws text at the content Z depth (in front of background). */
    public static void drawText(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.NORMAL);
    }

    /** Draws text without depth testing (renders on top of world). */
    public static void drawTextSeeThrough(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.SEE_THROUGH);
    }

    /** Internal text renderer with configurable display mode. */
    private static void drawTextInternal(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color,
            Font.DisplayMode displayMode) {
        poseStack.pushPose();
        poseStack.translate(0, 0, CONTENT_Z);
        font.drawInBatch(text, x, y, color, false,
            poseStack.last().pose(), buffers,
            displayMode, 0, LightCoordsUtil.FULL_BRIGHT);
        poseStack.popPose();
    }

    /**
     * Applies billboard rotation so a panel faces the camera.
     * Yaw faces the camera; pitch tilts to match camera look angle, scaled by pitchFactor.
     */
    public static void applyBillboardRotation(PoseStack poseStack, Camera camera,
            float pitchFactor) {
        float yaw = (float) Math.toRadians(-camera.yRot() + 180);
        float pitch = (float) Math.toRadians(camera.xRot()) * pitchFactor;
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX(-pitch));
    }

    /**
     * Orients a panel flat against a block face, facing outward.
     * NORTH faces south (toward player looking north), SOUTH faces north, etc.
     * Only handles horizontal faces; UP/DOWN are handled by {@link #applyFlatRotation}.
     */
    public static void applyFaceRotation(PoseStack poseStack, Direction face) {
        float yRot = switch (face) {
            case NORTH -> (float) Math.PI;        // 180 degrees
            case SOUTH -> 0f;
            case EAST  -> (float) Math.PI / 2f;   // 90 degrees
            case WEST  -> (float) -Math.PI / 2f;  // -90 degrees
            default    -> 0f;
        };
        poseStack.mulPose(new Quaternionf().rotationY(yRot));
    }

    /**
     * Orients a panel to lie flat on the Y plane, billboarding yaw only.
     * The panel folds flat (X-rot 90) so the player looks down at it.
     */
    public static void applyFlatRotation(PoseStack poseStack, Camera camera) {
        float yaw = (float) Math.toRadians(-camera.yRot() + 180);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI / 2f));
    }

    /**
     * Frame-rate-independent exponential smoothing.
     * Moves current toward target at a rate governed by time constant tau.
     */
    public static float smoothToward(float current, float target, float dt, float tau) {
        float factor = 1f - (float) Math.exp(-dt / tau);
        return current + (target - current) * factor;
    }

    // --- Shared goo row rendering constants and methods ---

    /** Height of one HUD row (icon + text). */
    public static final float ROW_HEIGHT = 11f;

    /** Icon render size in scaled pixels. */
    public static final float ICON_SIZE = 10f;

    /** Gap between icon and text in a goo row. */
    public static final float ICON_TEXT_GAP = 2f;

    /** Default text color (white). */
    public static final int TEXT_COLOR = 0xFFFFFFFF;

    /**
     * Renders a goo type icon + amount text row, vertically centered within
     * {@link #ROW_HEIGHT}. Used by canister and vat HUD renderers.
     */
    public static void renderGooRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooType type, String amountText,
            float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / 2f;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / 2f;
        renderIcon(poseStack, buffers, type, x, iconY);
        drawText(font, buffers, poseStack, amountText,
            x + ICON_SIZE + ICON_TEXT_GAP, textY, TEXT_COLOR);
    }

    /** Renders a goo type icon quad at the content Z depth. */
    public static void renderIcon(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, float x, float y) {
        Identifier tex = Identifier.fromNamespaceAndPath("goo",
            "textures/item/" + type.getId() + "_icon_bordered.png");
        VertexConsumer vc = buffers.getBuffer(RenderTypes.text(tex));
        PoseStack.Pose pose = poseStack.last();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        iconVertex(vc, pose, x, y, CONTENT_Z, 0f, 0f);
        iconVertex(vc, pose, x, y2, CONTENT_Z, 0f, 1f);
        iconVertex(vc, pose, x2, y2, CONTENT_Z, 1f, 1f);
        iconVertex(vc, pose, x2, y, CONTENT_Z, 1f, 0f);
    }

    /** Computes the widest goo row width for panel sizing. */
    public static float computeMaxRowWidth(Font font, GooContents contents) {
        float max = 0;
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            String text = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            float w = ICON_SIZE + ICON_TEXT_GAP + font.width(text);
            if (w > max) max = w;
        }
        return max;
    }

    /**
     * Returns the horizontal direction whose outward normal is most anti-parallel to
     * the player's look vector - i.e. the face most directly visible to the player.
     */
    public static Direction bestPerpendicularFace(Vec3 look) {
        double ax = Math.abs(look.x);
        double az = Math.abs(look.z);
        if (ax > az) {
            return look.x > 0 ? Direction.WEST : Direction.EAST;
        }
        return look.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * Computes frame delta-time in seconds from System.nanoTime().
     * The caller provides a single-element array that persists across frames.
     * Clamps to 0.1s to handle first-frame and lag spikes.
     */
    public static float computeDeltaTime(long[] lastFrameNanos) {
        long now = System.nanoTime();
        float dt = (lastFrameNanos[0] == 0) ? 0.016f : (now - lastFrameNanos[0]) / 1_000_000_000f;
        lastFrameNanos[0] = now;
        return Math.min(dt, 0.1f);
    }
}
