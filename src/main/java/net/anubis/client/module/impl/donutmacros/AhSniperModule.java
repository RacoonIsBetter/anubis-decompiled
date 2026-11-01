package net.anubis.client.module.impl.donutmacros;

import net.anubis.client.chat.ModuleChat;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.PacketReceiveNettyEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AhSniperModule extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String EXACT = "Exactly these";
   private static final String AT_LEAST = "At least these";
   private final StringSetting item = this.addSetting(
      "item", new StringSetting("Item", SnipeTarget.Preset.SHARD_AXE.value).presets(SnipeTarget.Preset.choices())
   );
   private final StringSetting maxPrice = this.addSetting("max-price", new StringSetting("Buy Below (each)", "").hint("e.g. 50m"));
   private final StringSetting enchants = this.addSetting("enchants", new StringSetting("Enchantments", "").hint("e.g. protection 2, unbreaking 3, mending"));
   private final ModeSetting enchantMatch = this.addSetting(
      "enchant-match", new ModeSetting("Enchant Match", "Exactly these", "Exactly these", "At least these")
   );
   private final NumberSetting minHours = this.addSetting("min-hours", new NumberSetting("Min Time Left (h)", 0.0F, 0.0F, 48.0F).step(0.5F));
   private final NumberSetting refresh = this.addSetting("refresh", new NumberSetting("Refresh Every (ms)", 300.0F, 100.0F, 2000.0F).step(50.0F));
   private final NumberSetting settle = this.addSetting("settle", new NumberSetting("Wait After Page Change (ms)", 200.0F, 50.0F, 1000.0F).step(25.0F));
   private final StringSetting budget = this.addSetting("budget", new StringSetting("Max Total Spend", "").hint("e.g. 500m, empty = no limit"));
   private final BooleanSetting requireConfirm = this.addSetting("require-confirm", new BooleanSetting("Only Buy With Confirm", false));
   private final NumberSetting maxBuys = this.addSetting("max-buys", new NumberSetting("Stop After (buys, 0 = never)", 0.0F, 0.0F, 100.0F).step(1.0F));
   private final BooleanSetting autoSell = this.addSetting("auto-sell", new BooleanSetting("Auto Sell", false));
   private final StringSetting sellPrice = this.addSetting("sell-price", new StringSetting("Sell Price (each)", "").hint("e.g. 60m"));
   private final MacroWorker worker = new MacroWorker("Anubis AH Sniper");
   private volatile SniperBot bot;

   public AhSniperModule() {
      super(
         "ah-sniper", "AH Sniper", "Refreshes the AH and instantly buys your item below your price, with optional auto selling.", ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();
      this.enchantMatch.visibleWhen(() -> !this.enchants.get().isBlank());
      this.sellPrice.visibleWhen(this.autoSell::get);
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.getNetworkHandler() != null && mc.getSession() != null) {
         long max = ServerText.parseMoney(this.maxPrice.get());
         if (max <= 0L) {
            this.say(ModuleChat.body().warn("Set Buy Below to a price like 50m."));
            this.requestDisable();
         } else {
            SnipeTarget target = SnipeTarget.parse(
               this.item.get(), this.enchants.get(), this.enchantMatch.is("Exactly these"), max, Math.round(this.minHours.get() * 60.0F)
            );
            if (target == null) {
               this.say(ModuleChat.body().warn("Pick an item to snipe first."));
               this.requestDisable();
            } else {
               long spendLimit = 0L;
               if (!this.budget.get().isBlank()) {
                  spendLimit = ServerText.parseMoney(this.budget.get());
                  if (spendLimit <= 0L) {
                     this.say(ModuleChat.body().warn("Set Max Total Spend to a price like 500m, or leave it empty."));
                     this.requestDisable();
                     return;
                  }
               }

               long sell = 0L;
               if (this.autoSell.get()) {
                  sell = ServerText.parseMoney(this.sellPrice.get());
                  if (sell <= 0L) {
                     this.say(ModuleChat.body().warn("Set Sell Price to a price like 60m, or turn Auto Sell off."));
                     this.requestDisable();
                     return;
                  }

                  if (sell <= max) {
                     this.say(ModuleChat.body().warn("Sell Price must be higher than Buy Below").text(", or the sniper would buy your own listing back."));
                     this.requestDisable();
                     return;
                  }
               }

               SniperBot created = new SniperBot(
                  this,
                  this::stopFromBot,
                  this.worker,
                  target,
                  this.refresh.getInt(),
                  this.settle.getInt(),
                  spendLimit,
                  this.requireConfirm.get(),
                  this.maxBuys.getInt(),
                  sell,
                  mc.getSession().getUsername()
               );
               this.bot = created;
               this.worker.start(created::run, failure -> {
                  LOGGER.error("[Anubis] AH Sniper stopped", failure);
                  this.say(ModuleChat.body().danger("Stopped").text(": " + failure));
                  this.stopFromBot(created);
               });
            }
         }
      } else {
         this.say(ModuleChat.body().warn("Join a server first."));
         this.requestDisable();
      }
   }

   void stopFromBot(SniperBot from) {
      MinecraftClient.getInstance().execute(() -> {
         if (this.bot == from && this.isEnabled()) {
            this.setEnabled(false);
         }
      });
   }

   @Override
   protected void onDisable() {
      SniperBot stopped = this.bot;
      this.bot = null;
      this.worker.stop();
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (stopped != null && player != null) {
         if (player.currentScreenHandler != player.playerScreenHandler) {
            player.closeHandledScreen();
         } else {
            Dialogs.close(mc);
         }

         this.say(ModuleChat.body().text(stopped.summary()));
      }
   }

   @EventHandler
   public void onPacket(PacketReceiveNettyEvent event) {
      SniperBot current = this.bot;
      if (current != null && event.packet instanceof GameMessageS2CPacket chat && chat.content() != null) {
         current.onChat(chat.content().getString());
      }
   }

   private void say(ModuleChat.Body body) {
      ModuleChat.send(this, body);
   }
}
