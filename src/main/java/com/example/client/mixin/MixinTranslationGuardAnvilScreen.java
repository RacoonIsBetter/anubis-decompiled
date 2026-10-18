package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.anubis.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(AnvilScreen.class)
public abstract class MixinTranslationGuardAnvilScreen {
   @WrapMethod(method = "onSlotUpdate(Lnet/minecraft/screen/ScreenHandler;ILnet/minecraft/item/ItemStack;)V")
   private void anubis$guardSlot(ScreenHandler menu, int slot, ItemStack stack, Operation<Void> original) {
      TranslationGuard.run(() -> original.call(new Object[]{menu, slot, stack}));
   }

   @WrapMethod(method = "onRenamed(Ljava/lang/String;)V")
   private void anubis$guardName(String name, Operation<Void> original) {
      TranslationGuard.run(() -> original.call(new Object[]{name}));
   }
}
