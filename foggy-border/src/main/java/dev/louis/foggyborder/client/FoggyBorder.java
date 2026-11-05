package dev.louis.foggyborder.client;

import dev.louis.foggyborder.client.config.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FoggyBorder implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("foggyborder");
    public static Config config = Config.read();

    @Override
    public void onInitializeClient() {
        KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.foggyborder.settings", 79 /* O */, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settings.consumeClick()) {
                client.setScreenAndShow(new SettingsScreen());
            }
        });
        LOGGER.info("Foggy Border initialised");
    }
}
