package com.xeno.goo.library;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.xeno.goo.GooMod;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.FolderName;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public class FileHelper {
    private static final Gson GSON_INSTANCE = new GsonBuilder().setPrettyPrinting().create();
    private static final Type JSON_SERIALIZER_GOOP_MAPPING_TYPE = new TypeToken<TreeMap<String, GooEntry>>(){}.getType();
    private static final Type JSON_SERIALIZER_EQUIVALENCY_TYPE = new TypeToken<TreeMap<String, String>>(){}.getType();
    private static final Type JSON_SERIALIZER_COMPOSITE_TYPE = new TypeToken<TreeMap<String, ComplexEntry>>(){}.getType();

    private static Path getWorldGooDataDirectoryPath(ServerWorld world) {
        Path worldPath = world.getServer().func_240776_a_(FolderName.field_237247_c_).toFile().toPath();
        return worldPath.resolve("goop");
    }

    private static File getOrCreateWorldSaveFile(Path worldSaveDirectory, String worldFileName) {
        try {
            if (worldFileName.equals("")) {
                throw new IOException("Nice try with the empty file name or whatever.");
            }
            File worldSaveDirAsFile = new File(worldSaveDirectory.toUri());
            checkOrCreateDirectory(worldSaveDirAsFile);

            File woldSaveFile = checkOrCreateFile(worldSaveDirectory.resolve(worldFileName).toFile());
            return woldSaveFile;
        }  catch(IOException e) {
                // GooMod.warn("Caught something trying to save with an Empty file name, shame on you.");
        }
        return null;
    }


    public static File openWorldFile(ServerWorld world, String worldFileName) {
        return getOrCreateWorldSaveFile(getWorldGooDataDirectoryPath(world), worldFileName);
    }

    private static File checkOrCreateFile(File mappingsFile) {
        try {
            if (!mappingsFile.isFile()) {
                if (!mappingsFile.createNewFile()) {
                    throw new IOException("File not found and could not be created!");
                }
            }
            if (!mappingsFile.canRead() || !mappingsFile.canWrite()) {
                throw new IOException("Improper access to the file contents!");
            }
        } catch (IOException ef){
            GooMod.warn(ef.getMessage());
            return null;
        }
        return mappingsFile;
    }

    private static boolean checkOrCreateDirectory(File goopDir)
    {
        try {
            if (!goopDir.isDirectory()) {
                if (!goopDir.mkdir()) {
                    throw new IOException("Entry directory in save file not found and could not be created!");
                }
            }
        } catch (IOException ed) {
            GooMod.warn(ed.getMessage());
            return false;
        }
        return true;
    }

    public static Map<String, GooEntry> readEntryFile(File mappingsFile) {
        JsonElement element = new JsonObject();
        if (mappingsFile.length() > 0) {
            try (FileReader reader = new FileReader(mappingsFile.getAbsolutePath())) {
                JsonStreamParser parser = new JsonStreamParser(reader);
                if (parser.hasNext()) {
                    element = parser.next().getAsJsonObject();
                }
            } catch (EOFException eof) {
                GooMod.warn("EOF on mappings file, was it supposed to be empty? (" + mappingsFile.toString() + ")");
            } catch (IOException ioe) {
                GooMod.warn("Couldn't read the mappings file. Maybe real bad! (" + mappingsFile.toString() + ")");
            }
        }

        return GSON_INSTANCE.fromJson(element, JSON_SERIALIZER_GOOP_MAPPING_TYPE);
    }

    public static void writeEntryFile(File mappingsFile, Map<String, GooEntry> values) {
        try (FileWriter writer = new FileWriter(mappingsFile.getAbsolutePath())) {
            String jsonString = GSON_INSTANCE.toJson(values, JSON_SERIALIZER_GOOP_MAPPING_TYPE);
            writer.write(jsonString);
            writer.flush();
        } catch (IOException ioe) {
            GooMod.error("Can't write the mapping file! This is maybe real bad!");
        }
    }

    public static Map<String, String> readEquivalencyFile(File mappingsFile) {
        JsonElement element = new JsonObject();
        if (mappingsFile.length() > 0) {
            try (FileReader reader = new FileReader(mappingsFile.getAbsolutePath())) {
                JsonStreamParser parser = new JsonStreamParser(reader);
                if (parser.hasNext()) {
                    element = parser.next().getAsJsonObject();
                }
            } catch (EOFException eof) {
                GooMod.debug("EOF on equivalency file - not a real error, you just didn't have Equivalency pairings. This is fine!");
            } catch (IOException ioe) {
                GooMod.warn("Error reading the equivalency file. This could be a perms issue or maybe it just didn't exist.");
            }
        }

        return GSON_INSTANCE.fromJson(element, JSON_SERIALIZER_EQUIVALENCY_TYPE);
    }

    public static void writeEquivalencyFile(File mappingsFile, Map<String, String> values) {
        try (FileWriter writer = new FileWriter(mappingsFile.getAbsolutePath())) {
            String jsonString = GSON_INSTANCE.toJson(values, JSON_SERIALIZER_EQUIVALENCY_TYPE);
            writer.write(jsonString);
            writer.flush();
        } catch (IOException ioe) {
            GooMod.error("Can't write equivalency file! This is maybe real bad!");
        }
    }

    public static Map<String, ComplexEntry> readCompositeFile(File file)
    {
        JsonElement element = new JsonObject();
        if (file.length() > 0) {
            try (FileReader reader = new FileReader(file.getAbsolutePath())) {
                JsonStreamParser parser = new JsonStreamParser(reader);
                if (parser.hasNext()) {
                    element = parser.next().getAsJsonObject();
                }
            } catch (EOFException eof) {
                GooMod.debug("EOF on composite file - not a real error, you just didn't have Equivalency pairings. This is fine!");
            } catch (IOException ioe) {
                GooMod.warn("Error reading the composite file. This could be a perms issue or maybe it just didn't exist.");
            }
        }

        return GSON_INSTANCE.fromJson(element, JSON_SERIALIZER_COMPOSITE_TYPE);
    }

    public static void writeCompositeFile(File file, Map<String, ComplexEntry> equivalencies)
    {
        try (FileWriter writer = new FileWriter(file.getAbsolutePath())) {
            String jsonString = GSON_INSTANCE.toJson(equivalencies, JSON_SERIALIZER_COMPOSITE_TYPE);
            writer.write(jsonString);
            writer.flush();
        } catch (IOException ioe) {
            GooMod.error("Can't write the composite file! This is maybe real bad!");
        }
    }
}
