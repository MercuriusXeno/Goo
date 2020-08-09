package com.xeno.goo.client.models;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.fluid.Fluid;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelLoader;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.VanillaResourceType;

import java.util.function.Predicate;

public class CrucibleModelLoader implements IModelLoader<DynamicCrucibleModel>
{
    public static final CrucibleModelLoader INSTANCE = new CrucibleModelLoader();

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
    public DynamicCrucibleModel read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
    {
        if (!modelContents.has("fluid"))
            throw new RuntimeException("Crucible model requires 'fluid' value.");

        ResourceLocation fluidName = new ResourceLocation(modelContents.get("fluid").getAsString());

        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidName);

        boolean flip = false;
        if (modelContents.has("flipGas"))
        {
            flip = modelContents.get("flipGas").getAsBoolean();
        }

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
        return new DynamicCrucibleModel(fluid, flip, tint, coverIsMask, applyFluidLuminosity);
    }
}
