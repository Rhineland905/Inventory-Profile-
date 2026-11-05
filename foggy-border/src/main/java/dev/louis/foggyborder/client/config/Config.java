package dev.louis.foggyborder.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.louis.foggyborder.client.FoggyBorder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Same file and fields as the original mod (config/foggyborder.json). */
public class Config {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("foggyborder.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean disableWorldborder = true;
    public boolean dontRenderBehindBorder = true;
    public float fogEndDistanceMultiplier = 1;
    public float minimumFogEndDistance = 2f;

    public void write() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            FoggyBorder.LOGGER.error("Error writing config, skipping writing.", e);
        }
    }

    public static Config read() {
        if (!Files.exists(CONFIG_PATH)) {
            Config c = new Config();
            c.write();
            return c;
        }
        try {
            Config c = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
            return c != null ? c : new Config();
        } catch (Exception e) {
            FoggyBorder.LOGGER.warn("Error reading config, using default.");
            return new Config();
        }
    }
}
