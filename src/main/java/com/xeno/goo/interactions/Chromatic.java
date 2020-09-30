package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.item.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chromatic
{
    public static void registerInteractions()
    {
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_wool", 0, Chromatic::dyeWool);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_concrete_powder", 1, Chromatic::dyeConcretePowder);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_concrete", 2, Chromatic::dyeConcrete);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_terracotta", 3, Chromatic::dyeTerracotta);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_glazed_terracotta", 4, Chromatic::dyeTerracotta);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_glass", 5, Chromatic::dyeGlass);
    }

    private final static Map<MaterialColor, DyeColor> cycleMap = new HashMap<>();
    private final static Map<MaterialColor, Block> woolMap = new HashMap<>();
    private final static Map<MaterialColor, Block> concretePowderMap = new HashMap<>();
    private final static Map<MaterialColor, Block> concreteMap = new HashMap<>();
    private final static Map<MaterialColor, Block> terracottaMap = new HashMap<>();
    private final static Map<MaterialColor, Block> glazedTerracottaMap = new HashMap<>();
    private final static Map<MaterialColor, Block> glassMap = new HashMap<>();
    private final static Map<Block, MaterialColor> uncoloredVariants = new HashMap<>();

    static {
        // map cycled dye colors loop
        for(DyeColor color : DyeColor.values()) {
            int i = color.getId();
            if (i == DyeColor.values().length - 1) {
                i = 0;
            } else {
                i++;
            }
            cycleMap.put(color.getMapColor(), DyeColor.byId(i));
        }

        // map wool colors to each respective block
        woolMap.put(MaterialColor.SNOW, Blocks.WHITE_WOOL);
        woolMap.put(MaterialColor.ADOBE, Blocks.ORANGE_WOOL);
        woolMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_WOOL);
        woolMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_WOOL);
        woolMap.put(MaterialColor.YELLOW, Blocks.YELLOW_WOOL);
        woolMap.put(MaterialColor.LIME, Blocks.LIME_WOOL);
        woolMap.put(MaterialColor.PINK, Blocks.PINK_WOOL);
        woolMap.put(MaterialColor.GRAY, Blocks.GRAY_WOOL);
        woolMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_WOOL);
        woolMap.put(MaterialColor.CYAN, Blocks.CYAN_WOOL);
        woolMap.put(MaterialColor.PURPLE, Blocks.PURPLE_WOOL);
        woolMap.put(MaterialColor.BLUE, Blocks.BLUE_WOOL);
        woolMap.put(MaterialColor.BROWN, Blocks.BROWN_WOOL);
        woolMap.put(MaterialColor.GREEN, Blocks.GREEN_WOOL);
        woolMap.put(MaterialColor.RED, Blocks.RED_WOOL);
        woolMap.put(MaterialColor.BLACK, Blocks.BLACK_WOOL);

        // map concrete powder to each respective block
        concretePowderMap.put(MaterialColor.SNOW, Blocks.WHITE_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.ADOBE, Blocks.ORANGE_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.YELLOW, Blocks.YELLOW_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.LIME, Blocks.LIME_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.PINK, Blocks.PINK_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.GRAY, Blocks.GRAY_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.CYAN, Blocks.CYAN_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.PURPLE, Blocks.PURPLE_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.BLUE, Blocks.BLUE_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.BROWN, Blocks.BROWN_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.GREEN, Blocks.GREEN_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.RED, Blocks.RED_CONCRETE_POWDER);
        concretePowderMap.put(MaterialColor.BLACK, Blocks.BLACK_CONCRETE_POWDER);

        // map concrete to each respective block
        concreteMap.put(MaterialColor.SNOW, Blocks.WHITE_CONCRETE);
        concreteMap.put(MaterialColor.ADOBE, Blocks.ORANGE_CONCRETE);
        concreteMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_CONCRETE);
        concreteMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_CONCRETE);
        concreteMap.put(MaterialColor.YELLOW, Blocks.YELLOW_CONCRETE);
        concreteMap.put(MaterialColor.LIME, Blocks.LIME_CONCRETE);
        concreteMap.put(MaterialColor.PINK, Blocks.PINK_CONCRETE);
        concreteMap.put(MaterialColor.GRAY, Blocks.GRAY_CONCRETE);
        concreteMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_CONCRETE);
        concreteMap.put(MaterialColor.CYAN, Blocks.CYAN_CONCRETE);
        concreteMap.put(MaterialColor.PURPLE, Blocks.PURPLE_CONCRETE);
        concreteMap.put(MaterialColor.BLUE, Blocks.BLUE_CONCRETE);
        concreteMap.put(MaterialColor.BROWN, Blocks.BROWN_CONCRETE);
        concreteMap.put(MaterialColor.GREEN, Blocks.GREEN_CONCRETE);
        concreteMap.put(MaterialColor.RED, Blocks.RED_CONCRETE);
        concreteMap.put(MaterialColor.BLACK, Blocks.BLACK_CONCRETE);

        // map glass to each respective block
        glassMap.put(MaterialColor.SNOW, Blocks.WHITE_STAINED_GLASS);
        glassMap.put(MaterialColor.ADOBE, Blocks.ORANGE_STAINED_GLASS);
        glassMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_STAINED_GLASS);
        glassMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_STAINED_GLASS);
        glassMap.put(MaterialColor.YELLOW, Blocks.YELLOW_STAINED_GLASS);
        glassMap.put(MaterialColor.LIME, Blocks.LIME_STAINED_GLASS);
        glassMap.put(MaterialColor.PINK, Blocks.PINK_STAINED_GLASS);
        glassMap.put(MaterialColor.GRAY, Blocks.GRAY_STAINED_GLASS);
        glassMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_STAINED_GLASS);
        glassMap.put(MaterialColor.CYAN, Blocks.CYAN_STAINED_GLASS);
        glassMap.put(MaterialColor.PURPLE, Blocks.PURPLE_STAINED_GLASS);
        glassMap.put(MaterialColor.BLUE, Blocks.BLUE_STAINED_GLASS);
        glassMap.put(MaterialColor.BROWN, Blocks.BROWN_STAINED_GLASS);
        glassMap.put(MaterialColor.GREEN, Blocks.GREEN_STAINED_GLASS);
        glassMap.put(MaterialColor.RED, Blocks.RED_STAINED_GLASS);
        glassMap.put(MaterialColor.BLACK, Blocks.BLACK_STAINED_GLASS);

        // map terracotta to each respective block
        terracottaMap.put(MaterialColor.SNOW, Blocks.WHITE_TERRACOTTA);
        terracottaMap.put(MaterialColor.ADOBE, Blocks.ORANGE_TERRACOTTA);
        terracottaMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_TERRACOTTA);
        terracottaMap.put(MaterialColor.YELLOW, Blocks.YELLOW_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIME, Blocks.LIME_TERRACOTTA);
        terracottaMap.put(MaterialColor.PINK, Blocks.PINK_TERRACOTTA);
        terracottaMap.put(MaterialColor.GRAY, Blocks.GRAY_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_TERRACOTTA);
        terracottaMap.put(MaterialColor.CYAN, Blocks.CYAN_TERRACOTTA);
        terracottaMap.put(MaterialColor.PURPLE, Blocks.PURPLE_TERRACOTTA);
        terracottaMap.put(MaterialColor.BLUE, Blocks.BLUE_TERRACOTTA);
        terracottaMap.put(MaterialColor.BROWN, Blocks.BROWN_TERRACOTTA);
        terracottaMap.put(MaterialColor.GREEN, Blocks.GREEN_TERRACOTTA);
        terracottaMap.put(MaterialColor.RED, Blocks.RED_TERRACOTTA);
        terracottaMap.put(MaterialColor.BLACK, Blocks.BLACK_TERRACOTTA);

        // map glazed terracotta to each respective block
        glazedTerracottaMap.put(MaterialColor.SNOW, Blocks.WHITE_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.ADOBE, Blocks.ORANGE_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.MAGENTA, Blocks.MAGENTA_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.LIGHT_BLUE, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.YELLOW, Blocks.YELLOW_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.LIME, Blocks.LIME_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.PINK, Blocks.PINK_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.GRAY, Blocks.GRAY_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.LIGHT_GRAY, Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.CYAN, Blocks.CYAN_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.PURPLE, Blocks.PURPLE_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.BLUE, Blocks.BLUE_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.BROWN, Blocks.BROWN_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.GREEN, Blocks.GREEN_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.RED, Blocks.RED_GLAZED_TERRACOTTA);
        glazedTerracottaMap.put(MaterialColor.BLACK, Blocks.BLACK_GLAZED_TERRACOTTA);

        // map uncolored variants
        uncoloredVariants.put(Blocks.GLASS, MaterialColor.SNOW);
        uncoloredVariants.put(Blocks.TERRACOTTA, MaterialColor.ADOBE);
    }
    private static DyeColor cycleDyeColor(World world, BlockState state, BlockPos pos)
    {
        return cycleDyeColor(state.getMaterialColor(world, pos));
    }

    private static DyeColor cycleDyeColor(MaterialColor originalColor)
    {
        return cycleMap.get(originalColor);
    }

    private static boolean dyeTerracotta(InteractionContext ic)
    {
        if (!isTerracotta(ic)) {
            return false;
        }

        DyeColor newColor;
        if (isUncoloredVariant(ic)) {
            newColor = colorOfUncoloredVariant(ic);
        } else {
            newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        }
        Block dyedBlock = terracottaMap.get(newColor.getMapColor());
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static DyeColor colorOfUncoloredVariant(InteractionContext ic)
    {
        return cycleDyeColor(uncoloredVariants.get(ic.block()));
    }

    private static boolean isUncoloredVariant(InteractionContext ic)
    {
        return uncoloredVariants.containsKey(ic.block());
    }

    private static boolean isTerracotta(InteractionContext ic)
    {
        // in the colored map or the uncolored variant
        return terracottaMap.containsValue(ic.block()) || ic.block().equals(Blocks.TERRACOTTA);
    }

    private static boolean dyeGlass(InteractionContext ic)
    {
        if (!isGlass(ic)) {
            return false;
        }

        DyeColor newColor;
        if (isUncoloredVariant(ic)) {
            newColor = colorOfUncoloredVariant(ic);
        } else {
            newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        }
        Block dyedBlock = glassMap.get(newColor.getMapColor());
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isGlass(InteractionContext ic)
    {
        // in the colored map or the uncolored variant
        return glassMap.containsValue(ic.block()) || ic.block().equals(Blocks.GLASS);
    }

    private static boolean dyeConcrete(InteractionContext ic)
    {
        if (!isConcrete(ic)) {
            return false;
        }

        DyeColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = concreteMap.get(newColor.getMapColor());
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isConcrete(InteractionContext ic)
    {
        return concreteMap.containsValue(ic.block());
    }

    private static boolean dyeConcretePowder(InteractionContext ic)
    {
        if (!isConcretePowder(ic)) {
            return false;
        }

        DyeColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = concretePowderMap.get(newColor.getMapColor());
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isConcretePowder(InteractionContext ic)
    {
        return concretePowderMap.containsValue(ic.block());
    }

    private static boolean dyeWool(InteractionContext ic)
    {
        if (!isWool(ic)) {
            return false;
        }

        DyeColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = woolMap.get(newColor.getMapColor());
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isWool(InteractionContext ic)
    {
        return woolMap.containsValue(ic.block());
    }
}
