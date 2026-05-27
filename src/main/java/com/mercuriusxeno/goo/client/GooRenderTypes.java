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

    /** Depth state that always passes (see-through rendering). */
    private static final DepthStencilState DEPTH_ALWAYS = new DepthStencilState(
            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false);

    /** Lines pipeline with depth test disabled for see-through ghost outlines. */
    public static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/lines_no_depth"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for see-through wireframe lines (ghost outline). */
    public static final RenderType LINES_NO_DEPTH = RenderType.create(
            "goo_lines_no_depth",
            RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    /** Quads pipeline with depth test disabled for see-through ghost fill. */
    public static final RenderPipeline QUADS_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/quads_no_depth"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for see-through translucent fill quads (ghost outline). */
    public static final RenderType QUADS_NO_DEPTH = RenderType.create(
            "goo_quads_no_depth",
            RenderSetup.builder(QUADS_NO_DEPTH_PIPELINE)
                    .sortOnUpload()
                    .createRenderSetup()
    );

    /** Quads pipeline with additive blend (SRC_ALPHA, ONE) and no depth test.
     * Used for the ghost fill brightening pass. */
    public static final RenderPipeline QUADS_ADDITIVE_NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/quads_additive_no_depth"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(DEPTH_ALWAYS)
            .build();

    /** RenderType for additive-blend see-through quads (ghost fill glow pass). */
    public static final RenderType QUADS_ADDITIVE_NO_DEPTH = RenderType.create(
            "goo_quads_additive_no_depth",
            RenderSetup.builder(QUADS_ADDITIVE_NO_DEPTH_PIPELINE)
                    .sortOnUpload()
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

    /**
     * Nether black-hole corona pipeline: second render pass that emits
     * the same sphere mesh at a slightly larger radius with additive
     * LIGHTNING blend and depth-write OFF, producing an emissive halo
     * that sits in the annular gap just outside the main sphere's
     * silhouette. Uses {@code nether_corona.vsh / .fsh} which discards
     * pixels inside the main silhouette based on the corona mesh's
     * fresnel value.
     */
    public static final RenderPipeline NETHER_CORONA = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_corona"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_corona"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_corona"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the corona halo pass. */
    public static final RenderType NETHER_CORONA_TYPE = RenderType.create(
            "goo_nether_corona",
            RenderSetup.builder(NETHER_CORONA)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Nether black-hole accretion-disk pipeline: third render pass that
     * emits a flat annular ring in the world XZ plane around the sphere,
     * inner radius pinned to the main sphere radius and outer radius at
     * {@code DISK_OUTER_SCALE * main}. Additive LIGHTNING blend with
     * depth write off, so the disk layers onto whatever was drawn behind
     * it. The fragment shader decodes a per-vertex radial distance
     * from {@code Color.r} and discards any fragment whose interpolated
     * radial distance is below the main sphere radius (strictly "no
     * geometry inside the sphere").
     */
    public static final RenderPipeline NETHER_DISK = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_disk"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_disk"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_disk"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the accretion-disk pass. */
    public static final RenderType NETHER_DISK_TYPE = RenderType.create(
            "goo_nether_disk",
            RenderSetup.builder(NETHER_DISK)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Cube black-hole edge-glow pipeline used by the
     * {@code CubeHoleStyle} experiment. Same shape budget as the
     * corona - additive LIGHTNING blend, depth test on, depth write
     * off - but drawn over a cube mesh with a fragment shader that
     * highlights the per-face edges instead of the fresnel silhouette.
     * Each vertex carries its intra-face UV in {@code Color.rg} so the
     * shader can compute edge distance without any ray math.
     */
    public static final RenderPipeline NETHER_CUBE_EDGE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/nether_cube_edge"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_cube_edge"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/nether_cube_edge"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the cube edge-glow pass. */
    public static final RenderType NETHER_CUBE_EDGE_TYPE = RenderType.create(
            "goo_nether_cube_edge",
            RenderSetup.builder(NETHER_CUBE_EDGE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Voronoi fissure pipeline: procedural crack pattern on a sphere mesh.
     * Translucent blend, depth test on (occluded by terrain), depth write off
     * (cracks are overlay), cull off (visible from inside). Reserved for
     * future use - not currently wired to any effect.
     */
    public static final RenderPipeline VORONOI_FISSURE = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/voronoi_fissure"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/voronoi_fissure"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/voronoi_fissure"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType for the voronoi fissure sphere (reserved, not wired). */
    public static final RenderType VORONOI_FISSURE_TYPE = RenderType.create(
            "goo_voronoi_fissure",
            RenderSetup.builder(VORONOI_FISSURE)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    /**
     * Goo fluid pipeline: translucent fullbright fluid with no directional
     * shading (so faces look uniformly bright regardless of orientation),
     * no lightmap multiplication (world light doesn't dim it), and
     * writeDepth=ON so the cuboid faces depth-test correctly against each
     * other and the surrounding canister body. Texture is sampled from
     * Sampler0; no overlay, no lightmap (skipped via shader defines).
     */
    public static final RenderPipeline GOO_FLUID = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/goo_fluid"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    /** RenderType for goo fluid surfaces. Per-texture-key memoized below. */
    private static final java.util.function.Function<Identifier, RenderType> GOO_FLUID_FACTORY =
            net.minecraft.util.Util.memoize(texture -> RenderType.create(
                    "goo_fluid",
                    RenderSetup.builder(GOO_FLUID)
                            .withTexture("Sampler0", texture)
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    /**
     * Returns the goo-fluid render type for the given texture atlas.
     *
     * @param texture the texture atlas identifier (typically blocks atlas)
     * @return memoized RenderType
     */
    public static RenderType gooFluid(Identifier texture) {
        return GOO_FLUID_FACTORY.apply(texture);
    }

    /**
     * Crystal shard pipeline: translucent glass splinter quads scattered
     * in a cloud volume. Depth test on, depth write off, cull off.
     */
    public static final RenderPipeline CRYSTAL_SHARD = RenderPipeline.builder(
                    RenderPipelines.MATRICES_PROJECTION_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/crystal_shard"))
            .withVertexShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crystal_shard"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/crystal_shard"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(
                    DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    /** RenderType that submits the crystal shard splinters with scene copy sampler. */
    public static final RenderType CRYSTAL_SHARD_TYPE = RenderType.create(
            "goo_crystal_shard",
            RenderSetup.builder(CRYSTAL_SHARD)
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
        event.registerPipeline(NETHER_CORONA);
        event.registerPipeline(NETHER_DISK);
        event.registerPipeline(NETHER_CUBE_EDGE);
        event.registerPipeline(LINES_NO_DEPTH_PIPELINE);
        event.registerPipeline(QUADS_NO_DEPTH_PIPELINE);
        event.registerPipeline(QUADS_ADDITIVE_NO_DEPTH_PIPELINE);
        event.registerPipeline(VORONOI_FISSURE);
        event.registerPipeline(CRYSTAL_SHARD);
        event.registerPipeline(GOO_FLUID);
    }
}
