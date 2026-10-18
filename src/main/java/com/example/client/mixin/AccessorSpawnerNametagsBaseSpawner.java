package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.block.spawner.MobSpawnerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(MobSpawnerLogic.class)
public interface AccessorSpawnerNametagsBaseSpawner {
   @Accessor("spawnEntry")
   MobSpawnerEntry anubis$spawnerNametagsNextSpawnData();

   @Accessor("requiredPlayerRange")
   int anubis$spawnerNametagsRequiredPlayerRange();
}
