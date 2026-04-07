package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.CrucibleBlock;
import com.mercuriusxeno.goo.block.CrucibleBlockEntity;
import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Renders a compact in-world HUD panel when the player's crosshair targets
 * a crucible's basin. The panel sits on the basin rim at the point farthest
 * from the player and billboards to face the camera. Each goo type shows as
 * an icon with "reservoir / total" volumes.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrucibleHudRenderer {
    /** Height of one row (icon + text line). */
    private static final float ROW_HEIGHT = 11f;
    /** Icon render size in scaled pixels (matches the 10x10 tooltip icons). */
    private static final float ICON_SIZE = 10f;
    /** Gap between icon and text. */
    private static final float ICON_TEXT_GAP = 2f;

    // Background texture delegated to InWorldHud.BG_TEXTURE
    /** Volume text color (white). */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Separator color (dim gray). */
    private static final int SEP_COLOR = 0xFF888888;
    /** Fuel text color (orange, matching the blaze rod bar). */
    private static final int FUEL_COLOR = 0xFFFF6600;
    /** Number of depleted blaze rod texture stages (0 = most depleted, 7 = fullest). */
    private static final int BLAZE_ROD_STAGES = 8;

    /** Exponential smoothing time constant in seconds. Lower = snappier. */
    private static final float SMOOTH_TAU = 0.1f;

    /** Pitch threshold (radians) below which the retracting panel is considered flush. */
    private static final float RETRACT_THRESHOLD = 0.01f;

    /** Y threshold in block-local coords: below this is the fuel rod area, not the basin. */
    private static final double BASIN_MIN_Y = 10.0 / 16.0;

    /** Basin top Y in block-local coords (top of the basin walls). */
    private static final double BASIN_TOP_Y = 1.0;

    /** How far above the basin top to lift the panel anchor (in blocks). */
    private static final double HUD_LIFT = 0.05;

    /**
     * Cosine threshold for side-preference bias. When the camera's forward
     * vector aligns with a cardinal axis within this angle (~25 degrees),
     * side midpoints get a score bonus over corners. cos(25) ~ 0.906.
     */
    private static final float SIDE_BIAS_COS_THRESHOLD = 0.906f;

    /** Score bonus given to side midpoints when look vector is near-cardinal. */
    private static final float SIDE_BIAS_BONUS = 0.5f;

    /** Z-nudge for panel to prevent z-fighting on the basin rim. */
    private static final double RIM_Z_NUDGE = -0.01;

    /** Below-rim sign for selecting nearest rim point. */
    private static final float BELOW_RIM_SIGN = -1f;

    /** Divisor for centering calculations. */
    private static final int HALF = 2;

    /** Half-width divisor for panel centering. */
    private static final float HALF_F = 2f;

    /** Ticks per second for fuel time conversion. */
    private static final float TICKS_PER_SECOND = 20f;

    /** Fuel display threshold: show integer seconds above this. */
    private static final float FUEL_INT_THRESHOLD = 10f;

    /** Format string for integer-second fuel display. */
    private static final String FUEL_INT_FORMAT = "%.0fs";

    /** Format string for decimal-second fuel display. */
    private static final String FUEL_DEC_FORMAT = "%.1fs";

    /** Separator between reservoir and total volumes. */
    private static final String VOLUME_SEPARATOR = " / ";

    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/item/";

    /** Texture path suffix for bordered goo type icons. */
    private static final String ICON_PATH_SUFFIX = "_icon_bordered.png";

    /** Texture path prefix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_PREFIX = "textures/item/depleted_blaze_rod_";

    /** Texture path suffix for depleted blaze rod stages. */
    private static final String BLAZE_ROD_SUFFIX = ".png";

    /**
     * 8 rim anchor points on the basin top, in block-local XZ coords.
     * 4 corners + 4 side midpoints. Y is always BASIN_TOP_Y + HUD_LIFT.
     * The isSide flag distinguishes midpoints from corners for bias scoring.
     */
    private static final RimPoint[] RIM_POINTS = {
        new RimPoint(1f / 16f,  1f / 16f,  false),  // NW corner
        new RimPoint(15f / 16f, 1f / 16f,  false),  // NE corner
        new RimPoint(1f / 16f,  15f / 16f, false),   // SW corner
        new RimPoint(15f / 16f, 15f / 16f, false),   // SE corner
        new RimPoint(8f / 16f,  1f / 16f,  true),    // N midpoint
        new RimPoint(8f / 16f,  15f / 16f, true),    // S midpoint
        new RimPoint(1f / 16f,  8f / 16f,  true),    // W midpoint
        new RimPoint(15f / 16f, 8f / 16f,  true),    // E midpoint
    };

    // --- Animation state (static, persists across frames) ---

    /** Block position currently showing the HUD, or null if idle. */
    private static @Nullable BlockPos trackedPos;

    /** Smoothed pitch angle in radians (0 = flush with rim, positive = tilted toward camera). */
    private static float currentPitch;

    /** True when crosshair has left and the panel is animating back to flush. */
    private static boolean retracting;

    /** System.nanoTime() of the last frame, for delta-time calculation. */
    private static final long[] lastFrameNanos = {0};

    private CrucibleHudRenderer() {}

    /**
     * Renders the crucible HUD after entities are drawn.
     * Drives the state machine and dispatches rendering when active.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos target = getTargetPos();
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);

        updateState(target, dt);

        if (trackedPos == null) { return; }

        CrucibleBlockEntity be = lookupCrucible(trackedPos);
        if (be == null) {
            clearState();
            return;
        }

        renderRimPanel(event.getPoseStack(), be, camera);
    }

    /**
     * State machine: manages transitions between idle, emerging, and retracting.
     * Updates currentPitch each frame via exponential smoothing.
     *
     * @param target the current aim target
     * @param dt the delta time in seconds
     */
    private static void updateState(@Nullable BlockPos target, float dt) {
        boolean hasTarget = target != null;
        boolean sameTarget = hasTarget && target.equals(trackedPos);

        if (hasTarget && !sameTarget) {
            beginEmerge(target);
        } else if (hasTarget && retracting) {
            retracting = false;
        } else if (!hasTarget && trackedPos != null && !retracting) {
            retracting = true;
        }

        advancePitch(dt);
    }

    /**
     * Advances the pitch toward the target value and completes retract if flush.
     *
     * @param dt the delta time in seconds
     */
    private static void advancePitch(float dt) {
        if (trackedPos == null) { return; }

        float targetPitch = retracting ? 0f : 1f;
        currentPitch = InWorldHud.smoothToward(currentPitch, targetPitch, dt, SMOOTH_TAU);

        if (retracting && currentPitch < RETRACT_THRESHOLD) {
            clearState();
        }
    }

    /**
     * Initializes state for a new emerge animation on the given block.
     *
     * @param pos the block position
     */
    private static void beginEmerge(BlockPos pos) {
        trackedPos = pos;
        currentPitch = 0f;
        retracting = false;
    }

    /** Resets all state to idle. */
    private static void clearState() {
        trackedPos = null;
        currentPitch = 0f;
        retracting = false;
    }

    // smoothToward delegated to InWorldHud.smoothToward

    /**
     * Returns the block position of the targeted crucible basin, or null.
     * Only returns a target when the crosshair hits the basin portion (Y >= 10/16),
     * not the fuel rod area below.
     *
     * @return the targetPos
     */
    private static @Nullable BlockPos getTargetPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) { return null; }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) { return null; }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof CrucibleBlock)) { return null; }
        if (hitsBelowBasin(hit, pos)) { return null; }
        return pos;
    }

    /**
     * Returns true if the hit location is below the basin floor (fuel rod area).
     *
     * @param hit the block hit result
     * @param pos the block position
     * @return true if the condition is met
     */
    private static boolean hitsBelowBasin(BlockHitResult hit, BlockPos pos) {
        double localY = hit.getLocation().y - pos.getY();
        return localY < BASIN_MIN_Y;
    }

    /**
     * Looks up the CrucibleBlockEntity at the given position, or null.
     *
     * @param pos the block position
     * @return the crucible, or null if not found
     */
    private static @Nullable CrucibleBlockEntity lookupCrucible(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) { return null; }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CrucibleBlockEntity cbe ? cbe : null;
    }

    /**
     * Renders the HUD panel on the basin rim at the farthest point from the camera.
     * Billboards to face the player with smoothed emerge animation.
     *
     * @param poseStack the pose stack for rendering
     * @param be the block entity instance
     * @param camera the render camera
     */
    private static void renderRimPanel(PoseStack poseStack, CrucibleBlockEntity be,
            Camera camera) {
        Vec3 cam = camera.position();
        BlockPos pos = be.getBlockPos();

        poseStack.pushPose();
        translateToRimPoint(poseStack, pos, cam, camera);
        applyBillboardRotation(poseStack, camera);
        poseStack.translate(0, 0, RIM_Z_NUDGE);
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);

        renderPanel(poseStack, be);
        poseStack.popPose();
    }

    /**
     * Translates the pose stack to the farthest rim point, lifted above the basin,
     * relative to camera position. The point is selected from 8 candidates
     * (4 corners + 4 side midpoints) with look-alignment bias.
     *
     * @param poseStack the pose stack for rendering
     * @param pos the block position
     * @param cam the cam
     * @param camera the render camera
     */
    private static void translateToRimPoint(PoseStack poseStack, BlockPos pos,
            Vec3 cam, Camera camera) {
        RimPoint rp = selectBestRimPoint(pos, camera);
        double rx = pos.getX() + rp.x() - cam.x;
        double ry = pos.getY() + BASIN_TOP_Y + HUD_LIFT - cam.y;
        double rz = pos.getZ() + rp.z() - cam.z;
        poseStack.translate(rx, ry, rz);
    }

    /**
     * Selects the best rim point for the HUD panel. When the camera is above
     * the basin rim, picks the farthest point (behind the basin). When below,
     * picks the nearest point (in front) so the panel isn't hidden by the walls.
     * Side midpoints get a bias when the look vector is near-cardinal.
     *
     * @param pos the block position
     * @param camera the render camera
     * @return the selected result
     */
    private static RimPoint selectBestRimPoint(BlockPos pos, Camera camera) {
        float yawRad = (float) Math.toRadians(camera.yRot());
        float forwardX = (float) -Math.sin(yawRad);
        float forwardZ = (float) Math.cos(yawRad);
        Vec3 cam = camera.position();
        boolean nearCardinal = isNearCardinal(forwardX, forwardZ);
        boolean belowRim = cam.y < pos.getY() + BASIN_TOP_Y;

        float bestScore = Float.NEGATIVE_INFINITY;
        RimPoint best = RIM_POINTS[0];

        float sign = belowRim ? BELOW_RIM_SIGN : 1f;

        for (RimPoint rp : RIM_POINTS) {
            float score = scoreRimPoint(rp, pos, cam, forwardX, forwardZ, nearCardinal, sign);
            if (score > bestScore) {
                bestScore = score;
                best = rp;
            }
        }
        return best;
    }

    /**
     * Returns true if the camera's forward XZ vector is within the bias threshold
     * of a cardinal axis (N/S/E/W), meaning the player is looking roughly straight
     * along one axis.
     *
     * @param forwardX the forwardX
     * @param forwardZ the forwardZ
     * @return true if nearCardinal
     */
    private static boolean isNearCardinal(float forwardX, float forwardZ) {
        float absX = Math.abs(forwardX);
        float absZ = Math.abs(forwardZ);
        float dominant = Math.max(absX, absZ);
        return dominant >= SIDE_BIAS_COS_THRESHOLD;
    }

    /**
     * Scores a rim point candidate. Base score is the dot product of the
     * camera-to-point vector with the camera forward direction, multiplied by
     * sign (+1 = farthest wins, -1 = nearest wins). Side midpoints receive a
     * bias bonus in the same direction.
     *
     * @param rp the rp
     * @param pos the block position
     * @param cam the cam
     * @param forwardX the forwardX
     * @param forwardZ the forwardZ
     * @param nearCardinal the nearCardinal
     * @param sign the sign
     * @return the computed score
     */
    private static float scoreRimPoint(RimPoint rp, BlockPos pos, Vec3 cam,
            float forwardX, float forwardZ, boolean nearCardinal, float sign) {
        float dx = pos.getX() + rp.x() - (float) cam.x;
        float dz = pos.getZ() + rp.z() - (float) cam.z;
        float score = (dx * forwardX + dz * forwardZ) * sign;
        if (nearCardinal && rp.isSide()) {
            score += SIDE_BIAS_BONUS;
        }
        return score;
    }

    /**
     * Applies billboard rotation, delegating to InWorldHud with current pitch.
     *
     * @param poseStack the pose stack for rendering
     * @param camera the render camera
     */
    private static void applyBillboardRotation(PoseStack poseStack, Camera camera) {
        InWorldHud.applyBillboardRotation(poseStack, camera, currentPitch);
    }

    /**
     * Renders the compact panel: optional gasket partner row, one row per goo type,
     * plus an optional fuel row.
     * Goo rows: {icon} reservoir / total. Fuel row: {blaze rod icon} ##.#s
     *
     * @param poseStack the pose stack for rendering
     * @param be the block entity instance
     */
    private static void renderPanel(PoseStack poseStack, CrucibleBlockEntity be) {
        GooContents reservoir = be.getReservoir();
        GooContents pool = getPoolContents(be);
        boolean hasFuel = !be.getFuelRod().isEmpty();
        boolean hasGoo = !reservoir.isEmpty() || !pool.isEmpty();
        if (!hasGoo && !hasFuel) { return; }

        PanelLayout layout = measurePanelLayout(reservoir, pool, be.getFuelRod(), hasGoo, hasFuel);

        MultiBufferSource.BufferSource buffers =
            Minecraft.getInstance().renderBuffers().bufferSource();

        float halfW = layout.panelWidth / HALF_F;
        InWorldHud.renderBackground(poseStack, buffers, -halfW, -layout.panelHeight,
            layout.panelWidth, layout.panelHeight);

        float contentX = -halfW + InWorldHud.BORDER;
        float contentY = -layout.panelHeight + InWorldHud.BORDER;

        renderPanelContent(poseStack, buffers, reservoir, layout.total, layout.types,
            be.getFuelRod(), hasGoo, hasFuel, contentX, contentY);
        buffers.endBatch();
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
        Font font = Minecraft.getInstance().font;
        float gooWidth = hasGoo ? measureMaxRowWidth(font, reservoir, total, types) : 0;
        float fuelWidth = hasFuel ? measureFuelRowWidth(font, fuelRod) : 0;
        float contentWidth = Math.max(gooWidth, fuelWidth);
        int rowCount = types.size() + (hasFuel ? 1 : 0);
        float panelWidth = contentWidth + InWorldHud.BORDER * HALF;
        float panelHeight = InWorldHud.BORDER * HALF + rowCount * ROW_HEIGHT;
        return new PanelLayout(panelWidth, panelHeight, total, types);
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
        int row = 0;
        if (hasGoo) {
            float gooY = contentY + row * ROW_HEIGHT;
            renderRows(poseStack, font, buffers, reservoir, total, types, contentX, gooY);
            row += types.size();
        }
        if (hasFuel) {
            float fuelY = contentY + row * ROW_HEIGHT;
            renderFuelRow(poseStack, font, buffers, fuelRod, contentX, fuelY);
        }
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
        return ICON_SIZE + ICON_TEXT_GAP + maxW;
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
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF_F;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF_F;
        renderTexturedQuad(poseStack, buffers, iconTexture(type), x, iconY);
        float textX = x + ICON_SIZE + ICON_TEXT_GAP;
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
        String sep = VOLUME_SEPARATOR;
        String totText = GooTooltipHandler.formatFluidDisplayCompact(totalVol);
        float cx = x;
        InWorldHud.drawText(font, buffers, poseStack, resText, cx, y, TEXT_COLOR);
        cx += font.width(resText);
        InWorldHud.drawText(font, buffers, poseStack, sep, cx, y, SEP_COLOR);
        cx += font.width(sep);
        InWorldHud.drawText(font, buffers, poseStack, totText, cx, y, TEXT_COLOR);
    }

    /**
     * Measures the width of the fuel row: blaze rod icon + seconds text.
     *
     * @param font the font renderer
     * @param fuelRod the fuel rod item stack
     * @return the measured width in pixels
     */
    private static float measureFuelRowWidth(Font font, ItemStack fuelRod) {
        String text = formatFuelSeconds(fuelRod);
        return ICON_SIZE + ICON_TEXT_GAP + font.width(text);
    }

    /**
     * Renders the fuel row: depleted blaze rod icon + remaining seconds, both vertically centered.
     *
     * @param poseStack the pose stack for rendering
     * @param font the font renderer
     * @param buffers the buffer source for rendering
     * @param fuelRod the fuel rod item stack
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private static void renderFuelRow(PoseStack poseStack, Font font,
            MultiBufferSource buffers, ItemStack fuelRod, float x, float y) {
        float iconY = y + (ROW_HEIGHT - ICON_SIZE) / HALF_F;
        float textY = y + (ROW_HEIGHT - font.lineHeight) / HALF_F;
        Identifier tex = blazeRodTexture(fuelRod);
        renderTexturedQuad(poseStack, buffers, tex, x, iconY);
        String text = formatFuelSeconds(fuelRod);
        float textX = x + ICON_SIZE + ICON_TEXT_GAP;
        InWorldHud.drawText(font, buffers, poseStack, text, textX, textY, FUEL_COLOR);
    }

    /**
     * Formats fuel remaining as seconds with one decimal: "42.3s".
     *
     * @param fuelRod the fuel rod item stack
     * @return the formatted string
     */
    private static String formatFuelSeconds(ItemStack fuelRod) {
        int ticks = fuelTicksRemaining(fuelRod);
        float seconds = ticks / TICKS_PER_SECOND;
        if (seconds >= FUEL_INT_THRESHOLD) { return String.format(FUEL_INT_FORMAT, seconds); }
        return String.format(FUEL_DEC_FORMAT, seconds);
    }

    /**
     * Returns fuel ticks remaining: full 1200 for a vanilla blaze rod, else from data component.
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static int fuelTicksRemaining(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return DepletedBlazeRodItem.FULL_FUEL_TICKS; }
        return DepletedBlazeRodItem.getTicksRemaining(fuelRod);
    }

    /**
     * Returns the appropriate depleted blaze rod texture for the current fuel level.
     *
     * @param fuelRod the fuel rod item stack
     * @return the blaze rod texture identifier
     */
    private static Identifier blazeRodTexture(ItemStack fuelRod) {
        float fraction = fuelFraction(fuelRod);
        int stage = Math.min((int) (fraction * BLAZE_ROD_STAGES), BLAZE_ROD_STAGES - 1);
        return Identifier.fromNamespaceAndPath(Goo.MODID,
            BLAZE_ROD_PREFIX + stage + BLAZE_ROD_SUFFIX);
    }

    /**
     * Computes fuel fraction (0.0 = depleted, 1.0 = fresh).
     *
     * @param fuelRod the fuel rod item stack
     * @return the result
     */
    private static float fuelFraction(ItemStack fuelRod) {
        if (fuelRod.is(Items.BLAZE_ROD)) { return 1f; }
        int remaining = DepletedBlazeRodItem.getTicksRemaining(fuelRod);
        return (float) remaining / DepletedBlazeRodItem.FULL_FUEL_TICKS;
    }

    /**
     * Returns the texture Identifier for a goo type's item icon.
     *
     * @param type the goo type
     * @return the icon texture identifier
     */
    private static Identifier iconTexture(GooType type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID,
            ICON_PATH_PREFIX + type.getId() + ICON_PATH_SUFFIX);
    }

    /**
     * Renders a textured quad (ICON_SIZE x ICON_SIZE) in world space.
     * Uses textSeeThrough so icons respect the same depth as text and background.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers the buffer source for rendering
     * @param tex the texture identifier
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    private static void renderTexturedQuad(PoseStack poseStack, MultiBufferSource buffers,
            Identifier tex, float x, float y) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.text(tex));
        PoseStack.Pose pose = poseStack.last();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        InWorldHud.iconVertex(vc, pose, x, y, InWorldHud.CONTENT_Z, 0f, 0f);
        InWorldHud.iconVertex(vc, pose, x, y2, InWorldHud.CONTENT_Z, 0f, 1f);
        InWorldHud.iconVertex(vc, pose, x2, y2, InWorldHud.CONTENT_Z, 1f, 1f);
        InWorldHud.iconVertex(vc, pose, x2, y, InWorldHud.CONTENT_Z, 1f, 0f);
    }


    /** Pre-computed panel dimensions and merged contents for rendering. */
    private record PanelLayout(float panelWidth, float panelHeight,
            GooContents total, Set<GooType> types) {
    }

    /**
     * A candidate anchor point on the basin rim.
     * @param x block-local X coordinate
     * @param z block-local Z coordinate
     * @param isSide true for side midpoints, false for corners
     */
    private record RimPoint(float x, float z, boolean isSide) {
    }
}
