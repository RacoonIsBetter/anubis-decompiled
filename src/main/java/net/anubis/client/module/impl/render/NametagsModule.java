package net.anubis.client.module.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.MobilePerformance;
import net.anubis.client.util.NameFilter;
import net.anubis.client.util.StableTopK;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.joml.Matrix3x2fStack;

@Environment(EnvType.CLIENT)
public class NametagsModule extends ClientModule {
   private static final int PANEL_RGB = 1447965;
   private static final int BORDER = 402653183;
   private static final int SHADOW_NEAR = 771751936;
   private static final int SHADOW_FAR = 385875968;
   private static final int DIVIDER = 536870911;
   private static final int EMPTY_SLOT = 452984831;
   private static final int TEXT_NAME = -1447442;
   private static final int TEXT_LABEL = -6052949;
   private static final int TEXT_VALUE = -657929;
   private static final int STATUS_GOOD = -13577896;
   private static final int STATUS_WARN = -10742;
   private static final int STATUS_BAD = -47814;
   private static final int ITEM_COUNT_COLOR = -10742;
   private static final float TEXT_GUARD_BELOW = 0.55F;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final int PADDING = 4;
   private static final int ROW_GAP = 3;
   private static final int ICON_SIZE = 10;
   private static final int ICON_GAP = 2;
   private static final int TEXT_GAP = 4;
   private static final int SECTION_GAP = 3;
   private static final float ICON_SCALE = 0.625F;
   private static final int TAG_CAP = 64;
   private static final double FULL_SIZE_DISTANCE = 8.0;
   private static final Comparator<NametagsModule.Nearby> NEAREST = Comparator.comparingDouble(NametagsModule.Nearby::distanceSq);
   private final StableTopK<NametagsModule.Nearby> mobilePlayers = new StableTopK<>(NEAREST);
   private final StableTopK<NametagsModule.Nearby> mobileItems = new StableTopK<>(NEAREST);
   private final BooleanSetting sPing = this.addSetting("ping", new BooleanSetting("Ping", true));
   private final BooleanSetting sHeldItems = this.addSetting("held-items", new BooleanSetting("Held Items", true));
   private final BooleanSetting sArmor = this.addSetting("armor", new BooleanSetting("Armor", true));
   private final BooleanSetting sDurability = this.addSetting("durability", new BooleanSetting("Durability", true));
   private final BooleanSetting sHealth = this.addSetting("health", new BooleanSetting("Health", true));
   private final BooleanSetting sHideVanilla = this.addSetting("hide-vanilla", new BooleanSetting("Hide Vanilla", true));
   private final BooleanSetting sItems = this.addSetting("items", new BooleanSetting("Dropped Items", true));
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 128.0F, 8.0F, 512.0F).step(1.0F));
   private final NumberSetting sMaxTags = this.addSetting("max-tags", new NumberSetting("Max Tags", 64.0F, 1.0F, 64.0F).step(1.0F));
   private final NumberSetting sScale = this.addSetting("scale", new NumberSetting("Scale", 1.0F, 0.5F, 2.0F).step(0.1F));
   private final NumberSetting sMinScale = this.addSetting("min-scale", new NumberSetting("Min Scale", 0.55F, 0.3F, 1.0F).step(0.05F));
   private final NumberSetting sBackgroundAlpha = this.addSetting("background-alpha", new NumberSetting("Background Alpha", 0.45F, 0.0F, 1.0F).step(0.05F));

   public NametagsModule() {
      super("nametags", "Nametags", "Player nametags with held items, armour, health, ping and distance.", ModuleCategory.RENDER);
      this.sDurability.visibleWhen(() -> this.sHeldItems.get() || this.sArmor.get());
   }

   public static boolean hideVanillaNametags() {
      NametagsModule module = ModuleRegistry.get(NametagsModule.class);
      return module != null && module.isEnabled() && module.sHideVanilla.get();
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null && !mc.options.hudHidden) {
         Camera camera = mc.gameRenderer.getCamera();
         if (camera != null && camera.isReady()) {
            float partialTick = event.partialTick;
            Vec3d eye = mc.player.getCameraPosVec(partialTick);
            double range = Math.max(1.0F, this.sRange.get());
            double rangeSq = range * range;
            int budget = Math.max(1, Math.min(64, this.sMaxTags.getInt()));
            boolean mobile = MobilePerformance.active();

            try {
               if (!mobile) {
                  this.clearMobileTargets();
               }

               if (mobile) {
                  this.mobilePlayers.reset(budget);
               }

               List<NametagsModule.Nearby> players = mobile ? null : new ArrayList<>();

               for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                  if (player != mc.player && !player.isRemoved() && !player.isDead()) {
                     double distanceSq = player.getLerpedPos(partialTick).squaredDistanceTo(eye);
                     if (distanceSq <= rangeSq) {
                        if (mobile) {
                           offerNearest(this.mobilePlayers, player, distanceSq);
                        } else {
                           players.add(new NametagsModule.Nearby(player, distanceSq));
                        }
                     }
                  }
               }

               players = nearest(mobile ? this.mobilePlayers.result() : players, budget);
               int itemBudget = budget - players.size();
               if (mobile) {
                  this.mobileItems.reset(itemBudget);
               }

               List<NametagsModule.Nearby> items = mobile ? this.mobileItems.result() : new ArrayList<>();
               if (this.sItems.get() && itemBudget > 0) {
                  for (Entity entity : mc.world.getEntities()) {
                     if (entity instanceof ItemEntity item && !item.isRemoved() && !item.getStack().isEmpty()) {
                        double distanceSq = item.getLerpedPos(partialTick).squaredDistanceTo(eye);
                        if (distanceSq <= rangeSq) {
                           if (mobile) {
                              offerNearest(this.mobileItems, item, distanceSq);
                           } else {
                              items.add(new NametagsModule.Nearby(item, distanceSq));
                           }
                        }
                     }
                  }

                  items = nearest(mobile ? this.mobileItems.result() : items, itemBudget);
               }

               for (int i = items.size() - 1; i >= 0; i--) {
                  this.renderItemTag(event.graphics, mc, camera, (ItemEntity)items.get(i).entity(), partialTick);
               }

               for (int i = players.size() - 1; i >= 0; i--) {
                  this.renderTag(event.graphics, mc, camera, (AbstractClientPlayerEntity)players.get(i).entity(), partialTick);
               }
            } finally {
               if (mobile) {
                  this.clearMobileTargets();
               }
            }
         } else {
            this.clearMobileTargets();
         }
      } else {
         this.clearMobileTargets();
      }
   }

   @Override
   protected void onDisable() {
      this.clearMobileTargets();
   }

   private void clearMobileTargets() {
      this.mobilePlayers.reset(0);
      this.mobileItems.reset(0);
   }

   private static void offerNearest(StableTopK<NametagsModule.Nearby> nearest, Entity entity, double distanceSq) {
      NametagsModule.Nearby worst = nearest.worst();
      if (worst == null || Double.compare(distanceSq, worst.distanceSq()) < 0) {
         nearest.offer(new NametagsModule.Nearby(entity, distanceSq));
      }
   }

   private static List<NametagsModule.Nearby> nearest(List<NametagsModule.Nearby> candidates, int limit) {
      candidates.sort(NEAREST);
      return candidates.size() <= limit ? candidates : candidates.subList(0, limit);
   }

   private void renderTag(DrawContext graphics, MinecraftClient mc, Camera camera, AbstractClientPlayerEntity player, float partialTick) {
      Vec3d anchor = player.getLerpedPos(partialTick).add(0.0, player.getHeight() + 0.15, 0.0);
      float[] screen = WorldLabel.project(mc, anchor, graphics.getScaledWindowWidth(), graphics.getScaledWindowHeight());
      if (screen != null) {
         TextRenderer font = mc.textRenderer;
         String name = NameFilter.apply(player.getGameProfile().name());
         String pingText = null;
         int pingColor = -6052949;
         if (this.sPing.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (info != null) {
               int latency = info.getLatency();
               pingText = latency + "ms";
               pingColor = latency < 60 ? -13577896 : (latency < 130 ? -10742 : -47814);
            }
         }

         List<ItemStack> hands = this.sHeldItems.get() ? heldItems(player) : List.of();
         List<ItemStack> armor = this.sArmor.get() ? wornArmor(player) : List.of();
         String healthText = null;
         int healthColor = -13577896;
         float health = player.getHealth() + player.getAbsorptionAmount();
         if (this.sHealth.get() && Float.isFinite(health)) {
            float fraction = Math.min(1.0F, health / Math.max(1.0F, player.getMaxHealth()));
            healthText = String.valueOf((int)Math.ceil(health));
            healthColor = fraction > 0.6F ? -13577896 : (fraction > 0.3F ? -10742 : -47814);
         }

         double dist = anchor.distanceTo(camera.getCameraPos());
         String distText = Math.round((float)dist) + "m";
         int handsW = iconsWidth(hands.size());
         int armorW = iconsWidth(armor.size());
         int healthW = healthText != null ? font.getWidth(healthText) : 0;
         int distW = font.getWidth(distText);
         int row1W = font.getWidth(name) + (pingText != null ? 5 + font.getWidth(pingText) : 0);
         int row2W = extend(0, handsW, 0);
         row2W = extend(row2W, armorW, 7);
         row2W = extend(row2W, healthW, 4);
         row2W = extend(row2W, distW, 4);
         int contentW = Math.max(row1W, row2W);
         int panelW = contentW + 8;
         int panelH = 8 + 9 + 3 + 10;
         float baseScale = Math.max(0.5F, Math.min(2.0F, this.sScale.get()));
         double minScale = Math.max(0.3F, Math.min(1.0F, this.sMinScale.get()));
         float distScale = (float)Math.min(1.0, Math.max(minScale, 8.0 / Math.max(1.0, dist)));
         float scale = baseScale * distScale;
         float surface = Math.max(0.0F, Math.min(1.0F, this.sBackgroundAlpha.get()));
         boolean guard = surface < 0.55F;
         float chrome = Math.min(1.0F, surface * 2.0F);
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(screen[0], screen[1]);
         pose.scale(scale, scale);
         int x = -panelW / 2;
         int y = -panelH - 1;
         drawPanel(graphics, x, y, x + panelW, y + panelH, surface, chrome);
         int left = x + 4;
         int textY = y + 4;
         graphics.drawText(font, name, left, textY, -1447442, guard);
         if (pingText != null) {
            graphics.drawText(font, pingText, left + font.getWidth(name) + 5, textY, pingColor, guard);
         }

         int rowY = y + 4 + 9 + 3;
         int labelY = rowY + (10 - 9) / 2 + 1;
         int cursor = left;
         boolean placed = false;
         if (!hands.isEmpty()) {
            cursor = this.drawIcons(graphics, font, player, hands, 0, cursor, rowY, true, chrome);
            placed = true;
         }

         if (!armor.isEmpty()) {
            if (placed) {
               int lineX = cursor + 3;
               graphics.fill(lineX, rowY + 1, lineX + 1, rowY + 10 - 1, scaleAlpha(536870911, chrome));
               cursor += 7;
            }

            cursor = this.drawIcons(graphics, font, player, armor, hands.size(), cursor, rowY, false, chrome);
            placed = true;
         }

         if (healthText != null) {
            if (placed) {
               cursor += 4;
            }

            graphics.drawText(font, healthText, cursor, labelY, healthColor, guard);
            cursor += healthW;
            placed = true;
         }

         if (placed) {
            cursor += 4;
         }

         graphics.drawText(font, distText, cursor, labelY, -6052949, guard);
         pose.popMatrix();
      }
   }

   private static List<ItemStack> heldItems(AbstractClientPlayerEntity player) {
      ItemStack main = player.getMainHandStack();
      ItemStack off = player.getOffHandStack();
      return main.isEmpty() && off.isEmpty() ? List.of() : List.of(main, off);
   }

   private static List<ItemStack> wornArmor(AbstractClientPlayerEntity player) {
      List<ItemStack> worn = new ArrayList<>(ARMOR_SLOTS.length);

      for (EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack stack = player.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            worn.add(stack);
         }
      }

      return worn;
   }

   private static int iconsWidth(int count) {
      return count == 0 ? 0 : count * 12 - 2;
   }

   private static int extend(int total, int width, int gap) {
      if (width <= 0) {
         return total;
      } else {
         return total == 0 ? width : total + gap + width;
      }
   }

   private int drawIcons(
      DrawContext graphics, TextRenderer font, AbstractClientPlayerEntity holder, List<ItemStack> stacks, int seedOffset, int x, int y, boolean counts, float chrome
   ) {
      for (int i = 0; i < stacks.size(); i++) {
         if (i > 0) {
            x += 2;
         }

         this.drawIcon(graphics, font, holder, stacks.get(i), holder.getId() * 8 + seedOffset + i, x, y, counts, chrome);
         x += 10;
      }

      return x;
   }

   private void drawIcon(DrawContext graphics, TextRenderer font, AbstractClientPlayerEntity holder, ItemStack stack, int seed, int x, int y, boolean count, float chrome) {
      if (stack.isEmpty()) {
         graphics.fill(x + 2, y + 2, x + 10 - 2, y + 10 - 2, scaleAlpha(452984831, chrome));
      } else {
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(x, y);
         pose.scale(0.625F, 0.625F);
         graphics.drawItem(holder, stack, 0, 0, seed);
         if (this.sDurability.get() && stack.isItemBarVisible()) {
            graphics.fill(2, 13, 15, 15, -16777216);
            graphics.fill(2, 13, 2 + stack.getItemBarStep(), 14, 0xFF000000 | stack.getItemBarColor());
         }

         if (count && stack.getCount() > 1) {
            String text = String.valueOf(stack.getCount());
            graphics.drawText(font, text, 17 - font.getWidth(text), 9, -657929, true);
         }

         pose.popMatrix();
      }
   }

   private void renderItemTag(DrawContext graphics, MinecraftClient mc, Camera camera, ItemEntity item, float partialTick) {
      Vec3d anchor = item.getLerpedPos(partialTick).add(0.0, 0.35, 0.0);
      float[] screen = WorldLabel.project(mc, anchor, graphics.getScaledWindowWidth(), graphics.getScaledWindowHeight());
      if (screen != null) {
         ItemStack stack = item.getStack();
         String itemName = stack.getName().getString();
         int count = stack.getCount();
         String countText = count > 1 ? "x" + count : null;
         TextRenderer font = mc.textRenderer;
         int nameW = font.getWidth(itemName);
         int countW = countText != null ? font.getWidth(countText) : 0;
         int totalW = nameW + (countText != null ? 3 + countW : 0);
         double dist = anchor.distanceTo(camera.getCameraPos());
         float scale = (float)Math.min(1.0, Math.max(0.25, 6.0 / Math.max(1.0, dist)));
         Matrix3x2fStack pose = graphics.getMatrices();
         pose.pushMatrix();
         pose.translate(screen[0], screen[1]);
         pose.scale(scale, scale);
         int tx = -totalW / 2;
         int ty = -9 / 2;
         graphics.drawText(font, itemName, tx, ty, -1447442, true);
         if (countText != null) {
            graphics.drawText(font, countText, tx + nameW + 3, ty, -10742, true);
         }

         pose.popMatrix();
      }
   }

   private static void drawPanel(DrawContext g, int x1, int y1, int x2, int y2, float surface, float chrome) {
      drawR4Rect(g, x1 - 1, y1 + 1, x2 + 1, y2 + 2, scaleAlpha(385875968, chrome));
      drawR4Rect(g, x1, y1 + 1, x2, y2 + 1, scaleAlpha(771751936, chrome));
      drawR4Rect(g, x1, y1, x2, y2, Math.round(surface * 255.0F) << 24 | 1447965);
      drawR4Border(g, x1, y1, x2, y2, scaleAlpha(402653183, chrome));
   }

   private static int scaleAlpha(int argb, float factor) {
      int alpha = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
      return alpha << 24 | argb & 16777215;
   }

   private static void drawR4Rect(DrawContext g, int x1, int y1, int x2, int y2, int color) {
      if (color >>> 24 != 0) {
         g.fill(x1 + 4, y1, x2 - 4, y1 + 1, color);
         g.fill(x1 + 2, y1 + 1, x2 - 2, y1 + 2, color);
         g.fill(x1 + 1, y1 + 2, x2 - 1, y1 + 4, color);
         g.fill(x1, y1 + 4, x2, y2 - 4, color);
         g.fill(x1 + 1, y2 - 4, x2 - 1, y2 - 2, color);
         g.fill(x1 + 2, y2 - 2, x2 - 2, y2 - 1, color);
         g.fill(x1 + 4, y2 - 1, x2 - 4, y2, color);
      }
   }

   private static void drawR4Border(DrawContext g, int x1, int y1, int x2, int y2, int color) {
      if (color >>> 24 != 0) {
         g.fill(x1 + 4, y1, x2 - 4, y1 + 1, color);
         g.fill(x1 + 4, y2 - 1, x2 - 4, y2, color);
         g.fill(x1, y1 + 4, x1 + 1, y2 - 4, color);
         g.fill(x2 - 1, y1 + 4, x2, y2 - 4, color);
         g.fill(x1 + 2, y1 + 1, x1 + 4, y1 + 2, color);
         g.fill(x1 + 1, y1 + 2, x1 + 2, y1 + 4, color);
         g.fill(x2 - 4, y1 + 1, x2 - 2, y1 + 2, color);
         g.fill(x2 - 2, y1 + 2, x2 - 1, y1 + 4, color);
         g.fill(x1 + 1, y2 - 4, x1 + 2, y2 - 2, color);
         g.fill(x1 + 2, y2 - 2, x1 + 4, y2 - 1, color);
         g.fill(x2 - 2, y2 - 4, x2 - 1, y2 - 2, color);
         g.fill(x2 - 4, y2 - 2, x2 - 2, y2 - 1, color);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Nearby(Entity entity, double distanceSq) {
   }
}
