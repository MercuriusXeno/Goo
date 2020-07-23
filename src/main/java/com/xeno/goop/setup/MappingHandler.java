package com.xeno.goop.setup;

import com.google.gson.*;
import com.xeno.goop.GoopMod;
import com.xeno.goop.network.GoopValueSyncPacketData;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.core.jackson.Log4jJsonObjectMapper;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MappingHandler {
    private static final String MAPPING_SAVE_DATA_FILENAME = "goopMappings.json";
    private static GoopValueMappingData goopValueMappings;
    public static void reloadMappings(@Nonnull World world) {
        tryLoadFromFile(world);
    }

    private static void tryLoadFromFile(World world) {
        String worldName = world.getWorldInfo().getWorldName();
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
        if (mappingsFile.length() > 0) {
            try (FileReader reader = new FileReader(mappingsFile.getAbsolutePath())) {
                JsonStreamParser parser = new JsonStreamParser(reader);
                if (parser.hasNext()) {
                    parentArray = parser.next().getAsJsonArray();
                }
            } catch (EOFException eof) {
                System.out.println("EOF on mappings file - not a real error, you just didn't have Goop mappings. This is fine!");
                eof.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        GoopValueMappingData data = GoopValueMappingData.deserializeFromJson(parentArray);

        seedAllRecipeOutputItemNames(world);

        edifyBaseItemMappingData(data);

        edifyRecipeOutputMappingData(data);

        data.sortMappings();

        // todo scan recipes for exploit loops or disparate quantity yields

        MappingHandler.goopValueMappings = data;

        try (FileWriter writer = new FileWriter(mappingsFile.getAbsolutePath())) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(GoopValueMappingData.serializeMappingData(MappingHandler.goopValueMappings).toString());
            String prettyJson = gson.toJson(je);
            writer.write(prettyJson);
            writer.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void edifyBaseItemMappingData(GoopValueMappingData data) {
        List<String> missingMappings = scanRegistriesForUnmappedResourceLocations(data);
        if (missingMappings.size() == 0) {
            return;
        }
        if (GoopMod.DEBUG) {
            System.out.println("Missing baseline mappings! (this is not an error):");
        }
        missingMappings.forEach((m) -> {
            if (allRecipeItems.stream().anyMatch(r -> r.equals(m))) {
                return;
            }
            if (!data.tryAddingDefaultMapping(m)) {
                if (GoopMod.DEBUG) {
                    int namespaceIndex = m.indexOf(":");
                    String namespaceInvariantRegistryName = m.substring(namespaceIndex);
                    String allCapsRegistryName = namespaceInvariantRegistryName.toUpperCase();
                    System.out.println("addMapping(Items." + allCapsRegistryName + ", goopValue(,))");
                }
                data.addEmptyMapping(m);
            }
        });
    }

    private static void edifyRecipeOutputMappingData(GoopValueMappingData data) {
        List<String> missingMappings = scanRegistriesForUnmappedResourceLocations(data);

        missingMappings.forEach((m) -> {
            if (allRecipeItems.stream().noneMatch(r -> r.equals(m))) {
                return;
            }
            if (!data.tryAddingDefaultMapping(m)) {
                data.addEmptyMapping(m);
            }
        });
    }

    public static List<String> scanRegistriesForUnmappedResourceLocations(GoopValueMappingData data) {
        return allRegistryItems.stream().filter(r -> data.getMappings().stream().noneMatch(m -> m.getItemResourceLocation().equals(r) && m.getGoopValues().size() > 0)).collect(Collectors.toList());
    }

    private static final List<String> allRegistryItems = getAllItemRegistryNames();
    private static List<String> getAllItemRegistryNames() {
        return ForgeRegistries.ITEMS.getValues().stream().map(x -> Objects.requireNonNull(x.getRegistryName()).toString()).collect(Collectors.toList());
    }

    private static List<String> allRecipeItems;
    private static void seedAllRecipeOutputItemNames(@Nonnull World world) {
        if (allRecipeItems == null) {
            allRecipeItems = new ArrayList<>();
        } else {
            return;
        }
        RecipeManager recMan = world.getRecipeManager();
        recMan.getRecipes().forEach(r -> allRecipeItems.add(Objects.requireNonNull(r.getRecipeOutput().getItem().getRegistryName()).toString()));
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
