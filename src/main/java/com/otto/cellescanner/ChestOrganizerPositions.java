package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles persistent storage for configured chest items/icons.
 * Key format is normalized string: "x,y,z,dimension".
 */
public final class ChestOrganizerPositions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;
    private static Map<String, String> icons = new HashMap<String, String>();

    private ChestOrganizerPositions() {
    }

    public static void init(File configDir) {
        file = new File(configDir, "chest_organizer.json");
        load();
    }

    private static void load() {
        if (file == null || !file.exists()) {
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            Type type = new TypeToken<HashMap<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                icons = loaded;
            }
        } catch (Exception e) {
            System.err.println("[CelleScanner] Failed to read chest organizer icons: " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(icons, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getIcon(BlockPos pos, int dimension) {
        return icons.get(makeKey(pos, dimension));
    }

    public static void setIcon(BlockPos pos, int dimension, String itemRegistryName) {
        if (pos == null || itemRegistryName == null || itemRegistryName.isEmpty()) {
            return;
        }
        icons.put(makeKey(pos, dimension), itemRegistryName);
        save();
    }

    public static void removeIcon(BlockPos pos, int dimension) {
        if (pos == null) {
            return;
        }
        icons.remove(makeKey(pos, dimension));
        save();
    }

    public static Map<String, String> getAll() {
        return new HashMap<String, String>(icons);
    }

    public static String makeKey(BlockPos pos, int dimension) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "," + dimension;
    }

    public static BlockPos parseBlockPos(String key) {
        String[] parts = key.split(",");
        if (parts.length >= 3) {
            return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
        return null;
    }

    public static int parseDimension(String key) {
        String[] parts = key.split(",");
        if (parts.length >= 4) {
            return Integer.parseInt(parts[3]);
        }
        return 0; // Default to overworld if absent
    }
}
