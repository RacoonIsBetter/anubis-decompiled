package net.anubis.client.module.impl.combat.crystal;

import com.example.client.mixin.AccessorLocalPlayer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.impl.combat.melee.MeleeAttacks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.enchantment.effect.EnchantmentValueEffect;
import net.minecraft.enchantment.effect.value.AddEnchantmentEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult.Success;
import net.minecraft.util.ActionResult.SwingSource;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class Crystals {
   public static final int READY = -2;
   public static final int NO_ROUTE = -1;
   private static final int MIN_CONFIRM_TICKS = 4;
   private static final int MAX_CONFIRM_TICKS = 20;
   private static final float RTT_SMOOTHING = 0.25F;
   private static final double RESERVE_RANGE = 12.0;
   private static final Set<Block> USABLE_BLOCKS = Set.of(
      Blocks.RESPAWN_ANCHOR, Blocks.NOTE_BLOCK, Blocks.LEVER, Blocks.REPEATER, Blocks.REDSTONE_WIRE, Blocks.CAKE, Blocks.COMPOSTER, Blocks.DRAGON_EGG
   );
   private static final Long2IntOpenHashMap PLACED_AT = new Long2IntOpenHashMap();
   private static float measuredRtt;

   private Crystals() {
   }

   public static boolean isBase(BlockState state) {
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   public static Vec3d explosionAt(BlockPos base) {
      return Vec3d.ofBottomCenter(base.up());
   }

   public static Box boxOn(BlockPos base) {
      return EntityType.END_CRYSTAL.getDimensions().getBoxAt(explosionAt(base));
   }

   public static boolean hasCrystal(World level, BlockPos base, Entity ignore) {
      BlockPos above = base.up();

      for (EndCrystalEntity crystal : level.getNonSpectatingEntities(EndCrystalEntity.class, new Box(above))) {
         if (crystal != ignore && crystal.getBlockPos().equals(above)) {
            return true;
         }
      }

      return false;
   }

   public static boolean canHost(World level, BlockPos base, Entity ignore) {
      BlockPos above = base.up();
      if (!level.isAir(above)) {
         return false;
      }

      Box space = new Box(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);

      for (Entity entity : level.getOtherEntities(ignore, space)) {
         if (entity.isAlive()) {
            return false;
         }
      }

      return true;
   }

   public static BlockPos obsidianTarget(ClientWorld level, ClientPlayerEntity player, BlockHitResult hit, BlockState clicked) {
      BlockPos target = placedObsidian(level, player, hit, clicked);
      return target != null && canHost(level, target, null) ? target : null;
   }

   public static BlockPos placedObsidian(ClientWorld level, ClientPlayerEntity player, BlockHitResult hit, BlockState clicked) {
      if (!player.shouldCancelInteraction() && reactsToUse(level, hit.getBlockPos(), clicked)) {
         return null;
      } else {
         ItemPlacementContext context = new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(Items.OBSIDIAN), hit);
         if (!context.canPlace()) {
            return null;
         } else {
            BlockPos target = context.getBlockPos();
            if (level.isOutOfHeightLimit(target) || !level.getWorldBorder().contains(target)) {
               return null;
            } else {
               return !level.canPlace(Blocks.OBSIDIAN.getDefaultState(), target, ShapeContext.ofPlacement(player)) ? null : target;
            }
         }
      }
   }

   private static boolean reactsToUse(World level, BlockPos pos, BlockState state) {
      Block block = state.getBlock();
      if (block instanceof BlockEntityProvider || USABLE_BLOCKS.contains(block)) {
         return true;
      } else {
         return !state.isIn(BlockTags.DOORS)
               && !state.isIn(BlockTags.TRAPDOORS)
               && !state.isIn(BlockTags.FENCE_GATES)
               && !state.isIn(BlockTags.BUTTONS)
               && !state.isIn(BlockTags.CAULDRONS)
               && !state.isIn(BlockTags.CANDLE_CAKES)
               && !state.isIn(BlockTags.FLOWER_POTS)
            ? state.createScreenHandlerFactory(level, pos) != null
            : true;
      }
   }

   public static int route(ClientPlayerEntity player, Item item, BlockState clicked, int preferredSlot) {
      ItemStack main = player.getMainHandStack();
      if (main.isOf(item)) {
         return -2;
      }

      boolean inOffhand = player.getOffHandStack().isOf(item);
      if (inOffhand && letsClickThrough(main, clicked)) {
         return -2;
      }

      PlayerInventory inventory = player.getInventory();
      int slot = hotbarSlotOf(inventory, item);
      if (slot != -1) {
         return slot;
      }

      if (!inOffhand) {
         return -1;
      }

      if (PlayerInventory.isValidHotbarIndex(preferredSlot) && letsClickThrough(inventory.getStack(preferredSlot), clicked)) {
         return preferredSlot;
      }

      for (int var8 = 0; var8 < PlayerInventory.getHotbarSize(); var8++) {
         if (letsClickThrough(inventory.getStack(var8), clicked)) {
            return var8;
         }
      }

      return -1;
   }

   public static boolean letsClickThrough(ItemStack stack, BlockState clicked) {
      if (stack.isEmpty()) {
         return true;
      } else if (!stack.contains(DataComponentTypes.CONSUMABLE)
         && !stack.contains(DataComponentTypes.BLOCKS_ATTACKS)
         && !stack.contains(DataComponentTypes.KINETIC_WEAPON)) {
         EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
         return equippable != null && equippable.swappable()
            ? false
            : stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE) || stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isIn(ItemTags.AXES) && isBase(clicked);
      } else {
         return false;
      }
   }

   public static boolean isCombatItem(ItemStack stack) {
      return stack.isEmpty() || stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.MACE) || stack.isOf(Items.TOTEM_OF_UNDYING);
   }

   public static int hotbarSlotOf(PlayerInventory inventory, Item item) {
      for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
         if (inventory.getStack(slot).isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   public static boolean strike(MinecraftClient mc, ClientPlayerEntity player, Entity target) {
      if (player != null && mc.interactionManager != null && target != null && !target.isRemoved() && !player.isSpectator()) {
         if (!MeleeAttacks.actedThisTick(player)
            && MeleeAttacks.handsFree(mc, player)
            && player.isSprinting() == ((AccessorLocalPlayer)player).anubis$wasSprinting()) {
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean rightClick(MinecraftClient mc, ClientPlayerEntity player, BlockHitResult hit) {
      if (!MeleeAttacks.actedThisTick(player) && MeleeAttacks.handsFree(mc, player)) {
         Hand hand = letsClickThrough(player.getMainHandStack(), mc.world.getBlockState(hit.getBlockPos())) ? Hand.OFF_HAND : Hand.MAIN_HAND;
         ItemStack stack = player.getStackInHand(hand);
         if (!stack.isItemEnabled(mc.world.getEnabledFeatures())) {
            return false;
         }

         int countBefore = stack.getCount();
         if (mc.interactionManager.interactBlock(player, hand, hit) instanceof Success success && success.swingSource() == SwingSource.CLIENT) {
            player.swingHand(hand);
            if (!stack.isEmpty() && (stack.getCount() != countBefore || player.isInCreativeMode())) {
               mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static int weaponRoute(ClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      if (!swings(player, main) || !enchantsAddDamage(main) && !dealsDamage(player, main)) {
         PlayerInventory inventory = player.getInventory();
         int fallback = -1;

         for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (swings(player, stack)) {
               if (enchantsAddDamage(stack)) {
                  return slot;
               }

               if (fallback == -1 && dealsDamage(player, stack)) {
                  fallback = slot;
               }
            }
         }

         return fallback;
      } else {
         return -2;
      }
   }

   private static boolean swings(ClientPlayerEntity player, ItemStack stack) {
      return !stack.contains(DataComponentTypes.PIERCING_WEAPON) && !player.isBelowMinimumAttackCharge(stack, 0);
   }

   public static boolean enchantsAddDamage(ItemStack weapon) {
      float bonus = 0.0F;

      for (Entry<RegistryEntry<Enchantment>> entry : weapon.getEnchantments().getEnchantmentEntries()) {
         Enchantment enchantment = (Enchantment)((RegistryEntry)entry.getKey()).value();

         for (EnchantmentEffectEntry<EnchantmentValueEffect> effect : enchantment.getEffect(EnchantmentEffectComponentTypes.DAMAGE)) {
            if (effect.requirements().isPresent() || !(effect.effect() instanceof AddEnchantmentEffect add)) {
               return false;
            }

            bonus += add.value().getValue(entry.getIntValue());
         }
      }

      return bonus > 0.0F;
   }

   public static boolean dealsDamage(LivingEntity player, ItemStack mainHand) {
      EntityAttributeInstance damage = new EntityAttributeInstance(EntityAttributes.ATTACK_DAMAGE, changed -> {});
      damage.setBaseValue(player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE));

      for (StatusEffectInstance effect : player.getStatusEffects()) {
         effect.getEffectType().value().forEachAttributeModifier(effect.getAmplifier(), (attribute, modifier) -> include(damage, attribute, modifier));
      }

      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         ItemStack stack = slot == EquipmentSlot.MAINHAND ? mainHand : player.getEquippedStack(slot);
         if (!stack.isEmpty() && !stack.shouldBreak()) {
            stack.applyAttributeModifiers(slot, (attribute, modifier) -> include(damage, attribute, modifier));
         }
      }

      return (float)damage.getValue() > 0.0F;
   }

   private static void include(EntityAttributeInstance damage, RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) {
      if (EntityAttributes.ATTACK_DAMAGE.equals(attribute)) {
         damage.removeModifier(modifier.id());
         damage.addTemporaryModifier(modifier);
      }
   }

   public static int confirmWindow(MinecraftClient mc, ClientPlayerEntity player) {
      return MathHelper.clamp(rttTicks(mc, player) + 2, 4, 20);
   }

   public static int rttTicks(MinecraftClient mc, ClientPlayerEntity player) {
      return measuredRtt > 0.0F ? Math.round(measuredRtt) : latencyMs(mc, player) / 50 + 1;
   }

   public static void notePlace(ClientPlayerEntity player, BlockPos base) {
      ObjectIterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> iterator = PLACED_AT.long2IntEntrySet().iterator();

      while (iterator.hasNext()) {
         int age = player.age - ((it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)iterator.next()).getIntValue();
         if (age < 0 || age > 20) {
            iterator.remove();
         }
      }

      PLACED_AT.put(base.asLong(), player.age);
   }

   public static void noteSpawn(ClientPlayerEntity player, BlockPos base) {
      long key = base.asLong();
      if (PLACED_AT.containsKey(key)) {
         int ticks = player.age - PLACED_AT.remove(key);
         if (ticks > 0 && ticks <= 20) {
            measuredRtt = measuredRtt > 0.0F ? measuredRtt + (ticks - measuredRtt) * 0.25F : ticks;
         }
      }
   }

   public static boolean placing(ClientPlayerEntity player, BlockPos base, int window) {
      long key = base.asLong();
      return PLACED_AT.containsKey(key) && sent(player, PLACED_AT.get(key), window);
   }

   public static boolean anyPlacing(ClientPlayerEntity player, int window) {
      IntIterator var2 = PLACED_AT.values().iterator();

      while (var2.hasNext()) {
         int tick = (Integer)var2.next();
         if (sent(player, tick, window)) {
            return true;
         }
      }

      return false;
   }

   public static List<Box> reservedBoxes(ClientPlayerEntity player, int window) {
      List<Box> boxes = new ArrayList<>();

      for (EndCrystalEntity crystal : player.getEntityWorld().getNonSpectatingEntities(EndCrystalEntity.class, player.getBoundingBox().expand(12.0))) {
         if (crystal.isAlive()) {
            boxes.add(crystal.getBoundingBox());
         }
      }

      ObjectIterator var5 = PLACED_AT.long2IntEntrySet().iterator();

      while (var5.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var5.next();
         if (sent(player, entry.getIntValue(), window)) {
            boxes.add(boxOn(BlockPos.fromLong(entry.getLongKey())));
         }
      }

      return boxes;
   }

   private static boolean sent(ClientPlayerEntity player, int tick, int window) {
      int age = player.age - tick;
      return age >= 0 && age < window;
   }

   public static int humanGap() {
      double roll = ThreadLocalRandom.current().nextDouble();
      return roll < 0.7 ? 2 : (roll < 0.95 ? 3 : 4);
   }

   public static void forgetRtt() {
      PLACED_AT.clear();
      measuredRtt = 0.0F;
   }

   private static int latencyMs(MinecraftClient mc, ClientPlayerEntity player) {
      ClientPlayNetworkHandler connection = mc.getNetworkHandler();
      if (connection == null) {
         return 0;
      }

      PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
      return info == null ? 0 : Math.max(info.getLatency(), 0);
   }

   public static boolean isKeyDown(MinecraftClient mc, int key) {
      if (!ModuleKeybinds.isBindable(key)) {
         return false;
      } else {
         return key >= 1000 ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), key - 1000) == 1 : AnubisKeyPolling.isKeyDown(mc.getWindow(), key);
      }
   }

   public static boolean isPhysicallyDown(MinecraftClient mc, KeyBinding mapping) {
      Key key;
      try {
         key = InputUtil.fromTranslationKey(mapping.getBoundKeyTranslationKey());
      } catch (IllegalArgumentException unknown) {
         return false;
      }

      int value = key.getCode();
      if (value < 0) {
         return false;
      }

      return switch (key.getCategory()) {
         case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), value) == 1;
         case KEYSYM -> AnubisKeyPolling.isKeyDown(mc.getWindow(), value);
         default -> false;
      };
   }
}
