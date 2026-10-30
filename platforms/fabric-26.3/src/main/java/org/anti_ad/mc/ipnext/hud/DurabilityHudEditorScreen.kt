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
import org.anti_ad.mc.ipnext.config.ConfigScreenSettings
import org.anti_ad.mc.ipnext.config.DurabilityHudSettings
import kotlin.math.roundToInt

/** Drag the durability HUD with the mouse; resize it with the wheel or the gold corner handle. */
class DurabilityHudEditorScreen(private val parent: Screen?) :
        Screen(Component.translatable("inventoryprofiles.gui.durability_hud_editor.title")) {

    private enum class Mode { NONE, MOVE, RESIZE }

    private var mode = Mode.NONE
    private var grabDx = 0.0
    private var grabDy = 0.0
    private var last: DurabilityHud.Layout? = null

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0x40000000)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        val layout = DurabilityHud.renderForEditor(graphics)
        last = layout
        val hover = inside(layout, mouseX.toDouble(), mouseY.toDouble())
        val frame = if (mode != Mode.NONE || hover) DurabilityHud.SHU_RED else DurabilityHud.KIN_GOLD
        graphics.outline(layout.x - 2, layout.y - 2, layout.width + 4, layout.height + 4, frame)
        val hx = layout.x + layout.width
        val hy = layout.y + layout.height
        graphics.fill(hx - 3, hy - 3, hx + 3, hy + 3, DurabilityHud.KIN_GOLD)

        val help = Component.translatable("inventoryprofiles.gui.durability_hud_editor.help")
        graphics.centeredText(font, title, width / 2, 8, DurabilityHud.KIN_GOLD)
        graphics.centeredText(font, help, width / 2, 20, DurabilityHud.WASHI_TEXT)
        graphics.centeredText(font, Component.literal("${DurabilityHudSettings.DURABILITY_HUD_SCALE.integerValue}%"), width / 2, 32, DurabilityHud.WASHI_TEXT)
    }

    private fun inside(l: DurabilityHud.Layout, x: Double, y: Double): Boolean =
            x >= l.x && x <= l.x + l.width && y >= l.y && y <= l.y + l.height

    private fun onHandle(l: DurabilityHud.Layout, x: Double, y: Double): Boolean =
            x >= l.x + l.width - 5 && x <= l.x + l.width + 5 && y >= l.y + l.height - 5 && y <= l.y + l.height + 5

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val l = DurabilityHud.layout(width, height)
        if (event.button() == 0) {
            if (onHandle(l, event.x(), event.y())) {
                mode = Mode.RESIZE
                return true
            } else if (inside(l, event.x(), event.y())) {
                mode = Mode.MOVE
                grabDx = event.x() - l.x
                grabDy = event.y() - l.y
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val l = DurabilityHud.layout(width, height)
        when (mode) {
            Mode.MOVE   -> {
                val nx = (event.x() - grabDx).roundToInt().coerceIn(0, maxOf(0, width - l.width))
                val ny = (event.y() - grabDy).roundToInt().coerceIn(0, maxOf(0, height - l.height))
                moveTo(nx, ny, l.width, l.height)
                return true
            }
            Mode.RESIZE -> {
                val wanted = ((event.x() - l.x) / l.boxW * 100).roundToInt()
                resizeKeepingTopLeft(l, wanted)
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
        val l = DurabilityHud.layout(width, height)
        if (vertical != 0.0) {
            val step = if (vertical > 0) 10 else -10
            resizeKeepingTopLeft(l, DurabilityHudSettings.DURABILITY_HUD_SCALE.integerValue + step)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 82) { // R: reset size and position
            DurabilityHudSettings.DURABILITY_HUD_SCALE.value = 100
            DurabilityHudSettings.DURABILITY_HUD_OFFSET_X.value = 4
            DurabilityHudSettings.DURABILITY_HUD_OFFSET_Y.value = 4
            return true
        }
        return super.keyPressed(event)
    }

    private fun resizeKeepingTopLeft(l: DurabilityHud.Layout, wantedScale: Int) {
        val scale = (wantedScale / 5 * 5).coerceIn(DurabilityHud.MIN_SCALE, DurabilityHud.MAX_SCALE)
        DurabilityHudSettings.DURABILITY_HUD_SCALE.value = scale
        val newW = (l.boxW * scale / 100f).toInt()
        val newH = (l.boxH * scale / 100f).toInt()
        moveTo(l.x.coerceIn(0, maxOf(0, width - newW)), l.y.coerceIn(0, maxOf(0, height - newH)), newW, newH)
    }

    private fun moveTo(x: Int, y: Int, w: Int, h: Int) {
        val (ox, oy) = DurabilityHud.offsetsFor(x, y, w, h, width, height)
        DurabilityHudSettings.DURABILITY_HUD_OFFSET_X.value = ox.coerceIn(-2000, 2000)
        DurabilityHudSettings.DURABILITY_HUD_OFFSET_Y.value = oy.coerceIn(-2000, 2000)
    }

    override fun onClose() {
        ConfigScreenSettings.saveLoadManager.save()
        if (parent != null) Minecraft.getInstance().setScreenAndShow(parent) else super.onClose()
    }

    companion object {
        fun open() {
            val mc = Minecraft.getInstance()
            mc.setScreenAndShow(DurabilityHudEditorScreen(org.anti_ad.mc.common.vanilla.Vanilla.screen()))
        }
    }
}
