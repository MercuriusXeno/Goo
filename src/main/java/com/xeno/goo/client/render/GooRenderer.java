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

import java.util.*;

import static java.lang.Math.PI;
import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class GooRenderer extends EntityRenderer<GooEntity>
{
    public static final float CIRCUMSCRIBED_RADIUS_TO_EDGE_RATIO = 0.9510565f;
    public static final float VOLUME_TO_CUBIC_EDGE_COEFFICIENT = 2.1816949f;
    public static final Vector3d[] ICOSAHEDRAL_VERTICES;
    public static final Triangle[] UNIT_TRIANGLES;
    static {
        ICOSAHEDRAL_VERTICES = generateIcosahedralVertices();
        UNIT_TRIANGLES = makeTriangles(2);
    }

    private static Vector3d computeHalfVertex(Vector3d v1, Vector3d v2)
    {
        double newX = v1.x + v2.x;
        double newY = v1.y + v2.y;
        double newZ = v1.z + v2.z;
        // float scale = CIRCUMSCRIBED_RADIUS_TO_EDGE_RATIO / (float)Math.sqrt(newX * newX + newY *newY + newZ * newZ);
        float scale = 1f / (float)Math.sqrt(newX * newX + newY *newY + newZ * newZ);
        return new Vector3d(newX, newY, newZ).scale(scale);
    }

    private static Triangle[] makeTriangles(int depth)
    {
        Triangle[] initialSet = new Triangle[] {
                // top segment
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[0], GooRenderer.ICOSAHEDRAL_VERTICES[1], GooRenderer.ICOSAHEDRAL_VERTICES[2]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[0], GooRenderer.ICOSAHEDRAL_VERTICES[2], GooRenderer.ICOSAHEDRAL_VERTICES[3]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[0], GooRenderer.ICOSAHEDRAL_VERTICES[3], GooRenderer.ICOSAHEDRAL_VERTICES[4]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[0], GooRenderer.ICOSAHEDRAL_VERTICES[4], GooRenderer.ICOSAHEDRAL_VERTICES[5]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[0], GooRenderer.ICOSAHEDRAL_VERTICES[5], GooRenderer.ICOSAHEDRAL_VERTICES[1]),
                // second row
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[9], GooRenderer.ICOSAHEDRAL_VERTICES[5], GooRenderer.ICOSAHEDRAL_VERTICES[4]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[10], GooRenderer.ICOSAHEDRAL_VERTICES[1], GooRenderer.ICOSAHEDRAL_VERTICES[5]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[6], GooRenderer.ICOSAHEDRAL_VERTICES[2], GooRenderer.ICOSAHEDRAL_VERTICES[1]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[7], GooRenderer.ICOSAHEDRAL_VERTICES[3], GooRenderer.ICOSAHEDRAL_VERTICES[2]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[8], GooRenderer.ICOSAHEDRAL_VERTICES[4], GooRenderer.ICOSAHEDRAL_VERTICES[3]),
                // third row
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[5], GooRenderer.ICOSAHEDRAL_VERTICES[9], GooRenderer.ICOSAHEDRAL_VERTICES[10]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[1], GooRenderer.ICOSAHEDRAL_VERTICES[10], GooRenderer.ICOSAHEDRAL_VERTICES[6]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[2], GooRenderer.ICOSAHEDRAL_VERTICES[6], GooRenderer.ICOSAHEDRAL_VERTICES[7]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[3], GooRenderer.ICOSAHEDRAL_VERTICES[7], GooRenderer.ICOSAHEDRAL_VERTICES[8]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[4], GooRenderer.ICOSAHEDRAL_VERTICES[8], GooRenderer.ICOSAHEDRAL_VERTICES[9]),
                // bottom segment
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[11], GooRenderer.ICOSAHEDRAL_VERTICES[7], GooRenderer.ICOSAHEDRAL_VERTICES[6]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[11], GooRenderer.ICOSAHEDRAL_VERTICES[8], GooRenderer.ICOSAHEDRAL_VERTICES[7]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[11], GooRenderer.ICOSAHEDRAL_VERTICES[9], GooRenderer.ICOSAHEDRAL_VERTICES[8]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[11], GooRenderer.ICOSAHEDRAL_VERTICES[10], GooRenderer.ICOSAHEDRAL_VERTICES[9]),
                new Triangle(GooRenderer.ICOSAHEDRAL_VERTICES[11], GooRenderer.ICOSAHEDRAL_VERTICES[6], GooRenderer.ICOSAHEDRAL_VERTICES[10])
        };
        List<Triangle> results = new ArrayList<>(Arrays.asList(initialSet));
        for(int i = 0; i < depth; i++) {
            results.addAll(fragmentToDepth(results));
        }

        return results.toArray(new Triangle[0]);
    }

    private static List<Triangle> fragmentToDepth(List<Triangle> initialSet)
    {
        List<Triangle> results = new ArrayList<>();
        for(Triangle t : initialSet) {
            Vector3d v1 = computeHalfVertex(t.v1, t.v2);
            Vector3d v2 = computeHalfVertex(t.v2, t.v3);
            Vector3d v3 = computeHalfVertex(t.v3, t.v1);
            // central bisection
            results.add(new Triangle(v1, v2, v3));
            // first bisection
            results.add(new Triangle(t.v1, v1, v3));
            // second bisection
            results.add(new Triangle(t.v2, v2, v1));
            // third bisection
            results.add(new Triangle(t.v3, v3, v2));
        }
        return results;
    }

    private static Vector3d[] generateIcosahedralVertices()
    {
        float H_ANGLE = (float)(PI / 180f * 72f);    // 72 degree = 360 / 5
        float V_ANGLE = (float)(Math.atan(1.0f / 2f));  // elevation = 26.565 degree

        Vector3d[] vertices = new Vector3d[12];    // array of 12 vertices (x,y,z)
        float longitude, latitude;                            // coords
        float hAngle1 = (float)(-PI / 2f - H_ANGLE / 2f);  // start from -126 deg at 1st row
        float hAngle2 = (float)(-PI / 2f);                // start from -90 deg at 2nd row

        // the first top vertex at (0, 0, r)
        vertices[0] = new Vector3d(0, 1f, 0);

        // compute 10 vertices at 1st and 2nd rows
        for(int i = 1; i <= 5; ++i)
        {
            longitude  = (float)Math.sin(V_ANGLE);            // elevaton
            latitude = (float)Math.cos(V_ANGLE);            // length on XY plane
            float x1 = latitude * (float)Math.cos(hAngle1);
            float y1 = longitude;
            float z1 = latitude * (float)Math.sin(hAngle1);
            float x2 = latitude * (float)Math.cos(hAngle2);
            float y2 = -longitude;
            float z2 = latitude * (float)Math.sin(hAngle2);
            vertices[i] = new Vector3d(x1, y1, z1);
            vertices[i + 5] = new Vector3d(x2, y2, z2);

            // next horizontal angles
            hAngle1 += H_ANGLE;
            hAngle2 += H_ANGLE;
        }

        // the last bottom vertex at (0, 0, -r)
        vertices[11] = new Vector3d(0, -1f, 0);
        return vertices;
    }

    public GooRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return PlayerContainer.LOCATION_BLOCKS_TEXTURE;
    }

    @Override
    public void render(GooEntity entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer bufferType, int light)
    {
        stack.push();
        RenderType rType = GooRenderHelper.GOO;
        IVertexBuilder buffer = bufferType.getBuffer(rType);
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlasTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE).getSprite(entity.goo.getFluid().getAttributes().getStillTexture());

        // simply put.. we use the client's interpretation of the location when the entity is held. it's smoother.
        if (entity.isHeld()) {
            Vector3d heldVector = entity.getSenderHoldPosition().subtract(entity.getPositionVec());
            if (heldVector.length() > 0.1f) {
                entity.startQuivering();
            }
            stack.translate(heldVector.x, heldVector.y, heldVector.z);
            this.applyRotations(stack, entity.owner().rotationYaw, entity.owner().rotationPitch);
        } else {
            float f = MathHelper.interpolateAngle(partialTicks, entity.prevRotationYaw, entity.rotationYaw);
            float f1 = MathHelper.interpolateAngle(partialTicks, entity.prevRotationPitch, entity.rotationPitch);
            this.applyRotations(stack, f, f1);
        }

        float scale = (entity.cubicSize() / VOLUME_TO_CUBIC_EDGE_COEFFICIENT) * CIRCUMSCRIBED_RADIUS_TO_EDGE_RATIO;

        // Texture and noise gen should be stored so they aren't remade every frame...
        SimplexNoiseGenerator sgen = new SimplexNoiseGenerator(new Random(entity.getPosition().toLong()));
        Matrix4f matrix = stack.getLast().getMatrix();

        Triangle[] triangles = scaleAndWiggle(UNIT_TRIANGLES, sgen, entity.quiverTimer(), scale);

        for (Triangle t : triangles) {
            renderTriangle(matrix, buffer, t, light, sprite);
        }
        stack.pop();
    }

    protected void applyRotations(MatrixStack matrixStackIn, float rotationYaw, float rotationPitch) {
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(90 - MathHelper.wrapDegrees(rotationYaw)));
        matrixStackIn.rotate(Vector3f.ZP.rotationDegrees(MathHelper.wrapDegrees(rotationPitch)));
    }

    public static final float EDGE_TO_ALTITUDE_EQUILATERAL_RATIO = 0.8660254f;

    private void renderTriangle(Matrix4f matrix, IVertexBuilder buffer, Triangle t, int light, TextureAtlasSprite sprite)
    {
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v1.x, (float)t.v1.y, (float)t.v1.z)
                .color(1f, 1f, 1f, 1f)
                .tex(sprite.getInterpolatedU(16f / 2f), sprite.getInterpolatedV(EDGE_TO_ALTITUDE_EQUILATERAL_RATIO * 16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v1.normalize().x, (float)t.v1.normalize().y, (float)t.v1.normalize().z)
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v2.x, (float)t.v2.y, (float)t.v2.z)
                .color(1f, 1f, 1f, 1f)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(0f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v2.normalize().x, (float)t.v2.normalize().y, (float)t.v2.normalize().z)
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, (float)t.v3.x, (float)t.v3.y, (float)t.v3.z)
                .color(1f, 1f, 1f, 1f)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal((float)t.v3.normalize().x, (float)t.v3.normalize().y, (float)t.v3.normalize().z)
                .endVertex();
    }

    public static float remap(float value, float currentLow, float currentHigh, float newLow, float newHigh) {
        return newLow + (value - currentLow) * (newHigh - newLow) / (currentHigh - currentLow);
    }

    private Triangle[] scaleAndWiggle(Triangle[] triangles, SimplexNoiseGenerator sgen, int quiverTimer, float scale) {
        List<Triangle> wiggledTriangles = new ArrayList<>();
        float cycleTimer = quiverTimer / 4f;
        float wiggle = 0.1f * scale;
        wiggle *= remap(MathHelper.sin(cycleTimer), -1, 1, 0, 1);

        // translate coordinates using sgen, range and remap
        for(Triangle t : triangles) {
            Vector3d[] newVs = new Vector3d[3];
            int i = 0;
            for (Vector3d v : new Vector3d[] { t.v1, t.v2, t.v3 }) {
                newVs[i] = v.scale(scale);
                newVs[i] = newVs[i].add(remap((float) sgen.func_227464_a_(newVs[i].x, newVs[i].y, newVs[i].z + cycleTimer), -1, 1f, -wiggle, wiggle),
                        remap((float) sgen.func_227464_a_(newVs[i].x + cycleTimer, newVs[i].y, newVs[i].z + cycleTimer), -1, 1f, -wiggle, wiggle),
                        remap((float) sgen.func_227464_a_(newVs[i].x + cycleTimer, newVs[i].y, newVs[i].z), -1, 1f, -wiggle, wiggle));
                i++;
            }
            wiggledTriangles.add(new Triangle(newVs[0], newVs[1], newVs[2]));
        }
        return wiggledTriangles.toArray(new Triangle[0]);
    }
}
