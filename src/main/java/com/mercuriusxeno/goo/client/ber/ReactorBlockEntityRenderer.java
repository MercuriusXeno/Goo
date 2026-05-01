package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
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

    /** Reactor body texture for wheel sprite lookup. */
    private static final Identifier REACTOR_BODY_TEXTURE =
            Identifier.fromNamespaceAndPath("goo", "textures/block/reactor_body.png");

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
    /** Top of body / bottom of upper gasket. */
    private static final float BODY_TOP = 14f / 16f;
    /** Top of upper gasket. */
    private static final float GASKET_TOP = 15f / 16f;

    /** Fluid inset from canister walls. */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Shared fluid geometry for the reactor canister slot. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
            new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

    /** Body side U range: 4px / 16px. */
    private static final float BODY_U1 = 0.25f;
    /** Body side V range: 12px / 16px. */
    private static final float BODY_V1 = 0.75f;

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

    /** West wheel X position (flush with block face). */
    private static final float WHEEL_WEST_X = 0f;

    /** East wheel X position (flush with block face). */
    private static final float WHEEL_EAST_X = 1f;

    /** Texture size for reactor_body.png. */
    private static final float TEX_SIZE = 64f;

    /** Wheel UV coords: origin u51,v35, 10x10 pixels in a 64x64 texture. */
    private static final float WHEEL_U0 = 51f / TEX_SIZE;
    private static final float WHEEL_V0 = 35f / TEX_SIZE;
    private static final float WHEEL_U1 = 61f / TEX_SIZE;
    private static final float WHEEL_V1 = 45f / TEX_SIZE;

    /** Max wheel speed in degrees per tick at full crafting. */
    private static final float MAX_WHEEL_SPEED = 12f;

    /** Acceleration in degrees/tick/tick when crafting. */
    private static final float WHEEL_ACCEL = 0.5f;

    /** Deceleration in degrees/tick/tick when not crafting. */
    private static final float WHEEL_DECEL = 0.3f;
    /** Number of vertices per wheel quad. */
    private static final int WHEEL_CORNERS = 4;
    /** Stride between consecutive (y,z) pairs in the corner array. */
    private static final int WHEEL_YZ_STRIDE = 2;
    /** First two vertices use V1, last two use V0. */
    private static final int WHEEL_UV_SPLIT = 2;

    /** Speed threshold below which the wheel snaps to rest at the nearest 90. */
    private static final float IDLE_SNAP_SPEED = 0.8f;

    /** Slow idle speed for coasting to aligned position. */
    private static final float IDLE_COAST_SPEED = 0.4f;

    /** Alignment tolerance in degrees. */
    private static final float SNAP_TOLERANCE = 0.5f;

    /** Normal sign for the west-facing wheel quad. */
    private static final float NORMAL_WEST = -1f;

    /** 90-degree symmetry period. */
    private static final float SYMMETRY_PERIOD = 90f;

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
        state.wheelAngle = be.wheelAngle + be.wheelSpeed * partialTick;
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
        be.wheelAngle = (be.wheelAngle + be.wheelSpeed) % SYMMETRY_PERIOD;
    }

    /**
     * Decelerates the wheel. Below the idle threshold, coasts slowly
     * toward the nearest 90-degree-aligned rest position, then stops.
     *
     * @param be the block entity
     */
    private static void decelerateWheel(ReactorBlockEntity be) {
        if (be.wheelSpeed <= 0f) {
            be.wheelSpeed = 0f;
            return;
        }
        if (be.wheelSpeed > IDLE_SNAP_SPEED) {
            be.wheelSpeed = Math.max(0f, be.wheelSpeed - WHEEL_DECEL);
            return;
        }
        float remainder = be.wheelAngle % SYMMETRY_PERIOD;
        if (remainder < SNAP_TOLERANCE || remainder > SYMMETRY_PERIOD - SNAP_TOLERANCE) {
            be.wheelAngle = 0f;
            be.wheelSpeed = 0f;
        } else {
            be.wheelSpeed = IDLE_COAST_SPEED;
        }
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
        submitWheel(state, poseStack, nodeCollector, WHEEL_WEST_X, true);
        submitWheel(state, poseStack, nodeCollector, WHEEL_EAST_X, false);
        poseStack.popPose();
    }

    /**
     * Renders a single wheel as a flat quad rotated around the X axis.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the node collector
     * @param x             the X position of the wheel face
     * @param flipU         true to flip U coords for the west-facing wheel
     */
    private static void submitWheel(ReactorRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            float x, boolean flipU) {
        int light = state.lightCoords;
        float angle = state.wheelAngle;
        float u0 = flipU ? WHEEL_U1 : WHEEL_U0;
        float u1 = flipU ? WHEEL_U0 : WHEEL_U1;
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityCutout(REACTOR_BODY_TEXTURE),
                (pose, c) -> {
                    RenderContext ctx = new RenderContext(pose, c, light);
                    emitRotatedWheel(ctx, x, angle, u0, u1);
                });
    }

    /**
     * Emits a wheel quad rotated around its center on the X axis.
     * The quad vertices are computed from the rotation angle.
     *
     * @param ctx   the render context
     * @param x     the X position
     * @param angle the rotation angle in degrees
     * @param u0    the left U coordinate
     * @param u1    the right U coordinate
     */
    private static void emitRotatedWheel(RenderContext ctx, float x,
            float angle, float u0, float u1) {
        float[] yz = computeWheelCorners(angle);
        float nx = x < BLOCK_CENTER ? NORMAL_WEST : 1f;

        for (int v = 0; v < WHEEL_CORNERS; v++) {
            emitWheelVertex(ctx, x, u0, u1, v, yz, nx);
        }
    }

    private static void emitWheelVertex(RenderContext ctx, float x, float u0, float u1, int v, float[] yz, float nx) {
        float u = getWheelVertexU(u0, u1, v);
        float wv = getWheelVertexV(v);
        emitWheelVertex(ctx, x, yz[v * WHEEL_YZ_STRIDE], yz[v * WHEEL_YZ_STRIDE + 1], u, wv, nx);
    }

    private static float getWheelVertexV(int v) {
        return (v < WHEEL_UV_SPLIT) ? WHEEL_V1 : WHEEL_V0;
    }

    private static float getWheelVertexU(float u0, float u1, int v) {
        return (v == 0 || v == WHEEL_CORNERS - 1) ? u0 : u1;
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
                RenderTypes.entityCutout(CANISTER_SIDE),
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
