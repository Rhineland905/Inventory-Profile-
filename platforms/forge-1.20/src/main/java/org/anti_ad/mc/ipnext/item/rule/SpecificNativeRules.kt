@file:Suppress("UnstableApiUsage")

package org.anti_ad.mc.ipnext.item.rule

import net.minecraftforge.common.capabilities.CapabilityProvider
import org.anti_ad.mc.ipnext.item.NbtUtils
import org.anti_ad.mc.ipnext.item.rule.natives.NativeRule
import org.anti_ad.mc.ipnext.item.rule.natives.rule
import org.anti_ad.mc.ipnext.mixinhelpers.IMixinSerializedCapsAccessor

class CapNbtComparatorRule : NativeRule() {
    init {
        comparator = { a, b -> // compare a.tag and b.tag
            val res =if (a.sourceStack is CapabilityProvider<*> && b.sourceStack is CapabilityProvider<*>) {
                val nbtA = (a.sourceStack as IMixinSerializedCapsAccessor).`IPN$serializeCaps`()
                val nbtB = (b.sourceStack as IMixinSerializedCapsAccessor).`IPN$serializeCaps`()
                NbtUtils.compareNbt(nbtA, nbtB)
            } else {
                0
            }
            res
        }
    }
}


val cap_nbt_comparator /**/ by rule(::CapNbtComparatorRule)

fun initSpecificNativeRules() {

}
