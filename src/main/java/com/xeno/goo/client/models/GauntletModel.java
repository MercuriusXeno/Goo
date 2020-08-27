package com.xeno.goo.client.models;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.xeno.goo.items.GooHolder;
import com.xeno.goo.items.GooHolderData;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.*;
import net.minecraftforge.client.model.geometry.IModelGeometry;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.VanillaResourceType;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GauntletModel implements IModelGeometry<GauntletModel>
{
    // minimal Z offset to prevent depth-fighting
    private static final float NORTH_Z_COVER = 7.496f / 16f;
    private static final float SOUTH_Z_COVER = 8.504f / 16f;
    private static final float NORTH_Z_FLUID = 7.498f / 16f;
    private static final float SOUTH_Z_FLUID = 8.502f / 16f;

    private final Fluid fluid;
    private final boolean tint;
    private final boolean coverIsMask;
    private final boolean applyFluidLuminosity;

    @Deprecated
    public GauntletModel(Fluid fluid, boolean tint, boolean coverIsMask)
    {
        this(fluid, tint, coverIsMask, true);
    }

    public GauntletModel(Fluid fluid, boolean tint, boolean coverIsMask, boolean applyFluidLuminosity)
    {
        this.fluid = fluid;
        this.tint = tint;
        this.coverIsMask = coverIsMask;
        this.applyFluidLuminosity = applyFluidLuminosity;
    }

    /**
     * Returns a new ModelDynBucket representing the given fluid, but with the same
     * other properties (flipGas, tint, coverIsMask).
     */
    public GauntletModel withFluid(Fluid newFluid)
    {
        return new GauntletModel(newFluid, false, applyFluidLuminosity);
    }

    @Override
    public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<RenderMaterial, TextureAtlasSprite> spriteGetter, IModelTransform modelTransform, ItemOverrideList overrides, ResourceLocation modelLocation)
    {
        RenderMaterial particleLocation = owner.isTexturePresent("particle") ? owner.resolveTexture("particle") : null;
        RenderMaterial baseLocation = owner.isTexturePresent("base") ? owner.resolveTexture("base") : null;
        RenderMaterial fluidMaskLocation = owner.isTexturePresent("fluid") ? owner.resolveTexture("fluid") : null;
        RenderMaterial coverLocation = owner.isTexturePresent("fluid") ? owner.resolveTexture("cover") : null;

        IModelTransform transformsFromModel = owner.getCombinedTransform();

        TextureAtlasSprite fluidSprite = fluid != Fluids.EMPTY ? spriteGetter.apply(ForgeHooksClient.getBlockMaterial(fluid.getAttributes().getStillTexture())) : null;
        TextureAtlasSprite coverSprite = (coverLocation != null && (!coverIsMask || baseLocation != null)) ? spriteGetter.apply(coverLocation) : null;

        ImmutableMap<TransformType, TransformationMatrix> transformMap =
                PerspectiveMapWrapper.getTransforms(new ModelTransformComposition(transformsFromModel, modelTransform));

        TextureAtlasSprite particleSprite = particleLocation != null ? spriteGetter.apply(particleLocation) : null;

        if (particleSprite == null) particleSprite = fluidSprite;
        if (particleSprite == null && !coverIsMask) particleSprite = coverSprite;

        TransformationMatrix transform = modelTransform.getRotation();

        ItemMultiLayerBakedModel.Builder builder = ItemMultiLayerBakedModel.builder(owner, particleSprite, new CrucibleContainedOverrideList(overrides, bakery, owner, this), transformMap);

        if (baseLocation != null)
        {
            // build base (insidest)
            builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemLayerModel.getQuadsForSprites(ImmutableList.of(baseLocation), transform, spriteGetter));
        }

        if (fluidMaskLocation != null && fluidSprite != null)
        {
            TextureAtlasSprite templateSprite = spriteGetter.apply(fluidMaskLocation);
            if (templateSprite != null)
            {
                // build liquid layer (inside)
                int luminosity = applyFluidLuminosity ? fluid.getAttributes().getLuminosity() : 0;
                int color = tint ? fluid.getAttributes().getColor() : 0xFFFFFFFF;
                builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, NORTH_Z_FLUID, Direction.NORTH, color, 1, luminosity));
                builder.addQuads(ItemLayerModel.getLayerRenderType(luminosity > 0), ItemTextureQuadConverter.convertTexture(transform, templateSprite, fluidSprite, SOUTH_Z_FLUID, Direction.SOUTH, color, 1, luminosity));
            }
        }

        if (coverIsMask)
        {
            if (coverSprite != null && baseLocation != null)
            {
                TextureAtlasSprite baseSprite = spriteGetter.apply(baseLocation);
                builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.convertTexture(transform, coverSprite, baseSprite, NORTH_Z_COVER, Direction.NORTH, 0xFFFFFFFF, 2));
                builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.convertTexture(transform, coverSprite, baseSprite, SOUTH_Z_COVER, Direction.SOUTH, 0xFFFFFFFF, 2));
            }
        }
        else
        {
            if (coverSprite != null)
            {
                builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.genQuad(transform, 0, 0, 16, 16, NORTH_Z_COVER, coverSprite, Direction.NORTH, 0xFFFFFFFF, 2));
                builder.addQuads(ItemLayerModel.getLayerRenderType(false), ItemTextureQuadConverter.genQuad(transform, 0, 0, 16, 16, SOUTH_Z_COVER, coverSprite, Direction.SOUTH, 0xFFFFFFFF, 2));
            }
        }

        builder.setParticle(particleSprite);

        return builder.build();
    }


    public enum Loader implements IModelLoader<GauntletModel>
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
        public GauntletModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
        {
            if (!modelContents.has("fluid"))
                throw new RuntimeException("Crucible model requires 'fluid' value.");

            ResourceLocation fluidName = new ResourceLocation(modelContents.get("fluid").getAsString());

            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidName);

            boolean tint = true;
            if (modelContents.has("applyTint"))
            {
                tint = modelContents.get("applyTint").getAsBoolean();
            }

            boolean coverIsMask = true;
            if (modelContents.has("coverIsMask"))
            {
                coverIsMask = modelContents.get("coverIsMask").getAsBoolean();
            }

            boolean applyFluidLuminosity = true;
            if (modelContents.has("applyFluidLuminosity"))
            {
                applyFluidLuminosity = modelContents.get("applyFluidLuminosity").getAsBoolean();
            }

            // create new model with correct liquid
            return new GauntletModel(fluid, tint, coverIsMask, applyFluidLuminosity);
        }
    }

    @Override
    public Collection<RenderMaterial> getTextures(IModelConfiguration owner, Function<ResourceLocation, IUnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors)
    {
        Set<RenderMaterial> texs = Sets.newHashSet();

        if (owner.isTexturePresent("particle")) texs.add(owner.resolveTexture("particle"));
        if (owner.isTexturePresent("base")) texs.add(owner.resolveTexture("base"));
        if (owner.isTexturePresent("fluid")) texs.add(owner.resolveTexture("fluid"));
        if (owner.isTexturePresent("cover")) texs.add(owner.resolveTexture("cover"));

        return texs;
    }

    private static final class CrucibleContainedOverrideList extends ItemOverrideList
    {
        private final Map<String, IBakedModel> cache = Maps.newHashMap(); // contains all the baked models since they'll never change
        private final ItemOverrideList nested;
        private final ModelBakery bakery;
        private final IModelConfiguration owner;
        private final GauntletModel parent;

        private CrucibleContainedOverrideList(ItemOverrideList nested, ModelBakery bakery, IModelConfiguration owner, GauntletModel parent)
        {
            this.nested = nested;
            this.bakery = bakery;
            this.owner = owner;
            this.parent = parent;
        }

        @Override
        public IBakedModel func_239290_a_(IBakedModel originalModel, ItemStack stack, ClientWorld world, LivingEntity entity)
        {
            IBakedModel overridden = nested.func_239290_a_(originalModel, stack, world, entity);
            if (overridden != originalModel) return overridden;

            Item item = stack.getItem();
            if (!(item instanceof GooHolder)) {
                return originalModel;
            }
            GooHolderData holder = ((GooHolder)item).data(stack);
            holder.deserializeNBT(stack.getTag());

            Fluid fluid = holder.heldGoo().getFluid();

            if (fluid == null || fluid == Fluids.EMPTY) {
                return originalModel;
            }

            String key = Objects.requireNonNull(fluid.getRegistryName()).toString();

            if (!cache.containsKey(key))
            {
                GauntletModel unbaked = this.parent.withFluid(fluid);
                IBakedModel bakedModel = unbaked.bake(owner, bakery, ModelLoader.defaultTextureGetter(), ModelRotation.X0_Y0, this, new ResourceLocation("goo:crucible_override"));
                cache.put(key, bakedModel);
                return bakedModel;
            }

            return cache.get(key);
        }
    }
}