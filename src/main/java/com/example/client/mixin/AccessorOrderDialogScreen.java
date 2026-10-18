package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(DialogScreen.class)
public interface AccessorOrderDialogScreen {
   @Accessor("dialog")
   Dialog anubis$dialog();
}
