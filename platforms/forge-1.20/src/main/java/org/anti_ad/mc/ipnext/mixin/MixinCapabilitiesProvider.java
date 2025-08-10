package org.anti_ad.mc.ipnext.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilityProviderImpl;
import org.anti_ad.mc.ipnext.mixinhelpers.IMixinSerializedCapsAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings({"UnstableApiUsage"})
@Mixin(value = ItemStack.class, remap = false)
public abstract class MixinCapabilitiesProvider<B extends ICapabilityProviderImpl<B>> extends CapabilityProvider<B> implements IMixinSerializedCapsAccessor {

    protected MixinCapabilitiesProvider(Class<B> baseClass) {
        super(baseClass);
    }

    protected MixinCapabilitiesProvider(Class<B> baseClass, boolean isLazy) {
        super(baseClass, isLazy);
    }

    public @Nullable CompoundTag IPN$serializeCaps() {
        var caps = serializeCaps();
        if (caps == null) {
            caps = new CompoundTag();
        }
        return caps;
    }
}
