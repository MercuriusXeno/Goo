package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Custom render types for goo visuals. The additive glow line type uses
 * alpha-weighted additive blending (SRC_ALPHA, ONE) with depth-write disabled,
 * so overlapping multi-pass line segments accumulate brightness instead of
 * z-fighting against each other.
 */
public final class GooRenderTypes {
    /** Mod namespace for identifier construction. */
    private static final String NAMESPACE = "goo";

    /**
     * Lines pipeline with LIGHTNING blend (SRC_ALPHA, ONE) and no depth write.
     * Reuses vanilla line shaders; only blend and depth state differ.
     *
     * @return the result
     */
    public static final RenderPipeline LINES_ADDITIVE_GLOW = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/lines_additive_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .build();

    /** RenderType that draws lines with additive glow blending. */
    public static final RenderType LINES_GLOW = RenderType.create(
            "goo_lines_additive_glow",
            RenderSetup.builder(LINES_ADDITIVE_GLOW)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup()
    );

    /**
     * Nether black-hole pipeline: POSITION_COLOR billboard quad with a custom
     * vertex + fragment shader pair (nether_blackhole.vsh / .fsh). Reads the
     * implosion progress from the vertex Color.r channel and animates swirl
     * bands with {@code GameTime}. Depth write ON so the sphere solidly
     * occludes whatever the nether effect has chewed out of the world.
     */
    public static final RenderPipeline NETHER_BLACKHOLE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_blackhole"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_blackhole"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_blackhole"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    /** RenderType that submits the nether black-hole billboard quad. */
    public static final RenderType NETHER_BLACKHOLE_TYPE = RenderType.create(
            "goo_nether_blackhole",
            RenderSetup.builder(NETHER_BLACKHOLE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    private GooRenderTypes() {}

    /**
     * Registers custom pipelines with the NeoForge pipeline registry.
     *
     * @param event the event instance
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LINES_ADDITIVE_GLOW);
        event.registerPipeline(NETHER_BLACKHOLE);
    }
}
