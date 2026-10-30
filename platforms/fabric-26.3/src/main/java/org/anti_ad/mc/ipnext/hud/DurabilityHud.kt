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
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.anti_ad.mc.ipnext.config.DurabilityHudCorner
import org.anti_ad.mc.ipnext.config.DurabilityHudSettings
import org.anti_ad.mc.ipnext.config.DurabilityHudText

/**
 * On-screen durability of held tools and worn armor, drawn in a Japanese style:
 * a black lacquer panel with a gold frame and a vermilion stripe on top.
 */
object DurabilityHud {

    // Japanese traditional colors
    private const val URUSHI_BG = 0xD0120C0A.toInt()   // black lacquer
    const val KIN_GOLD = 0xFFC9A646.toInt()            // gold leaf
    const val SHU_RED = 0xFFC0272D.toInt()             // vermilion (torii)
    const val WASHI_TEXT = 0xFFF3E9D2.toInt()          // washi paper
    private const val MATCHA = 0xFF7BA23F.toInt()       // matcha green
    private const val YAMABUKI = 0xFFF8B500.toInt()     // yamabuki yellow
    private const val BAR_BG = 0xFF2B211D.toInt()

    private const val ICON = 16
    private const val GAP = 3
    private const val PAD = 4
    private const val STEP = 2

    const val MIN_SCALE = 50
    const val MAX_SCALE = 300

    private class Entry(val stack: ItemStack, val text: String, val fraction: Float?, val percent: Int = 100)

    /** Where the HUD is on screen, in screen pixels, plus its unscaled size. */
    class Layout(val x: Int, val y: Int, val width: Int, val height: Int, val boxW: Int, val boxH: Int, val scale: Float)

    private val handSlots = listOf(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    /** Normal in-game rendering, called after the hotbar. */
    fun render(graphics: GuiGraphicsExtractor) {
        if (DurabilityHudSettings.DURABILITY_HUD_ENABLED.booleanValue && org.anti_ad.mc.common.vanilla.Vanilla.screen() !is DurabilityHudEditorScreen) {
            val entries = currentEntries()
            if (entries.isNotEmpty()) {
                draw(graphics, entries)
            }
        }
    }

    /** Rendering inside the editor: always shows something, even with empty hands. */
    fun renderForEditor(graphics: GuiGraphicsExtractor): Layout {
        val entries = currentEntries().ifEmpty { demoEntries() }
        return draw(graphics, entries)
    }

    fun layout(sw: Int, sh: Int): Layout {
        val entries = currentEntries().ifEmpty { demoEntries() }
        return computeLayout(entries, sw, sh)
    }

    private fun currentEntries(): List<Entry> {
        val s = DurabilityHudSettings
        val player = Minecraft.getInstance().player ?: return emptyList()
        val slots = (if (s.DURABILITY_HUD_SHOW_HANDS.booleanValue) handSlots else emptyList()) +
                (if (s.DURABILITY_HUD_SHOW_ARMOR.booleanValue) armorSlots else emptyList())
        return slots.mapNotNull { slot -> toEntry(player.getItemBySlot(slot), s.DURABILITY_HUD_ONLY_DAMAGEABLE.booleanValue) }
    }

    private fun demoEntries(): List<Entry> {
        fun demo(item: net.minecraft.world.item.Item, used: Float): Entry? {
            val stack = ItemStack(item)
            stack.damageValue = (stack.maxDamage * used).toInt()
            return toEntry(stack, true)
        }
        return listOfNotNull(demo(Items.DIAMOND_PICKAXE, 0.3f), demo(Items.SHIELD, 0.85f), demo(Items.IRON_HELMET, 0.1f), demo(Items.DIAMOND_CHESTPLATE, 0.6f))
    }

    private fun toEntry(stack: ItemStack, onlyDamageable: Boolean): Entry? {
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
            return Entry(stack, text, left.toFloat() / max, percent)
        }
        return if (onlyDamageable) null else Entry(stack, if (stack.count > 1) "x${stack.count}" else "", null)
    }

    private fun entryWidth(entries: List<Entry>): Int {
        val font = Minecraft.getInstance().font
        val textWidth = entries.maxOf { font.width(it.text) }
        val hasText = textWidth > 0 || (DurabilityHudSettings.DURABILITY_HUD_SHOW_BAR.booleanValue && entries.any { it.fraction != null })
        return ICON + if (hasText) GAP + maxOf(textWidth, 24) else 0
    }

    private fun computeLayout(entries: List<Entry>, sw: Int, sh: Int): Layout {
        val s = DurabilityHudSettings
        val horizontal = s.DURABILITY_HUD_HORIZONTAL.booleanValue
        val entryW = entryWidth(entries)
        val contentW = if (horizontal) entries.size * entryW + (entries.size - 1) * (STEP + 2) else entryW
        val contentH = if (horizontal) ICON else entries.size * ICON + (entries.size - 1) * STEP
        val boxW = contentW + PAD * 2
        val boxH = contentH + PAD * 2 + 2 // +2 for the vermilion stripe
        val scale = s.DURABILITY_HUD_SCALE.integerValue.coerceIn(MIN_SCALE, MAX_SCALE) / 100f
        val realW = (boxW * scale).toInt()
        val realH = (boxH * scale).toInt()
        val ox = s.DURABILITY_HUD_OFFSET_X.integerValue
        val oy = s.DURABILITY_HUD_OFFSET_Y.integerValue
        val (x, y) = when (s.DURABILITY_HUD_POSITION.value) {
            DurabilityHudCorner.TOP_LEFT       -> ox to oy
            DurabilityHudCorner.TOP_RIGHT      -> (sw - realW - ox) to oy
            DurabilityHudCorner.BOTTOM_LEFT    -> ox to (sh - realH - oy)
            DurabilityHudCorner.NEXT_TO_HOTBAR -> (sw / 2 + 91 + 6 + ox) to (sh - realH - oy)
            else                               -> (sw - realW - ox) to (sh - realH - oy)
        }
        return Layout(x, y, realW, realH, boxW, boxH, scale)
    }

    /** Turns a wanted top-left position into offsets for the chosen corner. */
    fun offsetsFor(x: Int, y: Int, width: Int, height: Int, sw: Int, sh: Int): Pair<Int, Int> {
        return when (DurabilityHudSettings.DURABILITY_HUD_POSITION.value) {
            DurabilityHudCorner.TOP_LEFT       -> x to y
            DurabilityHudCorner.TOP_RIGHT      -> (sw - width - x) to y
            DurabilityHudCorner.BOTTOM_LEFT    -> x to (sh - height - y)
            DurabilityHudCorner.NEXT_TO_HOTBAR -> (x - (sw / 2 + 91 + 6)) to (sh - height - y)
            else                               -> (sw - width - x) to (sh - height - y)
        }
    }

    private fun draw(graphics: GuiGraphicsExtractor, entries: List<Entry>): Layout {
        val s = DurabilityHudSettings
        val font = Minecraft.getInstance().font
        val horizontal = s.DURABILITY_HUD_HORIZONTAL.booleanValue
        val showBar = s.DURABILITY_HUD_SHOW_BAR.booleanValue
        val showBg = s.DURABILITY_HUD_SHOW_BACKGROUND.booleanValue
        val warn = s.DURABILITY_HUD_WARN_PERCENT.integerValue
        val blinkOff = s.DURABILITY_HUD_WARN_BLINK.booleanValue && (System.currentTimeMillis() / 400) % 2 == 0L
        val layout = computeLayout(entries, graphics.guiWidth(), graphics.guiHeight())
        val entryW = entryWidth(entries)

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(layout.x.toFloat(), layout.y.toFloat())
        pose.scale(layout.scale, layout.scale)

        if (showBg) {
            graphics.fill(0, 0, layout.boxW, layout.boxH, URUSHI_BG)
            graphics.outline(0, 0, layout.boxW, layout.boxH, KIN_GOLD)
            graphics.fill(1, 1, layout.boxW - 1, 3, SHU_RED)
        }

        var cx = PAD
        var cy = PAD + 2
        for (e in entries) {
            graphics.item(e.stack, cx, cy)
            val low = e.fraction != null && e.percent <= warn
            val textX = cx + ICON + GAP
            if (e.text.isNotEmpty() && !(low && blinkOff)) {
                graphics.text(font, e.text, textX, cy + if (showBar && e.fraction != null) 1 else 4, if (low) SHU_RED else WASHI_TEXT, true)
            }
            if (showBar && e.fraction != null) {
                val barW = entryW - ICON - GAP
                val barY = cy + 12
                graphics.fill(textX, barY, textX + barW, barY + 2, BAR_BG)
                val color = when {
                    low               -> SHU_RED
                    e.fraction > 0.5f -> MATCHA
                    else              -> YAMABUKI
                }
                graphics.fill(textX, barY, textX + (barW * e.fraction).toInt().coerceAtLeast(1), barY + 2, color)
            }
            if (horizontal) cx += entryW + STEP + 2 else cy += ICON + STEP
        }

        pose.popMatrix()
        return layout
    }
}
