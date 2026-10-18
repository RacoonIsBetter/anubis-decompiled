package net.anubis.client.module.impl.combat;

import net.anubis.client.chat.ModuleChat;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

@Environment(EnvType.CLIENT)
public final class FriendGuardModule extends ClientModule {
   private static final long HINT_COOLDOWN_MS = 1500L;
   private static volatile FriendGuardModule active;
   private long lastHintAt;

   public FriendGuardModule() {
      super("friend-guard", "Friend Guard", "Stops you from hitting players on your friends list.", ModuleCategory.COMBAT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      this.lastHintAt = 0L;
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      this.lastHintAt = 0L;
   }

   public static boolean intercepts(PlayerEntity attacker, Entity target) {
      FriendGuardModule guard = active;
      if (!(guard != null && target instanceof PlayerEntity friend) || friend == attacker) {
         return false;
      } else if (attacker == MinecraftClient.getInstance().player && MeleeAttacks.isFriend(friend)) {
         guard.hint(friend);
         return true;
      } else {
         return false;
      }
   }

   public static boolean interceptsStab(ClientPlayerEntity player) {
      FriendGuardModule guard = active;
      if (guard != null && player != null) {
         Entity friend = MeleeAttacks.stabTarget(player, entity -> entity instanceof PlayerEntity other && MeleeAttacks.isFriend(other));
         if (friend == null) {
            return false;
         }

         guard.hint((PlayerEntity)friend);
         return true;
      } else {
         return false;
      }
   }

   private void hint(PlayerEntity friend) {
      if (this.notificationsOn()) {
         long now = System.currentTimeMillis();
         if (now - this.lastHintAt >= 1500L) {
            this.lastHintAt = now;
            ModuleChat.actionBar(this, ModuleChat.body().text("Held back your hit on ").name(friend.getGameProfile().name()));
         }
      }
   }
}
