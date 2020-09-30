package com.xeno.goo.interactions;

import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.item.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class Chromatic
{
    public static void registerInteractions()
    {
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_wool", 0, Chromatic::dyeWool);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_concrete_powder", 1, Chromatic::dyeConcretePowder);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_concrete", 2, Chromatic::dyeConcrete);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_terracotta", 3, Chromatic::dyeTerracotta);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_glazed_terracotta", 4, Chromatic::dyeGlazedTerracotta);
        GooInteractions.register(Registry.CHROMATIC_GOO.get(), "dye_glass", 5, Chromatic::dyeGlass);
    }

    private final static Map<MaterialColor, MaterialColor> cycleMap = new HashMap<>();
    private final static List<MaterialColor> terracottaColors = new ArrayList<>();
    // terracotta is weird
    private final static Map<MaterialColor, MaterialColor> terracottaCycleMap = new HashMap<>();
    private final static Map<MaterialColor, Block> woolMap = new HashMap<>();
    private final static Map<MaterialColor, Block> concretePowderMap = new HashMap<>();
    private final static Map<MaterialColor, Block> concreteMap = new HashMap<>();
    private final static Map<MaterialColor, Block> terracottaMap = new HashMap<>();
    private final static Map<MaterialColor, Block> glazedTerracottaMap = new HashMap<>();
    private final static Map<MaterialColor, Block> glassMap = new HashMap<>();

    private final static int minTerracottaColorIndex = 36;
    private final static int maxTerracottaColorIndex = 51;
    static {
        // map cycled dye colors loop
        for(DyeColor color : DyeColor.values()) {
            int i = color.getId();
            if (i == DyeColor.values().length - 1) {
                i = 0;
            } else {
                i++;
            }
            cycleMap.put(color.getMapColor(), DyeColor.byId(i).getMapColor());
        }

        terracottaColors.addAll(Arrays.asList(MaterialColor.COLORS).subList(minTerracottaColorIndex, maxTerracottaColorIndex + 1));

        for(MaterialColor color : terracottaColors) {
            int i = color.colorIndex;
            if (i == maxTerracottaColorIndex) {
                i = minTerracottaColorIndex;
            } else {
                i++;
            }
            terracottaCycleMap.put(color, MaterialColor.COLORS[i]);
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
        terracottaMap.put(MaterialColor.WHITE_TERRACOTTA, Blocks.WHITE_TERRACOTTA);
        terracottaMap.put(MaterialColor.ORANGE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA);
        terracottaMap.put(MaterialColor.MAGENTA_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIGHT_BLUE_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA);
        terracottaMap.put(MaterialColor.YELLOW_TERRACOTTA, Blocks.YELLOW_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIME_TERRACOTTA, Blocks.LIME_TERRACOTTA);
        terracottaMap.put(MaterialColor.PINK_TERRACOTTA, Blocks.PINK_TERRACOTTA);
        terracottaMap.put(MaterialColor.GRAY_TERRACOTTA, Blocks.GRAY_TERRACOTTA);
        terracottaMap.put(MaterialColor.LIGHT_GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA);
        terracottaMap.put(MaterialColor.CYAN_TERRACOTTA, Blocks.CYAN_TERRACOTTA);
        terracottaMap.put(MaterialColor.PURPLE_TERRACOTTA, Blocks.PURPLE_TERRACOTTA);
        terracottaMap.put(MaterialColor.BLUE_TERRACOTTA, Blocks.BLUE_TERRACOTTA);
        terracottaMap.put(MaterialColor.BROWN_TERRACOTTA, Blocks.BROWN_TERRACOTTA);
        terracottaMap.put(MaterialColor.GREEN_TERRACOTTA, Blocks.GREEN_TERRACOTTA);
        terracottaMap.put(MaterialColor.RED_TERRACOTTA, Blocks.RED_TERRACOTTA);
        terracottaMap.put(MaterialColor.BLACK_TERRACOTTA, Blocks.BLACK_TERRACOTTA);

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
    }

    private static MaterialColor cycleDyeColor(World world, BlockState state, BlockPos pos)
    {
        return cycleDyeColor(state.getMaterialColor(world, pos));
    }

    private static MaterialColor cycleDyeColor(MaterialColor originalColor)
    {
        return cycleMap.get(originalColor);
    }

    private static MaterialColor cycleTerracottaColor(World world, BlockState state, BlockPos pos)
    {
        return cycleTerracottaColor(state.getMaterialColor(world, pos));
    }

    private static MaterialColor cycleTerracottaColor(MaterialColor originalColor)
    {
        return terracottaCycleMap.get(originalColor);
    }

    private static boolean dyeTerracotta(InteractionContext ic)
    {
        if (!isTerracotta(ic)) {
            return false;
        }

        MaterialColor newColor;
        if (ic.block().equals(Blocks.TERRACOTTA)) {
            newColor = MaterialColor.ORANGE_TERRACOTTA;
        } else {
            newColor = cycleTerracottaColor(ic.world(), ic.blockState(), ic.blockPos());
        }
        Block dyedBlock = terracottaMap.get(newColor);
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
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

        MaterialColor newColor;
        if (ic.block().equals(Blocks.GLASS)) {
            newColor = MaterialColor.SNOW;
        } else {
            newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        }
        Block dyedBlock = glassMap.get(newColor);
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

        MaterialColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = concreteMap.get(newColor);
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean dyeGlazedTerracotta(InteractionContext ic)
    {
        if (!isGlazedTerracotta(ic)) {
            return false;
        }

        MaterialColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = glazedTerracottaMap.get(newColor);
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isGlazedTerracotta(InteractionContext ic)
    {
        return glazedTerracottaMap.containsValue(ic.block());
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

        MaterialColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = concretePowderMap.get(newColor);
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

        MaterialColor newColor = cycleDyeColor(ic.world(), ic.blockState(), ic.blockPos());
        Block dyedBlock = woolMap.get(newColor);
        ic.setBlockState(dyedBlock.getDefaultState());
        return true;
    }

    private static boolean isWool(InteractionContext ic)
    {
        return woolMap.containsValue(ic.block());
    }
}
