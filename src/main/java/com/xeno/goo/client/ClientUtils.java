package com.xeno.goo.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.math.vector.Matrix4f;

public class ClientUtils {

    public static void drawTexturedRect(IVertexBuilder builder, MatrixStack transform, float x, float y, float w, float h,
                                        float r, float g, float b, float alpha, float u0, float u1, float v0, float v1)
    {
        Matrix4f mat = transform.getLast().getMatrix();
        builder.pos(mat, x, y+h, 0)
                .color(r, g, b, alpha)
                .tex(u0, v1)
                .overlay(OverlayTexture.NO_OVERLAY)
                .lightmap(0xf000f0)
                .normal(1, 1, 1)
                .endVertex();
        builder.pos(mat, x+w, y+h, 0)
                .color(r, g, b, alpha)
                .tex(u1, v1)
                .overlay(OverlayTexture.NO_OVERLAY)
                .lightmap(15728880)
                .normal(1, 1, 1)
                .endVertex();
        builder.pos(mat, x+w, y, 0)
                .color(r, g, b, alpha)
                .tex(u1, v0)
                .overlay(OverlayTexture.NO_OVERLAY)
                .lightmap(15728880)
                .normal(1, 1, 1)
                .endVertex();
        builder.pos(mat, x, y, 0)
                .color(r, g, b, alpha)
                .tex(u0, v0)
                .overlay(OverlayTexture.NO_OVERLAY)
                .lightmap(15728880)
                .normal(1, 1, 1)
                .endVertex();
    }

    public static void drawTexturedRect(IVertexBuilder builder, MatrixStack transform, int x, int y, int w, int h, float picSize,
                                        int u0, int u1, int v0, int v1)
    {
        drawTexturedRect(builder, transform, x, y, w, h, 1, 1, 1, 1, u0/picSize, u1/picSize, v0/picSize, v1/picSize);
    }
}
