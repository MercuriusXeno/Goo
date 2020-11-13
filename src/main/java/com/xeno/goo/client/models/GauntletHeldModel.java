package com.xeno.goo.client.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.client.model.geometry.IModelGeometry;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.VanillaResourceType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GauntletHeldModel implements IModelGeometry<GauntletHeldModel>
{
    // minimal Z offset to prevent depth-fighting
    private static final float NORTH_Z_FLUID = 7.498f / 16f;
    private static final float SOUTH_Z_FLUID = 8.502f / 16f;

    private final Fluid fluid;
    private final boolean applyFluidLuminosity;

    @Deprecated
    public GauntletHeldModel(Fluid fluid)
    {
        this(fluid,true);
    }

    public GauntletHeldModel(Fluid fluid, boolean applyFluidLuminosity)
    {
        this.fluid = fluid;
        this.applyFluidLuminosity = applyFluidLuminosity;
    }

    /**
     * Returns a new ModelDynBucket representing the given fluid, but with the same
     * other properties (flipGas, tint, coverIsMask).
     */
    public GauntletHeldModel withFluid(Fluid newFluid)
    {
        return new GauntletHeldModel(newFluid, applyFluidLuminosity);
    }

    @Override
    public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<RenderMaterial, TextureAtlasSprite> spriteGetter, IModelTransform modelTransform, ItemOverrideList overrides, ResourceLocation modelLocation)
    {
        RenderMaterial particleLocation = owner.isTexturePresent("particle") ? owner.resolveTexture("particle") : null;
        RenderMaterial sideLocation = owner.isTexturePresent("side") ? owner.resolveTexture("side") : null;
        RenderMaterial topLocation = owner.isTexturePresent("top") ? owner.resolveTexture("top") : null;
        RenderMaterial bottomLocation = owner.isTexturePresent("bottom") ? owner.resolveTexture("bottom") : null;
        RenderMaterial endLocation = owner.isTexturePresent("end") ? owner.resolveTexture("end") : null;
        RenderMaterial openingLocation = owner.isTexturePresent("opening") ? owner.resolveTexture("opening") : null;
        RenderMaterial sideMaskLocation = owner.isTexturePresent("side_mask") ? owner.resolveTexture("side_mask") : null;
        RenderMaterial endMaskLocation = owner.isTexturePresent("end_mask") ? owner.resolveTexture("end_mask") : null;

        IModelTransform transformsFromModel = owner.getCombinedTransform();

        TextureAtlasSprite fluidSprite = fluid != Fluids.EMPTY ? spriteGetter.apply(ForgeHooksClient.getBlockMaterial(fluid.getAttributes().getStillTexture())) : null;
        // TextureAtlasSprite coverSprite = (coverLocation != null && (!coverIsMask || baseLocation != null)) ? spriteGetter.apply(coverLocation) : null;

        ImmutableMap<TransformType, TransformationMatrix> transformMap =
                PerspectiveMapWrapper.getTransforms(new ModelTransformComposition(transformsFromModel, modelTransform));

        TextureAtlasSprite particleSprite = particleLocation != null ? spriteGetter.apply(particleLocation) : null;

        if (particleSprite == null) particleSprite = fluidSprite;
        // if (particleSprite == null && !coverIsMask) particleSprite = coverSprite;

        TransformationMatrix transform = modelTransform.getRotation();

        ItemMultiLayerBakedModel.Builder builder = ItemMultiLayerBakedModel.builder(owner, particleSprite, new GauntletContainedOverrideList(overrides, bakery, owner, this), transformMap);

        if (sideLocation != null)
        {
            // build base (insidest)

            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(sideLocation), transform, spriteGetter));
            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(endLocation), transform, spriteGetter));
            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(openingLocation), transform, spriteGetter));
            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(topLocation), transform, spriteGetter));
            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(bottomLocation), transform, spriteGetter));
        }

        if (sideMaskLocation != null && fluidSprite != null)
        {
            TextureAtlasSprite templateSprite = spriteGetter.apply(sideMaskLocation);
            if (templateSprite != null)
            {
                // build liquid layer (inside)
                int luminosity = applyFluidLuminosity ? fluid.getAttributes().getLuminosity() : 0;
                int color = fluid.getAttributes().getColor();
//                if (fluid.isEquivalentTo(Registry.CHROMATIC_GOO.get())) {
//                    color = FluidCuboidHelper.colorizeChromaticGoo();
//                }
                builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, NORTH_Z_FLUID, Direction.NORTH, color, 1, luminosity));
                builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, SOUTH_Z_FLUID, Direction.SOUTH, color, 1, luminosity));
            }
        }
        builder.setParticle(particleSprite);

        return builder.build();
    }

    @Override
    public Collection<RenderMaterial> getTextures(IModelConfiguration owner, Function<ResourceLocation, IUnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors)
    {
        Set<RenderMaterial> texs = Sets.newHashSet();

        if (owner.isTexturePresent("particle")) texs.add(owner.resolveTexture("particle"));
        if (owner.isTexturePresent("end_mask")) texs.add(owner.resolveTexture("end_mask"));
        if (owner.isTexturePresent("side_mask")) texs.add(owner.resolveTexture("side_mask"));
        if (owner.isTexturePresent("end")) texs.add(owner.resolveTexture("end"));
        if (owner.isTexturePresent("top")) texs.add(owner.resolveTexture("top"));
        if (owner.isTexturePresent("side")) texs.add(owner.resolveTexture("side"));
        if (owner.isTexturePresent("bottom")) texs.add(owner.resolveTexture("bottom"));
        if (owner.isTexturePresent("opening")) texs.add(owner.resolveTexture("opening"));

        return texs;
    }


    public enum Loader implements IModelLoader<GauntletHeldModel>
    {
        INSTANCE;

        @Override
        public IResourceType getResourceType()
        {
            return VanillaResourceType.MODELS;
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager)
        {
            // no need to clear cache since we create a new model instance
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate)
        {
            // no need to clear cache since we create a new model instance
        }

        @Override
        public GauntletHeldModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
        {
            if (!modelContents.has("side_mask"))
                throw new RuntimeException("Gauntlet model requires 'side_mask' value.");

            if (!modelContents.has("end_mask"))
                throw new RuntimeException("Gauntlet model requires 'end_mask' value.");

            ResourceLocation fluidName = new ResourceLocation(modelContents.get("side_mask").getAsString());

            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidName);

            boolean applyFluidLuminosity = true;
            if (modelContents.has("applyFluidLuminosity"))
            {
                applyFluidLuminosity = modelContents.get("applyFluidLuminosity").getAsBoolean();
            }

            // create new model with correct liquid
            return new GauntletHeldModel(fluid, applyFluidLuminosity);
        }
    }

    private static final class GauntletContainedOverrideList extends ItemOverrideList
    {
        private final Map<String, IBakedModel> cache = Maps.newHashMap(); // contains all the baked models since they'll never change
        private final ItemOverrideList nested;
        private final ModelBakery bakery;
        private final IModelConfiguration owner;
        private final GauntletHeldModel parent;

        private GauntletContainedOverrideList(ItemOverrideList nested, ModelBakery bakery, IModelConfiguration owner, GauntletHeldModel parent)
        {
            this.nested = nested;
            this.bakery = bakery;
            this.owner = owner;
            this.parent = parent;
        }

        @Override
        public IBakedModel getOverrideModel(IBakedModel originalModel, ItemStack stack, ClientWorld world, LivingEntity entity)
        {
            IBakedModel overridden = nested.getOverrideModel(originalModel, stack, world, entity);
            if (overridden != originalModel) return overridden;
            return FluidUtil.getFluidContained(stack)
                    .map(fluidStack -> {
                        Fluid fluid = fluidStack.getFluid();
                        String name = fluid.getRegistryName().toString();
//                        if (fluid.isEquivalentTo(Registry.CHROMATIC_GOO.get())) {
//                            String color = String.valueOf(FluidCuboidHelper.colorizeChromaticGoo());
//                            name = name + color;
//                        }

                        if (!cache.containsKey(name))
                        {
                            GauntletHeldModel unbaked = this.parent.withFluid(fluid);
                            IBakedModel bakedModel = unbaked.bake(owner, bakery, ModelLoader.defaultTextureGetter(), ModelRotation.X0_Y0, this, new ResourceLocation(GooMod.MOD_ID, "gauntlet_override"));
                            cache.put(name, bakedModel);
                            return bakedModel;
                        }

                        return cache.get(name);
                    })
                    // not a fluid item apparently
                    .orElse(originalModel); // empty bucket
        }
    }
}