package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.GooEntityModel;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.library.AngleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class GooEntityRenderer extends EntityRenderer<GooEntity> implements IEntityRenderer<GooEntity, GooEntityModel>
{
    private final GooEntityModel entityModel;
    private List<LayerRenderer<GooEntity, GooEntityModel>> layerRenderers;
    public final boolean addLayer(LayerRenderer<GooEntity, GooEntityModel> layer) {
        return this.layerRenderers.add(layer);
    }

    public GooEntityRenderer(EntityRendererManager renderManager, GooEntityModel m)
    {
        super(renderManager);
        this.entityModel = new GooEntityModel(16);
        layerRenderers = new ArrayList<>();
        this.addLayer(new GooEntityLayer(this));
    }

    @Override
    public void render(GooEntity e, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        bufferIn.getBuffer(RenderType.getEntityTranslucent(e.gooBase().getEntityTexture()));

        matrixStackIn.push();
        double x = MathHelper.lerp(partialTicks, e.lastTickPosX, e.getPosX());
        double y = MathHelper.lerp(partialTicks, e.lastTickPosY, e.getPosY());
        double z = MathHelper.lerp(partialTicks, e.lastTickPosZ, e.getPosZ());
        // set position for the renderer with a lerp of the position
        Vector3d lerp = new Vector3d(x, y, z);
        matrixStackIn.translate(lerp.x - e.lastTickPosX,lerp.y - e.lastTickPosY, lerp.z - e.lastTickPosZ);
        matrixStackIn.scale(e.sizeRatio(), e.sizeRatio(), e.sizeRatio());

        // GooMod.debug("Rotation Pitch " + e.rotationPitch + " Yaw " + e.rotationYaw);
        // matrixStackIn.rotate(AngleHelper.rotateToMatchLookVector(e.rotationPitch, e.rotationYaw));
        float f = MathHelper.interpolateAngle(partialTicks, e.prevRotationYaw, e.rotationYaw);
        float f1 = MathHelper.interpolateAngle(partialTicks, e.prevRotationPitch, e.rotationPitch);
        float f2 = f1 - f;

        float f6 = MathHelper.lerp(partialTicks, e.prevRotationPitch, e.rotationPitch);
        float f7 = (float)e.ticksExisted + partialTicks;
        this.applyRotations(matrixStackIn, e.rotationYaw, e.rotationPitch);
        matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
        this.preRenderCallback(e, matrixStackIn, partialTicks);
        matrixStackIn.translate(0.0D, (double)-1.501F, 0.0D);
        float f8 = 0.0F;
        float f5 = 0.0F;

        this.entityModel.setLivingAnimations(e, f5, f8, partialTicks);
        this.entityModel.setRotationAngles(e, f5, f8, f7, f2, f6);
        Minecraft minecraft = Minecraft.getInstance();
        boolean flag = true;
        boolean flag1 = false;
        boolean flag2 = minecraft.func_238206_b_(e);
        RenderType rendertype = getRenderType(e, flag, flag1, flag2);
        if (rendertype != null) {
            IVertexBuilder ivertexbuilder = bufferIn.getBuffer(rendertype);
            int i = getPackedOverlay(e, this.getOverlayProgress(e, partialTicks));
            this.entityModel.render(matrixStackIn, ivertexbuilder, packedLightIn, i, 1.0F, 1.0F, 1.0F, flag1 ? 0.15F : 1.0F);
        }

        if (!e.isSpectator()) {
            for(LayerRenderer<GooEntity, GooEntityModel> layerRenderer : this.layerRenderers) {
                layerRenderer.render(matrixStackIn, bufferIn, packedLightIn, e, f5, f8, partialTicks, f7, f2, f6);
            }
        }

        matrixStackIn.pop();
        // super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }

    private RenderType getRenderType(GooEntity entityIn, boolean visible, boolean notVisibleButNotInvisible, boolean funkyGlowingFlag)
    {
        ResourceLocation resourcelocation = this.getEntityTexture(entityIn);
        if (notVisibleButNotInvisible) {
            return RenderType.func_239268_f_(resourcelocation);
        } else if (visible) {
            return this.entityModel.getRenderType(resourcelocation);
        } else {
            return funkyGlowingFlag ? RenderType.getOutline(resourcelocation) : null;
        }
    }

    public static int getPackedOverlay(GooEntity livingEntityIn, float uIn) {
        return OverlayTexture.getPackedUV(OverlayTexture.getU(uIn), OverlayTexture.getV(false));
    }

    protected float getOverlayProgress(GooEntity e, float partialTicks) {
        return 0.0F;
    }

    private void preRenderCallback(GooEntity entityIn, MatrixStack matrixStackIn, float partialTicks)
    {
        // NO OP
    }

    protected void applyRotations(MatrixStack matrixStackIn, float rotationYaw, float rotationPitch) {
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(90 - MathHelper.wrapDegrees(rotationYaw)));
        matrixStackIn.rotate(Vector3f.ZP.rotationDegrees(MathHelper.wrapDegrees(rotationPitch)));
    }

    @Override
    public GooEntityModel getEntityModel()
    {
        return this.entityModel;
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return entity.texture();
    }
}
