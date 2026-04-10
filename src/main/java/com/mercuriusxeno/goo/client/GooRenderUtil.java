package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Shared rendering utilities for block entity renderers (BERs).
 * Extracted from canister, crucible, and vat BERs to eliminate duplication.
 */
public final class GooRenderUtil {

    /** Mod namespace for resource locations. */
    private static final String NAMESPACE = "goo";
    /** Sprite path prefix for fluid textures. */
    private static final String FLUID_PREFIX = "fluid/";
    /** Sprite path suffix for fluid textures. */
    private static final String FLUID_SUFFIX = "_fluid";
    /** Fully opaque white in ARGB. */
    public static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private GooRenderUtil() {}

    /**
     * Looks up the fluid sprite for a goo type from the block texture atlas.
     * The sprite ID follows the pattern "goo:fluid/{typeId}_fluid".
     *
     * @param type the goo type
     * @return the fluidSprite, or null if not found
     */
    public static TextureAtlasSprite lookupFluidSprite(GooType type) {
        Identifier spriteId = Identifier.fromNamespaceAndPath(
            NAMESPACE, FLUID_PREFIX + type.getId() + FLUID_SUFFIX);
        return Minecraft.getInstance().getAtlasManager()
            .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(spriteId);
    }

    /**
     * Returns true if the player's crosshair is currently on the given
     * block position. Used by BERs to highlight when aimed at.
     *
     * @param pos the block position
     * @return true if blockTargeted
     */
    public static boolean isBlockTargeted(BlockPos pos) {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(pos);
    }

    /**
     * Emits a vertex with an explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param u the U texture coordinate
     * @param v the V texture coordinate
     * @param nx the X normal component
     * @param ny the Y normal component
     * @param nz the Z normal component
     */
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

    /**
     * Emits a vertex with full-white opaque color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param u the U texture coordinate
     * @param v the V texture coordinate
     * @param nx the X normal component
     * @param ny the Y normal component
     * @param nz the Z normal component
     */
    public static void vertex(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y, float z, float u, float v,
            float nx, float ny, float nz) {
        vertexColored(pose, c, light, OPAQUE_WHITE, x, y, z, u, v, nx, ny, nz);
    }

    /**
     * Emits a horizontal liquid surface quad with explicit ARGB color and upward normal.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param z1 the maximum Z bound
     * @param y the Y coordinate
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void liquidSurface(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x0, float z0, float x1, float z1,
            float y, float u0, float u1, float v0, float v1) {
        vertexColored(pose, c, light, color, x0, y, z0, u0, v0, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x0, y, z1, u0, v1, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x1, y, z1, u1, v1, 0f, 1f, 0f);
        vertexColored(pose, c, light, color, x1, y, z0, u1, v0, 0f, 1f, 0f);
    }

    // -- Axis-aligned face helpers --
    // Each emits a quad for one face of a box.
    // Positive normal = outward-facing CCW winding. Negative = reversed.

    /**
     * UV rectangle: texture coordinate bounds for a quad face.
     *
     * @param u0 the minimum U coordinate
     * @param v0 the minimum V coordinate
     * @param u1 the maximum U coordinate
     * @param v1 the maximum V coordinate
     */
    public record UvRect(float u0, float v0, float u1, float v1) {}

    /**
     * Y-axis face (top when ny > 0, bottom when ny < 0).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    public static void faceY(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y,
            float z0, float z1, UvRect uv, float ny) {
        if (ny > 0) {
            faceYUp(pose, c, light, x0, x1, y, z0, z1, uv, ny);
        } else {
            faceYDown(pose, c, light, x0, x1, y, z0, z1, uv, ny);
        }
    }

    /**
     * Emits a top-facing Y quad with CCW winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    private static void faceYUp(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y,
            float z0, float z1, UvRect uv, float ny) {
        vertex(pose, c, light, x0, y, z0, uv.u0, uv.v0, 0f, ny, 0f);
        vertex(pose, c, light, x0, y, z1, uv.u0, uv.v1, 0f, ny, 0f);
        vertex(pose, c, light, x1, y, z1, uv.u1, uv.v1, 0f, ny, 0f);
        vertex(pose, c, light, x1, y, z0, uv.u1, uv.v0, 0f, ny, 0f);
    }

    /**
     * Emits a bottom-facing Y quad with reversed winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
    private static void faceYDown(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y,
            float z0, float z1, UvRect uv, float ny) {
        vertex(pose, c, light, x1, y, z0, uv.u1, uv.v0, 0f, ny, 0f);
        vertex(pose, c, light, x1, y, z1, uv.u1, uv.v1, 0f, ny, 0f);
        vertex(pose, c, light, x0, y, z1, uv.u0, uv.v1, 0f, ny, 0f);
        vertex(pose, c, light, x0, y, z0, uv.u0, uv.v0, 0f, ny, 0f);
    }

    /**
     * X-axis face (east when nx > 0, west when nx < 0).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    public static void faceX(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y0, float y1,
            float z0, float z1, UvRect uv, float nx) {
        if (nx > 0) {
            faceXEast(pose, c, light, x, y0, y1, z0, z1, uv, nx);
        } else {
            faceXWest(pose, c, light, x, y0, y1, z0, z1, uv, nx);
        }
    }

    /**
     * Emits an east-facing X quad with CCW winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    private static void faceXEast(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y0, float y1,
            float z0, float z1, UvRect uv, float nx) {
        vertex(pose, c, light, x, y1, z1, uv.u1, uv.v0, nx, 0f, 0f);
        vertex(pose, c, light, x, y0, z1, uv.u1, uv.v1, nx, 0f, 0f);
        vertex(pose, c, light, x, y0, z0, uv.u0, uv.v1, nx, 0f, 0f);
        vertex(pose, c, light, x, y1, z0, uv.u0, uv.v0, nx, 0f, 0f);
    }

    /**
     * Emits a west-facing X quad with reversed winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
    private static void faceXWest(PoseStack.Pose pose, VertexConsumer c,
            int light, float x, float y0, float y1,
            float z0, float z1, UvRect uv, float nx) {
        vertex(pose, c, light, x, y1, z0, uv.u1, uv.v0, nx, 0f, 0f);
        vertex(pose, c, light, x, y0, z0, uv.u1, uv.v1, nx, 0f, 0f);
        vertex(pose, c, light, x, y0, z1, uv.u0, uv.v1, nx, 0f, 0f);
        vertex(pose, c, light, x, y1, z1, uv.u0, uv.v0, nx, 0f, 0f);
    }

    /**
     * Z-axis face (south when nz > 0, north when nz < 0).
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    public static void faceZ(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y0,
            float y1, float z, UvRect uv, float nz) {
        if (nz > 0) {
            faceZSouth(pose, c, light, x0, x1, y0, y1, z, uv, nz);
        } else {
            faceZNorth(pose, c, light, x0, x1, y0, y1, z, uv, nz);
        }
    }

    /**
     * Emits a south-facing Z quad with CCW winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    private static void faceZSouth(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y0,
            float y1, float z, UvRect uv, float nz) {
        vertex(pose, c, light, x0, y1, z, uv.u1, uv.v0, 0f, 0f, nz);
        vertex(pose, c, light, x0, y0, z, uv.u1, uv.v1, 0f, 0f, nz);
        vertex(pose, c, light, x1, y0, z, uv.u0, uv.v1, 0f, 0f, nz);
        vertex(pose, c, light, x1, y1, z, uv.u0, uv.v0, 0f, 0f, nz);
    }

    /**
     * Emits a north-facing Z quad with reversed winding.
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
    private static void faceZNorth(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float x1, float y0,
            float y1, float z, UvRect uv, float nz) {
        vertex(pose, c, light, x1, y1, z, uv.u0, uv.v0, 0f, 0f, nz);
        vertex(pose, c, light, x1, y0, z, uv.u0, uv.v1, 0f, 0f, nz);
        vertex(pose, c, light, x0, y0, z, uv.u1, uv.v1, 0f, 0f, nz);
        vertex(pose, c, light, x0, y1, z, uv.u1, uv.v0, 0f, 0f, nz);
    }
}
