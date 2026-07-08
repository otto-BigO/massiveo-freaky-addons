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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles portal routing calculations: loads portal zones and entrances from
 * config/massiveo_portals.json. If the destination cell lies in a portal zone,
 * but the player is not, it routes the bot to the portal entrance first.
 */
public final class PortalRouting {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;
    private static List<Portal> portals = new ArrayList<Portal>();

    public static final class Portal {
        public String name;
        public int entranceX;
        public int entranceY;
        public int entranceZ;
        public double minX, maxX;
        public double minY, maxY;
        public double minZ, maxZ;
        public List<String> targetGangs = new ArrayList<String>();

        public Portal(String name, int ex, int ey, int ez, double minX, double maxX, double minY, double maxY, double minZ, double maxZ, List<String> targetGangs) {
            this.name = name;
            this.entranceX = ex;
            this.entranceY = ey;
            this.entranceZ = ez;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.targetGangs = targetGangs != null ? targetGangs : new ArrayList<String>();
        }

        public BlockPos getEntrance() {
            return new BlockPos(entranceX, entranceY, entranceZ);
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private PortalRouting() {
    }

    public static void init(File configDir) {
        file = new File(configDir, "massiveo_portals.json");
        load();
    }

    private static List<Portal> defaults() {
        List<Portal> d = new ArrayList<Portal>();
        
        d.add(new Portal("Portal 1", -4, 66, -720, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 1 Cellegang", "Portal 1 gang", "Portal 1")));

        d.add(new Portal("Portal 2", 0, 66, -720, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 2 Cellegang", "Portal 2 gang", "Portal 2")));

        d.add(new Portal("Portal 3", 5, 66, -715, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 3 Cellegang", "Portal 3 gang", "Portal 3")));

        d.add(new Portal("Portal 4", -4, 65, -670, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 4 Cellegang", "Portal 4 gang", "Portal 4")));

        d.add(new Portal("Portal 5", -22, 65, -651, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 5 Cellegang", "Portal 5 gang", "Portal 5")));

        d.add(new Portal("Portal 6", -48, 65, -651, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 6 Cellegang", "Portal 6 gang", "Portal 6")));

        d.add(new Portal("Portal 7", -58, 65, -651, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 7 Cellegang", "Portal 7 gang", "Portal 7")));

        d.add(new Portal("Portal 8", -53, 65, -651, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 8 Cellegang", "Portal 8 gang", "Portal 8")));

        d.add(new Portal("Portal 9", -12, 65, -651, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Portal 9 Cellegang", "Portal 9 gang", "Portal 9")));

        d.add(new Portal("Portal til pre celler", -71, 56, -727, 0, 0, 0, 0, 0, 0, 
            Arrays.asList("Pre Cellegang", "Pre-release Cellegang", "Pre-release gang", "b-pre-release gang", "pre")));

        return d;
    }

    public static void load() {
        List<Portal> loaded = new ArrayList<Portal>();
        if (file != null && file.exists()) {
            FileReader reader = null;
            try {
                reader = new FileReader(file);
                Type type = new TypeToken<List<Portal>>() {}.getType();
                List<Portal> parsed = GSON.fromJson(reader, type);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                System.err.println("[CelleScanner] Kunne ikke læse portal-fil: " + e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {}
                }
            }
        }

        boolean changed = loaded.isEmpty() && (file == null || !file.exists());
        for (Portal def : defaults()) {
            if (!containsSame(loaded, def)) {
                loaded.add(def);
                changed = true;
            }
        }

        portals = loaded;
        if (changed) {
            save();
        }
    }

    private static boolean containsSame(List<Portal> list, Portal p) {
        for (Portal e : list) {
            if (e != null && e.name != null && e.name.equalsIgnoreCase(p.name)) {
                return true;
            }
        }
        return false;
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
                GSON.toJson(portals, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static BlockPos getPortalEntranceFor(double playerX, double playerY, double playerZ, double targetX, double targetY, double targetZ) {
        for (Portal p : portals) {
            if (p.contains(targetX, targetY, targetZ) && !p.contains(playerX, playerY, playerZ)) {
                return p.getEntrance();
            }
        }
        return null;
    }

    public static Portal getPortalForGang(String gangName) {
        if (gangName == null || gangName.isEmpty()) {
            return null;
        }
        for (Portal p : portals) {
            if (p.targetGangs != null) {
                for (String tg : p.targetGangs) {
                    if (gangName.equalsIgnoreCase(tg)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }
}
