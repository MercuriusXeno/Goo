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
        // pre-calculate the offset of the body stretch.
        // as the body stretches, the eyes and body are offset by a preset Z position to give the illusion of slinking.
        // when the "stretch" is 1.0 (not stretched), this Offset should be zero.
        // when the "stretch" offset is at its peak, this offset should be half the difference in size between 1.0 and peak.
        double scaledDepth = snailDepth / 16d;
        double bodyOffset = -((stretchRatio - 1.0d) * scaledDepth / 2d);
        double eyesOffset = -((stretchRatio - 1.0d) * scaledDepth);
        if (this.body != null) {
            // transform body based on stretch ratio
            matrixStack.push();
            matrixStack.translate(0f, 0f, bodyOffset);
            matrixStack.scale(1f, 1f, stretchRatio);
            body.render(matrixStack, buffer, packedLight, packedOverlay);
            matrixStack.pop();
        }

        if (this.leftEye != null && this.rightEye != null) {
            matrixStack.push();
            matrixStack.translate(0f, 0f, eyesOffset);
            leftEye.render(matrixStack, buffer, packedLight, packedOverlay);
            rightEye.render(matrixStack, buffer, packedLight, packedOverlay);
            matrixStack.pop();
        }

        if (this.shell != null) {
            shell.render(matrixStack, buffer, packedLight, packedOverlay);
        }
    }
}