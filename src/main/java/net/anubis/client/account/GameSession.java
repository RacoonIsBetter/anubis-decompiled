package net.anubis.client.account;

import com.example.client.mixin.AccessorAccountsMinecraft;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.UserApiService.UserProperties;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.ProfileKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class GameSession {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private YggdrasilAuthenticationService mojangAuth;

   GameSession() {
   }

   GameSession.Prepared prepare(AccountType type, Credentials credentials) {
      MinecraftClient mc = MinecraftClient.getInstance();
      boolean offline = type == AccountType.CRACKED;
      Session user = new Session(credentials.name(), credentials.uuid(), credentials.accessToken(), Optional.empty(), Optional.empty());
      UserApiService api = offline ? UserApiService.OFFLINE : this.mojangAuth(mc).createUserApiService(credentials.accessToken());

      UserProperties properties;
      try {
         properties = api.fetchProperties();
      } catch (AuthenticationException e) {
         LOGGER.warn("[Anubis] Couldn't fetch user properties for {}", credentials.name(), e);
         properties = UserApiService.OFFLINE_PROPERTIES;
      }

      ProfileResult profile = offline ? null : mc.getApiServices().sessionService().fetchProfile(credentials.uuid(), true);
      return new GameSession.Prepared(user, api, properties, profile, offline);
   }

   void apply(GameSession.Prepared prepared) {
      MinecraftClient mc = MinecraftClient.getInstance();
      AccessorAccountsMinecraft game = (AccessorAccountsMinecraft)mc;
      game.anubis$setUser(prepared.user());
      game.anubis$setUserApiService(prepared.api());
      game.anubis$setUserPropertiesFuture(CompletableFuture.completedFuture(prepared.properties()));
      game.anubis$setProfileFuture(CompletableFuture.completedFuture(prepared.profile()));
      game.anubis$setPlayerSocialManager(new SocialInteractionsManager(mc, prepared.api()));
      game.anubis$setProfileKeyPairManager(
         prepared.offline() ? ProfileKeys.MISSING : ProfileKeys.create(prepared.api(), prepared.user(), mc.runDirectory.toPath())
      );
      game.anubis$setReportingContext(AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), prepared.api()));
   }

   private YggdrasilAuthenticationService mojangAuth(MinecraftClient mc) {
      if (this.mojangAuth == null) {
         this.mojangAuth = new YggdrasilAuthenticationService(mc.getNetworkProxy());
      }

      return this.mojangAuth;
   }

   @Environment(EnvType.CLIENT)
   record Prepared(Session user, UserApiService api, UserProperties properties, ProfileResult profile, boolean offline) {
   }
}
