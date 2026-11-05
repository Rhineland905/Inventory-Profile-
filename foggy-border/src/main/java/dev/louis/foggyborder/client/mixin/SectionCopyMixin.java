package dev.louis.foggyborder.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.louis.foggyborder.client.FoggyBorder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chunk meshes skip blocks that lie beyond the world border ("don't render behind border"). */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionCopy")
public class SectionCopyMixin {
    private static @Nullable WorldBorder foggyborder$border() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getWorldBorder();
    }

    @WrapMethod(method = "getBlockEntity")
    private BlockEntity foggyborder$noBlockEntitiesOutside(BlockPos pos, Operation<BlockEntity> original) {
        if (FoggyBorder.config.dontRenderBehindBorder) {
            WorldBorder border = foggyborder$border();
            if (border != null && !border.isWithinBounds(pos)) return null;
        }
        return original.call(pos);
    }

    @ModifyExpressionValue(
            method = "getBlockState",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/chunk/SectionCopy;section:Lnet/minecraft/world/level/chunk/PalettedContainer;")
    )
    private @Nullable PalettedContainer<BlockState> foggyborder$noBlocksOutside(@Nullable PalettedContainer<BlockState> original,
                                                                              @Local(argsOnly = true) BlockPos pos) {
        if (FoggyBorder.config.dontRenderBehindBorder) {
            WorldBorder border = foggyborder$border();
            if (border != null && !border.isWithinBounds(pos.getX(), pos.getZ(), 4)) return null;
        }
        return original;
    }
}
