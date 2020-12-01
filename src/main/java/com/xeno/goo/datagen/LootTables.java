package com.xeno.goo.datagen;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalBlock;
import net.minecraft.data.DataGenerator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;

public class LootTables extends BaseLootTableProvider {
    public LootTables(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
    }

    @Override
    protected void addTables() {
        lootTables.put(BlocksRegistry.Crucible.get(), createGooContainerLootTable("crucible", BlocksRegistry.Crucible.get()));
        lootTables.put(BlocksRegistry.Bulb.get(), createGooContainerWithContainmentLootTable("goo_bulb", BlocksRegistry.Bulb.get()));
        lootTables.put(BlocksRegistry.Mixer.get(), createGooContainerLootTable("mixer", BlocksRegistry.Mixer.get()));
        lootTables.put(BlocksRegistry.Solidifier.get(), createGooContainerLootTable("solidifier", BlocksRegistry.Solidifier.get()));
        lootTables.put(BlocksRegistry.Gooifier.get(), createGooContainerLootTable("gooifier", BlocksRegistry.Gooifier.get()));
        lootTables.put(BlocksRegistry.Drain.get(), createMundaneTable("drain", BlocksRegistry.Drain.get()));
        lootTables.put(BlocksRegistry.CrystalNest.get(), createMundaneTable("crystal_nest", BlocksRegistry.CrystalNest.get()));
        lootTables.put(BlocksRegistry.Lobber.get(), createMundaneTable("lobber", BlocksRegistry.Lobber.get()));
        lootTables.put(BlocksRegistry.Pump.get(), createMundaneTable("pump", BlocksRegistry.Pump.get()));
        lootTables.put(BlocksRegistry.Trough.get(), createGooContainerLootTable("trough", BlocksRegistry.Trough.get()));

        BlocksRegistry.CrystalBlocks.forEach(this::createLootTableForDecorativeBlock);
    }

    private void createLootTableForDecorativeBlock(ResourceLocation resourceLocation, RegistryObject<CrystalBlock> crystalBlockRegistryObject) {
        lootTables.put(crystalBlockRegistryObject.get(), createMundaneTable(resourceLocation.getPath(), crystalBlockRegistryObject.get()));
    }
}
