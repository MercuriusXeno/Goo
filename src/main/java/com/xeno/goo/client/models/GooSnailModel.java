package com.xeno.goo.client.models;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooSnail;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.model.*;
import net.minecraft.client.renderer.model.Model;
import net.minecraft.client.renderer.model.ModelRenderer;

import java.util.*;
import java.util.function.Function;

public class GooSnailModel extends EntityModel<GooSnail> {
    private float stretchRatio = 1f;
    private ModelRenderer body;
    private ModelRenderer shell;
    private ModelRenderer leftEye;
    private ModelRenderer rightEye;

    public GooSnailModel(boolean isHidden) {
        textureWidth = 64;
        textureHeight = 64;

        if (isHidden) {
            shell = new ModelRenderer(this);
            shell.setRotationPoint(0.0F, 24.0F, 0.0F);
            // shell only!
            shell.setTextureOffset(0, 0).addBox(-2.0F, -8.0F, -3.0F, 4.0F, 8.0F, 8.0F, 0.0F, false);
        } else {
            float snailDepth = 14f;
            // shell
            shell = new ModelRenderer(this);
            shell.setRotationPoint(0.0F, 24.0F, 0.0F);
            shell.setTextureOffset(0, 0).addBox(-2.0F, -11.0F, -3.0F, 4.0F, 8.0F, 8.0F, 0.0F, false);

            // body
            body = new ModelRenderer(this);
            body.setRotationPoint(0.0F, 24.0F, 0.0F);
            body.setTextureOffset(10, 2).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 3.0F, snailDepth, 0.0F, false);

            // eyes
            leftEye = new ModelRenderer(this);
            leftEye.setRotationPoint(0.0F, 24.0F, 0.0F);
            leftEye.setTextureOffset(0, 0).addBox(-2.0F, -6.0F, -7.0F, 1.0F, 3.0F, 1.0F, 0.0F, false);

            rightEye = new ModelRenderer(this);
            rightEye.setRotationPoint(0.0F, 24.0F, 0.0F);
            rightEye.setTextureOffset(0, 0).addBox(1.0F, -6.0F, -7.0F, 1.0F, 3.0F, 1.0F, 0.0F, false);
        }
    }

    @Override
    public void setRotationAngles(GooSnail entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        stretchRatio = entityIn.getBodyStretch();
    }

    private static final float snailDepth = 14f;
    @Override
    public void render(MatrixStack matrixStack, IVertexBuilder buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha){
        if (this.body != null) {
            // transform body based on stretch ratio
            matrixStack.push();
            matrixStack.scale(1f, 1f, stretchRatio);
            body.render(matrixStack, buffer, packedLight, packedOverlay);
            matrixStack.pop();
        }

        if (this.leftEye != null) {
            matrixStack.push();
            matrixStack.translate(0f, 0f, stretchRatio * snailDepth - snailDepth);
            leftEye.render(matrixStack, buffer, packedLight, packedOverlay);
            matrixStack.pop();
        }

        if (this.rightEye != null) {
            matrixStack.push();
            matrixStack.translate(0f, 0f, stretchRatio * snailDepth - snailDepth);
            rightEye.render(matrixStack, buffer, packedLight, packedOverlay);
            matrixStack.pop();
        }

        if (this.shell != null) {
            shell.render(matrixStack, buffer, packedLight, packedOverlay);
        }
    }
}