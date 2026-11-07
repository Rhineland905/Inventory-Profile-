package dev.schematicprogress;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** config/schematic-progress.properties: web port plus the last schematic and where it was placed. */
final class Config {
    private Config() {}

    static int port = 8765;
    /** false = only this computer can open the page; true = also a phone in the same Wi-Fi. */
    static boolean allowLan = false;

    static String file = "";
    static boolean hasOrigin = false;
    static int ox, oy, oz, rot;
    static String dimension = "";

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("schematic-progress.properties");
    }

    static Path schematicsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path())) {
            p.load(r);
        } catch (Exception ignored) {
            save();
            return;
        }
        port = parse(p.getProperty("port"), port);
        allowLan = Boolean.parseBoolean(p.getProperty("allowLan", "false"));
        file = p.getProperty("file", "");
        hasOrigin = Boolean.parseBoolean(p.getProperty("hasOrigin", "false"));
        ox = parse(p.getProperty("originX"), 0);
        oy = parse(p.getProperty("originY"), 0);
        oz = parse(p.getProperty("originZ"), 0);
        rot = Math.floorMod(parse(p.getProperty("rotation"), 0), 4);
        dimension = p.getProperty("dimension", "");
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("port", Integer.toString(port));
        p.setProperty("allowLan", Boolean.toString(allowLan));
        p.setProperty("file", file);
        p.setProperty("hasOrigin", Boolean.toString(hasOrigin));
        p.setProperty("originX", Integer.toString(ox));
        p.setProperty("originY", Integer.toString(oy));
        p.setProperty("originZ", Integer.toString(oz));
        p.setProperty("rotation", Integer.toString(rot));
        p.setProperty("dimension", dimension);
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                p.store(w, "Schematic Progress. allowLan=true lets other devices in your network open the page.");
            }
        } catch (Exception e) {
            SchematicProgressClient.LOG.warn("Could not save config", e);
        }
    }

    private static int parse(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
