package com.example.client.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderManager.class)
public interface AccessorSkinChangeEntityRenderDispatcher {
   @Accessor("playerRenderers")
   Map<PlayerSkinType, PlayerEntityRenderer<AbstractClientPlayerEntity>> anubis$skinChangePlayerRenderers();
}
