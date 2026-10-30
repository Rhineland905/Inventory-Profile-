package org.anti_ad.mc.ipnext.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.anti_ad.mc.ipnext.hud.DurabilityHud;
import org.anti_ad.mc.ipnext.hud.DurabilityHudEditorScreen;

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
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(20);
            context.takeScreenshot("ipn_inventory");
            DurabilityHudEditorScreen[] holder = new DurabilityHudEditorScreen[1];
            context.setScreen(() -> holder[0] = new DurabilityHudEditorScreen(null));
            context.waitTicks(10);
            context.takeScreenshot("ipn_editor");
            context.runOnClient(mc -> {
                DurabilityHudEditorScreen editor = holder[0];
                editor.mouseScrolled(0, 0, 0, 1);
                editor.mouseScrolled(0, 0, 0, 1);
                editor.mouseScrolled(0, 0, 0, 1);
                editor.mouseScrolled(0, 0, 0, 1);
                editor.mouseScrolled(0, 0, 0, 1);
                DurabilityHud.Layout l = DurabilityHud.INSTANCE.layout(editor.width, editor.height);
                MouseButtonInfo left = new MouseButtonInfo(0, 0);
                editor.mouseClicked(new MouseButtonEvent(l.getX() + 5, l.getY() + 5, left), false);
                editor.mouseDragged(new MouseButtonEvent(40, 60, left), 0, 0);
                editor.mouseReleased(new MouseButtonEvent(40, 60, left));
            });
            context.waitTicks(10);
            context.takeScreenshot("ipn_editor_moved_bigger");
            context.setScreen(() -> null);
            context.waitTicks(10);
            context.takeScreenshot("ipn_hud_after_edit");
        }
    }
}
