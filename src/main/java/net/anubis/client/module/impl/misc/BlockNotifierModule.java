package net.anubis.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.anubis.client.esp.ChunkScanner;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.ChallengeToast;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BlockColorListSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;

@Environment(EnvType.CLIENT)
public final class BlockNotifierModule extends ClientModule implements ChunkScanner.Consumer {
   private static final int MAX_KNOWN = 50000;
   private final BlockColorListSetting blocks = this.addSetting("blocks", BlockColorListSetting.blockList("Blocks", List.of("minecraft:spawner")));
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final LongLinkedOpenHashSet known = new LongLinkedOpenHashSet();
   private final Map<Block, BlockNotifierModule.Finds> pending = new LinkedHashMap<>();
   private Set<String> scannedIds = Set.of();
   private RegistryKey<World> dimension;

   public BlockNotifierModule() {
      super(
         "block-notifier", "Block Notifier", "Shows a toast with the block and its coordinates when one of your chosen blocks shows up.", ModuleCategory.MISC
      );
      this.blocks.onChange(this::rescan);
   }

   @Override
   protected void onEnable() {
      this.known.clear();
      this.pending.clear();
      this.dimension = null;
      this.scannedIds = this.blocks.enabledIds();
      ChunkScanner.register(this);
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.known.clear();
      this.pending.clear();
   }

   private void rescan() {
      if (this.isEnabled()) {
         Set<String> ids = this.blocks.enabledIds();
         if (!ids.equals(this.scannedIds)) {
            this.scannedIds = ids;
            ChunkScanner.rescan();
         }
      }
   }

   @Override
   public boolean wants(BlockState state) {
      return this.blocks.isEnabled(state.getBlock());
   }

   @Override
   public void found(long pos, BlockState state) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         this.syncDimension(mc);
         if (this.known.add(pos)) {
            if (this.known.size() > 50000) {
               this.known.removeFirstLong();
            }

            BlockPos at = BlockPos.fromLong(pos);
            BlockNotifierModule.Finds finds = this.pending.computeIfAbsent(state.getBlock(), block -> new BlockNotifierModule.Finds());
            finds.count++;
            double distanceSq = mc.player == null ? 0.0 : mc.player.squaredDistanceTo(Vec3d.ofCenter(at));
            if (distanceSq < finds.nearestSq) {
               finds.nearestSq = distanceSq;
               finds.nearest = at;
            }
         }
      }
   }

   @Override
   public void removed(long pos) {
      this.known.remove(pos);
   }

   @Override
   public void reset() {
      this.known.clear();
      this.pending.clear();
   }

   private void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         this.syncDimension(mc);
         if (!this.pending.isEmpty()) {
            for (Entry<Block, BlockNotifierModule.Finds> entry : this.pending.entrySet()) {
               this.notify(entry.getKey(), entry.getValue());
            }

            this.pending.clear();
         }
      }
   }

   private void syncDimension(MinecraftClient mc) {
      RegistryKey<World> current = mc.world.getRegistryKey();
      if (!current.equals(this.dimension)) {
         this.dimension = current;
         this.known.clear();
         this.pending.clear();
      }
   }

   private void notify(Block block, BlockNotifierModule.Finds finds) {
      Item item = block.asItem();
      ItemStack icon = new ItemStack(item == Items.AIR ? Items.COMPASS : item);
      String title = block.getName().getString();
      BlockPos at = finds.nearest;
      Text message = Text.literal("at " + at.getX() + ", " + at.getY() + ", " + at.getZ());

      for (int i = 0; i < finds.count; i++) {
         ChallengeToast.addOrUpdate(icon, title, message, this.sound.get());
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Finds {
      int count;
      BlockPos nearest;
      double nearestSq = Double.MAX_VALUE;
   }
}
