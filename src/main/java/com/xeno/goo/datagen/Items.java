package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
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
        registerGooBulb();
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

    private void registerGooBulb()
    {
        withExistingParent(Registry.GOO_BULB.get().getRegistryName().getPath(), new ResourceLocation("builtin/entity"))
                .transforms()
                // GUI
                .transform(ModelBuilder.Perspective.GUI)
                .rotation(30, 225, 0)
                .translation(0, 0, 0)
                .scale(0.625f, 0.625f, 0.625f)
                .end()
                // GROUND
                .transform(ModelBuilder.Perspective.GROUND)
                .rotation(0, 0, 0)
                .translation(0, 3, 0)
                .scale(0.25f, 0.25f, 0.25f)
                .end()
                // FIXED
                .transform(ModelBuilder.Perspective.FIXED)
                .rotation(0, 0, 0)
                .translation(0, 0, 0)
                .scale(0.5f, 0.5f, 0.5f)
                .end()
                // THIRDPERSON_RIGHTHAND
                .transform(ModelBuilder.Perspective.THIRDPERSON_RIGHT)
                .rotation(75, 45, 0)
                .translation(0, 2.5f, 0)
                .scale(0.375f, 0.375f, 0.375f)
                .end()
                // THIRDPERSON_LEFTHAND
                .transform(ModelBuilder.Perspective.THIRDPERSON_LEFT)
                .rotation(75, 225, 0)
                .translation(0, 2.5f, 0)
                .scale(0.375f, 0.375f, 0.375f)
                .end()
                // FIRSTPERSON_RIGHTHAND
                .transform(ModelBuilder.Perspective.FIRSTPERSON_RIGHT)
                .rotation(0, 45, 0)
                .translation(0, 0, 0)
                .scale(0.4f, 0.4f, 0.4f)
                .end()
                // FIRSTPERSON_LEFTHAND
                .transform(ModelBuilder.Perspective.FIRSTPERSON_LEFT)
                .rotation(0, 225, 0)
                .translation(0, 0, 0)
                .scale(0.4f, 0.4f, 0.4f)
                .end()
        ;


    }
}
