package dev.portalbreaker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/** Key K (can be changed in Controls) opens the settings screen. */
public final class PortalBreakerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping open = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.portalbreaker.open", InputConstants.Type.KEYSYM, 75 /* K */, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                if (client.player != null) client.setScreenAndShow(new SettingsScreen());
            }
        });
    }
}
