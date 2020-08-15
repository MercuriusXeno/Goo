package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.GooEntityModel;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.entity.model.SlimeModel;

public class GooEntityLayer extends LayerRenderer<GooEntity, GooEntityModel>
{
    private final GooEntityModel gooModel = new GooEntityModel(0);
    public GooEntityLayer(GooEntityRenderer gooEntityRenderer) {super(gooEntityRenderer);}

    @Override
    public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, GooEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch)
    {
        if (!entity.isInvisible()) {
            this.getEntityModel().copyModelAttributesTo(this.gooModel);
            this.gooModel.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTicks);
            this.gooModel.setRotationAngles(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            IVertexBuilder ivertexbuilder = bufferIn.getBuffer(RenderType.getEntityTranslucent(this.getEntityTexture(entity)));
            this.gooModel.render(matrixStackIn, ivertexbuilder, packedLightIn, GooEntityRenderer.getPackedOverlay(entity, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
