package com.xeno.goo.datagen;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalBlock;
import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.data.DataGenerator;
import net.minecraft.loot.ConstantRange;
import net.minecraft.loot.ItemLootEntry;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTable.Builder;
import net.minecraft.loot.functions.CopyName;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;

public class LootTables extends BaseLootTableProvider {
    public LootTables(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
    }

    @Override
    protected void addTables() {
        blockLootTables.put(BlocksRegistry.Degrader.get(), createGooContainerLootTable("degrader", BlocksRegistry.Degrader.get()));
        blockLootTables.put(BlocksRegistry.Bulb.get(), createGooContainerWithContainmentLootTable("goo_bulb", BlocksRegistry.Bulb.get()));
        blockLootTables.put(BlocksRegistry.Mixer.get(), createGooContainerLootTable("mixer", BlocksRegistry.Mixer.get()));
        blockLootTables.put(BlocksRegistry.Solidifier.get(), createGooContainerLootTable("solidifier", BlocksRegistry.Solidifier.get()));
        blockLootTables.put(BlocksRegistry.Gooifier.get(), createGooContainerLootTable("gooifier", BlocksRegistry.Gooifier.get()));
        blockLootTables.put(BlocksRegistry.Drain.get(), createMundaneTable("drain", BlocksRegistry.Drain.get()));
        blockLootTables.put(BlocksRegistry.CrystalNest.get(), createMundaneTable("crystal_nest", BlocksRegistry.CrystalNest.get()));
        blockLootTables.put(BlocksRegistry.Lobber.get(), createMundaneTable("lobber", BlocksRegistry.Lobber.get()));
        blockLootTables.put(BlocksRegistry.Pump.get(), createMundaneTable("pump", BlocksRegistry.Pump.get()));
        blockLootTables.put(BlocksRegistry.Trough.get(), createGooContainerLootTable("trough", BlocksRegistry.Trough.get()));
        blockLootTables.put(BlocksRegistry.Crucible.get(), createGooContainerLootTable("crucible", BlocksRegistry.Crucible.get()));
        BlocksRegistry.CrystalBlocks.forEach(this::createLootTableForDecorativeBlock);

        advancementLootTables.put(new ResourceLocation(GooMod.MOD_ID, "crying_obsidian_hint_advancement_loot_table"), createCryingObsidianHintLootTable());
    }

    private Builder createCryingObsidianHintLootTable() {
        LootPool.Builder builder = LootPool.builder()
                .name("crying_obsidian_pickup_hint")
                .rolls(ConstantRange.of(1))
                .addEntry(ItemLootEntry.builder(ItemsRegistry.GOO_AND_YOU.get()));
        return LootTable.builder().addLootPool(builder);
    }

    private void createLootTableForDecorativeBlock(ResourceLocation resourceLocation, RegistryObject<CrystalBlock> crystalBlockRegistryObject) {
        blockLootTables.put(crystalBlockRegistryObject.get(), createMundaneTable(resourceLocation.getPath(), crystalBlockRegistryObject.get()));
    }
}
