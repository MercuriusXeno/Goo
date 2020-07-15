package com.xeno.goop.datagen;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.xeno.goop.GoopMod;
import com.xeno.goop.setup.Registration;
import net.minecraft.block.Block;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.IDataProvider;
import net.minecraft.data.LootTableProvider;
import net.minecraft.data.loot.BlockLootTables;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.functions.CopyName;
import net.minecraft.world.storage.loot.functions.CopyNbt;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LootTables extends LootTableProvider implements IDataProvider {
    public LootTables(DataGenerator dataGeneratorIn) {
        super(dataGeneratorIn);
    }

    private final List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootParameterSet>> tables = ImmutableList.of(
            Pair.of(BlockTables::new, LootParameterSets.BLOCK)
    );

    @Override
    protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootParameterSet>> getTables()
    {
        return tables;
    }

    @Override
    protected void validate(Map<ResourceLocation, LootTable> map, ValidationTracker validationtracker) {
        map.forEach((p_218436_2_, p_218436_3_) -> {
            LootTableManager.func_227508_a_(validationtracker, p_218436_2_, p_218436_3_);
        });
    }

    public static class BlockTables extends BlockLootTables
    {
        @Override
        protected void addTables()
        {
            this.registerLootTable(Registration.GOOP_BULB.get(), BlockTables::dropWithPackagedContents);
        }

        protected static LootTable.Builder dropWithPackagedContents(Block p_218544_0_) {
            return LootTable.builder()
                    .addLootPool(withSurvivesExplosion(p_218544_0_, LootPool.builder()
                            .rolls(ConstantRange.of(1))
                            .addEntry(ItemLootEntry.builder(p_218544_0_)
                                    .acceptFunction(CopyName.builder(CopyName.Source.BLOCK_ENTITY))
                                    .acceptFunction(CopyNbt.builder(CopyNbt.Source.BLOCK_ENTITY)
                                            .replaceOperation("Block", "BlockEntityTag.Block")
                                            .replaceOperation("BlockEntity", "BlockEntityTag.BlockEntity")))));
        }

        @Nonnull
        @Override
        protected Iterable<Block> getKnownBlocks()
        {
            return ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(b -> b.getRegistryName().getNamespace().equals(GoopMod.MOD_ID) && b.getRegistryName().equals(Registration.GOOP_BULB.get().getRegistryName()))
                    .collect(Collectors.toList());
        }
    }
}
