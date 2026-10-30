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
import org.anti_ad.mc.common.vanilla.Vanilla

/** Draws every info panel after the hotbar. Panels only show information, they never act for the player. */
object DurabilityHud {

    val panels: List<HudPanel> = listOf(DurabilityPanel, ItemCounterPanel, EffectsPanel, ClockPanel)

    fun render(graphics: GuiGraphicsExtractor) {
        LowDurabilityAlert.check()
        if (Vanilla.screen() !is DurabilityHudEditorScreen) {
            for (panel in panels) {
                if (panel.enabled()) {
                    val rows = panel.rows()
                    if (rows.isNotEmpty()) JpPanel.draw(graphics, panel, rows)
                }
            }
        }
        LowDurabilityAlert.draw(graphics)
    }

    /** Layout of the durability panel (kept for the screenshot test). */
    fun layout(sw: Int, sh: Int): Layout = JpPanel.layout(DurabilityPanel, DurabilityPanel.rowsForEditor(), sw, sh)
}
