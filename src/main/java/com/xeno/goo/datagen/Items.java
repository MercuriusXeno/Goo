package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.model.BuiltInModel;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;
import net.minecraftforge.common.model.Models;

public class Items extends ItemModelProvider {
    public Items(DataGenerator generator, ExistingFileHelper existingFileHelper) {
        super(generator, GooMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        registerGasket();
        registerGauntlet();
        registerComboGauntlet();
        registerCrucible();
        registerMobiusCrucible();
        registerGooAndYou();
    }

    private void registerMobiusCrucible()
    {
        // NO OP
    }

    private void registerCrucible()
    {
        // NO OP
    }

    private void registerComboGauntlet()
    {
        // NO OP
    }

    private void registerGauntlet()
    {
        // NO OP
    }

    private void registerGasket()
    {
        singleTexture(Registry.GASKET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gasket"));
    }

    private void registerGooAndYou()
    {
        singleTexture(Registry.GOO_AND_YOU.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/goo_and_you"));
    }
}
