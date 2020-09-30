package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.*;

public class ItemsProvider extends ItemModelProvider {
    public ItemsProvider(DataGenerator generator, ExistingFileHelper existingFileHelper) {
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
        withExistingParent(ItemsRegistry.GooBulb.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerMixer()
    {
        withExistingParent(ItemsRegistry.Mixer.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerCrucible()
    {
        withExistingParent(ItemsRegistry.Crucible.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerBasin()
    {
        withExistingParent(ItemsRegistry.Basin.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_basin"));
    }

    private void registerGauntlet()
    {
        withExistingParent(ItemsRegistry.Gauntlet.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_gauntlet"));
    }

    private void registerGasket()
    {
        singleTexture(ItemsRegistry.Gasket.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gasket"));
    }

    private void registerGooAndYou()
    {
        singleTexture(ItemsRegistry.GooAndYou.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/goo_and_you"));
    }
}
