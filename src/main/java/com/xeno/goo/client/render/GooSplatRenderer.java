package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class GooSplatRenderer extends EntityRenderer<GooSplat>
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

    public GooSplatRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_SPLAT.get(), GooSplatRenderer::new);
    }

    @Override
    public ResourceLocation getEntityTexture(GooSplat entity)
    {
        return PlayerContainer.LOCATION_BLOCKS_TEXTURE;
    }

    @Override
    public void render(GooSplat entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer bufferType, int light)
    {
        RenderType rType = GooRenderHelper.GOO_CUBE_BRIGHT;

        // disabling diffuse lighting makes the cube look "emissive" (lets fullbright work)
        // otherwise it just looks dull by nature, which is what we want most of the time.
        if (isBrightFluid(entity.goo().getFluid())) {
            light = GooRenderHelper.FULL_BRIGHT;
        } else {
            light = WorldRenderer.getCombinedLight(entity.getEntityWorld(), entity.getPosition());
        }
        IVertexBuilder buffer = bufferType.getBuffer(rType);
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                .getAtlasTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE)
                .getSprite(entity.goo().getFluid().getAttributes().getStillTexture());

        Vector3d scale = entity.shape();
        Vector3f[][] scaledQuads = scale(UNSCALED_QUADS, scale);
        stack.push();
        Matrix4f matrix = stack.getLast().getMatrix();
        int color = 0xCCFFFFFF;
        if (entity.goo().getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            color = FluidCuboidHelper.colorizeChromaticGoo();
        }
        renderCube(matrix, buffer, scaledQuads, color, light, sprite);
        stack.pop();
    }

    private boolean isBrightFluid(Fluid fluid)
    {
        return fluid.equals(Registry.MOLTEN_GOO.get()) || fluid.equals(Registry.ENERGETIC_GOO.get());
    }

    @Override
    protected int getBlockLight(GooSplat entityIn, BlockPos partialTicks)
    {
        if (isBrightFluid(entityIn.goo().getFluid())) {
            return 15;
        }
        return super.getBlockLight(entityIn, partialTicks);
    }

    private void renderCube(Matrix4f matrix, IVertexBuilder buffer, Vector3f[][] wiggledQuads, int color, int light, TextureAtlasSprite sprite)
    {
        // 6 quads, it's just a cube
        for(Vector3f[] surface : wiggledQuads) {            
            renderQuad(surface, matrix, buffer, color, light, sprite);
        }
    }

    private void renderQuad(Vector3f[] v, Matrix4f matrix, IVertexBuilder buffer, int color, int light, TextureAtlasSprite sprite)
    {
        Vector3f[] ns = {v[0].copy(), v[1].copy(), v[2].copy(), v[3].copy()};
        for(Vector3f n : ns) {
            n.normalize();
        }
        buffer.getVertexBuilder()
                .pos(matrix, v[0].getX(), v[0].getY(), v[0].getZ())
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .tex(sprite.getInterpolatedU(16f), sprite.getInterpolatedV(0f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(ns[0].getX(), ns[0].getY(), ns[0].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[1].getX(), v[1].getY(), v[1].getZ())
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(0f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(ns[1].getX(), ns[1].getY(), ns[1].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[2].getX(), v[2].getY(), v[2].getZ())
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .tex(sprite.getInterpolatedU(0f), sprite.getInterpolatedV(16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(ns[2].getX(), ns[2].getY(), ns[2].getZ())
                .endVertex();
        buffer.getVertexBuilder()
                .pos(matrix, v[3].getX(), v[3].getY(), v[3].getZ())
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .tex(sprite.getInterpolatedU(16f), sprite.getInterpolatedV(16f))
                .overlay(NO_OVERLAY)
                .lightmap(light)
                .normal(ns[3].getX(), ns[3].getY(), ns[3].getZ())
                .endVertex();
    }


    public static float remap(float value, float currentLow, float currentHigh, float newLow, float newHigh) {
        return newLow + (value - currentLow) * (newHigh - newLow) / (currentHigh - currentLow);
    }

    private Vector3f[][] scale(Vector3f[][] quads, Vector3d scale) {
        Vector3f[][] scaledQuads = new Vector3f[6][4]; // 6 surfaces, 4 vertices [a quad]

        // translate coordinates using sgen, range and remap
        int j = 0;
        for(Vector3f[] surface : quads) {
            Vector3f[] newVs = new Vector3f[4];
            int i = 0;
            for (Vector3f quad : surface) {
                newVs[i] = quad.copy();
                newVs[i].mul((float)scale.x, (float)scale.y, (float)scale.z);
                i++;
            }
            scaledQuads[j] = newVs;
            j++;
        }
        return scaledQuads;
    }
}
