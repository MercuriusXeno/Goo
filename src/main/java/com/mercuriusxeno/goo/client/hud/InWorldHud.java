package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.Map;

/**
 * Shared utilities for in-world HUD billboards rendered via nine-slice backgrounds.
 * Extracted from CrucibleHudRenderer for reuse by all machine HUD renderers.
 */
public final class InWorldHud {

    /** Full white color for quad rendering. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Degrees-to-radians offset for camera yaw (faces player). */
    private static final float YAW_OFFSET = 180;
    /** Half divisor for centering calculations. */
    private static final float HALF = 2f;
    /** Default frame delta-time when no previous frame exists. */
    private static final float DEFAULT_DT = 0.016f;
    /** Nanoseconds per second for delta-time conversion. */
    private static final float NANOS_PER_SECOND = 1_000_000_000f;
    /** Maximum delta-time clamp to handle lag spikes. */
    private static final float MAX_DT = 0.1f;
    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/item/";

    /** Water bucket item texture for HUD icon. */
    private static final Identifier WATER_BUCKET_ICON =
            Identifier.withDefaultNamespace("textures/item/water_bucket.png");

    /** Lava bucket item texture for HUD icon. */
    private static final Identifier LAVA_BUCKET_ICON =
            Identifier.withDefaultNamespace("textures/item/lava_bucket.png");
    /** Texture path suffix for goo type icons. */
    private static final String ICON_PATH_SUFFIX = "_icon_bordered.png";
    /** Goo mod namespace for resource identifiers. */
    private static final String NAMESPACE_GOO = "goo";
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

    // --- Shared goo row rendering constants and methods ---

    /** Height of one HUD row (icon + text). */
    public static final float ROW_HEIGHT = 11f;

    /** Icon render size in scaled pixels. */
    public static final float ICON_SIZE = 10f;

    /** Gap between icon and text in a goo row. */
    public static final float ICON_TEXT_GAP = 2f;

    /** Default text color (white). */
    public static final int TEXT_COLOR = 0xFFFFFFFF;

    private InWorldHud() {}

    /**
     * Renders a nine-slice background using the vanilla effect_background texture.
     * The 3px border is never stretched; edges stretch in one axis; center stretches freely.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param rect the panel rectangle (position + size)
     */
    public static void renderBackground(PoseStack poseStack, MultiBufferSource buffers,
            PanelRectangle rect) {
        renderBackgroundInternal(poseStack, buffers, rect, false);
    }

    /**
     * Renders a nine-slice background without depth testing (renders on top of world).
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param rect the panel rectangle (position + size)
     */
    public static void renderBackgroundSeeThrough(PoseStack poseStack, MultiBufferSource buffers,
            PanelRectangle rect) {
        renderBackgroundInternal(poseStack, buffers, rect, true);
    }

    /**
     * Internal nine-slice background renderer with optional see-through mode.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param rect the panel rectangle (position + size)
     * @param seeThrough whether to disable depth testing
     */
    private static void renderBackgroundInternal(PoseStack poseStack, MultiBufferSource buffers,
                                                 PanelRectangle rect, boolean seeThrough) {
        VertexConsumer vc = buffers.getBuffer(
            seeThrough ? RenderTypes.textSeeThrough(BG_TEXTURE) : RenderTypes.text(BG_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float x1 = rect.x() + BORDER;
        float x2 = rect.x() + rect.w() - BORDER;
        float y1 = rect.y() + BORDER;
        float y2 = rect.y() + rect.h() - BORDER;
        emitCorners(vc, pose, rect, x1, x2, y1, y2);
        emitEdgesAndCenter(vc, pose, rect, x1, x2, y1, y2);
    }

    /**
     * Emits the four corner quads of a nine-slice background.
     * @param vc the vertex consumer for quad output
     * @param pose the pose matrix entry
     * @param rect the panel rectangle (position + size)
     * @param x1 the left edge after border inset
     * @param x2 the right edge before border inset
     * @param y1 the top edge after border inset
     * @param y2 the bottom edge before border inset
     */
    private static void emitCorners(VertexConsumer vc, PoseStack.Pose pose,
            PanelRectangle rect,
            float x1, float x2, float y1, float y2) {
        float uB = BORDER_UV;
        float x = rect.x();
        float y = rect.y();
        float xw = x + rect.w();
        float yh = y + rect.h();
        nineSliceQuad(vc, pose, x,  y,  x1, y1, 0f,    0f,    uB,    uB);
        nineSliceQuad(vc, pose, x2, y,  xw, y1, 1f-uB, 0f,    1f,    uB);
        nineSliceQuad(vc, pose, x,  y2, x1, yh, 0f,    1f-uB, uB,    1f);
        nineSliceQuad(vc, pose, x2, y2, xw, yh, 1f-uB, 1f-uB, 1f,   1f);
    }

    /**
     * Emits the four edge quads and center quad of a nine-slice background.
     * @param vc the vertex consumer for quad output
     * @param pose the pose matrix entry
     * @param rect the panel rectangle (position + size)
     * @param x1 the left edge after border inset
     * @param x2 the right edge before border inset
     * @param y1 the top edge after border inset
     * @param y2 the bottom edge before border inset
     */
    private static void emitEdgesAndCenter(VertexConsumer vc, PoseStack.Pose pose,
            PanelRectangle rect,
            float x1, float x2, float y1, float y2) {
        float uB = BORDER_UV;
        float x = rect.x();
        float y = rect.y();
        float xw = x + rect.w();
        float yh = y + rect.h();
        nineSliceQuad(vc, pose, x1, y,  x2, y1, uB,    0f,    1f-uB, uB);
        nineSliceQuad(vc, pose, x1, y2, x2, yh, uB,    1f-uB, 1f-uB, 1f);
        nineSliceQuad(vc, pose, x,  y1, x1, y2, 0f,    uB,    uB,    1f-uB);
        nineSliceQuad(vc, pose, x2, y1, xw, y2, 1f-uB, uB,    1f,    1f-uB);
        nineSliceQuad(vc, pose, x1, y1, x2, y2, uB,    uB,    1f-uB, 1f-uB);
    }

    /**
     * Emits one quad of the nine-slice background at z=0.
     *
     * @param vc the vertex consumer
     * @param pose the pose matrix entry
     * @param px0 the left X pixel coordinate
     * @param py0 the top Y pixel coordinate
     * @param px1 the right X pixel coordinate
     * @param py1 the bottom Y pixel coordinate
     * @param u0 the minimum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void nineSliceQuad(VertexConsumer vc, PoseStack.Pose pose,
            float px0, float py0, float px1, float py1,
            float u0, float v0, float u1, float v1) {
        iconVertex(vc, pose, px0, py0, 0f, u0, v0);
        iconVertex(vc, pose, px0, py1, 0f, u0, v1);
        iconVertex(vc, pose, px1, py1, 0f, u1, v1);
        iconVertex(vc, pose, px1, py0, 0f, u1, v0);
    }

    /**
     * Adds a vertex with full-bright lighting at the given depth.
     *
     * @param vc the vertex consumer
     * @param pose the pose matrix entry
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param u the U texture coordinate
     * @param v the V texture coordinate
     */
    public static void iconVertex(VertexConsumer vc, PoseStack.Pose pose,
            float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
            .setColor(OPAQUE_WHITE)
            .setUv(u, v)
            .setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    /**
     * Draws text at the content Z depth (in front of background).
     *
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param text the text string to render
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param color the ARGB color value
     */
    public static void drawText(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.NORMAL);
    }

    /**
     * Draws text without depth testing (renders on top of world).
     *
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param text the text string to render
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param color the ARGB color value
     */
    public static void drawTextSeeThrough(Font font, MultiBufferSource buffers,
            PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.SEE_THROUGH);
    }

    /**
     * Internal text renderer with configurable display mode.
     *
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param text the text string to render
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param color the ARGB color value
     * @param displayMode the font display mode
     */
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
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     * @param pitchFactor the pitch animation factor [0, 1]
     */
    public static void applyBillboardRotation(PoseStack poseStack, Camera camera,
            float pitchFactor) {
        float yaw = (float) Math.toRadians(-camera.yRot() + YAW_OFFSET);
        float pitch = (float) Math.toRadians(camera.xRot()) * pitchFactor;
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX(-pitch));
    }

    /**
     * Orients a panel flat against a block face, facing outward.
     * NORTH faces south (toward player looking north), SOUTH faces north, etc.
     * Only handles horizontal faces; UP/DOWN are handled by {@link #applyFlatRotation}.
     *
     * @param poseStack the pose stack for rendering
     * @param face the block face direction
     */
    public static void applyFaceRotation(PoseStack poseStack, Direction face) {
        float yRot = switch (face) {
            case NORTH -> (float) Math.PI;        // 180 degrees
            case SOUTH -> 0f;
            case EAST  -> (float) Math.PI / HALF;   // 90 degrees
            case WEST  -> (float) -Math.PI / HALF;  // -90 degrees
            default    -> 0f;
        };
        poseStack.mulPose(new Quaternionf().rotationY(yRot));
    }

    /**
     * Orients a panel to lie flat on the Y plane, billboarding yaw only.
     * The panel folds flat (X-rot 90) so the player looks down at it.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     */
    public static void applyFlatRotation(PoseStack poseStack, Camera camera) {
        float yaw = (float) Math.toRadians(-camera.yRot() + YAW_OFFSET);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI / HALF));
    }

    /**
     * Frame-rate-independent exponential smoothing.
     * Moves current toward target at a rate governed by time constant tau.
     *
     * @param current the current smoothed value
     * @param target the desired target value
     * @param dt the delta time in seconds
     * @param tau the time constant controlling convergence speed
     * @return the new smoothed value after one frame step
     */
    public static float smoothToward(float current, float target, float dt, float tau) {
        float factor = 1f - (float) Math.exp(-dt / tau);
        return current + (target - current) * factor;
    }

    /**
     * Renders a goo type icon + amount text row, vertically centered within
     * {@link #ROW_HEIGHT}. Used by canister and vat HUD renderers.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param amountText the formatted volume text
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public static void renderGooRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooType type, String amountText,
            float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF;
        renderIcon(poseStack, buffers, type, x, iconY);
        drawText(font, buffers, poseStack, amountText,
            x + ICON_SIZE + ICON_TEXT_GAP, textY, TEXT_COLOR);
    }

    /**
     * See-through variant of {@link #renderGooRow} that renders on top
     * of world geometry. Used for chain marker billboards.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param amountText the formatted volume text
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public static void renderGooRowSeeThrough(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooType type, String amountText,
            float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF;
        renderIconSeeThrough(poseStack, buffers, type, x, iconY);
        drawTextSeeThrough(font, buffers, poseStack, amountText,
            x + ICON_SIZE + ICON_TEXT_GAP, textY, TEXT_COLOR);
    }

    /**
     * Renders every goo type in {@code contents} as a stack of rows starting
     * at {@code baseY + startRow * ROW_HEIGHT}. Centralizes the panel-painter
     * loop so canister and vat HUDs share one iteration path.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param contents  the goo contents to render
     * @param x         the left X coordinate for each row
     * @param baseY     the panel content top Y
     * @param startRow  the first row index for goo rows
     */
    public static void renderGooRows(PoseStack poseStack, Font font,
            MultiBufferSource buffers, GooContents contents,
            float x, float baseY, int startRow) {
        int row = startRow;
        for (Map.Entry<GooType, Integer> entry : contents.getAll().entrySet()) {
            float rowY = baseY + row * ROW_HEIGHT;
            String amountText = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            renderGooRow(poseStack, font, buffers, entry.getKey(), amountText, x, rowY);
            row++;
        }
    }

    /**
     * Renders a vanilla fluid row: tinted fluid icon + formatted mB amount.
     *
     * @param poseStack the pose stack
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param fluid     the vanilla fluid
     * @param amount    the volume in mB
     * @param x         the left X coordinate
     * @param y         the Y coordinate for this row
     */
    public static void renderFluidRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, Fluid fluid, int amount,
            float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF;
        renderFluidIconSeeThrough(poseStack, buffers, fluid, x, iconY);
        String amountText = GooTooltipHandler.formatFluidDisplayCompact(amount);
        drawTextSeeThrough(font, buffers, poseStack, amountText,
                x + ICON_SIZE + ICON_TEXT_GAP, textY, TEXT_COLOR);
    }

    /**
     * Computes the row width for a vanilla fluid entry.
     *
     * @param font   the font renderer
     * @param amount the volume in mB
     * @return the row width in scaled pixels
     */
    public static float computeFluidRowWidth(Font font, int amount) {
        String text = GooTooltipHandler.formatFluidDisplayCompact(amount);
        return ICON_SIZE + ICON_TEXT_GAP + font.width(text);
    }

    /**
     * Renders a vanilla fluid bucket icon without depth testing.
     *
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param fluid     the vanilla fluid
     * @param x         the X coordinate
     * @param y         the Y coordinate
     */
    private static void renderFluidIconSeeThrough(PoseStack poseStack,
            MultiBufferSource buffers, Fluid fluid, float x, float y) {
        Identifier tex = fluid.isSame(Fluids.WATER) ? WATER_BUCKET_ICON : LAVA_BUCKET_ICON;
        VertexConsumer vc = buffers.getBuffer(RenderTypes.textSeeThrough(tex));
        PoseStack.Pose pose = poseStack.last();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        iconVertex(vc, pose, x, y, CONTENT_Z, 0f, 0f);
        iconVertex(vc, pose, x, y2, CONTENT_Z, 0f, 1f);
        iconVertex(vc, pose, x2, y2, CONTENT_Z, 1f, 1f);
        iconVertex(vc, pose, x2, y, CONTENT_Z, 1f, 0f);
    }

    /**
     * Renders a goo type icon quad at the content Z depth.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public static void renderIcon(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, float x, float y) {
        emitIconQuad(poseStack, buffers, type, x, y, false);
    }

    /**
     * Renders a goo type icon without depth testing (see-through).
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param type the goo type
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public static void renderIconSeeThrough(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, float x, float y) {
        emitIconQuad(poseStack, buffers, type, x, y, true);
    }

    /**
     * Internal icon quad emitter with configurable depth test.
     *
     * @param poseStack  the pose stack
     * @param buffers    the buffer source
     * @param type       the goo type
     * @param x          the X coordinate
     * @param y          the Y coordinate
     * @param seeThrough true to disable depth testing
     */
    private static void emitIconQuad(PoseStack poseStack, MultiBufferSource buffers,
            GooType type, float x, float y, boolean seeThrough) {
        Identifier tex = Identifier.fromNamespaceAndPath(NAMESPACE_GOO,
            ICON_PATH_PREFIX + type.getId() + ICON_PATH_SUFFIX);
        VertexConsumer vc = buffers.getBuffer(
            seeThrough ? RenderTypes.textSeeThrough(tex) : RenderTypes.text(tex));
        PoseStack.Pose pose = poseStack.last();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        iconVertex(vc, pose, x, y, CONTENT_Z, 0f, 0f);
        iconVertex(vc, pose, x, y2, CONTENT_Z, 0f, 1f);
        iconVertex(vc, pose, x2, y2, CONTENT_Z, 1f, 1f);
        iconVertex(vc, pose, x2, y, CONTENT_Z, 1f, 0f);
    }

    /**
     * Computes the widest goo row width for panel sizing.
     *
     * @param font the font renderer
     * @param contents the goo contents to measure
     * @return the width of the widest row (icon + gap + text) in scaled pixels
     */
    public static float computeMaxRowWidth(Font font, GooContents contents) {
        float max = 0;
        for (Map.Entry<GooType, Integer> entry : contents.getAll().entrySet()) {
            String text = GooTooltipHandler.formatFluidDisplayCompact(entry.getValue());
            float w = ICON_SIZE + ICON_TEXT_GAP + font.width(text);
            if (w > max) { max = w; }
        }
        return max;
    }

    /**
     * Returns the horizontal direction whose outward normal is most anti-parallel to
     * the player's look vector - i.e. the face most directly visible to the player.
     *
     * @param look the player look direction vector
     * @return the horizontal direction most directly facing the player
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
     *
     * @param lastFrameNanos single-element array storing previous frame time
     * @return delta time in seconds, clamped to {@link #MAX_DT}
     */
    public static float computeDeltaTime(long[] lastFrameNanos) {
        long now = System.nanoTime();
        float dt = (lastFrameNanos[0] == 0) ? DEFAULT_DT : (now - lastFrameNanos[0]) / NANOS_PER_SECOND;
        lastFrameNanos[0] = now;
        return Math.min(dt, MAX_DT);
    }
}
