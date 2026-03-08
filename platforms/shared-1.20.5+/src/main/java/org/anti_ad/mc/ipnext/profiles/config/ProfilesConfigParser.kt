/*
 * Inventory Profiles Next
 *
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

package org.anti_ad.mc.ipnext.profiles.config


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.anti_ad.mc.alias.nbt.NbtElement
import org.anti_ad.mc.alias.util.Identifier
import org.anti_ad.mc.alias.util.IdentifierOf
import org.anti_ad.mc.common.annotation.MayThrow
import org.anti_ad.mc.common.extensions.tryOrPrint
import org.anti_ad.mc.ipnext.Log
import org.anti_ad.mc.ipnext.gen.ProfilesLexer
import org.anti_ad.mc.ipnext.gen.ProfilesParser
import org.anti_ad.mc.ipnext.ingame.`(asString)`
import org.anti_ad.mc.ipnext.item.NbtUtils
import org.anti_ad.mc.ipnext.parser.parseBy

object ProfilesConfig {

    @MayThrow // throw SyntaxErrorException
    fun parseRuleDefinition(text: String) {
        tryOrPrint(Log::warn) {
            val profiles = getProfiles(text)
            profiles.dump()
        }
    }

    @MayThrow // may throws
    fun getProfiles(content: String): List<ProfileData> =
            content.parseBy(::ProfilesLexer,
                            ::ProfilesParser,
                            lexerMode = ProfilesLexer.DEFAULT_MODE).script().toProfileList()

    fun asString(profiles: List<ProfileData>): String {
        var s = ""
        profiles.forEach{
             s += "${it}\n"
        }
        return s
    }
}

fun List<ProfileData>.dump() {
    Log.trace("Loaded fallowing profiles...")
    Log.clearIndent()
    Log.indent {
        forEach { profile ->
            Log.trace {
                if (profile.active != ProfileSlotId.NONE) {
                    "${profile.name} activate slot ${profile.active.name}"
                } else {
                    profile.name
                }
            }
            Log.indent {
                profile.slots.forEach { slotDef ->
                    Log.trace(slotDef.id.name)
                    Log.indent {
                        slotDef.items.forEach { item ->
                            val i = item.itemId + " -> " + (item.components ?: "")
                            Log.trace(i)
                        }
                    }
                }
            }
        }
    }
    Log.clearIndent()
}

val String.`(asIdentifier)`: Identifier
    get() = IdentifierOf(this)


private fun ProfilesParser.ScriptContext.toProfileList(): List<ProfileData> {
    return profile().map { profile ->
        ProfileData(
            profile.Id().text,
            ProfileSlotId.valueOfOrFAKE(profile.activeSlotName()?.text),
            profile.slotsDef().slotDef().map { slotDef ->
                ProfileSlot(
                    ProfileSlotId.valueOf(slotDef.slotname().text),
                    slotDef.orderRule()?.STRING()?.text?.unescape()?.removePrefix("@"),
                    slotDef.itemDef().map { it.toProfileItemData() })
            })
    }
}

private fun ProfilesParser.ItemDefContext.toProfileItemData(): ProfileItemData {
    return ProfileItemData(
        this.itemName().NamespacedId().text.unescape(),
        this.itemName().customName()?.STRING()?.text?.unescape() ?: "",
        this.components()?.component()?.map {
            val id = it.NamespacedId().toString().unescape().`(asIdentifier)`
            val nbtString = it.customName()?.STRING()?.text?.unescape() ?: ""
            ProfileComponentData(id, NbtUtils.parseNbtOrEmpty(nbtString))
        })
}

private fun String.unescape(): String {
    return (Json.parseToJsonElement(this) as? JsonPrimitive)
        ?.contentOrNull ?: this
}

private fun String.escape(): String {
    return Json.encodeToString(this)
}

fun String.fromEnchantmentLevel(): Int {
    return try {
        this.substring(IntRange(0, this.length - 2)).toInt()
    } catch (e: NumberFormatException) {
        try {
            this.toInt()
        } catch (e: NumberFormatException) {
            10
        }
    }
}

data class ProfileData(val name: String,
                       val active: ProfileSlotId,
                       val slots: List<ProfileSlot>,
                       val valid: Boolean = true) {

    override fun toString(): String {
        val start = if (active != ProfileSlotId.NONE) {
            "profile $name activate ${active.name}"
        } else {
            "profile $name"
        }
        return "$start\n%s\n".format(slots.joinToString(separator = "\n\t", prefix = "\t") {
            it.toString()
        })
    }

}

data class ProfileSlot(val id: ProfileSlotId,
                       val order: String?,
                       val items: List<ProfileItemData>) {

    override fun toString(): String {
        return id.toString() + "\n" + items.joinToString(separator = "\n\t\t", prefix = "\t\t") { it.toString() }
    }

}

data class ProfileComponentData(val id: Identifier,
                                val componentNbt: NbtElement?) {

    override fun toString(): String {
        return if (componentNbt == null) {
            "\"$id\""
        } else {
            "\"$id\"(${componentNbt.`(asString)`.escape()})"
        }
    }
}

data class ProfileItemData(val itemId: String,
                           val customName: String,
                           val components: List<ProfileComponentData>?) {

    override fun toString(): String {
        return when {
            !components.isNullOrEmpty() -> {
                itemIdString() + " -> [ " + components.joinToString(separator = ", ") + " ]"
            }
            else -> {
                itemIdString()
            }
        }
    }
    private fun itemIdString(): String {
        return if (customName.isNotBlank()) {
            "\"$itemId\"(\"$customName\")"
        } else {
            "\"$itemId\""
        }
    }
}

private var slotIdToProfileSlotId: MutableMap<Int, ProfileSlotId> = mutableMapOf()

fun ProfileSlotId.addSlotId(slotId: Int) {
    slotIdToProfileSlotId[slotId] = this
}


enum class ProfileSlotId(val slotId: Int) {

    NONE(-1),
    HOT1(36),
    HOT2(37),
    HOT3(38),
    HOT4(39),
    HOT5(40),
    HOT6(41),
    HOT7(42),
    HOT8(43),
    HOT9(44),
    FEET(8),
    LEGS(7),
    CHEST(6),
    HEAD(5),
    OFFHAND(45);


    companion object {
        fun valueOfOrFAKE(v: String?): ProfileSlotId {
            if (v == null) {
                return NONE;
            }
            return valueOf(v)
        }
        fun valueOf(i: Int): ProfileSlotId {
            return slotIdToProfileSlotId.get(i) ?: NONE
        }
    }

    init {
        this.addSlotId(slotId)
    }

    fun slotId(): Int {
        return slotId
    }


}

// regex    :-> "([^"]*)" : (.*)
// replace  :-> ["minecraft:\l$1"("$2")]
// regex    :\{id:"([^"]*)",lvl:(\d*)}
// replace  :'$1':$2

val testConfig get() = """
profile Main activate HOT1
    HOT1
        "minecraft:netherite_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
        "minecraft:diamond_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
    HOT2
        "minecraft:netherite_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5}")]
        "minecraft:diamond_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
        "minecraft:netherite_axe"
        "minecraft:diamond_axe"
    HOT3
        "minecraft:water_bucket"
    HOT4
        "minecraft:terracotta"
    CHEST
        "minecraft:netherite_chestplate"
    OFFHAND
        "minecraft:shield" -> ["minecraft:enchantments"("{'minecraft:unbreaking':3,'minecraft:mending':1}")]
    LEGS
        "minecraft:netherite_leggings" -> ["minecraft:enchantments"("{'minecraft:fire_protection':3,'minecraft:unbreaking':3}")]
    FEET
        "minecraft:netherite_boots" -> ["minecraft:enchantments"("{'minecraft:protection':4,'minecraft:unbreaking':3}")]

profile PvP activate HOT5
    HOT3
        "minecraft:netherite_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
        "minecraft:diamond_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
    HOT5
        "minecraft:netherite_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
        "minecraft:diamond_axe" -> ["minecraft:enchantments"("{'minecraft:efficiency':5,'minecraft:silk_touch':1,'minecraft:smite':5,'minecraft:unbreaking':3}")]
        "minecraft:diamond_axe"
    HOT4
        "minecraft:iron_pickaxe" -> ["minecraft:enchantments"("{'minecraft:efficiency':4,'minecraft:fortune':3}")]
    HOT7
        "minecraft:splash_potion" -> ["minecraft:potion_contents"("'minecraft:strong_harming'")]
    HOT6
        "minecraft:splash_potion" -> ["minecraft:potion_contents"("'minecraft:harming'")]
    HOT2
        "minecraft:splash_potion" -> ["minecraft:potion_contents"("'minecraft:weakness'")]
    HOT1
        "minecraft:splash_potion" -> ["minecraft:potion_contents"("'minecraft:regeneration'")]
    HOT8
        "minecraft:splash_potion" -> ["minecraft:potion_contents"("'minecraft:long_turtle_master'")]
    HOT9
    HOT1

profile StressTest
    HOT1 order by "@item_name"
        "#minecraft:pickaxes"
        "#minecraft:axes"
    HOT2 order by "@creative_menu_order"
        "#minecraft:pickaxes"
        "#minecraft:axes"
    HOT3
        "minecraft:wooden_*"
    HOT4 order by "@by_nutrition"
        "*" -> ["food"]
    HOT5 order by "@less_nutrition"
        "*" -> ["food"]
""".trimIndent()

@Suppress("UNUSED_PARAMETER")
fun main(args: Array<String>) {
    val r = ProfilesConfig.getProfiles(testConfig)
    println(ProfilesConfig.asString(r))
}
