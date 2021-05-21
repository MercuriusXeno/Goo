package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.DynamicBucketModel;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.DynamicBucketModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.fml.RegistryObject;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

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
        registerSnail();
        registerBuckets();
    }

    private void registerBuckets() {
        for(Entry<Supplier<GooFluid>, Supplier<Item>> entry : Registry.BucketSuppliers.entrySet()) {
            withExistingParent(entry.getValue().get().getRegistryName().getPath(), new ResourceLocation("forge", "bucket"))
                    .customLoader(DynamicBucketModelBuilder::begin)
                    .fluid(entry.getKey().get());
        }
    }

    private void registerGooCrystals() {
        ItemsRegistry.CRYSTALLIZED_GOO.forEach(this::generateModelForCrystallizedGoo);
    }

    private void generateModelForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> itemRegistryObject) {

        ItemModelBuilder model = withExistingParent(itemRegistryObject.get().getRegistryName().getPath(), mcLoc("item/handheld"))
                .texture("layer0", new ResourceLocation(GooMod.MOD_ID, "item/crystals/" + resourceLocation.getPath()));
//        if (resourceLocation.getPath().startsWith("chromatic")) {
//            model.texture("layer1", new ResourceLocation(GooMod.MOD_ID, "item/crystals/" + resourceLocation.getPath() + "_overlay"));
//        }
    }

    private void registerTrough()
    {
        withExistingParent(ItemsRegistry.TROUGH.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerBulbs()
    {
        withExistingParent(ItemsRegistry.GOO_BULB.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerMixer()
    {
        withExistingParent(ItemsRegistry.MIXER.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerCrucible()
    {
        withExistingParent(ItemsRegistry.CRUCIBLE.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_held_block"));
    }

    private void registerBasin()
    {
        withExistingParent(ItemsRegistry.BASIN.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "template_basin"));
    }

    private void registerGauntlet()
    {
        ResourceLocation heldLiquid = new ResourceLocation(GooMod.MOD_ID, Gauntlet.HELD_LIQUID_TAG_NAME);
        ItemModelBuilder builder = withExistingParent(Objects.requireNonNull(ItemsRegistry.GAUNTLET.get().getRegistryName()).getPath(), new ResourceLocation(GooMod.MOD_ID, "template_gauntlet_held"));
        Registry.FluidSuppliers.forEach((k, v) -> registerFluidOverrideForGauntlet(k, v, builder, heldLiquid));
    }

    private void registerSnail() {
        withExistingParent(ItemsRegistry.SNAIL.get().getRegistryName().getPath(), new ResourceLocation(GooMod.MOD_ID, "snail_item"));
    }

    private void registerFluidOverrideForGauntlet(ResourceLocation k, Supplier<GooFluid> v, ItemModelBuilder builder, ResourceLocation predicateName) {
        int subIndex = k.getPath().indexOf("_goo");
        String modelNamePrefix = k.getPath().substring(0, subIndex);
        String modelName = modelNamePrefix + "_gauntlet";
        ResourceLocation modelLoc = new ResourceLocation(GooMod.MOD_ID, modelName);
        ModelFile gauntletFluidModel = getExistingFile(modelLoc);
        builder.override()
                .predicate(predicateName, v.get().overrideIndex())
                .model(gauntletFluidModel);
    }

    private void registerGasket()
    {
        singleTexture(ItemsRegistry.GASKET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gasket"));
    }

    private void registerCrystalComb()
    {
        singleTexture(ItemsRegistry.CRYSTAL_COMB.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/crystal_comb"));
    }

    private void registerGooAndYou()
    {
        singleTexture(ItemsRegistry.GOO_AND_YOU.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/goo_and_you"));
    }

    private void registerSpawnEggs() {
        withExistingParent(ItemsRegistry.GOO_BEE_SPAWN_EGG.get().getRegistryName().getPath(), new ResourceLocation("item/template_spawn_egg"));
        withExistingParent(ItemsRegistry.GOO_SNAIL_SPAWN_EGG.get().getRegistryName().getPath(), new ResourceLocation("item/template_spawn_egg"));
        // withExistingParent(ItemsRegistry.LIGHTNING_BUG_SPAWN_EGG.get().getRegistryName().getPath(), new ResourceLocation("item/template_spawn_egg"));
    }
}
