package dev.louis.foggyborder.client.mixin;

import dev.louis.foggyborder.client.FoggyBorder;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the vanilla blue border wall. */
@Mixin(WorldBorderRenderer.class)
public class WorldBorderRendererMixin {
    @Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true)
    private void foggyborder$hideWall(CallbackInfo ci) {
        if (FoggyBorder.config.disableWorldborder) ci.cancel();
    }
}
