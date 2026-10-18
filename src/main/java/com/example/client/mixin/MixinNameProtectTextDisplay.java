package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.anubis.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity.TextLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TextDisplayEntity.class)
public abstract class MixinNameProtectTextDisplay {
   @Unique
   private int anubis$nameProtectGeneration;

   @ModifyExpressionValue(
      method = "splitLines(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;",
      at = @At(
         value = "FIELD",
         opcode = 180,
         ordinal = 0,
         target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity;textLines:Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;"
      )
   )
   private TextLines anubis$nameProtectRefresh(TextLines cached) {
      int generation = NameProtectModule.generation();
      if (generation == this.anubis$nameProtectGeneration) {
         return cached;
      }

      this.anubis$nameProtectGeneration = generation;
      return null;
   }

   @ModifyExpressionValue(
      method = "splitLines(Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$LineSplitter;)Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$TextLines;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/decoration/DisplayEntity$TextDisplayEntity$Data;text()Lnet/minecraft/text/Text;")
   )
   private Text anubis$nameProtectText(Text text) {
      return NameProtectModule.protect(text);
   }
}
