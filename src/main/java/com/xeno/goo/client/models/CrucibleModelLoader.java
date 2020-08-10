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

import javax.xml.ws.Holder;
import java.util.function.Predicate;

public class CrucibleModelLoader extends HolderModelLoader
{
    public static final CrucibleModelLoader INSTANCE = new CrucibleModelLoader();

    @Override
    String holderName()
    {
        return "crucible";
    }
}
