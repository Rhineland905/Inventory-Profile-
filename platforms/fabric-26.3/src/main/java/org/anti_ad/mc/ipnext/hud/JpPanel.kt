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
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.anti_ad.mc.ipnext.config.DurabilityHudCorner

/** Japanese traditional colors shared by every panel. */
object Jp {
    const val URUSHI_BG = 0xD0120C0A.toInt()   // black lacquer
    const val KIN_GOLD = 0xFFC9A646.toInt()     // gold leaf
    const val SHU_RED = 0xFFC0272D.toInt()      // vermilion (torii)
    const val WASHI_TEXT = 0xFFF3E9D2.toInt()   // washi paper
    const val MATCHA = 0xFF7BA23F.toInt()       // matcha green
    const val YAMABUKI = 0xFFF8B500.toInt()     // yamabuki yellow
    const val BAR_BG = 0xFF2B211D.toInt()

    fun blinkOff(): Boolean = (System.currentTimeMillis() / 400) % 2 == 0L
}

/** One line of a panel: an item or sprite icon, a text, and an optional durability bar. */
class Row(val stack: ItemStack? = null,
          val sprite: Identifier? = null,
          val text: String = "",
          val color: Int = Jp.WASHI_TEXT,
          val fraction: Float? = null,
          val barColor: Int = Jp.MATCHA,
          val textHidden: Boolean = false) {
    val hasIcon: Boolean get() = stack != null || sprite != null
}

/** Where a panel sits and how big it is; read from and written to the config. */
class Placement(val corner: () -> DurabilityHudCorner,
                val getX: () -> Int, val setX: (Int) -> Unit,
                val getY: () -> Int, val setY: (Int) -> Unit,
                val getScale: () -> Int, val setScale: (Int) -> Unit,
                val horizontal: () -> Boolean = { false },
                val background: () -> Boolean = { true },
                val bars: () -> Boolean = { true })

class Layout(val x: Int, val y: Int, val width: Int, val height: Int, val boxW: Int, val boxH: Int, val scale: Float)

abstract class HudPanel(val placement: Placement, val titleKey: String) {
    abstract fun enabled(): Boolean
    abstract fun rows(): List<Row>
    abstract fun demoRows(): List<Row>
    fun rowsForEditor(): List<Row> = rows().ifEmpty { demoRows() }
}

object JpPanel {
    private const val ICON = 16
    private const val GAP = 3
    private const val PAD = 4
    private const val STEP = 2
    const val MIN_SCALE = 50
    const val MAX_SCALE = 300

    private fun font() = Minecraft.getInstance().font

    private fun entryWidth(panel: HudPanel, rows: List<Row>): Int {
        val icon = if (rows.any { it.hasIcon }) ICON else 0
        val textWidth = rows.maxOf { font().width(it.text) }
        val bars = panel.placement.bars() && rows.any { it.fraction != null }
        val textPart = if (textWidth > 0 || bars) maxOf(textWidth, if (bars) 24 else 0) else 0
        return icon + if (icon > 0 && textPart > 0) GAP + textPart else textPart
    }

    private fun rowHeight(rows: List<Row>): Int = if (rows.any { it.hasIcon }) ICON else 10

    fun layout(panel: HudPanel, rows: List<Row>, sw: Int, sh: Int): Layout {
        val p = panel.placement
        val horizontal = p.horizontal()
        val entryW = entryWidth(panel, rows)
        val rowH = rowHeight(rows)
        val contentW = if (horizontal) rows.size * entryW + (rows.size - 1) * (STEP + 2) else entryW
        val contentH = if (horizontal) rowH else rows.size * rowH + (rows.size - 1) * STEP
        val boxW = contentW + PAD * 2
        val boxH = contentH + PAD * 2 + 2 // +2 for the vermilion stripe
        val scale = p.getScale().coerceIn(MIN_SCALE, MAX_SCALE) / 100f
        val realW = (boxW * scale).toInt()
        val realH = (boxH * scale).toInt()
        val ox = p.getX()
        val oy = p.getY()
        val (x, y) = when (p.corner()) {
            DurabilityHudCorner.TOP_LEFT       -> ox to oy
            DurabilityHudCorner.TOP_RIGHT      -> (sw - realW - ox) to oy
            DurabilityHudCorner.BOTTOM_LEFT    -> ox to (sh - realH - oy)
            DurabilityHudCorner.NEXT_TO_HOTBAR -> (sw / 2 + 91 + 6 + ox) to (sh - realH - oy)
            else                               -> (sw - realW - ox) to (sh - realH - oy)
        }
        return Layout(x, y, realW, realH, boxW, boxH, scale)
    }

    /** Turns a wanted top-left position into offsets for the panel's corner. */
    fun offsetsFor(panel: HudPanel, x: Int, y: Int, width: Int, height: Int, sw: Int, sh: Int): Pair<Int, Int> {
        return when (panel.placement.corner()) {
            DurabilityHudCorner.TOP_LEFT       -> x to y
            DurabilityHudCorner.TOP_RIGHT      -> (sw - width - x) to y
            DurabilityHudCorner.BOTTOM_LEFT    -> x to (sh - height - y)
            DurabilityHudCorner.NEXT_TO_HOTBAR -> (x - (sw / 2 + 91 + 6)) to (sh - height - y)
            else                               -> (sw - width - x) to (sh - height - y)
        }
    }

    fun draw(graphics: GuiGraphicsExtractor, panel: HudPanel, rows: List<Row>): Layout {
        val p = panel.placement
        val font = font()
        val horizontal = p.horizontal()
        val showBar = p.bars()
        val layout = layout(panel, rows, graphics.guiWidth(), graphics.guiHeight())
        val entryW = entryWidth(panel, rows)
        val rowH = rowHeight(rows)
        val anyIcon = rows.any { it.hasIcon }

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(layout.x.toFloat(), layout.y.toFloat())
        pose.scale(layout.scale, layout.scale)

        if (p.background()) {
            graphics.fill(0, 0, layout.boxW, layout.boxH, Jp.URUSHI_BG)
            graphics.outline(0, 0, layout.boxW, layout.boxH, Jp.KIN_GOLD)
            graphics.fill(1, 1, layout.boxW - 1, 3, Jp.SHU_RED)
            Sakura.cornerSprig(graphics, 0, 0, layout.boxW)
        }

        var cx = PAD
        var cy = PAD + 2
        for (r in rows) {
            if (r.stack != null) {
                graphics.item(r.stack, cx, cy)
            } else if (r.sprite != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, r.sprite, cx, cy, ICON, ICON)
            }
            val textX = cx + if (anyIcon) ICON + GAP else 0
            val bar = showBar && r.fraction != null
            if (r.text.isNotEmpty() && !r.textHidden) {
                val ty = cy + if (bar) 1 else (rowH - 8) / 2
                graphics.text(font, r.text, textX, ty, r.color, true)
            }
            if (bar) {
                val barW = entryW - (textX - cx)
                val barY = cy + 12
                graphics.fill(textX, barY, textX + barW, barY + 2, Jp.BAR_BG)
                graphics.fill(textX, barY, textX + (barW * r.fraction!!).toInt().coerceAtLeast(1), barY + 2, r.barColor)
            }
            if (horizontal) cx += entryW + STEP + 2 else cy += rowH + STEP
        }

        pose.popMatrix()
        return layout
    }
}
