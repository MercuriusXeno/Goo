package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.*;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.fml.RegistryObject;

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
        registerCrystalComb();
        registerCrucible();
        registerMixer();
        registerBulbs();
        registerGooCrystals();
        registerSpawnEggs();
        registerTrough();
    }

    private void registerGooCrystals() {
        ItemsRegistry.CrystallizedGoo.forEach(this::generateModelForCrystallizedGoo);
    }

    private void generateModelForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> itemRegistryObject) {
        String template = "";
        if (itemRegistryObject.get() instanceof GooSliver) {
            template = "template_sliver";
        } else if (itemRegistryObject.get() instanceof GooShard) {
            template = "template_shard";
        } else if (itemRegistryObject.get() instanceof GooCrystal) {
            template = "template_crystal";
        } else if (itemRegistryObject.get() instanceof GooChunk) {
            template = "template_chunk";
        } else if (itemRegistryObject.get() instanceof GooSlab) {
            template = "template_slab";
        }
        withExistingParent(resourceLocation.getPath(), new ResourceLocation(GooMod.MOD_ID, template));
    }

    private void registerTrough()
    {
        withExistingParent(ItemsRegistry.Trough.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
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

    private void registerCrystalComb()
    {
        singleTexture(ItemsRegistry.CrystalComb.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/crystal_comb"));
    }

    private void registerGooAndYou()
    {
        singleTexture(ItemsRegistry.GooAndYou.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/goo_and_you"));
    }

    private void registerSpawnEggs() {
        withExistingParent(ItemsRegistry.GooBeeSpawnEgg.get().getRegistryName().getPath(), new ResourceLocation("item/template_spawn_egg"));
    }
}
