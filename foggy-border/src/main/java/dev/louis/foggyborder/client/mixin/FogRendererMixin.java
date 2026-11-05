package dev.louis.foggyborder.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.louis.foggyborder.client.FoggyBorder;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pulls the fog in as the camera gets closer to the world border. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Inject(
            method = "setupFog",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F", ordinal = 0)
    )
    private void foggyborder$modifyFog(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darken, ClientLevel level,
                                       CallbackInfoReturnable<FogData> cir, @Local FogData fogData) {
        if (level == null) return;
        Vec3 pos = camera.position();
        double distance = level.getWorldBorder().getDistanceToBorder(pos.x, pos.z);

        fogData.environmentalEnd = (float) Math.min(
                Math.max(distance * FoggyBorder.config.fogEndDistanceMultiplier, FoggyBorder.config.minimumFogEndDistance),
                fogData.environmentalEnd);

        Vector4f color = fogData.color;
        if (distance < 32 && color != null) {
            // Same tint as the original: drop green and blue so the fog turns reddish right at the border.
            float mult = Math.min((float) (distance + 2 / 16f), 1);
            color.y = color.y * mult;
            color.z = color.z * mult;
        }
    }
}
