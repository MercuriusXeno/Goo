package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.entities.HexController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.*;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class RenderHelper extends RenderState
{
    private static final Vector3f NORMAL = new Vector3f(1, 1, 1);
    static {
        NORMAL.normalize();
    }
    public static final RenderType GOO_CUBE;
    public static final RenderType GOO_OVERLAY;
    public static final RenderType GOO_CUBE_BRIGHT;
    public static final int FULL_BRIGHT = 15728880;

    static {
        RenderState.DiffuseLightingState enableDiffuse = new RenderState.DiffuseLightingState(true);
        RenderState.DiffuseLightingState disableDiffuse = new RenderState.DiffuseLightingState(false);
        RenderState.OverlayState enableOverlay = new RenderState.OverlayState(true);
        RenderType.ShadeModelState notSmoothShade = new RenderState.ShadeModelState(false);
        RenderType.State brightCubeState = RenderType.State.getBuilder()
                .texture(RenderType.BLOCK_SHEET_MIPPED)
                .diffuseLighting(disableDiffuse)
                .overlay(enableOverlay)
                .shadeModel(RenderType.SHADE_DISABLED)
                .lightmap(RenderType.LIGHTMAP_ENABLED)
                .transparency(TRANSLUCENT_TRANSPARENCY)
                .build(true);
        GOO_CUBE_BRIGHT = RenderType.makeType(
                GooMod.MOD_ID + ":goo_cube_bright",
                DefaultVertexFormats.ENTITY, GL11.GL_QUADS, 128, true, true,
                brightCubeState);

        // GOO BLOCK
        /** Render type used for rendering fluids */
        GOO_CUBE = RenderType.makeType(
                GooMod.MOD_ID + ":goo_block",
                DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP, GL11.GL_QUADS, 256, true, true,
                RenderType.State.getBuilder()
                        .texture(RenderType.BLOCK_SHEET_MIPPED)
                        .diffuseLighting(disableDiffuse)
                        .shadeModel(notSmoothShade)
                        .cull(RenderType.CULL_DISABLED)
                        .lightmap(RenderType.LIGHTMAP_DISABLED)
                        .transparency(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .build(true));

        GOO_OVERLAY = RenderType.makeType(
                GooMod.MOD_ID + ":goo_overlay",
                DefaultVertexFormats.POSITION_COLOR_TEX, GL11.GL_QUADS, 256, true, false,
                RenderType.State.getBuilder()
                        .texture(RenderType.BLOCK_SHEET_MIPPED)
                        .shadeModel(RenderType.SHADE_DISABLED)
                        .lightmap(RenderType.LIGHTMAP_DISABLED)
                        .transparency(RenderType.NO_TRANSPARENCY)
                        .build(false));
    }

    @SuppressWarnings("deprecation")
    // stolen from elucent and baileyH, with love [EMBER_RENDER]
    public static final IParticleRenderType VAPOR_RENDER = new IParticleRenderType() {
        @Override
        public void beginRender(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.alphaFunc(GL11.GL_GREATER, 0.0f);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            // RenderSystem.enableCull();
            textureManager.bindTexture(AtlasTexture.LOCATION_PARTICLES_TEXTURE);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA.param, GlStateManager.DestFactor.ONE.param);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        }

        @Override
        public void finishRender(Tessellator tessellator) {
            tessellator.draw();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA.param, GlStateManager.DestFactor.ONE.param);
            RenderSystem.enableCull();
            RenderSystem.alphaFunc(GL11.GL_GREATER, 0.1F);
        }

        @Override
        public String toString() {
            return "goo:cloud_render";
        }
    };

    public RenderHelper(String nameIn, Runnable setupTaskIn, Runnable clearTaskIn)
    {
        super(nameIn, setupTaskIn, clearTaskIn);
    }


    private static Map<ResourceLocation, RenderType> GUI_RENDER_TYPES = new HashMap<>();
    public static RenderType getGui(ResourceLocation texture)
    {
        if (!GUI_RENDER_TYPES.containsKey(texture)) {
            GUI_RENDER_TYPES.put(texture, RenderType.makeType(
                    "gui_" + texture,
                    DefaultVertexFormats.POSITION_COLOR_TEX,
                    GL11.GL_QUADS,
                    256,
                    RenderType.State.getBuilder()
                            .texture(new TextureState(texture, false, false))
                            .alpha(new AlphaState(0.5F))
                            .build(false)));
        }

        return GUI_RENDER_TYPES.get(texture);
    }

    public static Vector3d lerpEntityPosition(float partialTicks, HexController entity)
    {
        return entity.getPositionVec().subtract(
                new Vector3d(MathHelper.lerp(partialTicks, entity.lastTickPosX, entity.getPosX()),
                        MathHelper.lerp(partialTicks, entity.lastTickPosY, entity.getPosY()),
                        MathHelper.lerp(partialTicks, entity.lastTickPosZ, entity.getPosZ()))
        );
    }

    public static Vector3d lerpEntityPosition(float partialTicks, Vector3d current, Vector3d previous)
    {
        return current.subtract(new Vector3d(MathHelper.lerp(partialTicks, previous.x, current.x),
                MathHelper.lerp(partialTicks, previous.y, current.y),
                MathHelper.lerp(partialTicks, previous.z, current.z)));
    }

    public static Quaternion yawToQuat(float rotationYaw)
    {
        return new Quaternion(new Vector3f(0f, 1f, 0f), rotationYaw, true);
    }

    public static Quaternion toQuaternion(float yaw, float pitch, float roll) // yaw (Z), pitch (Y), roll (X)
    {
        //Degree to radius:
        yaw = yaw * (float)Math.PI / 180f;
        pitch = pitch * (float)Math.PI / 180f;
        roll = roll * (float)Math.PI / 180f;


        // Abbreviations for the various angular functions
        float cy = MathHelper.cos(yaw * 0.5f);
        float sy = MathHelper.sin(yaw * 0.5f);
        float cp = MathHelper.cos(pitch * 0.5f);
        float sp = MathHelper.sin(pitch * 0.5f);
        float cr = MathHelper.cos(roll * 0.5f);
        float sr = MathHelper.sin(roll * 0.5f);

        Quaternion q = new Quaternion(
                cy * cp * sr - sy * sp * cr,
                sy * cp * sr + cy * sp * cr,
                sy * cp * cr - cy * sp * sr,
                cy * cp * cr + sy * sp * sr);
        return q;
    }

    public static void renderObject(Model3d object, MatrixStack matrix, IVertexBuilder buffer, int color, int light, int overlay) {
        renderObject(object, matrix, buffer, color, light, overlay, false);
    }

    public static void renderObject(Model3d object, MatrixStack matrix, IVertexBuilder buffer, int argb, int light, int overlay, boolean fakeDisableDiffuse) {
        if (object != null) {
            renderCube(object, matrix, buffer, argb, light, overlay, fakeDisableDiffuse);
        }
    }

    /**
     * @implNote From Tinker's
     */
    private static int calculateDelta(float min, float max) {
        //The texture can stretch over more blocks than the subtracted height is if min's decimal is bigger than max's decimal (causing UV over 1)
        // ignoring the decimals prevents this, as yd then equals exactly how many ints are between the two
        // for example, if max = 5.1 and min = 2.3, 2.8 (which rounds to 2), with the face array becoming 2.3, 3, 4, 5.1
        int delta = (int) (max - (int) min);
        // except in the rare case of max perfectly aligned with the block, causing the top face to render multiple times
        // for example, if max = 3 and min = 1, the values of the face array become 1, 2, 3, 3 as we then have middle ints
        if (max % 1d == 0) {
            delta--;
        }
        return delta;
    }


    /**
     * @implNote From Tinker's
     */
    private static float[] getBlockBounds(int delta, float start, float end) {
        float[] bounds = new float[2 + delta];
        bounds[0] = start;
        int offset = (int) start;
        for (int i = 1; i <= delta; i++) {
            bounds[i] = i + offset;
        }
        bounds[delta + 1] = end;
        return bounds;
    }

    /**
     * @implNote From Tinker's, Mekanism
     */
    public static void renderCube(Model3d cube, MatrixStack matrix, IVertexBuilder buffer, int argb, int light, int overlay, boolean fakeDisableDiffuse) {
        //TODO - 10.1: Further attempt to fix z-fighting at larger distances if we make it not render the sides when it is in a solid block
        // that may improve performance some, but definitely would reduce/remove the majority of remaining z-fighting that is going on
        //Shift it so that the min values are all greater than or equal to zero as the various drawing code
        // has some issues when it comes to handling negative numbers
        float xShift = MathHelper.floor(cube.minX);
        float yShift = MathHelper.floor(cube.minY);
        float zShift = MathHelper.floor(cube.minZ);
        matrix.push();
        matrix.translate(xShift, yShift, zShift);
        float minX = cube.minX - xShift;
        float minY = cube.minY - yShift;
        float minZ = cube.minZ - zShift;
        float maxX = cube.maxX - xShift;
        float maxY = cube.maxY - yShift;
        float maxZ = cube.maxZ - zShift;
        int xDelta = calculateDelta(minX, maxX);
        int yDelta = calculateDelta(minY, maxY);
        int zDelta = calculateDelta(minZ, maxZ);
        float[] xBounds = getBlockBounds(xDelta, minX, maxX);
        float[] yBounds = getBlockBounds(yDelta, minY, maxY);
        float[] zBounds = getBlockBounds(zDelta, minZ, maxZ);
        MatrixStack.Entry lastMatrix = matrix.getLast();
        Matrix4f matrix4f = lastMatrix.getMatrix();
        Matrix3f normalMatrix = lastMatrix.getNormal();
        Vector3f normal = fakeDisableDiffuse ? NORMAL : Vector3f.YP;
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f();
        // render each side
        for (int y = 0; y <= yDelta; y++) {
            SpriteInfo upSprite = y == yDelta ? cube.getSpriteToRender(Direction.UP) : null;
            SpriteInfo downSprite = y == 0 ? cube.getSpriteToRender(Direction.DOWN) : null;
            from.setY(yBounds[y]);
            to.setY(yBounds[y + 1]);
            for (int z = 0; z <= zDelta; z++) {
                SpriteInfo northSprite = z == 0 ? cube.getSpriteToRender(Direction.NORTH) : null;
                SpriteInfo southSprite = z == zDelta ? cube.getSpriteToRender(Direction.SOUTH) : null;
                from.setZ(zBounds[z]);
                to.setZ(zBounds[z + 1]);
                for (int x = 0; x <= xDelta; x++) {
                    SpriteInfo westSprite = x == 0 ? cube.getSpriteToRender(Direction.WEST) : null;
                    SpriteInfo eastSprite = x == xDelta ? cube.getSpriteToRender(Direction.EAST) : null;
                    //Set bounds
                    from.setX(xBounds[x]);
                    to.setX(xBounds[x + 1]);

                    putTexturedQuad(buffer, matrix4f, normalMatrix, westSprite, from, to, Direction.WEST, cube.westRotation(), argb, light, overlay, Direction.WEST.toVector3f(),
                            cube.westFlowing());
                    putTexturedQuad(buffer, matrix4f, normalMatrix, eastSprite, from, to, Direction.EAST, cube.eastRotation(), argb, light, overlay, Direction.EAST.toVector3f(),
                            cube.eastFlowing());
                    putTexturedQuad(buffer, matrix4f, normalMatrix, northSprite, from, to, Direction.NORTH, cube.northRotation(), argb, light, overlay, Direction.NORTH.toVector3f(),
                            cube.northFlowing());
                    putTexturedQuad(buffer, matrix4f, normalMatrix, southSprite, from, to, Direction.SOUTH, cube.southRotation(), argb, light, overlay, Direction.SOUTH.toVector3f(),
                            cube.southFlowing());
                    putTexturedQuad(buffer, matrix4f, normalMatrix, upSprite, from, to, Direction.UP, cube.upRotation(), argb, light, overlay, Direction.UP.toVector3f(),
                            cube.upFlowing());
                    putTexturedQuad(buffer, matrix4f, normalMatrix, downSprite, from, to, Direction.DOWN, cube.downRotation(), argb, light, overlay, Direction.DOWN.toVector3f(),
                            cube.downFlowing());
                }
            }
        }
        matrix.pop();
    }


    public static float getRed(int color) {
        return (color >> 16 & 0xFF) / 255.0F;
    }

    public static float getGreen(int color) {
        return (color >> 8 & 0xFF) / 255.0F;
    }

    public static float getBlue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    public static float getAlpha(int color) {
        return (color >> 24 & 0xFF) / 255.0F;
    }
    /**
     * @implNote From Mantle with some adjustments
     */
    private static void putTexturedQuad(IVertexBuilder buffer, Matrix4f matrix, Matrix3f normalMatrix, SpriteInfo spriteInfo, Vector3f from, Vector3f to,
            Direction face, int rotation, int argb, int light, int overlay, Vector3f normal, FluidType fluidType) {
        if (spriteInfo == null) {
            return;
        }
        // start with texture coordinates
        float x1 = from.getX(), y1 = from.getY(), z1 = from.getZ();
        float x2 = to.getX(), y2 = to.getY(), z2 = to.getZ();
        // choose UV based on opposite two axis
        float u1, u2, v1, v2;
        switch (face.getAxis()) {
            default:
            case Y:
                u1 = x1;
                u2 = x2;
                v1 = z2;
                v2 = z1;
                break;
            case Z:
                u1 = x2;
                u2 = x1;
                v1 = y1;
                v2 = y2;
                break;
            case X:
                u1 = z2;
                u2 = z1;
                v1 = y1;
                v2 = y2;
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

//        // wrap UV to be between 0 and 1, assumes none of the positions lie outside the 0,0,0 to 1,1,1 range
//        // however, one of them might be exactly on the 1.0 bound, that one should be set to 1 instead of left at 0
//        boolean bigger = u1 > u2;
//        u1 = u1 % 1;
//        u2 = u2 % 1;
//        if (bigger) {
//            if (u1 == 0) {
//                u1 = 1;
//            }
//        } else if (u2 == 0) {
//            u2 = 1;
//        }
//        bigger = v1 > v2;
//        v1 = v1 % 1;
//        v2 = v2 % 1;
//        if (bigger) {
//            if (v1 == 0) {
//                v1 = 1;
//            }
//        } else if (v2 == 0) {
//            v2 = 1;
//        }

        // if rotating by 90 or 270, swap U and V
        float minU, maxU, minV, maxV;
        // double size = fluidType == FluidType.STILL ? 8 : 16;
        if ((rotation % 180) == 90) {
            minU = spriteInfo.sprite.getInterpolatedU(v1 * spriteInfo.size);
            maxU = spriteInfo.sprite.getInterpolatedU(v2 * spriteInfo.size);
            minV = spriteInfo.sprite.getInterpolatedV(u1 * spriteInfo.size);
            maxV = spriteInfo.sprite.getInterpolatedV(u2 * spriteInfo.size);
        } else {
            minU = spriteInfo.sprite.getInterpolatedU(u1 * spriteInfo.size);
            maxU = spriteInfo.sprite.getInterpolatedU(u2 * spriteInfo.size);
            minV = spriteInfo.sprite.getInterpolatedV(v1 * spriteInfo.size);
            maxV = spriteInfo.sprite.getInterpolatedV(v2 * spriteInfo.size);
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
//
//        float minU = spriteInfo.sprite.getInterpolatedU(u1 * spriteInfo.size);
//        float maxU = spriteInfo.sprite.getInterpolatedU(u2 * spriteInfo.size);
//        float minV = spriteInfo.sprite.getInterpolatedV(v1 * spriteInfo.size);
//        float maxV = spriteInfo.sprite.getInterpolatedV(v2 * spriteInfo.size);
        float red = getRed(argb);
        float green = getGreen(argb);
        float blue = getBlue(argb);
        float alpha = getAlpha(argb);
        // add quads
        switch (face) {
            case DOWN:
                buffer.getVertexBuilder().pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
            case UP:
                buffer.getVertexBuilder().pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
            case NORTH:
                buffer.getVertexBuilder().pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
            case SOUTH:
                buffer.getVertexBuilder().pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
            case WEST:
                buffer.getVertexBuilder().pos(matrix, x1, y1, z2).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y2, z2).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y2, z1).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
            case EAST:
                buffer.getVertexBuilder().pos(matrix, x2, y1, z1).color(red, green, blue, alpha).tex(u1, v1).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z1).color(red, green, blue, alpha).tex(u2, v2).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(u3, v3).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                buffer.getVertexBuilder().pos(matrix, x2, y1, z2).color(red, green, blue, alpha).tex(u4, v4).overlay(overlay)
                        .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ())
                        .endVertex();
                break;
        }
    }

    private static void drawFace(IVertexBuilder buffer, Matrix4f matrix, Matrix3f normalMatrix, float red, float green, float blue, float alpha, float minU, float maxU,
            float minV, float maxV, int light, int overlay, Vector3f normal,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4) {
        buffer.getVertexBuilder().pos(matrix, x1, y1, z1).color(red, green, blue, alpha).tex(minU, maxV).overlay(overlay)
                .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.getVertexBuilder().pos(matrix, x2, y2, z2).color(red, green, blue, alpha).tex(minU, minV).overlay(overlay)
                .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.getVertexBuilder().pos(matrix, x3, y3, z3).color(red, green, blue, alpha).tex(maxU, minV).overlay(overlay)
                .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ()).endVertex();
        buffer.getVertexBuilder().pos(matrix, x4, y4, z4).color(red, green, blue, alpha).tex(maxU, maxV).overlay(overlay)
                .lightmap(light).normal(normalMatrix, normal.getX(), normal.getY(), normal.getZ()).endVertex();
    }

    public enum FluidType {
        UNSPECIFIED,
        STILL,
        FLOWING
    }

    public static TextureAtlasSprite getSprite(ResourceLocation spriteLocation) {
        return Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(spriteLocation);
    }

    public static TextureAtlasSprite getFluidTexture(FluidStack fluidStack, FluidType type) {
        Fluid fluid = fluidStack.getFluid();
        ResourceLocation spriteLocation;
        if (type == FluidType.STILL) {
            spriteLocation = fluid.getAttributes().getStillTexture(fluidStack);
        } else {
            spriteLocation = fluid.getAttributes().getFlowingTexture(fluidStack);
        }
        return getSprite(spriteLocation);
    }
}
