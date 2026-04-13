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
import org.anti_ad.mc.alias.registry.Registry
/*
import org.anti_ad.mc.alias.registry.RegistryKey
*/
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
import org.anti_ad.mc.ipnext.item.EMPTY
import org.anti_ad.mc.ipnext.item.ItemStack
import org.anti_ad.mc.ipnext.item.ItemType
import org.anti_ad.mc.ipnext.item.MutableItemStack
import org.anti_ad.mc.ipnext.item.empty
import org.anti_ad.mc.ipnext.item.vanillaStack
import org.anti_ad.mc.ipnext.mixin.IMixinContainerScreen
import org.anti_ad.mc.ipnext.mixin.IMixinSlot
import org.anti_ad.mc.ipnext.specific.asString
import kotlin.jvm.optionals.getOrNull
import org.anti_ad.mc.alias.item.ItemStack as VanillaItemStack

// ============
// vanillamapping code depends on mappings
// ============

// use `()` to avoid potential mapping name collision

inline val VanillaItemStack.`(itemType)`: ItemType
    get() = ItemType(item,
                     components as MergedComponentMap,
                     componentsPatch,
                     { this.isDamageableItem })
inline val VanillaItemStack.`(itemStack)`: ItemStack
    get() = if (isEmpty) ItemStack.EMPTY else ItemStack(`(itemType)`,
                                                        count)
inline val VanillaItemStack.`(mutableItemStack)`: MutableItemStack
    get() = if (isEmpty) MutableItemStack.empty() else MutableItemStack(`(itemType)`,
                                                                        count)

inline val Container.`(slots)`: List<Slot>
    get() = slots

inline val Container.`(syncId)`: Int
    get() = containerId

inline val Slot.`(id)`: Int
    get() = index
inline val Slot.`(invSlot)`: Int
    get() = (this as IMixinSlot).invSlot
inline val Slot.`(itemStack)`: ItemStack
    get() = item.`(itemStack)`
inline val Slot.`(vanillaStack)`: VanillaItemStack
    get() = this.item

inline val Slot.`(mutableItemStack)`: MutableItemStack
    get() = item.`(mutableItemStack)`
inline val Slot.`(inventory)`: Inventory
    get() = container
inline val Slot.`(inventoryOrNull)`: Inventory?
    get() = container
inline val Slot.`(left)`: Int
    get() = x
inline val Slot.`(top)`: Int
    get() = y
inline val Slot.`(topLeft)`: Point
    get() = Point(`(left)`,
                  `(top)`)

fun Slot.`(canInsert)`(itemStack: ItemStack): Boolean {
    return mayPlace(itemStack.vanillaStack)
}

inline val Screen.`(focusedSlot)`: Slot?
    get() = (this as? ContainerScreen<*>)?.`(rawFocusedSlot)`?.let {
        vPlayerSlotOf(it,
                      this)
    }

inline val ContainerScreen<*>.`(rawFocusedSlot)`: Slot?
    get() = (this as IMixinContainerScreen).focusedSlot

val ContainerScreen<*>.`(containerBounds)`: Rectangle
    get() = (this as IMixinContainerScreen).run {
        Rectangle(containerX,
                  containerY,
                  containerWidth,
                  containerHeight)
    }

inline val ContainerScreen<*>.`(container)`: Container
    get() = menu

inline var PlayerInventory.`(selectedSlot)`: Int
    get() = selectedSlot
    set(value) {
        selectedSlot = value
    }

// getSelectedTab() = method_2469()
inline val CreativeInventoryScreen.`(isInventoryTab)`: Boolean // method_2469() == ItemGroup.INVENTORY.getIndex()
    get() = this.isInventoryOpen

val ItemEnchantmentsComponent.`(enchantments)`: Set<RegistryEntry<Enchantment>>
    get() = this.keySet()

// ============
// Registry
// ============


fun <T: Any> DefaultedRegistry<T>.`(getIdentifier)`(value: T): Identifier {
    return getKey(value)
}

fun <T: Any> DefaultedRegistry<T>.`(getRawId)`(value: T): Int {
    return getId(value)
}

fun <T: Any> DefaultedRegistry<T>.`(getByIdentifier)`(id: Identifier): T {
    return getValue(id)
}

/*
fun <T> Registry<T>.`(getIdentifier)`(value: T): Identifier? {
    return getKey(value)
}

fun <T> Registry<T>.`(getRawId)`(value: T): Int {
    return getRawId(value)
}

fun <T> Registry<T>.`(getByIdentifier)`(id: Identifier): T? {
    return get(id)
}
*/

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
inline val NbtElement.`(type)`: Int
    get() = id.toInt()
inline val NbtElement.`(asString)`: String
    get() = this.asString

inline val NbtCompound.`(keys)`: Set<String>
    get() = keySet()

inline val MinecraftClient.`(window)`: Window
    get() = window

inline val MinecraftClient.`(options)`: GameOptions
    get() = options

inline val GameOptions.`(keyDrop)`: KeyBinding
    get() = keyDrop

inline val KeyBinding.`(isPressed)`: Boolean
    get() = isDown

inline val Window.`(scaledWidth)`: Int
    get() = guiScaledWidth

inline val Window.`(scaledHeight)`: Int
    get() = guiScaledHeight


@Suppress("NOTHING_TO_INLINE")
inline fun ClientPlayerInteractionManager.`(clickSlot)`(i: Int, j: Int, k: Int, slotActionType: SlotActionType, playerEntity: PlayerEntity) =
        this.handleContainerInput(i, j, k, slotActionType, playerEntity)

@Suppress("NOTHING_TO_INLINE")
inline fun PlayerContainer.`(onSlotClick)`(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) =
        this.clicked(slotIndex, button, actionType, player)

fun PlayerContainer.`(sendContentUpdates)`() = broadcastChanges()

val StonecutterContainer.`(selectedRecipe)`: Int
    get() = selectedRecipeIndex

fun MinecraftClient.`(send)`(runnable: Runnable) = schedule(runnable)
