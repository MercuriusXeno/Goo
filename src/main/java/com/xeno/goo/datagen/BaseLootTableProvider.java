package com.xeno.goo.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import net.minecraft.data.LootTableProvider;
import net.minecraft.loot.*;
import net.minecraft.loot.functions.CopyName;
import net.minecraft.loot.functions.CopyNbt;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseLootTableProvider extends LootTableProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // Filled by subclasses
    protected final Map<Block, LootTable.Builder> blockLootTables = new HashMap<>();
    protected final Map<ResourceLocation, LootTable.Builder> advancementLootTables = new HashMap<>();

    private final DataGenerator generator;

    public BaseLootTableProvider(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
        this.generator = dataGeneratorIn;
    }

    // Subclasses can override this to fill the 'lootTables' map.
    protected abstract void addTables();

    // absolutely bog standard loot table for things with no nbt requirements
    // used for drain, lobber, pump
    protected LootTable.Builder createMundaneTable(String name, Block block) {
        LootPool.Builder builder = LootPool.builder()
                .name(name)
                .rolls(ConstantRange.of(1))
                .addEntry(ItemLootEntry.builder(block)
                        .acceptFunction(CopyName.builder(CopyName.Source.BLOCK_ENTITY))
                );
        return LootTable.builder().addLootPool(builder);
    }

    // any tile that holds goo needs some nbt (goo) and its id
    protected LootTable.Builder createGooContainerLootTable(String name, Block block) {
        LootPool.Builder builder = LootPool.builder()
                .name(name)
                .rolls(ConstantRange.of(1))
                .addEntry(ItemLootEntry.builder(block)
                        .acceptFunction(CopyName.builder(CopyName.Source.BLOCK_ENTITY))
                        .acceptFunction(CopyNbt.builder(CopyNbt.Source.BLOCK_ENTITY)
                                .replaceOperation("goo", "BlockEntityTag.goo")
                                .replaceOperation("id", "BlockEntityTag.id"))
                );
        return LootTable.builder().addLootPool(builder);
    }

    protected LootTable.Builder createGooContainerWithContainmentLootTable(String name, Block block) {
        LootPool.Builder builder = LootPool.builder()
                .name(name)
                .rolls(ConstantRange.of(1))
                .addEntry(ItemLootEntry.builder(block)
                        .acceptFunction(CopyName.builder(CopyName.Source.BLOCK_ENTITY))
                        .acceptFunction(CopyNbt.builder(CopyNbt.Source.BLOCK_ENTITY)
                                .replaceOperation("goo", "BlockEntityTag.goo")
                                .replaceOperation(Registry.CONTAINMENT.getId().toString(), "BlockEntityTag." + Registry.CONTAINMENT.getId().toString())
                                .replaceOperation("id", "BlockEntityTag.id")
                        )
                );
        return LootTable.builder().addLootPool(builder);
    }

    @Override
    // Entry point
    public void act(DirectoryCache cache) {
        addTables();

        Map<ResourceLocation, LootTable> tables = new HashMap<>();
        for (Map.Entry<Block, LootTable.Builder> entry : blockLootTables.entrySet()) {
            tables.put(entry.getKey().getLootTable(), entry.getValue().setParameterSet(LootParameterSets.BLOCK).build());
        }
        for (Map.Entry<ResourceLocation, LootTable.Builder> entry : advancementLootTables.entrySet()) {
            tables.put(entry.getKey(), entry.getValue().setParameterSet(LootParameterSets.ADVANCEMENT).build());
        }
        writeTables(cache, tables);
    }

    // Actually write out the tables in the output folder
    private void writeTables(DirectoryCache cache, Map<ResourceLocation, LootTable> tables) {
        Path outputFolder = this.generator.getOutputFolder();
        tables.forEach((key, lootTable) -> {
            Path path = outputFolder.resolve("data/" + key.getNamespace() + "/loot_tables/" + key.getPath() + ".json");
            try {
                IDataProvider.save(GSON, cache, LootTableManager.toJson(lootTable), path);
            } catch (IOException e) {
                GooMod.error("Couldn't write loot table " + path + e);
            }
        });
    }

    @Override
    public String getName() {
        return "Goo LootTables";
    }
}