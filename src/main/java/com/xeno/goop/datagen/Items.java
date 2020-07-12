package com.xeno.goop.datagen;

import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registration;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ExistingFileHelper;
import net.minecraftforge.client.model.generators.ItemModelProvider;

public class Items extends ItemModelProvider {
    public Items(DataGenerator generator,  ExistingFileHelper existingFileHelper) {
        super(generator, GoopMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        singleTexture(Registration.GASKET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GoopMod.MOD_ID, "item/gasket"));
        withExistingParent(Registration.GOOP_BULB_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GoopMod.MOD_ID, "block/goop_bulb"));
        withExistingParent(Registration.GOOPIFIER_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GoopMod.MOD_ID, "block/goopifier"));
        withExistingParent(Registration.SOLIDIFIER_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GoopMod.MOD_ID, "block/solidifier"));
    }
}
