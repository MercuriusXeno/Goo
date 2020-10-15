package com.xeno.goo.datagen;

import com.xeno.goo.blocks.BlocksRegistry;
import net.minecraft.data.DataGenerator;

public class LootTables extends BaseLootTableProvider {
    public LootTables(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
    }

    @Override
    protected void addTables() {
        lootTables.put(BlocksRegistry.Crucible.get(), createCrucibleTable("crucible", BlocksRegistry.Crucible.get()));
    }
}
