package com.xeno.goop.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class FileHelper {

    public static File getOrCreateMappingDirectoryWithFileName(ServerWorld world, String mappingSaveDataFilename) {
        Path worldPath = world.getSaveHandler().getWorldDirectory().toPath();
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
            return null;
        }

        File mappingsFile = goopPath.resolve(mappingSaveDataFilename).toFile();
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
            return null;
        }

        return mappingsFile;
    }
}
