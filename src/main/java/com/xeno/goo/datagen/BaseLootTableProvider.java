package com.xeno.goo.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xeno.goo.GooMod;
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
    protected final Map<Block, LootTable.Builder> lootTables = new HashMap<>();

    private final DataGenerator generator;

    public BaseLootTableProvider(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
        this.generator = dataGeneratorIn;
    }

    // Subclasses can override this to fill the 'lootTables' map.
    protected abstract void addTables();

    // Subclasses can call this if they want a standard loot table. Modify this for your own needs
    protected LootTable.Builder createCrucibleTable(String name, Block block) {
        LootPool.Builder builder = LootPool.builder()
                .name(name)
                .rolls(ConstantRange.of(1))
                .addEntry(ItemLootEntry.builder(block)
                        .acceptFunction(CopyName.builder(CopyName.Source.BLOCK_ENTITY))
                        .acceptFunction(CopyNbt.builder(CopyNbt.Source.BLOCK_ENTITY)
                                .replaceOperation("goo", "BlockEntityTag.goo")
                                .replaceOperation("id", "BlockEntityTag.id"))
//                        .acceptFunction(SetContents.builderIn()
//                                .addLootEntry(DynamicLootEntry.func_216162_a(new ResourceLocation("minecraft", "contents"))))
                );
        return LootTable.builder().addLootPool(builder);
    }

    @Override
    // Entry point
    public void act(DirectoryCache cache) {
        addTables();

        Map<ResourceLocation, LootTable> tables = new HashMap<>();
        for (Map.Entry<Block, LootTable.Builder> entry : lootTables.entrySet()) {
            tables.put(entry.getKey().getLootTable(), entry.getValue().setParameterSet(LootParameterSets.BLOCK).build());
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