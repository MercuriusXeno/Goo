package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.generators.ExistingFileHelper;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;

public class Items extends ItemModelProvider {
    public Items(DataGenerator generator, ExistingFileHelper existingFileHelper) {
        super(generator, GooMod.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        singleTexture(Registry.GASKET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gasket"));
        singleTexture(Registry.GAUNTLET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/gauntlet"));
        singleTexture(Registry.CRUCIBLE.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/crucible"));
        singleTexture(Registry.COMBO_GAUNTLET.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/combo_gauntlet"));
        singleTexture(Registry.MOBIUS_CRUCIBLE.get().getRegistryName().getPath(), new ResourceLocation("item/handheld"),
                "layer0", new ResourceLocation(GooMod.MOD_ID, "item/mobius_crucible"));

    }
}
