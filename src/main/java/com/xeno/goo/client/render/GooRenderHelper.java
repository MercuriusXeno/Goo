package com.xeno.goo.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class GooRenderHelper extends RenderState
{
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

    public GooRenderHelper(String nameIn, Runnable setupTaskIn, Runnable clearTaskIn)
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
