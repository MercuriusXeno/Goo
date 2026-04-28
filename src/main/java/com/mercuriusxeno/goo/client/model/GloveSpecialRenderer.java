package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
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

    /**
     * Block atlas texture path for fluid sprite lookups.
     */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    // --- Blob center capture for arc rendering ---
    /**
     * Smoothing factor per frame (lower = more smoothing).
     */
    private static final float ARC_SMOOTH_FACTOR = 0.15f;
    /**
     * Pixels per block for coordinate conversion.
     */
    private static final float BLOCK_PIXELS = 16f;
    /**
     * Fully opaque white for untinted rendering.
     */
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    /**
     * Half-width of the held blob cuboid in model pixels.
     */
    private static final float BLOB_HW_PX = 2.5f;
    /**
     * Center X of the blob in model pixels.
     */
    private static final float BLOB_CX_PX = 8f;
    /**
     * Center Y of the blob in model pixels.
     */
    private static final float BLOB_CY_PX = 3.5f;
    /**
     * Center Z of the blob in model pixels.
     */
    private static final float BLOB_CZ_PX = 6f;
    /**
     * Normal direction for negative-facing surfaces.
     */
    private static final float NORMAL_NEG = -1f;
    /**
     * Minimum extent X in model pixels.
     */
    private static final float EXTENT_MIN_X_PX = 4.5f;
    /**
     * Minimum extent Y in model pixels.
     */
    private static final float EXTENT_MIN_Y_PX = -1f;
    /**
     * Minimum extent Z in model pixels.
     */
    private static final float EXTENT_MIN_Z_PX = 4f;
    /**
     * Maximum extent X in model pixels.
     */
    private static final float EXTENT_MAX_X_PX = 11.5f;
    /**
     * Maximum extent Y in model pixels.
     */
    private static final float EXTENT_MAX_Y_PX = 11f;
    /**
     * Maximum extent Z in model pixels.
     */
    private static final float EXTENT_MAX_Z_PX = 11.5f;
    /**
     * Camera-relative position of the held blob's center, captured during
     * item rendering. The arc renderer adds camera.position() to get world space.
     */
    private static volatile Vec3 blobCenterCamRel;
    /**
     * Smoothed blob center for arc origin - filters out swing jitter.
     */
    private static volatile Vec3 smoothedBlobCenter;

    /**
     * Returns the last captured camera-relative blob center. No age
     * check - once captured, the value is always valid. The position
     * updates every frame the glove's held blob renders, so staleness
     * is at most one frame (~16ms at 60fps). Returns null only if the
     * item renderer has never fired (first frame after world load,
     * before the glove has ever been held - in practice unreachable
     * because the arc handler only runs when you're holding the glove).
     *
     * @return camera-relative blob center, or null if never captured
     */
    /**
     * Creates a glove special renderer.
     */
    public GloveSpecialRenderer() {
    }

    /**
     * Returns the raw captured blob center (includes swing).
     *
     * @return camera-relative blob center, or null if never captured
     */
    public static @Nullable Vec3 getLastBlobCenterCamRel() {
        return blobCenterCamRel;
    }

    /**
     * Returns the smoothed blob center for arc origin (swing filtered out).
     *
     * @return smoothed camera-relative blob center, or null if never captured
     */
    public static @Nullable Vec3 getSmoothedBlobCenterCamRel() {
        return smoothedBlobCenter;
    }

    /**
     * Submits the baked glove body model (arm sheath geometry) for the given tier.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param gloveItem     the glove item instance
     */
    private static void submitGloveBody(PoseStack poseStack,
                                        SubmitNodeCollector nodeCollector, int packedLight, Item gloveItem) {
        QuadCollection model = GloveBodyModels.getModel(gloveItem);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> {
                    QuadInstance qi = new QuadInstance();
                    qi.setColor(COLOR_WHITE);
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
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param type          the goo type
     */
    private static void submitHeldBlob(PoseStack poseStack,
                                       SubmitNodeCollector nodeCollector, int packedLight, GooType type) {
        float hw = BLOB_HW_PX / BLOCK_PIXELS;
        float cx = BLOB_CX_PX / BLOCK_PIXELS;
        float cy = BLOB_CY_PX / BLOCK_PIXELS;
        float cz = BLOB_CZ_PX / BLOCK_PIXELS;

        captureBlobCenter(poseStack, cx, cy, cz);

        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> emitBlobFaces(pose, c, packedLight, type, cx, cy, cz, hw));
    }

    /**
     * Emits all six faces of the held blob cuboid.
     *
     * @param pose        the pose matrix entry
     * @param c           the vertex consumer for geometry output
     * @param packedLight the packed light value
     * @param type        the goo type determining the fluid texture
     * @param cx          the blob center X in block coords
     * @param cy          the blob center Y in block coords
     * @param cz          the blob center Z in block coords
     * @param hw          the half-width of the cuboid in block coords
     */
    private static void emitBlobFaces(PoseStack.Pose pose, VertexConsumer c, int packedLight,
                                      GooType type, float cx, float cy, float cz, float hw) {
        GooRenderUtil.UvRect uv = buildBlobUv(type);

        GooRenderUtil.faceY(pose, c, packedLight,
                cx - hw, cx + hw, cy + hw, cz - hw, cz + hw, uv, 1f);
        GooRenderUtil.faceY(pose, c, packedLight,
                cx - hw, cx + hw, cy - hw, cz - hw, cz + hw, uv, NORMAL_NEG);
        GooRenderUtil.faceX(pose, c, packedLight,
                cx + hw, cy - hw, cy + hw, cz - hw, cz + hw, uv, 1f);
        GooRenderUtil.faceX(pose, c, packedLight,
                cx - hw, cy - hw, cy + hw, cz - hw, cz + hw, uv, NORMAL_NEG);
        GooRenderUtil.faceZ(pose, c, packedLight,
                cx - hw, cx + hw, cy - hw, cy + hw, cz + hw, uv, 1f);
        GooRenderUtil.faceZ(pose, c, packedLight,
                cx - hw, cx + hw, cy - hw, cy + hw, cz - hw, uv, NORMAL_NEG);
    }

    /**
     * Builds the UV rectangle from the goo type's fluid sprite.
     *
     * @param type the goo type to look up the fluid sprite for
     * @return the UV rectangle covering the full fluid sprite
     */
    private static GooRenderUtil.UvRect buildBlobUv(GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        return new GooRenderUtil.UvRect(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    /**
     * Transforms the blob center through the current PoseStack to get
     * camera-relative coordinates and stores them for the arc renderer.
     *
     * @param poseStack the pose stack for rendering
     * @param cx        the center X in block coords
     * @param cy        the center Y in block coords
     * @param cz        the center Z in block coords
     */
    private static void captureBlobCenter(PoseStack poseStack,
                                          float cx, float cy, float cz) {
        Vector4f pos = new Vector4f(cx, cy, cz, 1.0f);
        poseStack.last().pose().transform(pos);
        Vec3 raw = new Vec3(pos.x(), pos.y(), pos.z());
        blobCenterCamRel = raw;
        Vec3 prev = smoothedBlobCenter;
        smoothedBlobCenter = prev == null ? raw : prev.lerp(raw, ARC_SMOOTH_FACTOR);
    }

    /**
     * Extracts the selected goo type from the glove item stack.
     *
     * @param stack the glove item stack
     * @return render data, or null if the item is not a glove
     */
    @Override
    public @Nullable GloveData extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof GooGloveItem)) {
            return null;
        }
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
     * @param data          extracted glove data (may be null)
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     * @param packedLight   packed light value
     * @param packedOverlay packed overlay value
     * @param hasFoil       whether the item has enchantment foil
     * @param outlineColor  outline color for selected items
     */
    @Override
    public void submit(@Nullable GloveData data,
                       PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        Item gloveItem = data != null ? data.item() : GooItems.GOO_GLOVE.get();
        submitGloveBody(poseStack, nodeCollector, packedLight, gloveItem);

        if (data != null && data.selectedType() != null) {
            submitHeldBlob(poseStack, nodeCollector, packedLight, data.selectedType());
        }

        poseStack.popPose();
    }

    /**
     * Reports the geometric extents of the glove for GUI rendering.
     * Covers the full glove volume including cuff and finger plate.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(EXTENT_MIN_X_PX / BLOCK_PIXELS, EXTENT_MIN_Y_PX / BLOCK_PIXELS, EXTENT_MIN_Z_PX / BLOCK_PIXELS));
        output.accept(new Vector3f(EXTENT_MAX_X_PX / BLOCK_PIXELS, EXTENT_MAX_Y_PX / BLOCK_PIXELS, EXTENT_MAX_Z_PX / BLOCK_PIXELS));
    }

    /**
     * Extracted render data from the glove item stack.
     *
     * @param item         the glove item instance (determines tier/body model)
     * @param selectedType the selected goo type, or null if none selected
     */
    public record GloveData(Item item, @Nullable GooType selectedType) {
    }

    /**
     * Unbaked factory for the glove special renderer. Registered as
     * "goo:glove_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GloveData> {

        /**
         * Codec for data-driven item model deserialization.
         */
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
