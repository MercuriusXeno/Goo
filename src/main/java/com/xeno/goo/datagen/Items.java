package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;

public class Items extends ItemModelProvider {
    public Items(DataGenerator generator, ExistingFileHelper existingFileHelper) {
        super(generator, GooMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        registerGasket();
        registerGauntlet();
        registerBasin();
        registerGooAndYou();
        registerCrucible();
        registerMixer();
        registerBulbs();
    }

    private void registerBulbs()
    {
        withExistingParent(Registry.GOO_BULB_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerMixer()
    {
        withExistingParent(Registry.MIXER_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerCrucible()
    {
        withExistingParent(Registry.CRUCIBLE_ITEM.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerBasin()
    {
        withExistingParent(Registry.BASIN.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_basin"));
    }

    private void registerGauntlet()
    {
        singleTexture(Registry.GAUNTLET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gauntlet"));
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
