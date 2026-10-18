package net.anubis.client.module.impl.combat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.impl.PacketReceiveEvent;
import net.anubis.client.event.impl.PacketSendEvent;
import net.anubis.client.event.impl.PlayerAttackEntityEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.impl.combat.crystal.Crystals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler;

@Environment(EnvType.CLIENT)
public final class CrystalOptimizerModule extends ClientModule {
   private static final int SAMPLE_CAPACITY = 64;
   private final ItemStack[] heldSamples = new ItemStack[64];
   private final int[] sampleTicks = new int[64];
   private int sampleCursor;
   private int sampleCount;
   private int ticks;
   private int trackedSince;
   private ClientPlayerEntity trackedPlayer;
   private ClientWorld trackedLevel;
   private boolean attackSent;
   private EndCrystalEntity pendingRemoval;
   private final CrystalOptimizerModule.AttackProbe attackProbe = new CrystalOptimizerModule.AttackProbe();
   private final Int2ObjectMap<CrystalOptimizerModule.Ghost> ghosts = new Int2ObjectOpenHashMap();
   private final IntSet stubborn = new IntOpenHashSet();
   private final IntSet serverRemoved = new IntOpenHashSet();

   public CrystalOptimizerModule() {
      super("crystal-optimizer", "Crystal Optimizer", "Removes crystals client-side as soon as your hit breaks them.", ModuleCategory.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != null && level == this.trackedLevel) {
         ObjectIterator var2 = this.ghosts.values().iterator();

         while (var2.hasNext()) {
            CrystalOptimizerModule.Ghost ghost = (CrystalOptimizerModule.Ghost)var2.next();
            revive(level, ghost.crystal());
         }
      }

      this.reset();
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onTick(TickEvent event) {
      this.attackSent = false;
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         if (player != this.trackedPlayer || level != this.trackedLevel) {
            this.reset();
            this.trackedPlayer = player;
            this.trackedLevel = level;
         }

         if (this.pendingRemoval != null && !this.pendingRemoval.isRemoved()) {
            level.removeEntity(this.pendingRemoval.getId(), RemovalReason.DISCARDED);
            this.ghosts.put(this.pendingRemoval.getId(), new CrystalOptimizerModule.Ghost(this.pendingRemoval, this.ticks));
         }

         this.pendingRemoval = null;
         this.ticks++;
         this.serverRemoved.removeIf(id -> level.getEntityById(id) == null);
         this.sample(player.getMainHandStack());
         this.restoreGhosts(mc, player, level);
      } else {
         if (this.trackedPlayer != null || this.sampleCount > 0) {
            this.reset();
         }
      }
   }

   @EventHandler
   public void onPacketReceive(PacketReceiveEvent event) {
      if (event.packet instanceof EntitiesDestroyS2CPacket remove) {
         IntListIterator var5 = remove.getEntityIds().iterator();

         while (var5.hasNext()) {
            int id = (Integer)var5.next();
            this.ghosts.remove(id);
            this.stubborn.remove(id);
            if (this.pendingRemoval != null && this.pendingRemoval.getId() == id) {
               this.pendingRemoval = null;
            }

            if (this.trackedLevel != null && this.trackedLevel.getEntityById(id) instanceof EndCrystalEntity) {
               this.serverRemoved.add(id);
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onPacketSend(PacketSendEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.isOnThread()) {
         if (event.packet instanceof PlayerInteractEntityC2SPacket interact) {
            this.attackSent = this.attackProbe.isAttack(interact);
         } else if (event.packet instanceof UpdateSelectedSlotC2SPacket carried) {
            ClientPlayerEntity player = mc.player;
            if (player != null && player == this.trackedPlayer && PlayerInventory.isValidHotbarIndex(carried.getSelectedSlot())) {
               this.sample(player.getInventory().getStack(carried.getSelectedSlot()));
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onAttack(PlayerAttackEntityEvent event) {
      boolean sent = this.attackSent;
      this.attackSent = false;
      if (sent && event.target instanceof EndCrystalEntity crystal) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         ClientWorld level = mc.world;
         if (player != null && level != null && player == this.trackedPlayer && crystal.getEntityWorld() == level) {
            if (!this.stubborn.contains(crystal.getId())
               && !this.serverRemoved.contains(crystal.getId())
               && this.breaksOnServer(mc, player, level, crystal)) {
               this.pendingRemoval = crystal;
            }
         }
      }
   }

   private void restoreGhosts(MinecraftClient mc, ClientPlayerEntity player, ClientWorld level) {
      if (!this.ghosts.isEmpty()) {
         int window = Crystals.confirmWindow(mc, player);
         ObjectIterator<CrystalOptimizerModule.Ghost> iterator = this.ghosts.values().iterator();

         while (iterator.hasNext()) {
            CrystalOptimizerModule.Ghost ghost = (CrystalOptimizerModule.Ghost)iterator.next();
            if (this.ticks - ghost.removedAt() > window) {
               iterator.remove();
               if (revive(level, ghost.crystal())) {
                  this.stubborn.add(ghost.crystal().getId());
               }
            }
         }
      }
   }

   private static boolean revive(ClientWorld level, EndCrystalEntity old) {
      if (level.getEntityById(old.getId()) != null) {
         return false;
      }

      EndCrystalEntity copy = new EndCrystalEntity(level, old.getX(), old.getY(), old.getZ());
      copy.setId(old.getId());
      copy.setUuid(old.getUuid());
      copy.setShowBottom(old.shouldShowBottom());
      level.addEntity(copy);
      return true;
   }

   private boolean breaksOnServer(MinecraftClient mc, ClientPlayerEntity player, ClientWorld level, EndCrystalEntity crystal) {
      if (crystal.isRemoved()) {
         return false;
      }

      if (!player.isSpectator() && !player.isDead() && !player.isUsingRiptide()) {
         ItemStack weapon = player.getMainHandStack();
         if (weapon.isItemEnabled(level.getEnabledFeatures()) && !player.isBelowMinimumAttackCharge(weapon, 0)) {
            if (!level.getWorldBorder().contains(crystal.getBlockPos())) {
               return false;
            } else {
               return !player.canAttackEntityIn(crystal.getBoundingBox(), 0.0)
                  ? false
                  : Crystals.enchantsAddDamage(weapon) || this.recentHandsDealDamage(mc, player, weapon);
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean recentHandsDealDamage(MinecraftClient mc, ClientPlayerEntity player, ItemStack held) {
      if (!Crystals.dealsDamage(player, held)) {
         return false;
      }

      int window = Crystals.confirmWindow(mc, player);
      boolean complete = this.ticks - this.trackedSince >= window;
      ItemStack checked = held;

      int walked;
      for (walked = 0; walked < this.sampleCount; walked++) {
         int index = Math.floorMod(this.sampleCursor - 1 - walked, 64);
         if (this.ticks - this.sampleTicks[index] > window) {
            break;
         }

         ItemStack sample = this.heldSamples[index];
         if (sample != checked) {
            if (!Crystals.dealsDamage(player, sample)) {
               return false;
            }

            checked = sample;
         }
      }

      if (walked == 64) {
         complete = false;
      }

      return complete || Crystals.dealsDamage(player, ItemStack.EMPTY);
   }

   private void sample(ItemStack stack) {
      this.heldSamples[this.sampleCursor] = stack;
      this.sampleTicks[this.sampleCursor] = this.ticks;
      this.sampleCursor = (this.sampleCursor + 1) % 64;
      if (this.sampleCount < 64) {
         this.sampleCount++;
      }
   }

   private void reset() {
      Arrays.fill(this.heldSamples, null);
      this.sampleCursor = 0;
      this.sampleCount = 0;
      this.trackedSince = this.ticks;
      this.trackedPlayer = null;
      this.trackedLevel = null;
      this.attackSent = false;
      this.pendingRemoval = null;
      this.ghosts.clear();
      this.stubborn.clear();
      this.serverRemoved.clear();
   }

   @Environment(EnvType.CLIENT)
   private static final class AttackProbe implements Handler {
      private boolean attack;

      boolean isAttack(PlayerInteractEntityC2SPacket packet) {
         this.attack = false;
         packet.handle(this);
         return this.attack;
      }

      public void interact(Hand hand) {
      }

      public void interactAt(Hand hand, Vec3d location) {
      }

      public void attack() {
         this.attack = true;
      }
   }

   @Environment(EnvType.CLIENT)
   private record Ghost(EndCrystalEntity crystal, int removedAt) {
   }
}
