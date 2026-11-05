package dev.traderoller;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** What trade we are looking for. */
public final class Settings {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("traderoller.properties");

    /** Comma separated, e.g. "mending, efficiency 5, unbreaking 3". Any one of them is enough. */
    public static volatile String targets = "mending";
    /** Highest emerald price we accept; 64 means any price. */
    public static volatile int maxPrice = 64;

    /** One wanted trade: an enchantment (on a book) or an item, with an optional minimum level. */
    public record Target(String id, int minLevel) {}

    private Settings() {}

    public static List<Target> parsed() {
        List<Target> out = new ArrayList<>();
        for (String raw : targets.split("[,;]")) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            int level = 0;
            int space = s.lastIndexOf(' ');
            if (space > 0) {
                int l = parseLevel(s.substring(space + 1));
                if (l > 0) {
                    level = l;
                    s = s.substring(0, space).trim();
                }
            }
            s = s.replace(' ', '_');
            if (!s.contains(":")) s = "minecraft:" + s;
            out.add(new Target(s, level));
        }
        return out;
    }

    private static int parseLevel(String s) {
        switch (s) {
            case "i": return 1;
            case "ii": return 2;
            case "iii": return 3;
            case "iv": return 4;
            case "v": return 5;
            default:
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return 0;
                }
        }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(FILE)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        targets = p.getProperty("targets", targets);
        try {
            maxPrice = Math.max(1, Math.min(64, Integer.parseInt(p.getProperty("maxPrice", "64").trim())));
        } catch (NumberFormatException ignored) { }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("targets", targets);
        p.setProperty("maxPrice", Integer.toString(maxPrice));
        try (Writer w = Files.newBufferedWriter(FILE)) {
            p.store(w, "Trade Roller settings");
        } catch (IOException e) {
            System.err.println("[TradeRoller] Could not save settings: " + e);
        }
    }
}
