package org.anti_ad.mc.ipnext.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Starts a world, equips damaged gear and takes screenshots of the HUD and the inventory. */
public class IpnScreenshotTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            context.waitTicks(60);
            String[] cmds = {
                    "gamemode survival @a",
                    "time set day",
                    "item replace entity @a weapon.mainhand with diamond_pickaxe[damage=1200]",
                    "item replace entity @a weapon.offhand with shield[damage=320]",
                    "item replace entity @a armor.head with iron_helmet[damage=20]",
                    "item replace entity @a armor.chest with diamond_chestplate[damage=420]",
                    "item replace entity @a armor.legs with golden_leggings[damage=80]",
                    "item replace entity @a armor.feet with netherite_boots[damage=100]",
                    "give @a totem_of_undying 3",
                    "give @a cobblestone 64",
                    "give @a oak_log 20",
                    "give @a bread 12"
            };
            for (String c : cmds) {
                sp.getServer().runCommand(c);
            }
            context.waitTicks(40);
            context.takeScreenshot("ipn_hud");
            context.runOnClient(mc -> mc.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(20);
            context.takeScreenshot("ipn_inventory");
            context.runOnClient(mc -> mc.setScreen(null));
            context.waitTicks(5);
        }
    }
}
