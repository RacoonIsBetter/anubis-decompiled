package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.Toast.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net/minecraft/client/toast/ToastManager$Entry")
public interface AccessorHudToastInstance {
   @Accessor("instance")
   Toast anubis$hudToast();

   @Accessor("topIndex")
   int anubis$hudFirstSlot();

   @Accessor("visibility")
   Visibility anubis$hudVisibility();

   @Accessor("visibleWidthPortion")
   float anubis$hudVisiblePortion();
}
