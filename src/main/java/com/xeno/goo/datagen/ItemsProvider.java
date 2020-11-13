package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.*;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.model.BlockModel;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.fml.RegistryObject;

import javax.annotation.Resource;
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
    }

    private void registerGooCrystals() {
        ItemsRegistry.CrystallizedGoo.forEach(this::generateModelForCrystallizedGoo);
    }

    private void generateModelForCrystallizedGoo(ResourceLocation resourceLocation, RegistryObject<CrystallizedGooAbstract> itemRegistryObject) {

        ItemModelBuilder model = withExistingParent(itemRegistryObject.get().getRegistryName().getPath(), mcLoc("item/handheld"))
                .texture("layer0", new ResourceLocation(GooMod.MOD_ID, "item/crystals/" + resourceLocation.getPath()));
        if (resourceLocation.getPath().startsWith("chromatic")) {
            model.texture("layer1", new ResourceLocation(GooMod.MOD_ID, "item/crystals/" + resourceLocation.getPath() + "_overlay"));
        }
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
        ResourceLocation heldLiquid = new ResourceLocation(GooMod.MOD_ID, Gauntlet.HELD_LIQUID_TAG_NAME);
        ItemModelBuilder builder = withExistingParent(Objects.requireNonNull(ItemsRegistry.Gauntlet.get().getRegistryName()).getPath(), new ResourceLocation(GooMod.MOD_ID, "template_gauntlet_held"));
        Registry.FluidSuppliers.forEach((k, v) -> registerFluidOverrideForGauntlet(k, v, builder, heldLiquid));
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
