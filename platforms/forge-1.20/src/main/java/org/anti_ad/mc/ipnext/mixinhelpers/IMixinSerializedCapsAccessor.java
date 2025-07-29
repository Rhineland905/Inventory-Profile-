package org.anti_ad.mc.ipnext.mixinhelpers;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface IMixinSerializedCapsAccessor {
    @Nullable
    CompoundTag IPN$serializeCaps();
}
