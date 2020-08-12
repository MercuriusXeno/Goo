package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.util.Map;

public class StupidRenderer extends EntityRenderer<GooEntity> {

    public ResourceLocation texture;
    public StupidRenderer(EntityRendererManager manager) {

        super(manager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO.get(), StupidRenderer::new);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        this.texture = entity.goo.getFluid().getAttributes().getStillTexture();
        return texture;
    }

    private void add(IVertexBuilder renderer, MatrixStack stack, float x, float y, float z, float u, float v) {
        renderer.pos(stack.getLast().getMatrix(), x, y, z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .tex(u, v)
                .lightmap(0, 240)
                .normal(1, 0, 0)
                .endVertex();
    }

    private static float diffFunction(long time, long delta, float scale) {
        long dt = time % (delta * 2);
        if (dt > delta) {
            dt = 2*delta - dt;
        }
        return dt * scale;
    }

    Map<GooBase, ResourceLocation> spriteCache
    @Override
    public void render(GooEntity e, float yaw, float pTicks, MatrixStack matrixStack, IRenderTypeBuffer buf, int light)
    {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(texture);

        // short sync rhythm
        if (e.world.getDayTime() % 80 < 20) {
            TextureAtlasSprite sprite =
        } else if (e.world.getDayTime() % 80 < 40) {

        } else if (e.world.getDayTime() % 80 < 60) {

        } else if (e.world.getDayTime() % 80 < 80) {

        }
        IVertexBuilder builder = buf.getBuffer(RenderType.getTranslucent());

        long time = System.currentTimeMillis();
        float dx1 = diffFunction(time, 1000, 0.0001f);
        float dx2 = diffFunction(time, 1500, 0.00005f);
        float dx3 = diffFunction(time, 1200, 0.00011f);
        float dx4 = diffFunction(time, 1300, 0.00006f);
        float dy1 = diffFunction(time, 1400, 0.00009f);
        float dy2 = diffFunction(time, 1600, 0.00007f);
        float dy3 = diffFunction(time, 1000, 0.00015f);
        float dy4 = diffFunction(time, 1200, 0.00003f);

        float angle = (time / 100) % 360;
        Quaternion rotation = Vector3f.YP.rotationDegrees(angle);
        float scale = 1.0f + diffFunction(time,1000, 0.001f);

        matrixStack.push();
        matrixStack.translate(.5, .5, .5);
        matrixStack.rotate(rotation);
        matrixStack.scale(scale, scale, scale);
        matrixStack.translate(-.5, -.5, -.5);

        add(builder, matrixStack, 0 + dx1, 0 + dy1, .5f, sprite.getMinU(), sprite.getMinV());
        add(builder, matrixStack, 1 - dx2, 0 + dy2, .5f, sprite.getMaxU(), sprite.getMinV());
        add(builder, matrixStack, 1 - dx3, 1 - dy3, .5f, sprite.getMaxU(), sprite.getMaxV());
        add(builder, matrixStack, 0 + dx4, 1 - dy4, .5f, sprite.getMinU(), sprite.getMaxV());

        add(builder, matrixStack, 0 + dx4, 1 - dy4, .5f, sprite.getMinU(), sprite.getMaxV());
        add(builder, matrixStack, 1 - dx3, 1 - dy3, .5f, sprite.getMaxU(), sprite.getMaxV());
        add(builder, matrixStack, 1 - dx2, 0 + dy2, .5f, sprite.getMaxU(), sprite.getMinV());
        add(builder, matrixStack, 0 + dx1, 0 + dy1, .5f, sprite.getMinU(), sprite.getMinV());

        matrixStack.pop();
    }
}