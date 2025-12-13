/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2023 Plamen K. Kosseff <p.kosseff@gmail.com>
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

package org.anti_ad.mc.ipnext.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.trading.MerchantOffer;
import org.anti_ad.mc.common.gui.NativeContext;
import org.anti_ad.mc.ipnext.event.villagers.VillagerTradeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public class MixinMerchantScreen {

    @Final
    @Shadow
    public MerchantScreen.TradeOfferButton[] tradeOfferButtons;



    @SuppressWarnings("DataFlowIssue")
    @Inject(method = "renderContents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;renderAndDecorateCostA(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;II)V", ordinal = 0))
    void render(GuiGraphics matrices, int mouseX, int mouseY, float delta, CallbackInfo ci, @Local(ordinal = 2) int i, @Local(ordinal = 3) int j, @Local(ordinal = 4) int k, @Local(ordinal = 5) int l, @Local(ordinal = 6) int m, @Local MerchantOffer tradeOffer) {
        MerchantScreen self = (MerchantScreen)((Object)this);
        VillagerTradeManager.INSTANCE.drawingButton(self, new NativeContext(matrices, RenderPipelines.GUI_TEXTURED), mouseX, mouseY, tradeOffer, i, j, k, l, m);
    }


}
