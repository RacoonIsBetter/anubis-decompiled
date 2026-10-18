package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;
import net.anubis.client.AnubisClientMod;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class SpawnerNotifierModule extends ClientModule implements ChunkScanner.Consumer {
   private static final String SPAWNER = "Spawner";
   private final BooleanSetting sSound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final BooleanSetting sRender = this.addSetting("render", new BooleanSetting("Render", true));
   private final ColorSetting sColor = this.addSetting("color", new ColorSetting("Color", -587246251));
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.1F, 256.0F);
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return SpawnerNotifierModule.this.sColor.argb();
      }

      @Override
      public int tracerColor(Object kind) {
         return 0;
      }
   }, () -> MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getRegistryKey());
   private final LongOpenHashSet notified = new LongOpenHashSet();

   public SpawnerNotifierModule() {
      super("spawner-notifier", "Spawner Notifier", "Scans chunks for mob spawners and highlights them.", ModuleCategory.DONUT);
      this.usesNotifications();
   }

   @Override
   protected void onEnable() {
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.layer.clear();
      this.notified.clear();
   }

   @Override
   public boolean wants(BlockState state) {
      return state.isOf(Blocks.SPAWNER);
   }

   @Override
   public void found(long pos, BlockState state) {
      this.layer.put(pos, "Spawner");
      if (this.notified.add(pos)) {
         this.notify(BlockPos.fromLong(pos));
      }
   }

   @Override
   public void removed(long pos) {
      this.layer.remove(pos);
   }

   @Override
   public void reset() {
      this.layer.clear();
      this.notified.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      this.layer.retainChunk(chunkKey, stillThere);
   }

   private void notify(BlockPos pos) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.notificationsOn()) {
         ModuleChat.send(this, foundMessage(mc, pos));
      }

      if (this.sSound.get() && mc.player != null && mc.world != null) {
         mc.getSoundManager().play(PositionedSoundInstance.ui(AnubisClientMod.SPAWNER_NOTIFY_SOUND, 1.0F, 1.0F));
      }
   }

   private static ModuleChat.Body foundMessage(MinecraftClient mc, BlockPos pos) {
      String mob = entityName(mc, pos);
      ModuleChat.Body body = mob == null ? ModuleChat.body().warn("Unknown") : ModuleChat.body().name(mob);
      body.text(" spawner ").good("found").text(" at ").coords(pos.getX(), pos.getY(), pos.getZ());
      if (mc.player != null) {
         long distance = Math.round(mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)));
         body.text(" (").value(distance + "m").text(")");
      }

      return body;
   }

   private static String entityName(MinecraftClient mc, BlockPos pos) {
      if (mc.world != null && mc.world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
         Entity entity = spawner.getLogic().getRenderedEntity(mc.world, pos);
         return entity == null ? null : entity.getType().getName().getString();
      } else {
         return null;
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      if (this.sRender.get()) {
         this.layer.render(event, this.style);
      }
   }
}
