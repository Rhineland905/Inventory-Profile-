package dev.schematicprogress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** N opens the dashboard in the browser; the "origin here" key is unbound by default. */
public final class SchematicProgressClient implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("schematicprogress");
    private static boolean announced;
    private static boolean autoLoaded;

    @Override
    public void onInitializeClient() {
        Config.load();
        try {
            Files.createDirectories(Config.schematicsDir());
        } catch (Exception ignored) {}
        WebServer.start();
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schematicprogress.open", InputConstants.KEY_N, KeyMapping.Category.MISC));
        KeyMapping origin = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schematicprogress.origin", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.consumeClick()) openBrowser(mc);
            while (origin.consumeClick()) {
                Tracker.originHere();
                say(mc, Tracker.active == null ? "Сначала выбери схематику на веб-панели" : Tracker.lastMessage);
            }
            if (mc.player != null && !announced) {
                announced = true;
                say(mc, WebServer.port < 0 ? "веб-панель не запустилась (порт занят)"
                        : "веб-панель: " + WebServer.url() + "  (клавиша N)");
            }
            if (mc.player != null && !autoLoaded) {
                autoLoaded = true;
                autoLoad();
            }
            Tracker.tick(mc);
        });
    }

    private static void autoLoad() {
        if (Config.file.isEmpty() || Tracker.active != null) return;
        Path file = Config.schematicsDir().resolve(Config.file);
        if (!Files.isRegularFile(file)) return;
        try {
            Tracker.activate(Schematic.load(file), file);
        } catch (Exception e) {
            Tracker.lastError = "Не удалось прочитать " + Config.file + ": " + e.getMessage();
        }
    }

    static void say(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Schematic Progress] " + text));
    }

    private static void openBrowser(Minecraft mc) {
        if (WebServer.port < 0) return;
        String url = WebServer.url();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (Exception e) {
            say(mc, "открой в браузере: " + url);
        }
    }
}
