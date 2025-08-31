package org.anti_ad.mc.ipnext.mixin;

import net.minecraft.item.Item;
import org.anti_ad.mc.ipnext.mixinhelpers.IMixinItemIndexValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;


@Mixin(Item.class)
public class MixinItemIndexValue implements IMixinItemIndexValue {

    @Unique
    int ipn$index = -1;

    @Override
    public int ipn$getIndex() {
        return ipn$index;
    }

    @Override
    public void ipn$setIndex(int value) {
        ipn$index = value;
    }
}
