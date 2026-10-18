package com.example.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.ProfileKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(MinecraftClient.class)
public interface AccessorAccountsMinecraft {
   @Mutable
   @Accessor("session")
   void anubis$setUser(Session var1);

   @Mutable
   @Accessor("userApiService")
   void anubis$setUserApiService(UserApiService var1);

   @Mutable
   @Accessor("userPropertiesFuture")
   void anubis$setUserPropertiesFuture(CompletableFuture<UserProperties> var1);

   @Mutable
   @Accessor("gameProfileFuture")
   void anubis$setProfileFuture(CompletableFuture<ProfileResult> var1);

   @Mutable
   @Accessor("socialInteractionsManager")
   void anubis$setPlayerSocialManager(SocialInteractionsManager var1);

   @Mutable
   @Accessor("profileKeys")
   void anubis$setProfileKeyPairManager(ProfileKeys var1);

   @Accessor("abuseReportContext")
   void anubis$setReportingContext(AbuseReportContext var1);
}
