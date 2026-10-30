/*
 * Inventory Profiles Next
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.anti_ad.mc.ipnext.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.anti_ad.mc.ipnext.config.DurabilityHudSettings
import org.anti_ad.mc.ipnext.config.DurabilityHudText
import org.anti_ad.mc.ipnext.config.ExtraHudSettings

private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string

/** Durability of held tools and worn armor. */
object DurabilityPanel : HudPanel(
        Placement({ DurabilityHudSettings.DURABILITY_HUD_POSITION.value },
                  { DurabilityHudSettings.DURABILITY_HUD_OFFSET_X.integerValue }, { DurabilityHudSettings.DURABILITY_HUD_OFFSET_X.value = it },
                  { DurabilityHudSettings.DURABILITY_HUD_OFFSET_Y.integerValue }, { DurabilityHudSettings.DURABILITY_HUD_OFFSET_Y.value = it },
                  { DurabilityHudSettings.DURABILITY_HUD_SCALE.integerValue }, { DurabilityHudSettings.DURABILITY_HUD_SCALE.value = it },
                  { DurabilityHudSettings.DURABILITY_HUD_HORIZONTAL.booleanValue },
                  { DurabilityHudSettings.DURABILITY_HUD_SHOW_BACKGROUND.booleanValue },
                  { DurabilityHudSettings.DURABILITY_HUD_SHOW_BAR.booleanValue }),
        "inventoryprofiles.gui.config.DurabilityHudSettings") {

    private val handSlots = listOf(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    override fun enabled() = DurabilityHudSettings.DURABILITY_HUD_ENABLED.booleanValue

    override fun rows(): List<Row> {
        val s = DurabilityHudSettings
        val player = Minecraft.getInstance().player ?: return emptyList()
        val slots = (if (s.DURABILITY_HUD_SHOW_HANDS.booleanValue) handSlots else emptyList()) +
                (if (s.DURABILITY_HUD_SHOW_ARMOR.booleanValue) armorSlots else emptyList())
        return slots.mapNotNull { toRow(player.getItemBySlot(it), s.DURABILITY_HUD_ONLY_DAMAGEABLE.booleanValue) }
    }

    override fun demoRows(): List<Row> {
        fun demo(item: net.minecraft.world.item.Item, used: Float): Row? {
            val stack = ItemStack(item)
            stack.damageValue = (stack.maxDamage * used).toInt()
            return toRow(stack, true)
        }
        return listOfNotNull(demo(Items.DIAMOND_PICKAXE, 0.3f), demo(Items.SHIELD, 0.85f), demo(Items.IRON_HELMET, 0.1f), demo(Items.DIAMOND_CHESTPLATE, 0.6f))
    }

    private fun toRow(stack: ItemStack, onlyDamageable: Boolean): Row? {
        if (stack.isEmpty) return null
        if (stack.isDamageableItem && stack.maxDamage > 0) {
            val max = stack.maxDamage
            val left = max - stack.damageValue
            val percent = left * 100 / max
            val text = when (DurabilityHudSettings.DURABILITY_HUD_TEXT.value) {
                DurabilityHudText.PERCENT -> "$percent%"
                DurabilityHudText.NUMBERS -> "$left/$max"
                DurabilityHudText.BOTH    -> "$left/$max $percent%"
                else                      -> ""
            }
            val low = percent <= DurabilityHudSettings.DURABILITY_HUD_WARN_PERCENT.integerValue
            val color = when {
                low           -> Jp.SHU_RED
                percent > 50  -> Jp.MATCHA
                else          -> Jp.YAMABUKI
            }
            return Row(stack = stack, text = text, color = if (low) Jp.SHU_RED else Jp.WASHI_TEXT,
                       fraction = left.toFloat() / max, barColor = color,
                       textHidden = low && DurabilityHudSettings.DURABILITY_HUD_WARN_BLINK.booleanValue && Jp.blinkOff())
        }
        return if (onlyDamageable) null else Row(stack = stack, text = if (stack.count > 1) "x${stack.count}" else "")
    }
}

/** How many totems, arrows, food and blocks you carry. */
object ItemCounterPanel : HudPanel(
        Placement({ ExtraHudSettings.ITEM_COUNTER_POSITION.value },
                  { ExtraHudSettings.ITEM_COUNTER_OFFSET_X.integerValue }, { ExtraHudSettings.ITEM_COUNTER_OFFSET_X.value = it },
                  { ExtraHudSettings.ITEM_COUNTER_OFFSET_Y.integerValue }, { ExtraHudSettings.ITEM_COUNTER_OFFSET_Y.value = it },
                  { ExtraHudSettings.ITEM_COUNTER_SCALE.integerValue }, { ExtraHudSettings.ITEM_COUNTER_SCALE.value = it },
                  { ExtraHudSettings.ITEM_COUNTER_HORIZONTAL.booleanValue }),
        "inventoryprofiles.config.category.extra_hud.item_counter") {

    override fun enabled() = ExtraHudSettings.ITEM_COUNTER_ENABLED.booleanValue

    private fun wantedIds(): List<String> = ExtraHudSettings.ITEM_COUNTER_ITEMS.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    override fun rows(): List<Row> {
        val player = Minecraft.getInstance().player ?: return emptyList()
        val inv = player.inventory
        val counts = linkedMapOf<String, Int>()
        val icons = mutableMapOf<String, ItemStack>()
        var food = 0
        var foodIcon: ItemStack? = null
        var blocks = 0
        var blockIcon: ItemStack? = null
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (!stack.isEmpty) {
                val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                counts[id] = (counts[id] ?: 0) + stack.count
                icons.putIfAbsent(id, stack)
                if (stack.has(DataComponents.FOOD)) {
                    food += stack.count
                    if (foodIcon == null) foodIcon = stack
                }
                if (stack.item is BlockItem) {
                    blocks += stack.count
                    if (blockIcon == null) blockIcon = stack
                }
            }
        }
        val hideZero = ExtraHudSettings.ITEM_COUNTER_HIDE_ZERO.booleanValue
        val rows = mutableListOf<Row>()
        for (id in wantedIds()) {
            val n = counts[id] ?: 0
            if (n > 0 || !hideZero) {
                val icon = icons[id] ?: iconFor(id)
                if (icon != null) rows += Row(stack = icon.copyWithCount(1), text = "$n", color = if (n == 0) Jp.SHU_RED else Jp.WASHI_TEXT)
            }
        }
        if (ExtraHudSettings.ITEM_COUNTER_FOOD.booleanValue && (food > 0 || !hideZero)) {
            rows += Row(stack = (foodIcon ?: ItemStack(Items.BREAD)).copyWithCount(1), text = "${tr("inventoryprofiles.hud.food")}: $food")
        }
        if (ExtraHudSettings.ITEM_COUNTER_BLOCKS.booleanValue && (blocks > 0 || !hideZero)) {
            rows += Row(stack = (blockIcon ?: ItemStack(Items.COBBLESTONE)).copyWithCount(1), text = "${tr("inventoryprofiles.hud.blocks")}: $blocks")
        }
        return rows
    }

    private fun iconFor(id: String): ItemStack? {
        val key = Identifier.tryParse(id) ?: return null
        val item = BuiltInRegistries.ITEM.getValue(key)
        return if (item == Items.AIR) null else ItemStack(item)
    }

    override fun demoRows(): List<Row> = listOf(Row(stack = ItemStack(Items.TOTEM_OF_UNDYING), text = "3"),
                                                Row(stack = ItemStack(Items.ARROW), text = "64"),
                                                Row(stack = ItemStack(Items.BREAD), text = "${tr("inventoryprofiles.hud.food")}: 12"))
}

/** Active potion effects with icons and time left. */
object EffectsPanel : HudPanel(
        Placement({ ExtraHudSettings.EFFECTS_HUD_POSITION.value },
                  { ExtraHudSettings.EFFECTS_HUD_OFFSET_X.integerValue }, { ExtraHudSettings.EFFECTS_HUD_OFFSET_X.value = it },
                  { ExtraHudSettings.EFFECTS_HUD_OFFSET_Y.integerValue }, { ExtraHudSettings.EFFECTS_HUD_OFFSET_Y.value = it },
                  { ExtraHudSettings.EFFECTS_HUD_SCALE.integerValue }, { ExtraHudSettings.EFFECTS_HUD_SCALE.value = it },
                  { ExtraHudSettings.EFFECTS_HUD_HORIZONTAL.booleanValue }),
        "inventoryprofiles.config.category.extra_hud.effects") {

    override fun enabled() = ExtraHudSettings.EFFECTS_HUD_ENABLED.booleanValue

    private val roman = listOf("", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X")

    private fun time(ticks: Int): String {
        val sec = ticks / 20
        return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
    }

    private fun row(inst: MobEffectInstance): Row {
        val name = inst.effect.value().displayName.string + (roman.getOrNull(inst.amplifier) ?: " ${inst.amplifier + 1}")
        val infinite = inst.isInfiniteDuration
        val ending = !infinite && inst.duration < 200
        val text = if (infinite) "$name ∞" else "$name ${time(inst.duration)}"
        return Row(sprite = Hud.getMobEffectSprite(inst.effect), text = text, color = if (ending) Jp.SHU_RED else Jp.WASHI_TEXT,
                   textHidden = ending && Jp.blinkOff())
    }

    override fun rows(): List<Row> {
        val player = Minecraft.getInstance().player ?: return emptyList()
        return player.activeEffects.sortedByDescending { it.duration }.map { row(it) }
    }

    override fun demoRows(): List<Row> = listOf(Row(stack = ItemStack(Items.POTION), text = "Speed II 2:30"),
                                                Row(stack = ItemStack(Items.POTION), text = "Night Vision 7:45"))
}

/** In-game time of day and day number. */
object ClockPanel : HudPanel(
        Placement({ ExtraHudSettings.CLOCK_HUD_POSITION.value },
                  { ExtraHudSettings.CLOCK_HUD_OFFSET_X.integerValue }, { ExtraHudSettings.CLOCK_HUD_OFFSET_X.value = it },
                  { ExtraHudSettings.CLOCK_HUD_OFFSET_Y.integerValue }, { ExtraHudSettings.CLOCK_HUD_OFFSET_Y.value = it },
                  { ExtraHudSettings.CLOCK_HUD_SCALE.integerValue }, { ExtraHudSettings.CLOCK_HUD_SCALE.value = it }),
        "inventoryprofiles.config.category.extra_hud.clock") {

    override fun enabled() = ExtraHudSettings.CLOCK_HUD_ENABLED.booleanValue

    private fun rowsFor(dayTime: Long): List<Row> {
        val t = ((dayTime % 24000) + 24000) % 24000
        val hours = ((t / 1000 + 6) % 24).toInt()
        val minutes = ((t % 1000) * 60 / 1000).toInt()
        val night = t in 13000..22999
        val rows = mutableListOf(Row(stack = ItemStack(Items.CLOCK), text = "%02d:%02d".format(hours, minutes), color = if (night) Jp.YAMABUKI else Jp.WASHI_TEXT))
        if (ExtraHudSettings.CLOCK_HUD_SHOW_DAY.booleanValue) {
            rows += Row(stack = ItemStack(Items.SUNFLOWER), text = tr("inventoryprofiles.hud.day", dayTime / 24000 + 1))
        }
        return rows
    }

    override fun rows(): List<Row> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        return rowsFor(level.overworldClockTime)
    }

    override fun demoRows(): List<Row> = rowsFor(30000)
}

/** Warns once when a held tool or worn armor piece gets close to breaking. Only informs, never acts. */
object LowDurabilityAlert {
    private val last = mutableMapOf<EquipmentSlot, Int>()
    private var message: String? = null
    private var until = 0L

    fun check() {
        val player = Minecraft.getInstance().player
        if (player == null || !ExtraHudSettings.LOW_DURABILITY_ALERT.booleanValue) {
            last.clear()
            return
        }
        val limit = ExtraHudSettings.LOW_DURABILITY_ALERT_PERCENT.integerValue
        for (slot in EquipmentSlot.entries) {
            if (slot.isArmor || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                val stack = player.getItemBySlot(slot)
                val percent = if (!stack.isEmpty && stack.isDamageableItem && stack.maxDamage > 0) (stack.maxDamage - stack.damageValue) * 100 / stack.maxDamage else -1
                val before = last[slot]
                if (percent in 0..limit && (before == null || before > limit)) {
                    if (before != null) fire(stack, percent)
                }
                last[slot] = percent
            }
        }
    }

    private fun fire(stack: ItemStack, percent: Int) {
        if (ExtraHudSettings.LOW_DURABILITY_ALERT_SOUND.booleanValue) {
            Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 0.6f))
        }
        if (ExtraHudSettings.LOW_DURABILITY_ALERT_TEXT.booleanValue) {
            message = tr("inventoryprofiles.hud.low_durability", stack.hoverName.string, percent)
            until = System.currentTimeMillis() + 3000
        }
    }

    fun draw(graphics: net.minecraft.client.gui.GuiGraphicsExtractor) {
        val text = message ?: return
        if (System.currentTimeMillis() > until) {
            message = null
            return
        }
        val font = Minecraft.getInstance().font
        val w = font.width(text) + 16
        val x = (graphics.guiWidth() - w) / 2
        val y = graphics.guiHeight() / 4
        graphics.fill(x, y, x + w, y + 18, Jp.URUSHI_BG)
        graphics.outline(x, y, w, 18, Jp.SHU_RED)
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, Jp.KIN_GOLD)
        graphics.text(font, text, x + 8, y + 7, Jp.WASHI_TEXT, true)
    }
}

/** Gold frame with a vermilion stripe around chests and other containers. Decoration only. */
object JpContainerFrame {
    fun draw(graphics: net.minecraft.client.gui.GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        if (!ExtraHudSettings.CONTAINER_JAPANESE_FRAME.booleanValue) return
        val l = x - 3
        val t = y - 3
        val r = x + w + 3
        val b = y + h + 3
        graphics.outline(l, t, r - l, b - t, Jp.KIN_GOLD)
        graphics.outline(l + 1, t + 1, r - l - 2, b - t - 2, 0xFF3A2A12.toInt())
        graphics.fill(l + 6, t - 3, r - 6, t, Jp.SHU_RED)
        graphics.fill(l + 6, t - 4, r - 6, t - 3, Jp.KIN_GOLD)
        for ((cx, cy) in listOf(l to t, r to t, l to b, r to b)) {
            graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, Jp.KIN_GOLD)
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, Jp.SHU_RED)
        }
    }
}
