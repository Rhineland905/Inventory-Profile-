package dev.portalbreaker;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Which block to break and how big the area is. In singleplayer the game client and
 * the built-in server run in the same process, so the settings screen and the
 * breaking code share these fields directly.
 */
public final class Settings {
    public static final int MAX_RADIUS = 16;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("portalbreaker.properties");

    public static volatile Block target = Blocks.REINFORCED_DEEPSLATE;
    /** -1 means "the connected group", otherwise the cube radius. */
    public static volatile int radius = -1;

    private Settings() {}

    public static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** Block by id such as "minecraft:obsidian" or just "obsidian"; null if unknown or air. */
    public static Block parse(String text) {
        String id = text.trim().toLowerCase();
        if (id.isEmpty()) return null;
        if (!id.contains(":")) id = "minecraft:" + id;
        for (Block b : BuiltInRegistries.BLOCK) {
            if (id(b).equals(id)) return b == Blocks.AIR ? null : b;
        }
        return null;
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        Block b = parse(p.getProperty("block", ""));
        if (b != null) target = b;
        try {
            radius = Math.max(-1, Math.min(MAX_RADIUS, Integer.parseInt(p.getProperty("radius", "-1").trim())));
        } catch (NumberFormatException ignored) { }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("block", id(target));
        p.setProperty("radius", Integer.toString(radius));
        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Portal Breaker settings; radius -1 = connected group");
        } catch (IOException e) {
            System.err.println("[PortalBreaker] Could not save settings: " + e);
        }
    }
}
