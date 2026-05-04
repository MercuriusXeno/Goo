package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Renders the output canister in the reactor's front hollow and the
 * spinning mixing wheels on the east/west faces.
 */
public class ReactorBlockEntityRenderer
        implements BlockEntityRenderer<ReactorBlockEntity, ReactorRenderState> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Reactor texture for wheel sprite lookup. */
    private static final Identifier REACTOR_TEXTURE =
            Identifier.fromNamespaceAndPath("goo", "textures/block/reactor.png");

    /** Canister body side texture. */
    private static final Identifier CANISTER_SIDE =
            Identifier.fromNamespaceAndPath("goo", "textures/block/canister_side.png");

    /** Copper endcap texture. */
    private static final Identifier COPPER_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /** Block center for rotation pivot. */
    private static final float BLOCK_CENTER = 0.5f;

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Hollow center in model space (south-facing). */
    private static final float HOLLOW_CX = 8f / 16f;
    /** Hollow center Z in model space: center of [0..6]. */
    private static final float HOLLOW_CZ = 3f / 16f;

    /** Bottom of canister in hollow (1px from block bottom). */
    private static final float GASKET_BOT = 1f / 16f;
    /** Top of lower gasket / bottom of body. */
    private static final float BODY_BOT = 2f / 16f;
    /** Top of body / bottom of upper gasket. 10px of body. */
    private static final float BODY_TOP = 12f / 16f;
    /** Top of upper gasket (1px gasket above body, 12px hollow ceiling). */
    private static final float GASKET_TOP = 13f / 16f;

    /** Fluid inset from canister walls. */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Shared fluid geometry for the reactor canister slot. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
            new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

    /** Body side U range: 4px / 16px. */
    private static final float BODY_U1 = 0.25f;
    /** Body side V range: 10px / 16px (matches the 10px body height). */
    private static final float BODY_V1 = 10f / 16f;

    /** Gasket side U start. */
    private static final float GS_U0 = 0.25f;
    /** Gasket side U end. */
    private static final float GS_U1 = 0.5f;
    /** Gasket side V end. */
    private static final float GS_V1 = 0.0625f;

    /** Wheel center Y and Z in block space (center of 3-13 range). */
    private static final float WHEEL_CENTER = 8f / 16f;

    /** Wheel radius: 5px (spans 3 to 13). */
    private static final float WHEEL_RADIUS = 5f / 16f;

    /** Wheel inset from the block face (model units, then converted).
     * Half the body's 0.01 inset so the wheel sits between the block
     * boundary (where neighbor faces live) and the body's outer face,
     * z-fighting neither. */
    private static final float WHEEL_INSET = 0.005f / 16f;

    /** West wheel X position. */
    private static final float WHEEL_WEST_X = WHEEL_INSET;

    /** East wheel X position. */
    private static final float WHEEL_EAST_X = 1f - WHEEL_INSET;

    /** Model UV space size declared by reactor.json (texture_size: [32, 32]). */
    private static final float TEX_SIZE = 32f;

    /** Sprite A: corner-based highlights at 1:30, 12, 10:30 of the cog.
     * Texture pixels (48,12)-(58,22); in 32-unit declared space
     * u 24-29, v 6-11. Shown for phase [0, 22.5) at display = phase
     * and again for phase [67.5, 90) at display = phase - 90. */
    private static final float WHEEL_A_U0 = 24f / TEX_SIZE;
    private static final float WHEEL_A_V0 = 6f / TEX_SIZE;
    private static final float WHEEL_A_U1 = 29f / TEX_SIZE;
    private static final float WHEEL_A_V1 = 11f / TEX_SIZE;

    /** Sprite B: edge-based highlights at 12, 10:30, 9 of the cog
     * (45 offset from sprite A). Texture pixels (48,22)-(58,32); in
     * 32-unit declared space u 24-29, v 11-16. Shown for phase
     * [22.5, 67.5) at display = phase - 90, so the displayed angle
     * runs from -67.5 to -22.5 across this band. */
    private static final float WHEEL_B_U0 = 24f / TEX_SIZE;
    private static final float WHEEL_B_V0 = 11f / TEX_SIZE;
    private static final float WHEEL_B_U1 = 29f / TEX_SIZE;
    private static final float WHEEL_B_V1 = 16f / TEX_SIZE;

    /** Max wheel speed in degrees per tick at full crafting. */
    private static final float MAX_WHEEL_SPEED = 12f;

    /** Acceleration in degrees/tick/tick when crafting. */
    private static final float WHEEL_ACCEL = 0.5f;

    /** Natural deceleration rate when crafting stops (degrees/tick/tick). */
    private static final float WHEEL_DECEL = 0.3f;

    /** Speed threshold below which the wheel hard-zeroes and snaps. */
    private static final float WHEEL_SPEED_EPSILON = 0.05f;

    /** Kinematic constant: stop distance under constant decel a is v^2 / (2a). */
    private static final float KINEMATIC_HALF = 2f;

    /** Number of vertices per wheel quad. */
    private static final int WHEEL_CORNERS = 4;
    /** Stride between consecutive (y,z) pairs in the corner array. */
    private static final int WHEEL_YZ_STRIDE = 2;
    /** First two vertices use V1, last two use V0. */
    private static final int WHEEL_UV_SPLIT = 2;

    /** Wheel cycle in degrees. The wheel rests at phase=0 (= 90).
     * Inside the cycle, three sub-arcs share the same forward motion
     * but swap sprites and display angle to keep the highlights in
     * apparent place: [0, 22.5) sprite A; [22.5, 67.5) sprite B with
     * display offset by -90; [67.5, 90) sprite A with display offset
     * by -90. The snap target is the next 90 mark, so the wheel
     * always settles at the rest position. */
    private static final float CYCLE_PERIOD = 90f;

    /** Phase at which sprite A swaps to sprite B (display jumps -90). */
    private static final float SPRITE_A_TO_B = 22.5f;

    /** Phase at which sprite B swaps back to sprite A (display continuous). */
    private static final float SPRITE_B_TO_A = 67.5f;

    /** Display-angle offset applied during the sprite-B band and the
     * trailing sprite-A band so the visible rotation runs continuous. */
    private static final float DISPLAY_ANGLE_OFFSET = -90f;

    /** Wheel rest snap increment in degrees -- one full cycle. */
    private static final float WHEEL_INCREMENT = CYCLE_PERIOD;

    /** Normal sign for the west-facing wheel quad. */
    private static final float NORMAL_WEST = -1f;

    /**
     * Creates a reactor BER.
     *
     * @param context the renderer provider context
     */
    public ReactorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ReactorRenderState createRenderState() {
        return new ReactorRenderState();
    }

    /**
     * Snapshots the output canister state and drives wheel animation.
     *
     * @param be            the block entity
     * @param state         the render state to populate
     * @param partialTick   the partial tick
     * @param cameraPos     the camera position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(ReactorBlockEntity be, ReactorRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(ReactorBlock.FACING);
        state.lightCoords = sampleHollowLight(be);
        state.crafting = be.getBlockState().getValue(ReactorBlock.CRAFTING);
        extractCanister(be, state);
        tickWheelAnimation(be, partialTick);
        state.wheelAngle = (be.wheelAngle + be.wheelSpeed * partialTick) % CYCLE_PERIOD;
    }

    /**
     * Reads canister contents into the render state.
     *
     * @param be    the block entity
     * @param state the render state
     */
    private static void extractCanister(ReactorBlockEntity be, ReactorRenderState state) {
        state.slot.present = !be.getOutputCanister().isEmpty();
        if (state.slot.present) {
            extractContents(be.getOutputCanister(), state);
        } else {
            state.slot.type = null;
            state.slot.fill = 0f;
        }
    }

    /**
     * Advances the wheel speed and angle on the block entity. Runs once
     * per frame before partial-tick interpolation.
     *
     * @param be          the block entity holding persistent wheel state
     * @param partialTick the partial tick (used to derive dt)
     */
    private static void tickWheelAnimation(ReactorBlockEntity be, float partialTick) {
        boolean crafting = be.getBlockState().getValue(ReactorBlock.CRAFTING);
        if (crafting) {
            be.wheelSpeed = Math.min(MAX_WHEEL_SPEED, be.wheelSpeed + WHEEL_ACCEL);
        } else {
            decelerateWheel(be);
        }
        be.wheelAngle = (be.wheelAngle + be.wheelSpeed) % CYCLE_PERIOD;
    }

    /**
     * Decelerates the wheel toward the next 45-degree rest increment.
     *
     * <p>Each tick:
     * <ol>
     *   <li>If speed is below {@link #WHEEL_SPEED_EPSILON}, hard-zero
     *       and snap to the nearest increment (avoids the asymptotic
     *       crawl from a recompute-each-tick formula).</li>
     *   <li>Otherwise compare natural stopping distance
     *       {@code v^2 / (2 * WHEEL_DECEL)} against distance to the
     *       next increment. If we'd <i>undershoot</i> (stop before the
     *       boundary), reduce the resistance to {@code v^2 / (2 * d)}
     *       so we land exactly. If we have enough speed to reach the
     *       boundary at natural decel, use the natural rate and let
     *       the next tick re-evaluate.</li>
     * </ol>
     *
     * @param be the block entity
     */
    private static void decelerateWheel(ReactorBlockEntity be) {
        if (be.wheelSpeed < WHEEL_SPEED_EPSILON) {
            be.wheelSpeed = 0f;
            be.wheelAngle = Math.round(be.wheelAngle / WHEEL_INCREMENT) * WHEEL_INCREMENT;
            return;
        }
        float dRemaining = WHEEL_INCREMENT - (be.wheelAngle % WHEEL_INCREMENT);
        float naturalStopDist = (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * WHEEL_DECEL);
        float decel = (naturalStopDist < dRemaining)
                ? (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * dRemaining)
                : WHEEL_DECEL;
        be.wheelSpeed = Math.max(0f, be.wheelSpeed - decel);
    }

    /**
     * Reads compression level and goo fill from the canister stack.
     *
     * @param canister the canister item stack
     * @param state    the render state to populate
     */
    private static void extractContents(ItemStack canister, ReactorRenderState state) {
        state.slot.matrices = GooEnchantments.getCompressionLevel(canister);
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (content.isEmpty()) {
            state.slot.type = null;
            state.slot.fill = 0f;
        } else {
            int cap = ContainerCapacity.canisterCapacity(state.slot.matrices);
            state.slot.type = content.getGooType();
            state.slot.fill = Math.min(1f, (float) content.amount() / cap);
        }
    }

    /**
     * Samples light from the block in front of the hollow opening.
     *
     * @param be the reactor block entity
     * @return packed light coordinates
     */
    private static int sampleHollowLight(ReactorBlockEntity be) {
        if (be.getLevel() == null) { return 0; }
        Direction facing = be.getBlockState().getValue(ReactorBlock.FACING);
        BlockPos frontPos = be.getBlockPos().relative(facing);
        return LevelRenderer.getLightCoords(be.getLevel(), frontPos);
    }

    /**
     * Submits canister geometry and spinning wheels.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(ReactorRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        submitWheels(state, poseStack, nodeCollector);
        if (!state.slot.present) { return; }
        poseStack.pushPose();
        rotateToFacing(poseStack, state.facing);
        submitBody(poseStack, nodeCollector, state);
        submitGaskets(poseStack, nodeCollector, state);
        if (state.slot.type != null && state.slot.fill > 0f) {
            submitFluid(poseStack, nodeCollector, state);
        }
        poseStack.popPose();
    }

    /**
     * Renders both wheels, rotating them to match the block facing so
     * they appear on the correct lateral sides (E/W for N/S facing,
     * N/S for E/W facing).
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     */
    private static void submitWheels(ReactorRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        poseStack.pushPose();
        rotateToFacing(poseStack, state.facing);
        // The world-space quad rotation is identical for both wheels.
        // The east viewer naturally sees CCW from that side; the west
        // viewer would see CW, so flipU=true on the west sprite mirrors
        // it horizontally and reverses the apparent direction. Leaving
        // east unmirrored avoids mirroring sprite B's asymmetric
        // highlights, which is what was breaking the swap continuity.
        submitWheel(state, poseStack, nodeCollector, WHEEL_WEST_X, true, false);
        submitWheel(state, poseStack, nodeCollector, WHEEL_EAST_X, false, false);
        poseStack.popPose();
    }

    /**
     * Renders a single wheel as a flat quad rotated around the X axis.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param x             the X position of the wheel face
     * @param flipU         true to swap u0/u1 (mirrors the sprite horizontally)
     * @param flipV         true to swap v0/v1 (mirrors the sprite vertically)
     */
    private static void submitWheel(ReactorRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            float x, boolean flipU, boolean flipV) {
        int light = state.lightCoords;
        float phase = state.wheelAngle;
        float displayAngle = displayAngleFor(phase);
        SpriteUv uv = flippedSpriteUvs(phase, flipU, flipV);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityCutout(REACTOR_TEXTURE),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, light);
                    emitRotatedWheel(ctx, x, displayAngle, uv.u0(), uv.u1(), uv.v0(), uv.v1());
                });
    }

    /** UV rectangle for one wheel sprite. */
    private record SpriteUv(float u0, float u1, float v0, float v1) {}

    /**
     * Maps phase to the display angle used to rotate the wheel quad.
     * Phase {@code [0, SPRITE_A_TO_B)} is shown 1:1; the rest of the
     * cycle uses a -90 offset so sprite B's pre-rotated highlights
     * land in the visually correct place.
     *
     * @param phase the wheel phase in [0, CYCLE_PERIOD)
     * @return the display angle in degrees
     */
    private static float displayAngleFor(float phase) {
        return (phase >= SPRITE_A_TO_B) ? phase + DISPLAY_ANGLE_OFFSET : phase;
    }

    /**
     * Returns the wheel UV rectangle, picking sprite A or B by phase
     * and applying horizontal/vertical flips for the requested side.
     *
     * @param phase the wheel phase
     * @param flipU swap U endpoints (mirror horizontally)
     * @param flipV swap V endpoints (mirror vertically)
     * @return the flipped UV rectangle
     */
    private static SpriteUv flippedSpriteUvs(float phase, boolean flipU, boolean flipV) {
        SpriteUv base = baseSpriteUvs(phase);
        return new SpriteUv(
                flipU ? base.u1() : base.u0(),
                flipU ? base.u0() : base.u1(),
                flipV ? base.v1() : base.v0(),
                flipV ? base.v0() : base.v1());
    }

    /**
     * Picks sprite A or sprite B UVs based on phase.
     *
     * @param phase the wheel phase
     * @return UV rectangle of the active sprite
     */
    private static SpriteUv baseSpriteUvs(float phase) {
        if (phase >= SPRITE_A_TO_B && phase < SPRITE_B_TO_A) {
            return new SpriteUv(WHEEL_B_U0, WHEEL_B_U1, WHEEL_B_V0, WHEEL_B_V1);
        }
        return new SpriteUv(WHEEL_A_U0, WHEEL_A_U1, WHEEL_A_V0, WHEEL_A_V1);
    }

    /**
     * Emits a wheel quad rotated around its center on the X axis.
     *
     * @param ctx   the render context
     * @param x     the X position
     * @param angle the rotation angle in degrees
     * @param u0    the left U coordinate
     * @param u1    the right U coordinate
     * @param v0    the top V coordinate
     * @param v1    the bottom V coordinate
     */
    private static void emitRotatedWheel(RenderContext ctx, float x,
            float angle, float u0, float u1, float v0, float v1) {
        // Negate angle so the top of the wheel rotates toward block-local
        // -Z (the hollow's front face). Visually: east wheel CW, west
        // wheel CCW from each side's outside view -- both wheels' tops
        // swing toward the front of the reactor.
        float[] yz = computeWheelCorners(-angle);
        float nx = x < BLOCK_CENTER ? NORMAL_WEST : 1f;

        for (int v = 0; v < WHEEL_CORNERS; v++) {
            emitWheelVertex(ctx, x, u0, u1, v0, v1, v, yz, nx);
        }
    }

    private static void emitWheelVertex(RenderContext ctx, float x,
            float u0, float u1, float v0, float v1, int v, float[] yz, float nx) {
        float u = getWheelVertexU(u0, u1, v);
        float wv = getWheelVertexV(v0, v1, v);
        emitWheelVertex(ctx, x, yz[v * WHEEL_YZ_STRIDE], yz[v * WHEEL_YZ_STRIDE + 1], u, wv, nx);
    }

    private static float getWheelVertexV(float v0, float v1, int v) {
        // 90-CW-rotated layout: V0,V3 = v1 (bottom); V1,V2 = v0 (top).
        return (v == 0 || v == WHEEL_CORNERS - 1) ? v1 : v0;
    }

    private static float getWheelVertexU(float u0, float u1, int v) {
        // 90-CW-rotated layout: V0,V1 = u0; V2,V3 = u1.
        return (v < WHEEL_UV_SPLIT) ? u0 : u1;
    }

    private static float @NonNull [] computeWheelCorners(float angle) {
        float rad = (float) Math.toRadians(angle);
        return computeWheelCorners((float) Math.cos(rad), (float) Math.sin(rad));
    }

    /**
     * Rotates the 4 wheel corners by cos/sin and returns {y0,z0,y1,z1,y2,z2,y3,z3}.
     * @param cos cosine of the rotation angle
     * @param sin sine of the rotation angle
     * @return interleaved {y,z} pairs for 4 corners
     */
    private static float[] computeWheelCorners(float cos, float sin) {
        float r = WHEEL_RADIUS;
        return new float[] {
            WHEEL_CENTER + (-r * cos + r * sin), WHEEL_CENTER + (-r * sin - r * cos),
            WHEEL_CENTER + (r * cos + r * sin),  WHEEL_CENTER + (r * sin - r * cos),
            WHEEL_CENTER + (r * cos - r * sin),  WHEEL_CENTER + (r * sin + r * cos),
            WHEEL_CENTER + (-r * cos - r * sin), WHEEL_CENTER + (-r * sin + r * cos),
        };
    }

    /**
     * Emits a single wheel quad vertex with standard lighting and overlay.
     * @param ctx the render context with pose and consumer
     * @param x the vertex X position
     * @param y the vertex Y position
     * @param z the vertex Z position
     * @param u the texture U coordinate
     * @param v the texture V coordinate
     * @param nx the face normal X component
     */
    private static void emitWheelVertex(RenderContext ctx, float x,
            float y, float z, float u, float v, float nx) {
        ctx.c().addVertex(ctx.pose(), x, y, z)
                .setColor(GooRenderUtil.OPAQUE_WHITE)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(ctx.light())
                .setNormal(nx, 0f, 0f);
    }

    /**
     * Rotates the pose stack so the south-facing model coordinates
     * align with the block's actual facing direction.
     *
     * @param poseStack the pose stack
     * @param facing    the block facing
     */
    private static void rotateToFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, 0, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-BLOCK_CENTER, 0, -BLOCK_CENTER);
    }

    /**
     * Renders the 4 side faces of the canister body.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param state         the render state
     */
    private static void submitBody(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, ReactorRenderState state) {
        int light = state.lightCoords;
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(CANISTER_SIDE),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, light);
                    CuboidBounds box = canisterBounds(BODY_BOT, BODY_TOP);
                    ctx.emitSides(box, new GooRenderUtil.UvRect(0, 0, BODY_U1, BODY_V1));
                });
    }

    /**
     * Renders copper endcaps at top and bottom.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param state         the render state
     */
    private static void submitGaskets(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, ReactorRenderState state) {
        int light = state.lightCoords;
        CuboidBounds base = canisterBounds(0, 0);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(COPPER_GASKET),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, light);
                    ctx.gasketBox(base.withY(BODY_TOP, GASKET_TOP), GS_U0, GS_U1, GS_V1);
                    ctx.gasketBox(base.withY(GASKET_BOT, BODY_BOT), GS_U0, GS_U1, GS_V1);
                });
    }

    /**
     * Renders the fluid surface inside the canister.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param state         the render state
     */
    private static void submitFluid(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, ReactorRenderState state) {
        int light = state.lightCoords;
        GooType type = state.slot.type;
        float fill = state.slot.fill;
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, light);
                    CuboidBounds b = SlotFluidGeometry.computeBounds(
                            FLUID_GEOM, HOLLOW_CX, HOLLOW_CZ, fill);
                    TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
                    SlotFluidGeometry.renderFluidTop(ctx, b, sprite);
                    SlotFluidGeometry.renderFluidSides(ctx, b, sprite, fill, FLUID_GEOM);
                });
    }

    /**
     * Builds a canister-sized cuboid centered in the hollow.
     *
     * @param yMin the bottom Y coordinate
     * @param yMax the top Y coordinate
     * @return the cuboid bounds
     */
    private static CuboidBounds canisterBounds(float yMin, float yMax) {
        return new CuboidBounds(
                HOLLOW_CX - HW, HOLLOW_CX + HW,
                HOLLOW_CZ - HW, HOLLOW_CZ + HW,
                yMin, yMax);
    }
}
