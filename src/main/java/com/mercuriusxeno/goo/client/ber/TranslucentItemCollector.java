package com.mercuriusxeno.goo.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy SubmitNodeCollector that intercepts submitItem calls and re-emits
 * the item's quads through submitCustomGeometry with a translucent render
 * type and per-vertex alpha. Used by the plexer BER for ghost item previews.
 */
class TranslucentItemCollector implements SubmitNodeCollector {

    /**
     * Fully opaque white - used to construct the alpha-tinted base color.
     */
    private static final int OPAQUE_WHITE = 0xFF;

    /**
     * Untinted sentinel - ARGB white means no tint modification.
     */
    private static final int NO_TINT = -1;

    private final SubmitNodeCollector delegate;
    private final int alphaColor;

    /**
     * Creates a proxy that renders item quads with translucent alpha.
     *
     * @param delegate the real collector to emit translucent geometry into
     * @param alpha    alpha value 0-255
     */
    TranslucentItemCollector(SubmitNodeCollector delegate, int alpha) {
        this.delegate = delegate;
        this.alphaColor = ARGB.color(alpha, OPAQUE_WHITE, OPAQUE_WHITE, OPAQUE_WHITE);
    }

    /**
     * Groups quads by their sprite's atlas location.
     *
     * @param quads the baked quads
     * @return quads grouped by atlas identifier
     */
    private static Map<Identifier, List<BakedQuad>> groupByAtlas(List<BakedQuad> quads) {
        Map<Identifier, List<BakedQuad>> map = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            Identifier atlas = quad.materialInfo().sprite().atlasLocation();
            map.computeIfAbsent(atlas, k -> new ArrayList<>()).add(quad);
        }
        return map;
    }

    /**
     * Looks up the tint color for a quad's tint index.
     *
     * @param tintIndex  the quad's tint index, or -1 if untinted
     * @param tintLayers the color array from the item color handler
     * @return the tint color, or NO_TINT if untinted
     */
    private static int resolveTint(int tintIndex, int[] tintLayers) {
        if (tintIndex >= 0 && tintIndex < tintLayers.length) {
            return tintLayers[tintIndex];
        }
        return NO_TINT;
    }

    /**
     * Intercepts item quads and re-emits them as translucent custom geometry.
     * Groups quads by atlas texture so block-atlas and item-atlas sprites
     * each bind the correct texture. Applies tint colors combined with alpha.
     *
     * @param poseStack      the pose stack
     * @param displayContext the item display context
     * @param lightCoords    packed light coordinates
     * @param overlayCoords  packed overlay coordinates
     * @param outlineColor   outline color (unused for ghost rendering)
     * @param tintLayers     per-tint-index colors from the item color handler
     * @param quads          the baked quads to render
     * @param foilType       the foil/glint type (unused for ghost rendering)
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext,
                           int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        Map<Identifier, List<BakedQuad>> byAtlas = groupByAtlas(quads);
        for (var entry : byAtlas.entrySet()) {
            RenderType renderType = RenderTypes.entityTranslucent(entry.getKey());
            List<BakedQuad> group = entry.getValue();
            delegate.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                QuadInstance qi = new QuadInstance();
                qi.setLightCoords(lightCoords);
                qi.setOverlayCoords(overlayCoords);
                for (BakedQuad quad : group) {
                    int tint = resolveTint(quad.materialInfo().tintIndex(), tintLayers);
                    qi.setColor(ARGB.multiply(alphaColor, tint));
                    buffer.putBakedQuad(pose, quad, qi);
                }
            });
        }
    }

    /**
     * Re-emits custom geometry with alpha-tinted vertex colors.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     CustomGeometryRenderer renderer) {
        delegate.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                renderer.render(pose, new AlphaTintConsumer(buffer, alphaColor)));
    }

    /**
     * Returns this collector since item rendering does not use ordering.
     */
    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> s) {
    }

    @Override
    public void submitNameTag(PoseStack p, @Nullable Vec3 a, int o, Component n,
                              boolean s, int l, double d, CameraRenderState c) {
    }

    @Override
    public void submitText(PoseStack p, float x, float y, FormattedCharSequence s,
                           boolean d, Font.DisplayMode m, int l, int c, int bg, int o) {
    }

    @Override
    public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf r) {
    }

    @Override
    public void submitLeash(PoseStack p, EntityRenderState.LeashState s) {
    }

    @Override
    public <S> void submitModel(Model<? super S> m, S s, PoseStack p, RenderType r,
                                int l, int o, int t, @Nullable TextureAtlasSprite sp, int oc,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay c) {
    }

    @Override
    public void submitModelPart(ModelPart m, PoseStack p, RenderType r, int l, int o,
                                @Nullable TextureAtlasSprite sp, boolean sh, boolean f, int t,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay c, int oc) {
    }

    @Override
    public void submitMovingBlock(PoseStack p, MovingBlockRenderState s) {
    }

    @Override
    public void submitBlockModel(PoseStack p, RenderType r,
                                 List<BlockStateModelPart> parts, int[] t, int l, int o, int oc) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack p, BlockStateModel m, long s, int pr) {
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer r) {
    }

    /**
     * VertexConsumer wrapper that multiplies alpha into all vertex colors.
     */
    private static final class AlphaTintConsumer implements VertexConsumer {
        private final VertexConsumer inner;
        private final int tint;

        AlphaTintConsumer(VertexConsumer inner, int tint) {
            this.inner = inner;
            this.tint = tint;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(ARGB.multiply(tint, ARGB.color(a, r, g, b)));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            inner.setColor(ARGB.multiply(tint, color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            inner.setLineWidth(width);
            return this;
        }
    }
}
