package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.FluidCuboid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;

public class FluidCuboidHelper
{
    private static final int COLOR_PHASE_DURATION_IN_SECONDS = 2;
    private static final int COLOR_PHASES = 9;
    private static final int CYCLE_TIMER = 20 * COLOR_PHASE_DURATION_IN_SECONDS;
    private static final int FULL_CYCLE_TIME = CYCLE_TIMER * COLOR_PHASES;
    public static int colorizeChromaticGoo()
    {
        if (Minecraft.getInstance().world == null) {
            return 0xd0ffffff;
        }
        // cycle timer

        int ticks = (int)(Minecraft.getInstance().world.getGameTime() % FULL_CYCLE_TIME);
        return getChromaFromTime(ticks);
    }

    private static int getChromaFromTime(int ticks)
    {
        float progress = (ticks % CYCLE_TIMER) / (float)CYCLE_TIMER;
        int nextBracketTicks = ticks + CYCLE_TIMER > FULL_CYCLE_TIME ?
                ticks - (FULL_CYCLE_TIME - CYCLE_TIMER) : ticks + CYCLE_TIMER;
        int chroma1 = getChromaFromBracket(ticks);
        int chroma2 = getChromaFromBracket(nextBracketTicks);
        return interpolateChroma(progress, chroma1, chroma2);
    }

    private static int interpolateChroma(float progress, int chroma1, int chroma2)
    {
        int a1 = chroma1 >> 24 & 0xff;
        int r1 = chroma1 >> 16 & 0xff;
        int g1 = chroma1 >> 8 & 0xff;
        int b1 = chroma1 & 0xff;
        int a2 = chroma2 >> 24 & 0xff;
        int r2 = chroma2 >> 16 & 0xff;
        int g2 = chroma2 >> 8 & 0xff;
        int b2 = chroma2 & 0xff;

        return (interpolate(progress, a1, a2) << 24)
                | (interpolate(progress, r1, r2) << 16)
                | (interpolate(progress, g1, g2) << 8)
                | (interpolate(progress, b1, b2));
    }

    private static int interpolate(float progress, int c1, int c2)
    {
        return (int)Math.floor((float)(c2 - c1) * progress + (float)c1);
    }

    private static int getChromaFromBracket(int ticks)
    {
        // 5 seconds (100 ticks) to shift from
        // red
        if (ticks < (CYCLE_TIMER)) {
            return 0xd0ff0000;
        }
        // orange
        if (ticks < 2 * CYCLE_TIMER) {
            return 0xd0ff7700;
        }
        // yellow
        if (ticks < 3 * CYCLE_TIMER) {
            return 0xd0ffff00;
        }
        // yellow-green
        if (ticks < 4 * CYCLE_TIMER) {
            return 0xd077ff22;
        }
        // green
        if (ticks < 5 * CYCLE_TIMER) {
            return 0xd000ff00;
        }
        // blue/green!
        if (ticks < 6 * CYCLE_TIMER) {
            return 0xd000ffff;
        }
        // blue
        if (ticks < 7 * CYCLE_TIMER) {
            return 0xd00000ff;
        }
        // purple
        if (ticks < 8 * CYCLE_TIMER) {
            return 0xd09000cc;
        }
        // magenta
        if (ticks < 9 * CYCLE_TIMER) {
            return 0xd0ff00ff;
        }

        return 0xd0ffffff;
    }

    /**
     * Renders a full fluid cuboid for the given data
     * @param matrices  Matrix stack instance
     * @param buffer    Buffer type
     * @param still     Still sprite
     * @param flowing   Flowing sprite
     * @param cube      Fluid cuboid
     * @param from      Fluid start
     * @param to        Fluid end
     * @param color     Fluid color
     * @param light     Quad lighting
     * @param isGas     If true, fluid is a gas
     */
    public static void renderCuboid(MatrixStack matrices, IVertexBuilder buffer, FluidCuboid cube, TextureAtlasSprite still, TextureAtlasSprite flowing, Vector3f from, Vector3f to, int color, int light, boolean isGas) {
        Matrix4f matrix = matrices.getLast().getMatrix();
        int rotation = isGas ? 180 : 0;
        for (Direction dir : Direction.values()) {
            FluidCuboid.FluidFace face = cube.getFace(dir);
            if (face != null) {
                boolean isFlowing = face.isFlowing();
                int faceRot = (rotation + face.rotation()) % 360;
                putTexturedQuad(buffer, matrix, isFlowing ? flowing : still, from, to, dir, color, light, faceRot, isFlowing);
            }
        }
    }

    /**
     * Adds a quad to the renderer
     * @param buffer    Renderer instnace
     * @param matrix      Render matrix
     * @param sprite      Sprite to render
     * @param from        Quad start
     * @param to          Quad end
     * @param face        Face to render
     * @param color       Color to use in rendering
     * @param brightness  Face brightness
     * @param flowing     If true, half texture coordinates
     */
    public static void putTexturedQuad(IVertexBuilder buffer, Matrix4f matrix, TextureAtlasSprite sprite, Vector3f from, Vector3f to, Direction face, int color, int brightness, int rotation, boolean flowing) {
        // start with texture coordinates
        float x1 = from.getX(), y1 = from.getY(), z1 = from.getZ();
        float x2 = to.getX(), y2 = to.getY(), z2 = to.getZ();
        // choose UV based on opposite two axis
        float u1, u2, v1, v2;
        switch (face.getAxis()) {
            case Y:
            default:
                u1 = x1; u2 = x2;
                v1 = z2; v2 = z1;
                break;
            case Z:
                u1 = x2; u2 = x1;
                v1 = y1; v2 = y2;
                break;
            case X:
                u1 = z2; u2 = z1;
                v1 = y1; v2 = y2;
                break;
        }
        // flip V when relevant
        if (rotation == 0 || rotation == 270) {
            float temp = v1;
            v1 = 1f - v2;
            v2 = 1f - temp;
        }
        // flip U when relevant
        if (rotation >= 180) {
            float temp = u1;
            u1 = 1f - u2;
            u2 = 1f - temp;
        }
        // if rotating by 90 or 270, swap U and V
        float minU, maxU, minV, maxV;
        double size = flowing ? 8 : 16;
        if ((rotation % 180) == 90) {
            minU = sprite.getInterpolatedU(v1 * size);
            maxU = sprite.getInterpolatedU(v2 * size);
            minV = sprite.getInterpolatedV(u1 * size);
            maxV = sprite.getInterpolatedV(u2 * size);
        } else {
            minU = sprite.getInterpolatedU(u1 * size);
            maxU = sprite.getInterpolatedU(u2 * size);
            minV = sprite.getInterpolatedV(v1 * size);
            maxV = sprite.getInterpolatedV(v2 * size);
        }
        // based on rotation, put coords into place
        float u3, u4, v3, v4;
        switch(rotation) {
            case 0:
            default:
                u1 = minU; v1 = maxV;
                u2 = minU; v2 = minV;
                u3 = maxU; v3 = minV;
                u4 = maxU; v4 = maxV;
                break;
            case 90:
                u1 = minU; v1 = minV;
                u2 = maxU; v2 = minV;
                u3 = maxU; v3 = maxV;
                u4 = minU; v4 = maxV;
                break;
            case 180:
                u1 = maxU; v1 = minV;
                u2 = maxU; v2 = maxV;
                u3 = minU; v3 = maxV;
                u4 = minU; v4 = minV;
                break;
            case 270:
                u1 = maxU; v1 = maxV;
                u2 = minU; v2 = maxV;
                u3 = minU; v3 = minV;
                u4 = maxU; v4 = minV;
                break;
        }
        // add quads
        int light1 = brightness >> 0x10 & 0xFFFF;
        int light2 = brightness & 0xFFFF;
        int alpha = color >> 24 & 0xFF;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        switch (face) {
            case DOWN:
                buffer.pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
            case UP:
                buffer.pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
            case NORTH:
                buffer.pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
            case SOUTH:
                buffer.pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
            case WEST:
                buffer.pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
            case EAST:
                buffer.pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u1, v1).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u2, v2).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u3, v3).lightmap(light1, light2).endVertex();
                buffer.pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u4, v4).lightmap(light1, light2).endVertex();
                break;
        }
    }
}
