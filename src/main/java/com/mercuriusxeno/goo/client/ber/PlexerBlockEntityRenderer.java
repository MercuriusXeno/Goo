package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.PlexerBlock;
import com.mercuriusxeno.goo.block.PlexerBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the reconstitution target item floating in the plexer cutaway.
 * Three visual states:
 * - Target set, not aiming to replace: slightly faded translucent ghost
 * - Empty cutaway, aiming with valid item: breathing pulse ghost
 * - Different target, aiming with valid item: crossfade between current and ghost
 */
public class PlexerBlockEntityRenderer
        implements BlockEntityRenderer<PlexerBlockEntity, PlexerRenderState> {

    /** Block center on X/Z axes (rotation pivot). */
    private static final float BLOCK_CENTER = 0.5f;

    /** Cutaway center Y in model space: midpoint of [8,13]. */
    private static final float CUTAWAY_Y = 10.5f / 16f;

    /** Z offset from block center to cutaway center (2/16 - 8/16). */
    private static final float CUTAWAY_Z_OFFSET = -6f / 16f;

    /** Item scale - roughly 5px in a 16px space. */
    private static final float ITEM_SCALE = 0.3f;

    /** Alpha for a target that is set and not being aimed at (slightly faded). */
    private static final int SETTLED_ALPHA = 210;

    /** Oscillation speed for the breathing pulse (radians per tick). */
    private static final float BREATHE_SPEED = 0.15f;

    /** Low end of the breathing pulse alpha range. */
    private static final int BREATHE_ALPHA_LOW = 80;

    /** High end of the breathing pulse alpha range. */
    private static final int BREATHE_ALPHA_HIGH = 170;

    /** Crossfade oscillation speed (radians per tick). */
    private static final float CROSSFADE_SPEED = 0.12f;

    /** Maximum alpha during crossfade. */
    private static final int CROSSFADE_ALPHA_MAX = 200;

    /** Midpoint of the oscillation cycle: old fades out below, new fades in above. */
    private static final float CROSSFADE_MIDPOINT = 0.5f;

    /** Divisor for normalizing sine output to 0-1 range. */
    private static final float SINE_RANGE_DIVISOR = 2f;

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState targetRenderState = new ItemStackRenderState();
    private final ItemStackRenderState ghostRenderState = new ItemStackRenderState();

    /**
     * @param context the renderer provider context
     */
    public PlexerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PlexerRenderState createRenderState() {
        return new PlexerRenderState();
    }

    /**
     * Snapshots the target item, facing, aim state, and held item from the
     * block entity and local player.
     *
     * @param be            the block entity instance
     * @param state         the render state to populate
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(PlexerBlockEntity be, PlexerRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.targetItem = be.getTargetItem();
        state.facing = be.getBlockState().getValue(PlexerBlock.FACING);
        extractAimState(be, state, partialTick);
    }

    /**
     * Captures the local player's aim and held item into the render state.
     *
     * @param be          the block entity to check cutaway hits against
     * @param state       the render state to populate
     * @param partialTick the partial tick for animation timing
     */
    private static void extractAimState(PlexerBlockEntity be,
            PlexerRenderState state, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        state.aimingAtCutaway = false;
        state.heldItem = ItemStack.EMPTY;
        state.heldItemValid = false;
        state.gameTime = (mc.level != null ? mc.level.getGameTime() : 0) + partialTick;
        if (mc.player == null) { return; }

        state.aimingAtCutaway = isCutawayAimed(mc, be);
        extractHeldItem(mc, be, state);
    }

    /**
     * Returns true if the player's crosshair is on this plexer's cutaway.
     *
     * @param mc the minecraft instance
     * @param be the plexer block entity
     * @return true if aiming at cutaway
     */
    private static boolean isCutawayAimed(Minecraft mc, PlexerBlockEntity be) {
        HitResult hit = mc.hitResult;
        return hit instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(be.getBlockPos())
                && be.isCutawayHit(bhr);
    }

    /**
     * Captures the player's held item if it is a valid reconstitution target.
     *
     * @param mc    the minecraft instance
     * @param be    the plexer block entity for validation
     * @param state the render state to populate
     */
    private static void extractHeldItem(Minecraft mc, PlexerBlockEntity be,
            PlexerRenderState state) {
        ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty() && be.isValidTarget(held)) {
            state.heldItem = cleanForPreview(held);
            state.heldItemValid = true;
        }
    }

    /**
     * Creates a clean single-count copy for ghost rendering, stripping
     * fluid and metadata from canisters so the preview matches what
     * the plexer would actually store as its target.
     *
     * @param stack the held item stack
     * @return a cleaned copy suitable for preview comparison and rendering
     */
    private static ItemStack cleanForPreview(ItemStack stack) {
        ItemStack clean = new ItemStack(stack.getItem(), 1);
        if (stack.getItem() instanceof CanisterItem) {
            CanisterItem.setFluidContent(clean, CanisterFluidContent.EMPTY);
            CanisterItem.setMetadata(clean, CanisterMetadata.EMPTY);
        }
        return clean;
    }

    /**
     * Dispatches to the appropriate rendering mode based on target and aim state.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        boolean hasTarget = !state.targetItem.isEmpty();
        boolean showGhost = state.aimingAtCutaway && state.heldItemValid
                && !ItemStack.isSameItemSameComponents(state.heldItem, state.targetItem);

        if (hasTarget && showGhost) {
            submitCrossfade(state, poseStack, nodeCollector);
        } else if (hasTarget) {
            submitSettledTarget(state, poseStack, nodeCollector);
        } else if (showGhost) {
            submitBreathingGhost(state, poseStack, nodeCollector);
        }
    }

    /**
     * Target is set and player is not aiming to replace - slightly faded ghost.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     */
    private void submitSettledTarget(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        if (!resolveModel(targetRenderState, state.targetItem)) { return; }
        submitItemAtCutaway(targetRenderState, state, poseStack, nodeCollector, SETTLED_ALPHA);
    }

    /**
     * Empty cutaway, aiming with valid item - breathing pulse between low and
     * high alpha.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     */
    private void submitBreathingGhost(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        if (!resolveModel(ghostRenderState, state.heldItem)) { return; }
        int alpha = oscillateAlpha(state.gameTime, BREATHE_SPEED,
                BREATHE_ALPHA_LOW, BREATHE_ALPHA_HIGH);
        submitItemAtCutaway(ghostRenderState, state, poseStack, nodeCollector, alpha);
    }

    /**
     * Different target set, aiming with valid item: sequential crossfade.
     * First half fades out the current target, second half fades in the ghost.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     */
    private void submitCrossfade(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float t = oscillateT(state.gameTime, CROSSFADE_SPEED);

        if (t < CROSSFADE_MIDPOINT) {
            float fade = 1f - (t / CROSSFADE_MIDPOINT);
            int alpha = Math.round(fade * CROSSFADE_ALPHA_MAX);
            if (alpha > 0 && resolveModel(targetRenderState, state.targetItem)) {
                submitItemAtCutaway(targetRenderState, state, poseStack, nodeCollector, alpha);
            }
        } else {
            float fade = (t - CROSSFADE_MIDPOINT) / CROSSFADE_MIDPOINT;
            int alpha = Math.round(fade * CROSSFADE_ALPHA_MAX);
            if (alpha > 0 && resolveModel(ghostRenderState, state.heldItem)) {
                submitItemAtCutaway(ghostRenderState, state, poseStack, nodeCollector, alpha);
            }
        }
    }

    /**
     * Submits an item render state at the cutaway position with the given alpha.
     *
     * @param renderState   the resolved item render state
     * @param state         the plexer render state for facing/light
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param alpha         the alpha value 0-255
     */
    private void submitItemAtCutaway(ItemStackRenderState renderState,
            PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int alpha) {
        TranslucentItemCollector proxy = new TranslucentItemCollector(nodeCollector, alpha);
        poseStack.pushPose();
        translateToCutaway(poseStack, state.facing);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        renderState.submit(poseStack, proxy, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * Resolves an item stack into the given render state.
     *
     * @param renderState the render state to populate
     * @param stack       the item stack to resolve
     * @return true if the model resolved successfully
     */
    private boolean resolveModel(ItemStackRenderState renderState, ItemStack stack) {
        itemModelResolver.updateForTopItem(
                renderState, stack, ItemDisplayContext.FIXED, null, null, 0);
        return !renderState.isEmpty();
    }

    /**
     * Returns a 0-1 oscillation value from game time and speed.
     *
     * @param gameTime the game time in ticks plus partial
     * @param speed    the oscillation speed in radians per tick
     * @return the oscillation value between 0 and 1
     */
    private static float oscillateT(float gameTime, float speed) {
        return (float) (Math.sin(gameTime * speed) + 1f) / SINE_RANGE_DIVISOR;
    }

    /**
     * Returns an alpha value oscillating between min and max.
     *
     * @param gameTime the game time in ticks plus partial
     * @param speed    the oscillation speed in radians per tick
     * @param min      the minimum alpha value
     * @param max      the maximum alpha value
     * @return the interpolated alpha value
     */
    private static int oscillateAlpha(float gameTime, float speed, int min, int max) {
        return lerpAlpha(oscillateT(gameTime, speed), min, max);
    }

    /**
     * Linearly interpolates between min and max by factor t.
     *
     * @param t   the interpolation factor 0-1
     * @param min the minimum value
     * @param max the maximum value
     * @return the interpolated value
     */
    private static int lerpAlpha(float t, int min, int max) {
        return min + Math.round(t * (max - min));
    }

    /**
     * Translates the pose stack to the cutaway center for the given facing.
     *
     * @param poseStack the pose stack
     * @param facing    the block facing direction
     */
    private static void translateToCutaway(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, CUTAWAY_Y, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0f, 0f, CUTAWAY_Z_OFFSET);
    }
}
