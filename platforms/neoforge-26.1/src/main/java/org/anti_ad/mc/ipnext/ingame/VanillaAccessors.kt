/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2019-2020 jsnimda <7615255+jsnimda@users.noreply.github.com>
 *   Copyright (c) 2021-2022 Plamen K. Kosseff <p.kosseff@gmail.com>
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

@file:Suppress("ObjectPropertyName", "FunctionName")

package org.anti_ad.mc.ipnext.ingame

import net.minecraft.resources.ResourceKey
import org.anti_ad.mc.alias.client.GameOptions
import org.anti_ad.mc.alias.client.KeyBinding
import org.anti_ad.mc.alias.client.MinecraftClient
import org.anti_ad.mc.alias.client.Window
import org.anti_ad.mc.alias.client.gui.screen.Screen
import org.anti_ad.mc.alias.client.gui.screen.ingame.ContainerScreen
import org.anti_ad.mc.alias.client.gui.screen.ingame.CreativeInventoryScreen
import org.anti_ad.mc.alias.client.gui.screen.recipebook.`(searchField)`
import org.anti_ad.mc.alias.client.gui.screen.recipebook.RecipeBookWidget
import org.anti_ad.mc.alias.client.gui.widget.TextFieldWidget
import org.anti_ad.mc.alias.client.network.ClientPlayerInteractionManager
import org.anti_ad.mc.alias.component.MergedComponentMap
import org.anti_ad.mc.alias.component.type.ItemEnchantmentsComponent
import org.anti_ad.mc.alias.enchantment.Enchantment
import org.anti_ad.mc.alias.entity.player.PlayerEntity
import org.anti_ad.mc.alias.inventory.Inventory
import org.anti_ad.mc.alias.inventory.PlayerInventory
import org.anti_ad.mc.alias.nbt.NbtCompound
import org.anti_ad.mc.alias.nbt.NbtElement
import org.anti_ad.mc.alias.registry.DefaultedRegistry
import org.anti_ad.mc.alias.registry.entry.RegistryEntry
import org.anti_ad.mc.alias.registry.tag.TagKey
import org.anti_ad.mc.alias.screen.Container
import org.anti_ad.mc.alias.screen.PlayerContainer
import org.anti_ad.mc.alias.screen.StonecutterContainer
import org.anti_ad.mc.alias.screen.slot.Slot
import org.anti_ad.mc.alias.screen.slot.SlotActionType
import org.anti_ad.mc.alias.util.Identifier
import org.anti_ad.mc.common.math2d.Point
import org.anti_ad.mc.common.math2d.Rectangle
import org.anti_ad.mc.common.vanilla.Vanilla
import org.anti_ad.mc.ipnext.item.EMPTY
import org.anti_ad.mc.ipnext.item.ItemStack
import org.anti_ad.mc.ipnext.item.ItemType
import org.anti_ad.mc.ipnext.item.MutableItemStack
import org.anti_ad.mc.ipnext.item.empty
import org.anti_ad.mc.ipnext.item.vanillaStack
import org.anti_ad.mc.ipnext.mixin.IMixinSlot
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull
import org.anti_ad.mc.alias.item.ItemStack as VanillaItemStack

// ============
// vanillamapping code depends on mappings
// ============

// use `()` to avoid potential mapping name collision

val VanillaItemStack.`(itemType)`: ItemType
    get() = ItemType(item,
                     components as MergedComponentMap,
                     componentsPatch,
                     { this.isDamageableItem })
val VanillaItemStack.`(itemStack)`: ItemStack
    get() = if (isEmpty) ItemStack.EMPTY else ItemStack(`(itemType)`,
                                                        count)
val VanillaItemStack.`(mutableItemStack)`: MutableItemStack
    get() = if (isEmpty) MutableItemStack.empty() else MutableItemStack(`(itemType)`,
                                                                        count)

val Container.`(slots)`: List<Slot>
    get() = slots

val Slot.`(id)`: Int
    get() = index
val Slot.`(invSlot)`: Int
    get() = (this as IMixinSlot).invSlot
val Slot.`(itemStack)`: ItemStack
    get() = item.`(itemStack)`
val Slot.`(vanillaStack)`: VanillaItemStack
    get() = this.item

val Slot.`(mutableItemStack)`: MutableItemStack
    get() = item.`(mutableItemStack)`
val Slot.`(inventory)`: Inventory
    get() = container
val Slot.`(inventoryOrNull)`: Inventory?
    get() = container
val Slot.`(left)`: Int
    get() = x
val Slot.`(top)`: Int
    get() = y
val Slot.`(topLeft)`: Point
    get() = Point(`(left)`,
                  `(top)`)

fun Slot.`(canInsert)`(itemStack: ItemStack): Boolean {
    return mayPlace(itemStack.vanillaStack)
}

val Screen.`(focusedSlot)`: Slot?
    get() = (this as? ContainerScreen<*>)?.`(rawFocusedSlot)`?.let {
        vPlayerSlotOf(it,
                      this)
    }

val ContainerScreen<*>.`(rawFocusedSlot)`: Slot?
    get() = hoveredSlot
val ContainerScreen<*>.`(containerBounds)`: Rectangle
    get() = Rectangle(leftPos,
                      topPos,
                      imageWidth,
                      imageHeight)

val ContainerScreen<*>.`(container)`: Container
    get() = menu

var PlayerInventory.`(selectedSlot)`: Int
    get() = selectedSlot
    set(value) {
        selectedSlot = value
    }

val CreativeInventoryScreen.`(isInventoryTab)`: Boolean // method_2469() == ItemGroup.INVENTORY.getIndex()
    get() = this.isInventoryOpen

val ItemEnchantmentsComponent.`(enchantments)`: Set<RegistryEntry<Enchantment>>
    get() = this.keySet()

// ============
// Registry
// ============

fun <T: Any> DefaultedRegistry<T>.`(getRawId)`(value: T): Int {
    return getId(value)
}

fun <T: Any> DefaultedRegistry<T>.`(getByIdentifier)`(id: Identifier): T {
    return getValue(id)
}

fun <T: Any> RegistryEntry<T>.`(is)`(id: Identifier): Boolean = this.`is`(id)
fun <T: Any> RegistryEntry<T>.`(is)`(id: TagKey<T>): Boolean = this.`is`(id)
fun <T: Any> RegistryEntry<T>.`(is)`(id: ResourceKey<T>): Boolean = this.`is`(id)
val <T: Any> RegistryEntry<T>.`(key)`: ResourceKey<T>?
    get() = this.unwrapKey().getOrNull()
val <T: Any> RegistryEntry<T>.`(value)`: T?
    get() = this.value()
val ResourceKey<*>.`(registry)`: Identifier
    get() = this.registry()
val ResourceKey<*>.`(identigier)`: Identifier
    get() = this.identifier()

// ============
// nbt Tag
// ============

val NbtElement.asString: String
    get() = this.asString().getOrElse { this.toString() }


val NbtElement.`(type)`: Int
    get() = id.toInt()
val NbtElement.`(asString)`: String
    get() = this.asString

val NbtCompound.`(keys)`: Set<String>
    get() = keySet()

val MinecraftClient.`(window)`: Window
    get() = window

val MinecraftClient.`(options)`: GameOptions
    get() = options

val GameOptions.`(keyDrop)`: KeyBinding
    get() = keyDrop

val KeyBinding.`(isPressed)`: Boolean
    get() = isDown

val Window.`(scaledWidth)`: Int
    get() = guiScaledWidth

val Window.`(scaledHeight)`: Int
    get() = guiScaledHeight

fun ClientPlayerInteractionManager.`(clickSlot)`(i: Int, j: Int, k: Int, slotActionType: SlotActionType, playerEntity: PlayerEntity) =
        this.handleContainerInput(i, j, k, slotActionType, playerEntity)

fun PlayerContainer.`(onSlotClick)`(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) =
        this.clicked(slotIndex, button, actionType, player)

fun PlayerContainer.`(sendContentUpdates)`() = broadcastChanges()

val StonecutterContainer.`(selectedRecipe)`: Int
    get() = selectedRecipeIndex

fun MinecraftClient.`(send)`(runnable: Runnable) = schedule(runnable)

// interpreted for creative inventory
// in-game safe
fun vFocusedSlot(): Slot? = Vanilla.screen()?.`(focusedSlot)`

fun vMainhandIndex() =
        Vanilla.playerInventory().`(selectedSlot)`

fun isInputFieldActive(scr: ContainerScreen<*>): Boolean {
    return scr.children()?.any {
        (it is RecipeBookWidget<*> && (it.`(searchField)`?.isFocused == true))
        || (it is TextFieldWidget && it.isFocused)
    } == true
}

fun vCursorStack() = Vanilla.playerInventory().player.containerMenu.carried?.`(itemStack)` ?: ItemStack.EMPTY
