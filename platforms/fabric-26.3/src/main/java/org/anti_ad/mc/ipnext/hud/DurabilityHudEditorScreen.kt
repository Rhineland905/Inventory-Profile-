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
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.anti_ad.mc.common.vanilla.Vanilla
import org.anti_ad.mc.ipnext.config.ConfigScreenSettings
import kotlin.math.roundToInt

/** Drag any info panel with the mouse; resize it with the wheel or its gold corner handle. */
class DurabilityHudEditorScreen(private val parent: Screen?) :
        Screen(Component.translatable("inventoryprofiles.gui.durability_hud_editor.title")) {

    private enum class Mode { NONE, MOVE, RESIZE }

    private var mode = Mode.NONE
    private var grabDx = 0.0
    private var grabDy = 0.0
    private var selected: HudPanel? = null

    private fun panels() = DurabilityHud.panels.filter { it.enabled() }

    private fun layoutOf(panel: HudPanel): Layout = JpPanel.layout(panel, panel.rowsForEditor(), width, height)

    private fun panelAt(x: Double, y: Double): HudPanel? =
            panels().lastOrNull { val l = layoutOf(it); inside(l, x, y) || onHandle(l, x, y) }

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x40000000)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val hovered = panelAt(mouseX.toDouble(), mouseY.toDouble())
        for (panel in panels()) {
            val l = JpPanel.draw(graphics, panel, panel.rowsForEditor())
            val active = panel == hovered || panel == selected
            graphics.outline(l.x - 2, l.y - 2, l.width + 4, l.height + 4, if (active) Jp.SHU_RED else Jp.KIN_GOLD)
            val hx = l.x + l.width
            val hy = l.y + l.height
            graphics.fill(hx - 3, hy - 3, hx + 3, hy + 3, Jp.KIN_GOLD)
            if (active) {
                val name = Component.translatable(panel.titleKey).string + " ${panel.placement.getScale()}%"
                val ty = if (l.y > 12) l.y - 12 else l.y + l.height + 4
                graphics.text(font, name, l.x, ty, Jp.YAMABUKI, true)
            }
        }
        val help = Component.translatable("inventoryprofiles.gui.durability_hud_editor.help")
        graphics.centeredText(font, title, width / 2, height / 2 - 16, Jp.KIN_GOLD)
        graphics.centeredText(font, help, width / 2, height / 2 - 4, Jp.WASHI_TEXT)
    }

    private fun inside(l: Layout, x: Double, y: Double): Boolean =
            x >= l.x && x <= l.x + l.width && y >= l.y && y <= l.y + l.height

    private fun onHandle(l: Layout, x: Double, y: Double): Boolean =
            x >= l.x + l.width - 5 && x <= l.x + l.width + 5 && y >= l.y + l.height - 5 && y <= l.y + l.height + 5

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val panel = panelAt(event.x(), event.y())
            if (panel != null) {
                selected = panel
                val l = layoutOf(panel)
                if (onHandle(l, event.x(), event.y())) {
                    mode = Mode.RESIZE
                } else {
                    mode = Mode.MOVE
                    grabDx = event.x() - l.x
                    grabDy = event.y() - l.y
                }
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val panel = selected ?: return super.mouseDragged(event, dx, dy)
        val l = layoutOf(panel)
        when (mode) {
            Mode.MOVE   -> {
                val nx = (event.x() - grabDx).roundToInt().coerceIn(0, maxOf(0, width - l.width))
                val ny = (event.y() - grabDy).roundToInt().coerceIn(0, maxOf(0, height - l.height))
                moveTo(panel, nx, ny, l.width, l.height)
                return true
            }
            Mode.RESIZE -> {
                resizeKeepingTopLeft(panel, l, ((event.x() - l.x) / l.boxW * 100).roundToInt())
                return true
            }
            Mode.NONE   -> return super.mouseDragged(event, dx, dy)
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (mode != Mode.NONE) {
            mode = Mode.NONE
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val panel = panelAt(mouseX, mouseY) ?: selected
        if (vertical != 0.0 && panel != null) {
            selected = panel
            val step = if (vertical > 0) 10 else -10
            resizeKeepingTopLeft(panel, layoutOf(panel), panel.placement.getScale() + step)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 82) { // R: reset size and position of the selected panel
            val panels = selected?.let { listOf(it) } ?: panels()
            for (p in panels) {
                p.placement.setScale(100)
                p.placement.setX(4)
                p.placement.setY(4)
            }
            return true
        }
        return super.keyPressed(event)
    }

    private fun resizeKeepingTopLeft(panel: HudPanel, l: Layout, wantedScale: Int) {
        val scale = (wantedScale / 5 * 5).coerceIn(JpPanel.MIN_SCALE, JpPanel.MAX_SCALE)
        panel.placement.setScale(scale)
        val newW = (l.boxW * scale / 100f).toInt()
        val newH = (l.boxH * scale / 100f).toInt()
        moveTo(panel, l.x.coerceIn(0, maxOf(0, width - newW)), l.y.coerceIn(0, maxOf(0, height - newH)), newW, newH)
    }

    private fun moveTo(panel: HudPanel, x: Int, y: Int, w: Int, h: Int) {
        val (ox, oy) = JpPanel.offsetsFor(panel, x, y, w, h, width, height)
        panel.placement.setX(ox.coerceIn(-2000, 2000))
        panel.placement.setY(oy.coerceIn(-2000, 2000))
    }

    override fun onClose() {
        ConfigScreenSettings.saveLoadManager.save()
        if (parent != null) Minecraft.getInstance().setScreenAndShow(parent) else super.onClose()
    }

    companion object {
        fun open() {
            Minecraft.getInstance().setScreenAndShow(DurabilityHudEditorScreen(Vanilla.screen()))
        }
    }
}
