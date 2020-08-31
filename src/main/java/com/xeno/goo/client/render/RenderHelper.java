package com.xeno.goo.client.render;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;

public class RenderHelper extends RenderState
{
    public static final RenderType GOO;

    public static final RenderType GOO_BLOCK;

    public static final RenderType GOO_OVERLAY;
    static {
        // GOO
        // todo 1.16 update to match vanilla where necessary (alternate render targets, etc.)
        RenderState.DiffuseLightingState enableDiffuse = new RenderState.DiffuseLightingState(true);
        RenderState.OverlayState enableOverlay = new RenderState.OverlayState(true);
        RenderState.CullState disableCull = new RenderState.CullState(false);

//        RenderState.WriteMaskState colorMask = new RenderState.WriteMaskState(true, false);
        RenderType.ShadeModelState smoothShade = new RenderState.ShadeModelState(true);
        RenderType.State glState = RenderType.State.getBuilder()
                .texture(new RenderState.TextureState(PlayerContainer.LOCATION_BLOCKS_TEXTURE, false, false))
                .transparency(TRANSLUCENT_TRANSPARENCY)
//                .writeMask(colorMask)
                .diffuseLighting(enableDiffuse)
                .cull(disableCull)
                .overlay(enableOverlay)
                .shadeModel(smoothShade)
                .build(true);
        GOO = RenderType.makeType(GooMod.MOD_ID + ":goo", DefaultVertexFormats.ENTITY, GL11.GL_TRIANGLES, 128, true, false, glState);
        //GOO = RenderType.makeType(GooMod.MOD_ID + ":goo", DefaultVertexFormats.ENTITY, GL11.GL_QUADS, 128, true, true, glState);

        // GOO BLOCK
        /** Render type used for rendering fluids */
        GOO_BLOCK = RenderType.makeType(
                GooMod.MOD_ID + ":goo_block",
                DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP, GL11.GL_QUADS, 256, true, false,
                RenderType.State.getBuilder().texture(new RenderState.TextureState(PlayerContainer.LOCATION_BLOCKS_TEXTURE, false, false))
                        .shadeModel(RenderType.SHADE_ENABLED)
                        .lightmap(RenderType.LIGHTMAP_ENABLED)
                        .texture(RenderType.BLOCK_SHEET_MIPPED)
                        .transparency(RenderType.TRANSLUCENT_TRANSPARENCY)
                        .build(false));

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

    public RenderHelper(String nameIn, Runnable setupTaskIn, Runnable clearTaskIn)
    {
        super(nameIn, setupTaskIn, clearTaskIn);
    }

    public static Vector3d lerpEntityPosition(float partialTicks, GooEntity entity)
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
}
