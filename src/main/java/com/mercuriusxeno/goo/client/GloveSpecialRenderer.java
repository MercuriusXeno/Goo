package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Special item renderer for glove items. Renders the glove body model
 * (arm sheath) and overlays a small fluid cuboid in the palm area when
 * a goo type is selected. Shared by glove, glove_netherite, and glove_exorite tiers.
 */
public class GloveSpecialRenderer implements SpecialModelRenderer<GloveSpecialRenderer.GloveData> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    // --- Blob center capture for arc rendering ---

    /**
     * Camera-relative position of the held blob's center, captured during
     * item rendering. The arc renderer adds camera.position() to get world space.
     */
    private static volatile Vec3 blobCenterCamRel = null;

    /** Frame counter to detect stale captures. */
    private static long captureFrame = -1;

    /** Max tick age before a capture is considered stale. Hand rendering
     *  may occur after arc rendering within the same frame, so we tolerate
     *  a one-tick lag to avoid first-frame-of-tick fallback flicker. */
    private static final long MAX_CAPTURE_AGE = 1;

    /**
     * Returns the camera-relative blob center if captured recently.
     * Tolerates a one-tick lag because first-person hand rendering can
     * fire after AfterOpaqueFeatures within the same render frame.
     *
     * @param currentTick the current game tick
     * @return camera-relative blob center, or null if stale
     */
    public static @Nullable Vec3 getBlobCenterCamRel(long currentTick) {
        long age = currentTick - captureFrame;
        return age >= 0 && age <= MAX_CAPTURE_AGE ? blobCenterCamRel : null;
    }

    /** Creates a glove special renderer. */
    public GloveSpecialRenderer() {
    }

    /**
     * Extracted render data from the glove item stack.
     *
     * @param selectedType the selected goo type, or null if none selected
     */
    /**
     * @param item         the glove item instance (determines tier/body model)
     * @param selectedType the selected goo type, or null if none selected
     */
    public record GloveData(Item item, @Nullable GooType selectedType) {}

    /**
     * Extracts the selected goo type from the glove item stack.
     *
     * @param stack the glove item stack
     * @return render data, or null if the item is not a glove
     */
    @Override
    public @Nullable GloveData extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof GooGloveItem)) return null;
        GooType type = GooGloveItem.getSelectedType(stack);
        // Suppress the held blob visual when the player has no goo of that type
        if (type != null && !GloveUseTracker.isSelectedTypeAvailable()) {
            type = null;
        }
        return new GloveData(stack.getItem(), type);
    }

    /**
     * Renders the glove body and optional held blob overlay.
     *
     * @param data           extracted glove data (may be null)
     * @param displayContext the display context (GUI, hand, etc.)
     * @param poseStack      the current pose stack
     * @param nodeCollector  the render node collector
     * @param packedLight    packed light value
     * @param packedOverlay  packed overlay value
     * @param hasFoil        whether the item has enchantment foil
     * @param outlineColor   outline color for selected items
     */
    @Override
    public void submit(@Nullable GloveData data,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        // Debug transform overlay - adjust with RSHIFT+hotkeys, remove when tuning is done
        if (GloveTransformDebug.enabled) {
            poseStack.translate(
                GloveTransformDebug.transX / 16f,
                GloveTransformDebug.transY / 16f,
                GloveTransformDebug.transZ / 16f);
            poseStack.mulPose(Axis.XP.rotationDegrees(GloveTransformDebug.rotX));
            poseStack.mulPose(Axis.YP.rotationDegrees(GloveTransformDebug.rotY));
            poseStack.mulPose(Axis.ZP.rotationDegrees(GloveTransformDebug.rotZ));
            float s = GloveTransformDebug.scale;
            poseStack.scale(s, s, s);
        }

        Item gloveItem = data != null ? data.item() : GooItems.GOO_GLOVE.get();
        submitGloveBody(poseStack, nodeCollector, packedLight, gloveItem);

        if (data != null && data.selectedType() != null) {
            submitHeldBlob(poseStack, nodeCollector, packedLight, data.selectedType());
        }

        poseStack.popPose();
    }

    /** Submits the baked glove body model (arm sheath geometry) for the given tier. */
    private static void submitGloveBody(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int packedLight, Item gloveItem) {
        QuadCollection model = GloveBodyModels.getModel(gloveItem);
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                QuadInstance qi = new QuadInstance();
                qi.setColor(0xFFFFFFFF);
                qi.setLightCoords(packedLight);
                qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for (BakedQuad quad : model.getAll()) {
                    c.putBakedQuad(pose, quad, qi);
                }
            });
    }

    /**
     * Renders a fluid cuboid (5x5x5 pixels) in the palm area,
     * sitting above the finger plate so the blob is substantial.
     */
    private static void submitHeldBlob(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int packedLight, GooType type) {
        float hw = 2.5f / 16f;
        float cx = 8f / 16f, cy = 3.5f / 16f, cz = 6f / 16f;

        captureBlobCenter(poseStack, cx, cy, cz);

        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> {
                TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
                float u0 = sprite.getU0(), u1 = sprite.getU1();
                float v0 = sprite.getV0(), v1 = sprite.getV1();
                GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(u0, v0, u1, v1);

                // Top and bottom faces
                GooRenderUtil.faceY(pose, c, packedLight,
                    cx - hw, cx + hw, cy + hw, cz - hw, cz + hw, uv, 1f);
                GooRenderUtil.faceY(pose, c, packedLight,
                    cx - hw, cx + hw, cy - hw, cz - hw, cz + hw, uv, -1f);
                // East and west faces
                GooRenderUtil.faceX(pose, c, packedLight,
                    cx + hw, cy - hw, cy + hw, cz - hw, cz + hw, uv, 1f);
                GooRenderUtil.faceX(pose, c, packedLight,
                    cx - hw, cy - hw, cy + hw, cz - hw, cz + hw, uv, -1f);
                // South and north faces
                GooRenderUtil.faceZ(pose, c, packedLight,
                    cx - hw, cx + hw, cy - hw, cy + hw, cz + hw, uv, 1f);
                GooRenderUtil.faceZ(pose, c, packedLight,
                    cx - hw, cx + hw, cy - hw, cy + hw, cz - hw, uv, -1f);
            });
    }

    /**
     * Transforms the blob center through the current PoseStack to get
     * camera-relative coordinates and stores them for the arc renderer.
     */
    private static void captureBlobCenter(PoseStack poseStack,
                                          float cx, float cy, float cz) {
        Vector4f pos = new Vector4f(cx, cy, cz, 1.0f);
        poseStack.last().pose().transform(pos);
        blobCenterCamRel = new Vec3(pos.x(), pos.y(), pos.z());
        Minecraft mc = Minecraft.getInstance();
        captureFrame = mc.level != null ? mc.level.getGameTime() : -1;
    }

    /**
     * Reports the geometric extents of the glove for GUI rendering.
     * Covers the full glove volume including cuff and finger plate.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(4.5f / 16f, -1f / 16f, 4f / 16f));
        output.accept(new Vector3f(11.5f / 16f, 11f / 16f, 11.5f / 16f));
    }

    /**
     * Unbaked factory for the glove special renderer. Registered as
     * "goo:glove_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GloveData> {

        /** Codec for data-driven item model deserialization. */
        public static final MapCodec<GloveSpecialRenderer.Unbaked> MAP_CODEC =
            MapCodec.unit(new GloveSpecialRenderer.Unbaked());

        @Override
        public MapCodec<GloveSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<GloveData> bake(SpecialModelRenderer.BakingContext context) {
            return new GloveSpecialRenderer();
        }
    }
}
