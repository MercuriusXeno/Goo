package com.xeno.goo.client.models;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.GooSnail;
import net.minecraft.client.renderer.entity.model.*;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class GooSnailModel extends EntityModel<GooSnail> {
    private final ModelRenderer bb_main;

    public GooSnailModel() {
        textureWidth = 64;
        textureHeight = 64;

        bb_main = new ModelRenderer(this);
        bb_main.setRotationPoint(0.0F, 24.0F, 0.0F);
        bb_main.setTextureOffset(0, 0).addBox(-2.0F, -11.0F, -3.0F, 4.0F, 8.0F, 8.0F, 0.0F, false);
        bb_main.setTextureOffset(10, 2).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 3.0F, 14.0F, 0.0F, false);
        bb_main.setTextureOffset(0, 0).addBox(-2.0F, -6.0F, -7.0F, 1.0F, 3.0F, 1.0F, 0.0F, false);
        bb_main.setTextureOffset(0, 0).addBox(1.0F, -6.0F, -7.0F, 1.0F, 3.0F, 1.0F, 0.0F, false);
    }

    @Override
    public void setRotationAngles(GooSnail entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

    }

    @Override
    public void render(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha){
        bb_main.render(matrixStack, buffer, packedLight, packedOverlay);
    }
}