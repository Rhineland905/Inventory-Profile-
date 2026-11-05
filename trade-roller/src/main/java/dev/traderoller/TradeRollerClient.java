package dev.traderoller;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** J starts or stops rerolling, U opens the settings (both can be changed in Controls). */
public final class TradeRollerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Settings.load();
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.traderoller.toggle", 74 /* J */, KeyMapping.Category.MISC));
        KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.traderoller.settings", 85 /* U */, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) Roller.toggle(client);
            while (settings.consumeClick()) {
                if (client.player != null) client.setScreenAndShow(new SettingsScreen());
            }
            Roller.tick(client);
        });
    }
}
