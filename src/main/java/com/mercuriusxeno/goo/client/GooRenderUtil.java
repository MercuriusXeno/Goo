package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * Shared rendering utilities for block entity renderers (BERs).
 * Extracted from canister, crucible, and vat BERs to eliminate duplication.
 */
public final class GooRenderUtil {

    private GooRenderUtil() {}

    /**
     * Looks up the fluid sprite for a goo type from the block texture atlas.
     * The sprite ID follows the pattern "goo:fluid/{typeId}_fluid".
     */
    public static TextureAtlasSprite lookupFluidSprite(GooType type) {
        Identifier spriteId = Identifier.fromNamespaceAndPath(
            "goo", "fluid/" + type.getId() + "_fluid");
        return Minecraft.getInstance().getAtlasManager()
            .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(spriteId);
    }

    /** Fully opaque white in ARGB. */
    public static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /** Emits a vertex with an explicit ARGB color. */
    public static void vertexColored(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x, float y, float z, float u, float v,
            float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /** Emits a vertex with full-white opaque color. */
    public static void vertex(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y, float z, float u, float v,
            float nx, float ny, float nz) {
        vertexColored(pose, c, light, OPAQUE_WHITE, x, y, z, u, v, nx, ny, nz);
    }

    /**
     * Emits a single fluid vertex with full-white opaque color and upward normal.
     * Convenience overload for flat liquid surface quads.
     */
    public static void fluidVertex(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y, float z, float u, float v) {
        vertex(pose, c, light, x, y, z, u, v, 0f, 1f, 0f);
    }

    /** Emits a horizontal liquid surface quad with explicit ARGB color and upward normal. */
    public static void liquidSurface(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x0, float z0, float x1, float z1,
            float y, float u0, float u1, float v0, float v1) {
        vertexColored(pose, c, light, color, x0, y, z0, u0, v0, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x0, y, z1, u0, v1, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x1, y, z1, u1, v1, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x1, y, z0, u1, v0, 0f, 1f, 0f);
    }

    /** Emits a horizontal liquid surface quad with downward normal (reverse winding). */
    public static void liquidSurfaceDown(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x0, float z0, float x1, float z1,
            float y, float u0, float u1, float v0, float v1) {
        vertexColored(pose, c, light, color, x1, y, z0, u1, v0, 0f, -1f, 0f);
        vertexColored(pose, c, light, color, x1, y, z1, u1, v1, 0f, -1f, 0f);
        vertexColored(pose, c, light, color, x0, y, z1, u0, v1, 0f, -1f, 0f);
        vertexColored(pose, c, light, color, x0, y, z0, u0, v0, 0f, -1f, 0f);
    }

    // -- Axis-aligned face helpers --
    // Each emits a quad for one face of a box.
    // Positive normal = outward-facing CCW winding. Negative = reversed.

    /** UV rectangle: texture coordinate bounds for a quad face. */
    public record UvRect(float u0, float v0, float u1, float v1) {}

    /** Y-axis face (top when ny > 0, bottom when ny < 0). */
    public static void faceY(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y,
            float z0, float z1, UvRect uv, float ny) {
        if (ny > 0) {
            vertex(pose, c, light, x0, y, z0, uv.u0, uv.v0, 0f, ny, 0f);
            vertex(pose, c, light, x0, y, z1, uv.u0, uv.v1, 0f, ny, 0f);
            vertex(pose, c, light, x1, y, z1, uv.u1, uv.v1, 0f, ny, 0f);
            vertex(pose, c, light, x1, y, z0, uv.u1, uv.v0, 0f, ny, 0f);
        } else {
            vertex(pose, c, light, x1, y, z0, uv.u1, uv.v0, 0f, ny, 0f);
            vertex(pose, c, light, x1, y, z1, uv.u1, uv.v1, 0f, ny, 0f);
            vertex(pose, c, light, x0, y, z1, uv.u0, uv.v1, 0f, ny, 0f);
            vertex(pose, c, light, x0, y, z0, uv.u0, uv.v0, 0f, ny, 0f);
        }
    }

    /** X-axis face (east when nx > 0, west when nx < 0). */
    public static void faceX(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y0, float y1,
            float z0, float z1, UvRect uv, float nx) {
        if (nx > 0) {
            vertex(pose, c, light, x, y1, z1, uv.u1, uv.v0, nx, 0f, 0f);
            vertex(pose, c, light, x, y0, z1, uv.u1, uv.v1, nx, 0f, 0f);
            vertex(pose, c, light, x, y0, z0, uv.u0, uv.v1, nx, 0f, 0f);
            vertex(pose, c, light, x, y1, z0, uv.u0, uv.v0, nx, 0f, 0f);
        } else {
            vertex(pose, c, light, x, y1, z0, uv.u1, uv.v0, nx, 0f, 0f);
            vertex(pose, c, light, x, y0, z0, uv.u1, uv.v1, nx, 0f, 0f);
            vertex(pose, c, light, x, y0, z1, uv.u0, uv.v1, nx, 0f, 0f);
            vertex(pose, c, light, x, y1, z1, uv.u0, uv.v0, nx, 0f, 0f);
        }
    }

    /** Z-axis face (south when nz > 0, north when nz < 0). */
    public static void faceZ(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y0,
            float y1, float z, UvRect uv, float nz) {
        if (nz > 0) {
            vertex(pose, c, light, x0, y1, z, uv.u1, uv.v0, 0f, 0f, nz);
            vertex(pose, c, light, x0, y0, z, uv.u1, uv.v1, 0f, 0f, nz);
            vertex(pose, c, light, x1, y0, z, uv.u0, uv.v1, 0f, 0f, nz);
            vertex(pose, c, light, x1, y1, z, uv.u0, uv.v0, 0f, 0f, nz);
        } else {
            vertex(pose, c, light, x1, y1, z, uv.u0, uv.v0, 0f, 0f, nz);
            vertex(pose, c, light, x1, y0, z, uv.u0, uv.v1, 0f, 0f, nz);
            vertex(pose, c, light, x0, y0, z, uv.u1, uv.v1, 0f, 0f, nz);
            vertex(pose, c, light, x0, y1, z, uv.u1, uv.v0, 0f, 0f, nz);
        }
    }
}
