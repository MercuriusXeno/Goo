package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
    /**
     * Lines pipeline with LIGHTNING blend (SRC_ALPHA, ONE) and no depth write.
     * Reuses vanilla line shaders; only blend and depth state differ.
     *
     * @return the result
     */
    public static final RenderPipeline LINES_ADDITIVE_GLOW = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("goo", "pipeline/lines_additive_glow"))
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

    private GooRenderTypes() {}

    /**
     * Registers custom pipelines with the NeoForge pipeline registry.
     *
     * @param event the event instance
     */
    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LINES_ADDITIVE_GLOW);
    }
}
