package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.*;
import net.minecraft.world.gen.SimplexNoiseGenerator;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.util.*;

import static java.lang.Math.PI;
import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class GooRenderer extends EntityRenderer<GooEntity>
{
    public static final float CIRCUMSCRIBED_RADIUS_TO_EDGE_RATIO = 0.9510565f;
    public static final float VOLUME_TO_CUBIC_EDGE_COEFFICIENT = 2.1816949f;
    // public static final double GOLDEN_RATIO = (1d + Math.sqrt(5d)) / 2d;

    public static float obtainRadiusByVolume(float volume) {
        // shrink it by 10d because it's a minecraft cube; 1000 mB = 1 b
        // 1000 cuberoot = 10, not 1. So divide it by 10.
        float a = (float)Math.cbrt(volume / VOLUME_TO_CUBIC_EDGE_COEFFICIENT) / 10f;
        return a * CIRCUMSCRIBED_RADIUS_TO_EDGE_RATIO;
    }

    public GooRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    @Nonnull
    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return PlayerContainer.LOCATION_BLOCKS_TEXTURE;
    }

    @Override
    public void render(GooEntity entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer bufferType, int light)
    {
        stack.push();
        RenderType rType = RenderHelper.GOO;
        IVertexBuilder buffer = bufferType.getBuffer(rType);
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlasTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE).getSprite(entity.goo.getFluid().getAttributes().getStillTexture());

        Vector3d interpVec = RenderHelper.lerpEntityPosition(partialTicks, entity);
        stack.translate(interpVec.x, interpVec.y, interpVec.z);
        // Texture and noise gen should be stored so they aren't remade every frame...
        SimplexNoiseGenerator sgen = new SimplexNoiseGenerator(new Random(entity.getPosition().toLong()));
        float f = MathHelper.interpolateAngle(partialTicks, entity.prevRotationYaw, entity.rotationYaw);
        float f1 = MathHelper.interpolateAngle(partialTicks, entity.prevRotationPitch, entity.rotationPitch);
        this.applyRotations(stack, f, f1);
        Matrix4f matrix = stack.getLast().getMatrix();

        Vector3d[] vertices = generateIcosahedralVertices(sgen, entity);

        // ten triangles that point up and use the same UV mappings - takes texture res as arg
        Triangle[] upTriangles = upTriangles(vertices, sprite);

        // ten triangles that point down and use the same UV mappings - takes texture res as arg
        Triangle[] downTriangles = downTriangles(vertices, sprite);

        for (Triangle[] a : new Triangle[][] { upTriangles, downTriangles }) {
            for (Triangle t : a) {
                buildTriangle(matrix, buffer, t, light);
            }
        }
        stack.pop();
    }

    protected void applyRotations(MatrixStack matrixStackIn, float rotationYaw, float rotationPitch) {
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(90 - MathHelper.wrapDegrees(rotationYaw)));
        matrixStackIn.rotate(Vector3f.ZP.rotationDegrees(MathHelper.wrapDegrees(rotationPitch)));
    }

    private void buildTriangle(Matrix4f matrix, IVertexBuilder buffer, Triangle t, int light)
    {
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v1.x, (float)t.v1.y, (float)t.v1.z)
                .color(1f, 1f, 1f, 1f)
                .tex(t.uv1.x, t.uv1.y)
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v1.normalize().x, (float)t.v1.normalize().y, (float)t.v1.normalize().z)
                .endVertex();
        // added twice because I do not know how to use the triangle render mode and I'm very not smart.
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v1.x, (float)t.v1.y, (float)t.v1.z)
                .color(1f, 1f, 1f, 1f)
                .tex(t.uv1.x, t.uv1.y)
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v1.normalize().x, (float)t.v1.normalize().y, (float)t.v1.normalize().z)
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v2.x, (float)t.v2.y, (float)t.v2.z)
                .color(1f, 1f, 1f, 1f)
                .tex(t.uv2.x, t.uv2.y)
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v2.normalize().x, (float)t.v2.normalize().y, (float)t.v2.normalize().z)
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v3.x, (float)t.v3.y, (float)t.v3.z)
                .color(1f, 1f, 1f, 1f)
                .tex(t.uv3.x, t.uv3.y)
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v3.normalize().x, (float)t.v3.normalize().y, (float)t.v3.normalize().z)
                .endVertex();
    }

    private Triangle[] upTriangles(Vector3d[] v, TextureAtlasSprite sprite)
    {
        return new Triangle[] {
                // top segment
                new Triangle(v[0], v[1], v[2], sprite, true),
                new Triangle(v[0], v[2], v[3], sprite, true),
                new Triangle(v[0], v[3], v[4], sprite, true),
                new Triangle(v[0], v[4], v[5], sprite, true),
                new Triangle(v[0], v[5], v[1], sprite, true),
                // third row
                new Triangle(v[5], v[9], v[10], sprite, true),
                new Triangle(v[1], v[10], v[6], sprite, true),
                new Triangle(v[2], v[6], v[7], sprite, true),
                new Triangle(v[3], v[7], v[8], sprite, true),
                new Triangle(v[4], v[8], v[9], sprite, true)
        };
    }

    private Triangle[] downTriangles(Vector3d[] v, TextureAtlasSprite sprite)
    {
        return new Triangle[] {
                // second row
                new Triangle(v[9], v[5], v[4], sprite, false),
                new Triangle(v[10], v[1], v[5], sprite, false),
                new Triangle(v[6], v[2], v[1], sprite, false),
                new Triangle(v[7], v[3], v[2], sprite, false),
                new Triangle(v[8], v[4], v[3], sprite, false),
                // bottom segment
                new Triangle(v[11], v[7], v[6], sprite, false),
                new Triangle(v[11], v[8], v[7], sprite, false),
                new Triangle(v[11], v[9], v[8], sprite, false),
                new Triangle(v[11], v[10], v[9], sprite, false),
                new Triangle(v[11], v[6], v[10], sprite, false)
        };
    }

    public class Triangle {
        public static final float EDGE_TO_ALTITUDE_EQUILATERAL_RATIO = 0.8660254f;
        // public final boolean isPointingUp;
        public final Vector3d v1;
        public final Vector3d v2;
        public final Vector3d v3;
        public final Vector2f uv1; // UV mapping of the first vertex
        public final Vector2f uv2; // UV mapping of the second vertex
        public final Vector2f uv3; // UV mapping of the third vertex
        public Triangle(Vector3d v1, Vector3d v2, Vector3d v3, TextureAtlasSprite sprite, boolean isPointingUp) {
            float h = equilateralHeight(EDGE_TO_ALTITUDE_EQUILATERAL_RATIO);
            // this.isPointingUp = isPointingUp;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            if (isPointingUp) {
                // triangle pointing up means the "starting" vertex is at the top, otherwise it's at the bottom.
                // always anticlockwise
                this.uv1 = new Vector2f(sprite.getInterpolatedU(16f / 2f), sprite.getInterpolatedV(h * 16f));
                this.uv2 = new Vector2f(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(0f));
                this.uv3 = new Vector2f(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(16f));
            } else {
                // triangle pointing down means the starting vertex is at the bottom and the two
                // vertex are at the h line.
                this.uv1 = new Vector2f(sprite.getInterpolatedU(16f), sprite.getInterpolatedV(h * 16f));
                this.uv2 = new Vector2f(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(h * 16f));
                this.uv3 = new Vector2f(sprite.getInterpolatedU(16f / 2f), sprite.getInterpolatedV( 0));
            }
        }

        private float equilateralHeight(float textureResolution)
        {
            return (textureResolution * EDGE_TO_ALTITUDE_EQUILATERAL_RATIO);
        }
    }

    public static float remap(float value, float currentLow, float currentHigh, float newLow, float newHigh) {
        return newLow + (value - currentLow) * (newHigh - newLow) / (currentHigh - currentLow);
    }

    private Vector3d[] generateIcosahedralVertices(SimplexNoiseGenerator sgen, GooEntity entity)
    {
        float gameTime = (((float) Minecraft.getInstance().world.getGameTime())) / 25f;
        float volume = entity.goo.getAmount();
        float radius = obtainRadiusByVolume(volume);
        float waveform = remap(MathHelper.sin(gameTime), -1, 1, 0.1f, 0.2f) + remap(MathHelper.cos(gameTime), -1, 1, 0.1f, 0.2f);
        float range = (radius / 10f) * waveform;

        float H_ANGLE = (float)(PI / 180f * 72f);    // 72 degree = 360 / 5
        float V_ANGLE = (float)(Math.atan(1.0f / 2f));  // elevation = 26.565 degree

        Vector3d[] vertices = new Vector3d[12];    // array of 12 vertices (x,y,z)
        float longitude, latitude;                            // coords
        float hAngle1 = (float)(-PI / 2f - H_ANGLE / 2f);  // start from -126 deg at 1st row
        float hAngle2 = (float)(-PI / 2f);                // start from -90 deg at 2nd row

        // the first top vertex at (0, 0, r)
        vertices[0] = new Vector3d(0, radius, 0);

        // compute 10 vertices at 1st and 2nd rows
        for(int i = 1; i <= 5; ++i)
        {
            longitude  = radius * (float)Math.sin(V_ANGLE);            // elevaton
            latitude = radius * (float)Math.cos(V_ANGLE);            // length on XY plane
            float x1 = latitude * (float)Math.cos(hAngle1);
            float y1 = longitude;
            float z1 = latitude * (float)Math.sin(hAngle1);
            float x2 = latitude * (float)Math.cos(hAngle2);
            float y2 = -longitude;
            float z2 = latitude * (float)Math.sin(hAngle2);
            // tranlate coordinates using sgen, range and remap
            float xx1 = remap((float)sgen.func_227464_a_(x1, y1, z1 + gameTime), -1, 1f, -range, range);
            float yy1 = remap((float)sgen.func_227464_a_(x1 + gameTime, y1, z1 + gameTime), -1, 1f, -range, range);
            float zz1 = remap((float)sgen.func_227464_a_(x1 + gameTime, y1, z1), -1, 1f, -range, range);
            float xx2 = remap((float)sgen.func_227464_a_(x2, y2, z2 + gameTime), -1, 1f, -range, range);
            float yy2 = remap((float)sgen.func_227464_a_(x2 + gameTime, y2, z2 + gameTime), -1, 1f, -range, range);
            float zz2 = remap((float)sgen.func_227464_a_(x2 + gameTime, y2, z2), -1, 1f, -range, range);
            vertices[i] = new Vector3d(x1 + xx1, y1 + yy1, z1 + zz1);
            vertices[i + 5] = new Vector3d(x2 + xx2, y2 + yy2, z2 + zz2);

            // next horizontal angles
            hAngle1 += H_ANGLE;
            hAngle2 += H_ANGLE;
        }

        // the last bottom vertex at (0, 0, -r)
        vertices[11] = new Vector3d(0, -radius, 0);
        return vertices;
    }
}
