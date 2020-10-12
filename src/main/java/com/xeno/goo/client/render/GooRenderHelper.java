package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;

public class GooRenderHelper extends RenderState
{
    public static final RenderType GOO_CUBE;
    public static final RenderType GOO_OVERLAY;
    public static final RenderType GOO_CUBE_BRIGHT;
    public static final int FULL_BRIGHT = 15728880;

    static {
        // GOO
        // todo 1.16 update to match vanilla where necessary (alternate render targets, etc.)
        RenderState.DiffuseLightingState enableDiffuse = new RenderState.DiffuseLightingState(true);
        RenderState.DiffuseLightingState disableDiffuse = new RenderState.DiffuseLightingState(false);
        RenderState.OverlayState enableOverlay = new RenderState.OverlayState(true);
        RenderState.CullState disableCull = new RenderState.CullState(false);
        RenderType.ShadeModelState notSmoothShade = new RenderState.ShadeModelState(false);
        RenderType.State brightCubeState = RenderType.State.getBuilder()
                .texture(new RenderState.TextureState(PlayerContainer.LOCATION_BLOCKS_TEXTURE, false, false))
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
                        .texture(new RenderState.TextureState(PlayerContainer.LOCATION_BLOCKS_TEXTURE, false, false))
                        .diffuseLighting(enableDiffuse)
                        .shadeModel(RenderType.SHADE_ENABLED)
                        .lightmap(RenderType.LIGHTMAP_ENABLED)
                        .transparency(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .build(true));

        GOO_OVERLAY = RenderType.makeType(
                GooMod.MOD_ID + ":goo_overlay",
                DefaultVertexFormats.POSITION_COLOR_TEX, GL11.GL_QUADS, 256, true, false,
                RenderType.State.getBuilder().texture(new RenderState.TextureState(PlayerContainer.LOCATION_BLOCKS_TEXTURE, false, false))
                    .shadeModel(RenderType.SHADE_DISABLED)
                    .lightmap(RenderType.LIGHTMAP_DISABLED)
                    .texture(RenderType.BLOCK_SHEET)
                    .transparency(RenderType.NO_TRANSPARENCY)
                    .build(false));
    }

    public GooRenderHelper(String nameIn, Runnable setupTaskIn, Runnable clearTaskIn)
    {
        super(nameIn, setupTaskIn, clearTaskIn);
    }

    public static Vector3d lerpEntityPosition(float partialTicks, GooBlob entity)
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
}
