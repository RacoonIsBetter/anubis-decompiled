package net.anubis.client.module.impl.misc;

import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.util.antivanish.AntiVanishText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.hit.HitResult.Type;

@Environment(EnvType.CLIENT)
public final class LegitAutoToolModule extends ClientModule {
   private static final int SWITCH_DELAY_TICKS = 2;
   private static final String SHARD_NAME = "shard pickaxe";
   private static final String SHARD_LORE = "breaks 9 blocks at once";
   private final BooleanSetting drill = this.addSetting("use-drill-for-instabreak", new BooleanSetting("Drill Instabreak", true));
   private final BooleanSetting delayed = this.addSetting("delay", new BooleanSetting("Delay", true));
   private BlockPos target;
   private int pending = -1;
   private int delay;
   private int origin = -1;
   private int ourSlot = -1;
   private int returnDelay;

   public LegitAutoToolModule() {
      super("legit-auto-tool", "Legit Auto Tool", "Auto tool that only switches hotbar slots like a player.", ModuleCategory.MISC);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      if (this.origin != -1 && MinecraftClient.getInstance().player != null) {
         this.select(this.origin);
      }

      this.reset();
   }

   private void reset() {
      this.target = null;
      this.pending = -1;
      this.delay = 0;
      this.origin = -1;
      this.ourSlot = -1;
      this.returnDelay = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayerEntity player = mc.player;
      if (player != null && mc.world != null) {
         int selected = player.getInventory().getSelectedSlot();
         if (this.origin != -1 && selected != this.ourSlot) {
            this.origin = -1;
         }

         BlockPos aimed = mc.currentScreen == null && mc.options.attackKey.isPressed() ? aimedBlock(mc) : null;
         BlockState state = aimed == null ? null : mc.world.getBlockState(aimed);
         if (aimed != null && !state.isAir()) {
            if (!aimed.equals(this.target)) {
               this.target = aimed.toImmutable();
               this.pending = -1;
               this.delay = 0;
            }

            int shard = shardSlot(player.getInventory(), state, selected);
            if (this.drill.get()) {
               int drillSlot = this.drillSlot(mc, state, aimed, shard);
               if (drillSlot != -1) {
                  if (drillSlot != selected) {
                     this.select(drillSlot);
                  }

                  this.pending = -1;
                  return;
               }
            }

            int best = this.bestSlot(mc, state, aimed, selected, shard);
            if (best == selected) {
               this.pending = -1;
            } else {
               if (this.pending != best) {
                  this.pending = best;
                  this.delay = this.switchDelay();
               }

               if (this.delay > 0) {
                  this.delay--;
               } else {
                  this.select(best);
                  this.pending = -1;
               }
            }
         } else {
            this.target = null;
            this.pending = -1;
            this.delay = 0;
            this.giveBack();
         }
      } else {
         this.reset();
      }
   }

   private static BlockPos aimedBlock(MinecraftClient mc) {
      return mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == Type.BLOCK ? hit.getBlockPos() : null;
   }

   private int drillSlot(MinecraftClient mc, BlockState state, BlockPos pos, int shard) {
      if (shard != -1) {
         return breakingDelta(mc, shard, state, pos) >= 1.0F ? shard : -1;
      }

      PlayerInventory inventory = mc.player.getInventory();

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (stack.isOf(Items.NETHERITE_PICKAXE) && !stack.willBreakNextUse() && breakingDelta(mc, slot, state, pos) >= 1.0F) {
            return slot;
         }
      }

      return -1;
   }

   private int bestSlot(MinecraftClient mc, BlockState state, BlockPos pos, int selected, int shard) {
      PlayerInventory inventory = mc.player.getInventory();
      ItemStack held = inventory.getStack(selected);
      boolean dropHeld = selected == this.ourSlot && this.origin != -1 && held.willBreakNextUse();
      int best = selected;
      float bestDelta = dropHeld ? -1.0F : breakingDelta(mc, selected, state, pos);

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.getStack(slot);
         if (slot != selected && !stack.isEmpty() && !stack.willBreakNextUse()) {
            float delta = breakingDelta(mc, slot, state, pos);
            if (delta > bestDelta) {
               bestDelta = delta;
               best = slot;
            }
         }
      }

      return shard != -1 && inventory.getStack(best).isIn(ItemTags.PICKAXES) ? shard : best;
   }

   private static int shardSlot(PlayerInventory inventory, BlockState state, int selected) {
      if (isShardFor(inventory.getStack(selected), state)) {
         return selected;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && isShardFor(inventory.getStack(slot), state)) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isShardFor(ItemStack stack, BlockState state) {
      return !stack.willBreakNextUse() && stack.isSuitableFor(state) && isShardPickaxe(stack);
   }

   private static boolean isShardPickaxe(ItemStack stack) {
      if (!stack.isIn(ItemTags.PICKAXES)) {
         return false;
      }

      if (AntiVanishText.normalize(stack.getName().getString()).contains("shard pickaxe")) {
         return true;
      }

      for (Text line : ((LoreComponent)stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)).lines()) {
         if (AntiVanishText.normalize(line.getString()).contains("breaks 9 blocks at once")) {
            return true;
         }
      }

      return false;
   }

   private static float breakingDelta(MinecraftClient mc, int slot, BlockState state, BlockPos pos) {
      float hardness = state.getHardness(mc.world, pos);
      if (hardness == -1.0F) {
         return 0.0F;
      }

      ClientPlayerEntity player = mc.player;
      ItemStack stack = player.getInventory().getStack(slot);
      float speed = stack.getMiningSpeedMultiplier(state);
      if (speed > 1.0F) {
         double[] own = new double[]{0.0};
         stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute == EntityAttributes.MINING_EFFICIENCY) {
               own[0] += modifier.value();
            }
         });
         speed += (float)own[0];
      }

      boolean correct = !state.isToolRequired() || stack.isSuitableFor(state);
      return withMiningModifiers(player, speed) / hardness / (correct ? 30 : 100);
   }

   private static float withMiningModifiers(ClientPlayerEntity player, float speed) {
      if (StatusEffectUtil.hasHaste(player)) {
         speed *= 1.0F + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2F;
      }

      StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
      if (fatigue != null) {
         speed *= switch (fatigue.getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      speed *= (float)player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
      if (player.isSubmergedIn(FluidTags.WATER)) {
         speed *= (float)player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
      }

      if (!player.isOnGround()) {
         speed /= 5.0F;
      }

      return speed;
   }

   private void select(int slot) {
      PlayerInventory inventory = MinecraftClient.getInstance().player.getInventory();
      if (this.origin == -1) {
         this.origin = inventory.getSelectedSlot();
      }

      inventory.setSelectedSlot(slot);
      this.ourSlot = slot;
      this.returnDelay = this.switchDelay();
   }

   private int switchDelay() {
      return this.delayed.get() ? 2 : 0;
   }

   private void giveBack() {
      if (this.origin != -1) {
         if (this.returnDelay > 0) {
            this.returnDelay--;
         } else {
            this.select(this.origin);
            this.origin = -1;
         }
      }
   }
}
