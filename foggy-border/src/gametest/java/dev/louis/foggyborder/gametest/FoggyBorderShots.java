package dev.louis.foggyborder.gametest;

import dev.louis.foggyborder.client.FoggyBorder;
import dev.louis.foggyborder.client.SettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Stands next to a small world border and takes screenshots with the mod on and off. */
public class FoggyBorderShots implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            context.waitTicks(60);
            for (String c : new String[]{
                    "gamemode creative @a", "time set day", "weather clear",
                    "worldborder center 0 0", "worldborder set 64",
                    "tp @a 0 ~ 0 90 0"}) {
                sp.getServer().runCommand(c);
            }
            context.waitTicks(100);
            context.takeScreenshot("fb_center_on");
            sp.getServer().runCommand("tp @a 24 ~ 0 -90 0");
            context.waitTicks(60);
            context.takeScreenshot("fb_near_on");
            sp.getServer().runCommand("tp @a 30 ~ 0 -90 0");
            context.waitTicks(60);
            context.takeScreenshot("fb_edge_on");
            sp.getServer().runCommand("tp @a 28 ~ 0 90 0");
            context.waitTicks(60);
            context.takeScreenshot("fb_edge_facing_border_on");

            context.runOnClient(mc -> {
                FoggyBorder.config.disableWorldborder = false;
                FoggyBorder.config.dontRenderBehindBorder = false;
            });
            context.setScreen(SettingsScreen::new);
            context.waitTicks(10);
            context.takeScreenshot("fb_settings");
            context.setScreen(() -> null);
            context.waitTicks(80);
            context.takeScreenshot("fb_edge_facing_border_vanilla_blocks");
        }
    }
}
