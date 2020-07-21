package com.xeno.goop.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonStreamParser;
import com.xeno.goop.network.GoopValueSyncPacketData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MappingHandler {
    private static final String MAPPING_SAVE_DATA_FILENAME = "goopMappings.json";

    private static GoopValueMappingData goopValueMappings;

    public static void reloadMappings(@Nonnull World world) {
        tryLoadFromFile(world.getWorldInfo().getWorldName());
    }

    private static void tryLoadFromFile(String worldName) {
        Path worldPath = Minecraft.getInstance().getSaveLoader().getSavesDir().resolve(worldName);
        Path goopPath = worldPath.resolve("goop");
        File goopDir = new File(goopPath.toUri());
        boolean madeDir = goopDir.isDirectory();

        try {
            if (!madeDir) {
                if (!goopDir.mkdir()) {
                    throw new IOException("Mapping directory in save file not found and could not be created!");
                }
            }
        } catch (IOException ed) {
            ed.printStackTrace();
            return;
        }

        File mappingsFile = goopPath.resolve(MAPPING_SAVE_DATA_FILENAME).toFile();
        try {
            if (!mappingsFile.isFile()) {
                if (!mappingsFile.createNewFile()) {
                    throw new IOException("Mappings file not found and could not be created!");
                }
            }
            if (!mappingsFile.canRead() || !mappingsFile.canWrite()) {
                throw new IOException("Improper access to the mapping file contents!");
            }
        } catch (IOException ef){
            ef.printStackTrace();
            return;
        }

        JsonArray parentArray = new JsonArray();
        try (FileReader reader = new FileReader(mappingsFile.getAbsolutePath())) {
            JsonStreamParser parser = new JsonStreamParser(reader);
            // without this an unexpected EOF can throw an error, but this should theoretically dodge it.
            // there should only be a single element in the buffer, the parent level array.
            if (parser.hasNext()) {
                parentArray = parser.next().getAsJsonArray();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        GoopValueMappingData data = GoopValueMappingData.deserializeFromJson(parentArray);

        edifyMappingData(data);

        MappingHandler.goopValueMappings = data;

        try (FileWriter writer = new FileWriter(mappingsFile.getAbsolutePath())) {
            writer.write(GoopValueMappingData.serializeMappingData(MappingHandler.goopValueMappings).toString());
            writer.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void edifyMappingData(GoopValueMappingData data) {
        List<String> missingMappings = scanRegistriesForUnmappedResourceLocations(data);

        // TODO fill in any missing mappings using procedural capture and default values where able, or create denial entries as needed, et al.
    }

    public static List<String> scanRegistriesForUnmappedResourceLocations(GoopValueMappingData data) {
        List<String> result = new ArrayList<>();
        result.addAll(allRegistryItems.stream().filter(r -> !data.getMappings().contains(r)).collect(Collectors.toList()));
        return result;
    }

    private static final List<String> allRegistryItems = getAllItemRegistryNames();
    private static List<String> getAllItemRegistryNames() {
        return ForgeRegistries.ITEMS.getValues().stream().map(x -> x.getRegistryName().toString()).collect(Collectors.toList());
    }

    private static void scanRecipesForMappings(@Nonnull World world) {
        RecipeManager recMan = world.getRecipeManager();
        int recipeIndex = 0;
        for(IRecipe<?> r : recMan.getRecipes()) {
            recipeIndex++;
            ItemStack output = r.getRecipeOutput();
            System.out.print("Recipe index " + recipeIndex + " outputs " + output.getItem().getRegistryName().getPath());
            List<Ingredient> inputs = r.getIngredients();
            System.out.print("| Valid inputs: ");
            int inputIndex = 0;
            for(Ingredient g : inputs) {
                if (g.hasNoMatchingItems()) {
                    continue;
                }
                inputIndex++;
                System.out.print(inputIndex + ") ");
                for(ItemStack s : g.getMatchingStacks()) {
                    if (s.isEmpty()) {
                        continue;
                    }
                    System.out.print(s.getItem().getRegistryName().getPath() + " ");
                }
            }
            System.out.println("");
        }
    }

    public static GoopValueSyncPacketData[] createPacketData() {
        // TODO
        return new GoopValueSyncPacketData[0];
    }

    public static void fromPacket(GoopValueSyncPacketData[] data) {

    }
}
