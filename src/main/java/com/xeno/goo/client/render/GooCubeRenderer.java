package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.gen.SimplexNoiseGenerator;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.util.Random;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class GooCubeRenderer extends EntityRenderer<GooEntity>
{
    private static final Vector3f[][] UNSCALED_QUADS = createQuads();

    private static Vector3f[][] createQuads()
    {
        Vector3f[][] surfaces = new Vector3f[6][4];
        int i = 0;
        for(Direction d : Direction.values()) {
            surfaces[i] = createQuadForDirection(d);
            i++;
        }
        return surfaces;
    }

    private static Vector3f[] createQuadForDirection(Direction d)
    {
        Vector3f up = Direction.UP.toVector3f();
        up.mul(0.5f);
        Vector3f down = Direction.DOWN.toVector3f();
        down.mul(0.5f);
        Vector3f north = Direction.NORTH.toVector3f();
        north.mul(0.5f);
        Vector3f south = Direction.SOUTH.toVector3f();
        south.mul(0.5f);
        Vector3f east = Direction.EAST.toVector3f();
        east.mul(0.5f);
        Vector3f west = Direction.WEST.toVector3f();
        west.mul(0.5f);
        // (8) vertices are as follows
        Vector3f une = combine(up, north, east);
        Vector3f use = combine(up, south, east);
        Vector3f usw = combine(up, south, west);
        Vector3f unw = combine(up, north, west);
        // inverted directions
        Vector3f dse = combine(down, south, east);
        Vector3f dne = combine(down, north, east);
        Vector3f dnw = combine(down, north, west);
        Vector3f dsw = combine(down, south, west);

        switch (d) {
            case UP:
                return new Vector3f[] { une, use, usw, unw };
            case DOWN:
                return new Vector3f[] { dse, dne, dnw, dsw };
            case NORTH:
                return new Vector3f[] { unw, dnw, dne, une };
            case SOUTH:
                return new Vector3f[] { use, dse, dsw, usw };
            case WEST:
                return new Vector3f[] { usw, dsw, dnw, unw };
            case EAST:
                return new Vector3f[] { une, dne, dse, use };
        }

        // failure state;
        return new Vector3f[] {
                new Vector3f(),
                new Vector3f(),
                new Vector3f(),
                new Vector3f()
        };
    }

    private static Vector3f combine(Vector3f v1, Vector3f v2, Vector3f v3)
    {
        Vector3f result = new Vector3f(); // zero
        result.add(v1);
        result.add(v2);
        result.add(v3);
        return result;
    }

    public GooCubeRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_ENTITY.get(), GooCubeRenderer::new);
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
        RenderType rType = GooRenderHelper.GOO_CUBE_DULL;

        // disabling diffuse lighting makes the cube look "emissive" (lets fullbright work)
        // otherwise it just looks dull by nature, which is what we want most of the time.
        if (isBrightFluid(entity.goo.getFluid())) {
            light = GooRenderHelper.FULL_BRIGHT;
            rType = GooRenderHelper.GOO_CUBE_BRIGHT;
        }
        IVertexBuilder buffer = bufferType.getBuffer(rType);
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlasTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE).getSprite(entity.goo.getFluid().getAttributes().getStillTexture());

        float f = MathHelper.interpolateAngle(partialTicks, entity.prevRotationYaw, entity.rotationYaw);
        float f1 = MathHelper.interpolateAngle(partialTicks, entity.prevRotationPitch, entity.rotationPitch);
        this.applyRotations(stack, f, f1);

        float scale = entity.cubicSize();

        // Texture and noise gen should be stored so they aren't remade every frame...
        SimplexNoiseGenerator sgen = new SimplexNoiseGenerator(new Random(entity.getPosition().toLong()));
        Vector3f[][] wiggledQuads = scaleAndWiggle(UNSCALED_QUADS, sgen, entity.quiverTimer(), scale);
        Matrix4f matrix = stack.getLast().getMatrix();
        renderCube(matrix, buffer, wiggledQuads, light, sprite);

        stack.pop();
    }

    private boolean isBrightFluid(Fluid fluid)
    {
        return fluid.equals(Registry.MOLTEN_GOO.get()) || fluid.equals(Registry.ENERGETIC_GOO.get());
    }

    @Override
    protected int getBlockLight(GooEntity entityIn, BlockPos partialTicks)
    {
        if (isBrightFluid(entityIn.goo.getFluid())) {
            return 15;
        }
        return super.getBlockLight(entityIn, partialTicks);
    }

    protected void applyRotations(MatrixStack matrixStackIn, float rotationYaw, float rotationPitch) {
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(90 - MathHelper.wrapDegrees(rotationYaw)));
        matrixStackIn.rotate(Vector3f.ZP.rotationDegrees(MathHelper.wrapDegrees(rotationPitch)));
    }

    private void renderCube(Matrix4f matrix, IVertexBuilder buffer, Vector3f[][] wiggledQuads, int light, TextureAtlasSprite sprite)
    {
        // 6 quads, it's just a cube
        for(Vector3f[] surface : wiggledQuads) {            
            renderQuad(surface, matrix, buffer, light, sprite);
        }
    }

    private void renderQuad(Vector3f[] v, Matrix4f matrix, IVertexBuilder buffer, int light, TextureAtlasSprite sprite)
    {
        buffer.getVertexBuilder()
                .pos(matrix, v[0].getX(), v[0].getY(), v[0].getZ())
                .color(1f, 1f, 1f, 0.8f)
                .tex(sprite.getInterpolatedU(16f), sprite.getInterpolatedV(0f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(v[0].getX(), v[0].getY(), v[0].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[1].getX(), v[1].getY(), v[1].getZ())
                .color(1f, 1f, 1f, 0.8f)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(0f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(v[1].getX(), v[1].getY(), v[1].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[2].getX(), v[2].getY(), v[2].getZ())
                .color(1f, 1f, 1f, 0.8f)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(v[2].getX(), v[2].getY(), v[2].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[3].getX(), v[3].getY(), v[3].getZ())
                .color(1f, 1f, 1f, 0.8f)
                .tex(sprite.getInterpolatedU(16f), sprite.getInterpolatedV(16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(v[3].getX(), v[3].getY(), v[3].getZ())
                .endVertex();
    }


    public static float remap(float value, float currentLow, float currentHigh, float newLow, float newHigh) {
        return newLow + (value - currentLow) * (newHigh - newLow) / (currentHigh - currentLow);
    }

    private Vector3f[][] scaleAndWiggle(Vector3f[][] quads, SimplexNoiseGenerator sgen, int quiverTimer, float scale) {
        Vector3f[][] wiggledQuads = new Vector3f[6][4]; // 6 surfaces, 4 vertices [a quad]
        float cycleTimer = quiverTimer / 4f;
        float wiggle = 0.1f * scale;
        wiggle *= remap(MathHelper.sin(cycleTimer), -1, 1, 0, 1);

        // translate coordinates using sgen, range and remap
        int j = 0;
        for(Vector3f[] surface : quads) {
            Vector3f[] newVs = new Vector3f[4];
            int i = 0;
            for (Vector3f quad : surface) {
                newVs[i] = quad.copy();
                newVs[i].mul(scale);
                newVs[i].add(remap((float) sgen.func_227464_a_(newVs[i].getX(), newVs[i].getY(), newVs[i].getZ() + cycleTimer), -1, 1f, -wiggle, wiggle),
                        remap((float) sgen.func_227464_a_(newVs[i].getX() + cycleTimer, newVs[i].getY(), newVs[i].getZ() + cycleTimer), -1, 1f, -wiggle, wiggle),
                        remap((float) sgen.func_227464_a_(newVs[i].getX() + cycleTimer, newVs[i].getY(), newVs[i].getZ()), -1, 1f, -wiggle, wiggle));
                i++;
            }
            wiggledQuads[j] = newVs;
            j++;
        }
        return wiggledQuads;
    }
}
