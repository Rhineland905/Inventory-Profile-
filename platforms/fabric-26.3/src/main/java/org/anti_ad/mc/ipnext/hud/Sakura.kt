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

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.anti_ad.mc.ipnext.config.ExtraHudSettings
import kotlin.math.sin

/** Pixel-art cherry blossoms and drifting petals. Decoration only. */
object Sakura {
    private const val LIGHT = 0xFFFFC7D9.toInt()
    private const val PINK = 0xFFF49AB8.toInt()
    private const val DEEP = 0xFFD9577F.toInt()
    private const val CORE = 0xFFFFE27A.toInt()
    private const val BRANCH = 0xFF5A3A2A.toInt()

    // 9x9 five-petal blossom: L light, P pink, D deep, Y stamen
    private val BLOSSOM = listOf(
            "...LLL...",
            "..LPPPL..",
            "LL.PDP.LL",
            "LPPDYDPPL",
            ".LPYYYPL.",
            "..PDYDP..",
            ".LPP.PPL.",
            ".LP...PL.",
            ".........")

    private val BUD = listOf(
            ".L.",
            "LPL",
            ".D.")

    private fun pattern(g: GuiGraphicsExtractor, rows: List<String>, x: Int, y: Int) {
        rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                val color = when (c) {
                    'L'  -> LIGHT
                    'P'  -> PINK
                    'D'  -> DEEP
                    'Y'  -> CORE
                    else -> 0
                }
                if (color != 0) g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, color)
            }
        }
    }

    fun flowersOn(): Boolean = ExtraHudSettings.SAKURA_FLOWERS.booleanValue

    private fun big(g: GuiGraphicsExtractor, x: Int, y: Int, scale: Float) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        pattern(g, BLOSSOM, 0, 0)
        pose.popMatrix()
    }

    /** A branch with blossoms over the top-right corner and a flower on the bottom-left corner of a panel. */
    fun cornerSprig(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        if (!flowersOn()) return
        val r = x + w
        g.fill(r - 30, y + 1, r - 6, y + 2, BRANCH)
        g.fill(r - 20, y + 2, r - 12, y + 3, BRANCH)
        pattern(g, BUD, r - 24, y + 2)
        pattern(g, BUD, r - 33, y)
        pattern(g, BLOSSOM, r - 21, y - 2)
        big(g, r - 11, y - 3, 1.6f)
        pattern(g, BLOSSOM, x - 4, y + h - 5)
    }

    /** Blossoms on the corners of the container frame and a branch along its top edge. */
    fun frame(g: GuiGraphicsExtractor, l: Int, t: Int, r: Int, b: Int) {
        if (!flowersOn()) return
        val bx = l + 10
        g.fill(bx, t - 6, bx + 46, t - 5, BRANCH)
        g.fill(bx + 20, t - 8, bx + 30, t - 7, BRANCH)
        pattern(g, BUD, bx + 14, t - 9)
        big(g, bx + 26, t - 16, 1.8f)
        pattern(g, BLOSSOM, bx + 52, t - 12)
        g.fill(bx + 46, t - 6, bx + 60, t - 5, BRANCH)
        pattern(g, BUD, bx + 42, t - 8)
        big(g, l - 7, t - 7, 1.6f)
        big(g, r - 7, t - 7, 1.6f)
        pattern(g, BLOSSOM, l - 5, b - 4)
        pattern(g, BLOSSOM, r - 4, b - 4)
    }

    /** Petals drifting down the screen, skipping the given rectangle (the open container). */
    fun petals(g: GuiGraphicsExtractor, skipL: Int, skipT: Int, skipR: Int, skipB: Int) {
        if (!ExtraHudSettings.SAKURA_PETALS.booleanValue) return
        val w = g.guiWidth()
        val h = g.guiHeight()
        val t = System.currentTimeMillis() / 1000.0
        for (i in 0 until 40) {
            val seed = (i * 7919 + 13) % 1000 / 1000.0
            val speed = 14 + (i * 37 % 19)
            val baseX = ((i * 0.618034) % 1.0) * w
            val y = ((t * speed + seed * (h + 20)) % (h + 20)) - 10
            val x = baseX + sin(t * 0.8 + i) * 18 + (t * 6) % 40 - 20
            val px = x.toInt()
            val py = y.toInt()
            if (px + 3 >= skipL && px <= skipR && py + 3 >= skipT && py <= skipB) continue
            val tilt = ((t * 2 + i) % 2.0) < 1.0
            if (tilt) {
                g.fill(px, py, px + 2, py + 1, LIGHT)
                g.fill(px + 1, py + 1, px + 3, py + 2, PINK)
            } else {
                g.fill(px + 1, py, px + 3, py + 1, LIGHT)
                g.fill(px, py + 1, px + 2, py + 2, PINK)
            }
        }
    }
}
