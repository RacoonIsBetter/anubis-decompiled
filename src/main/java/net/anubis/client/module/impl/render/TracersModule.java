package net.anubis.client.module.impl.render;

import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.anubis.client.config.FriendStore;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.gui.GuiPalette;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.MobilePerformance;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TextColor;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class TracersModule extends ClientModule implements HudOverlayProvider {
   private static final double DISTANCE_COLOR_RANGE = 60.0;
   private static final float ARROW_TIP = 0.73205F;
   private static final int WHITE = 16777215;
   private final Vector4f mobileClip = new Vector4f();
   private ArrayList<Entity> mobileTargets;
   private final BooleanSetting players = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting animals = this.addSetting("animals", new BooleanSetting("Animals", false));
   private final BooleanSetting waterAnimals = this.addSetting("water-animals", new BooleanSetting("Water Animals", false));
   private final BooleanSetting monsters = this.addSetting("monsters", new BooleanSetting("Monsters", false));
   private final BooleanSetting ambient = this.addSetting("ambient", new BooleanSetting("Ambient", false));
   private final BooleanSetting misc = this.addSetting("misc", new BooleanSetting("Misc", false));
   private final BooleanSetting ignoreSelf = this.addSetting("ignore-self", new BooleanSetting("Ignore Self", false));
   private final BooleanSetting ignoreFriends = this.addSetting("ignore-friends", new BooleanSetting("Ignore Friends", false));
   private final BooleanSetting showInvisible = this.addSetting("show-invisible", new BooleanSetting("Show Invisible", true));
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Lines", "Lines", "Offscreen"));
   private final ModeSetting target = this.addSetting("target", new ModeSetting("Target", "Body", "Head", "Body", "Feet"));
   private final BooleanSetting stem = this.addSetting("stem", new BooleanSetting("Stem", true));
   private final NumberSetting maxDistance = this.addSetting("max-distance", new NumberSetting("Max Distance", 256.0F, 0.0F, 256.0F).step(1.0F));
   private final NumberSetting distanceOffscreen = this.addSetting(
      "distance-offscreen", new NumberSetting("Distance Offscreen", 200.0F, 0.0F, 500.0F).step(1.0F)
   );
   private final NumberSetting sizeOffscreen = this.addSetting("size-offscreen", new NumberSetting("Size Offscreen", 10.0F, 2.0F, 50.0F).step(1.0F));
   private final BooleanSetting blinkOffscreen = this.addSetting("blink-offscreen", new BooleanSetting("Blink Offscreen", true));
   private final NumberSetting blinkOffscreenSpeed = this.addSetting(
      "blink-offscreen-speed", new NumberSetting("Blink Offscreen Speed", 4.0F, 1.0F, 15.0F).step(1.0F)
   );
   private final BooleanSetting distanceColors = this.addSetting("distance-colors", new BooleanSetting("Distance Colors", false));
   private final BooleanSetting friendOverride = this.addSetting("show-friend-colors", new BooleanSetting("Show Friend Colors", true));
   private final BooleanSetting teamColors = this.addSetting("team-colors", new BooleanSetting("Team Colors", true));
   private final ColorSetting friendColor = this.addSetting("friend-color", new ColorSetting("Friend Color", -16711756));
   private final ColorSetting playersColor = this.addSetting("players-colors", new ColorSetting("Players Color", 2144193997));
   private final ColorSetting animalsColor = this.addSetting("animals-color", new ColorSetting("Animals Color", 2140274577));
   private final ColorSetting waterAnimalsColor = this.addSetting("water-animals-color", new ColorSetting("Water Animals Color", 2140246527));
   private final ColorSetting monstersColor = this.addSetting("monsters-color", new ColorSetting("Monsters Color", 2147455377));
   private final ColorSetting ambientColor = this.addSetting("ambient-color", new ColorSetting("Ambient Color", 2135640907));
   private final ColorSetting miscColor = this.addSetting("misc-color", new ColorSetting("Misc Color", 2140246417));

   public TracersModule() {
      super("tracers", "Tracers", "Displays tracer lines to specified entities.", ModuleCategory.RENDER);
      this.target.visibleWhen(() -> this.style.is("Lines"));
      this.stem.visibleWhen(() -> this.style.is("Lines"));
      this.distanceOffscreen.visibleWhen(() -> this.style.is("Offscreen"));
      this.sizeOffscreen.visibleWhen(() -> this.style.is("Offscreen"));
      this.blinkOffscreen.visibleWhen(() -> this.style.is("Offscreen"));
      this.blinkOffscreenSpeed.visibleWhen(() -> this.style.is("Offscreen") && this.blinkOffscreen.get());
      this.friendOverride.visibleWhen(() -> this.distanceColors.get() && !this.ignoreFriends.get());
      this.teamColors.visibleWhen(() -> !this.distanceColors.get() && this.players.get());
      this.friendColor.visibleWhen(() -> !this.ignoreFriends.get() && this.players.get() && (!this.distanceColors.get() || this.friendOverride.get()));
      this.playersColor.visibleWhen(() -> !this.distanceColors.get() && this.players.get());
      this.animalsColor.visibleWhen(() -> !this.distanceColors.get() && this.animals.get());
      this.waterAnimalsColor.visibleWhen(() -> !this.distanceColors.get() && this.waterAnimals.get());
      this.monstersColor.visibleWhen(() -> !this.distanceColors.get() && this.monsters.get());
      this.ambientColor.visibleWhen(() -> !this.distanceColors.get() && this.ambient.get());
      this.miscColor.visibleWhen(() -> !this.distanceColors.get() && this.misc.get());
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.options.hudHidden && this.style.is("Lines")) {
         boolean mobile = MobilePerformance.active();

         try {
            for (Entity entity : this.targets(mc)) {
               int color = this.colorOf(mc, entity);
               Vec3d feet = entity.getLerpedPos(event.partialTick);
               double height = entity.getBoundingBox().getLengthY();

               double up = switch (this.target.get()) {
                  case "Head" -> height;
                  case "Body" -> height / 2.0;
                  default -> 0.0;
               };
               if (mobile) {
                  TracerEngine.draw(feet.x, feet.y + up, feet.z, color);
               } else {
                  TracerEngine.draw(feet.add(0.0, up, 0.0), color);
               }

               if (this.stem.get()) {
                  TracerEngine.stem(feet, height, color);
               }
            }
         } finally {
            this.clearTargets();
         }
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.style.is("Offscreen") && !MinecraftClient.getInstance().options.hudHidden;
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      MinecraftClient mc = MinecraftClient.getInstance();
      float partialTick = mc.getRenderTickCounter().getTickProgress(false);
      float blink = this.blinkOffscreen.get() ? this.blinkAlpha() : 1.0F;
      float reach = this.distanceOffscreen.get();
      float size = this.sizeOffscreen.get();
      boolean mobile = MobilePerformance.active();

      try {
         for (Entity entity : this.targets(mc)) {
            Vector4f clip = mobile ? TracerEngine.clip(entity.getLerpedPos(partialTick), this.mobileClip) : TracerEngine.clip(entity.getLerpedPos(partialTick));
            if (clip == null) {
               return;
            }

            if (!(clip.w > 0.0F) || !(Math.abs(clip.x) < clip.w) || !(Math.abs(clip.y) < clip.w)) {
               double angle = Math.atan2(-clip.y * screenH, clip.x * screenW);
               int color = this.colorOf(mc, entity);
               int alpha = (int)((color >>> 24) * blink);
               arrow(
                  dl,
                  screenW * 0.5F + reach * (float)Math.cos(angle),
                  screenH * 0.5F + reach * (float)Math.sin(angle),
                  size,
                  (float)angle,
                  GuiPalette.abgr(alpha << 24 | color & 16777215)
               );
            }
         }
      } finally {
         this.clearTargets();
      }
   }

   @Override
   protected void onDisable() {
      this.clearTargets();
   }

   private void clearTargets() {
      if (this.mobileTargets != null) {
         this.mobileTargets.clear();
      }
   }

   private List<Entity> targets(MinecraftClient mc) {
      if (mc.world != null && mc.player != null) {
         FreecamModule freecam = ModuleRegistry.get(FreecamModule.class);
         boolean skipSelf = this.ignoreSelf.get() || freecam == null || !freecam.isEnabled();
         double maxSq = (double)this.maxDistance.get() * this.maxDistance.get();
         Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
         double view = ((Integer)mc.options.getViewDistance().getValue() + 1) * 16.0;
         List<Entity> result;
         if (MobilePerformance.active()) {
            if (this.mobileTargets == null) {
               this.mobileTargets = new ArrayList<>();
            }

            this.mobileTargets.clear();
            result = this.mobileTargets;
         } else {
            result = new ArrayList<>();
         }

         for (Entity entity : mc.world.getEntities()) {
            if ((entity != mc.player || !skipSelf)
               && this.groupColor(entity) != null
               && !(entity.squaredDistanceTo(mc.player) > maxSq)
               && (!this.ignoreFriends.get() || !isFriend(entity))
               && (this.showInvisible.get() || !entity.isInvisible())
               && !(Math.abs(camera.x - entity.getX()) >= view)
               && !(Math.abs(camera.z - entity.getZ()) >= view)) {
               result.add(entity);
            }
         }

         return result;
      } else {
         return List.of();
      }
   }

   private ColorSetting groupColor(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return this.players.get() ? this.playersColor : null;
      }

      return switch (entity.getType().getSpawnGroup()) {
         case CREATURE -> this.animals.get() ? this.animalsColor : null;
         case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> this.waterAnimals.get() ? this.waterAnimalsColor : null;
         case MONSTER -> this.monsters.get() ? this.monstersColor : null;
         case AMBIENT -> this.ambient.get() ? this.ambientColor : null;
         default -> this.misc.get() ? this.miscColor : null;
      };
   }

   private static boolean isFriend(Entity entity) {
      return entity instanceof PlayerEntity player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
   }

   private int colorOf(MinecraftClient mc, Entity entity) {
      boolean friend = isFriend(entity);
      if (!this.distanceColors.get()) {
         if (entity instanceof PlayerEntity player) {
            int base = this.playersColor.argb();
            int alpha = base & 0xFF000000;
            if (friend) {
               return alpha | this.friendColor.rgb();
            }

            int team = this.teamColors.get() ? teamColor(player) : 16777215;
            return team == 16777215 ? base : alpha | team;
         } else {
            return this.groupColor(entity).argb();
         }
      } else {
         return friend && this.friendOverride.get() ? this.friendColor.argb() : distanceColor(mc, entity);
      }
   }

   private static int distanceColor(MinecraftClient mc, Entity entity) {
      double percent = mc.gameRenderer.getCamera().getCameraPos().distanceTo(entity.getEyePos()) / 60.0;
      if (percent > 1.0) {
         return -16711936;
      }

      int r = percent < 0.5 ? 255 : 255 - (int)(255.0 * (percent - 0.5) / 0.5);
      int g = percent < 0.5 ? (int)(255.0 * percent / 0.5) : 255;
      return 0xFF000000 | r << 16 | g << 8;
   }

   private static int teamColor(PlayerEntity player) {
      Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
      player.getDisplayName().visit((segment, text) -> {
         TextColor color = segment.getColor();
         counts.addTo(color == null ? 16777215 : color.getRgb(), text.length());
         return Optional.empty();
      }, Style.EMPTY);
      int best = 16777215;
      int bestCount = 0;
      ObjectIterator var4 = counts.int2IntEntrySet().iterator();

      while (var4.hasNext()) {
         Entry entry = (Entry)var4.next();
         if (entry.getIntValue() > bestCount) {
            best = entry.getIntKey();
            bestCount = entry.getIntValue();
         }
      }

      return best;
   }

   private float blinkAlpha() {
      double time = Util.getMeasuringTimeMs() * (this.blinkOffscreenSpeed.get() / 4.0);
      return (float)Math.abs(time % 1000.0 - 500.0) / 500.0F;
   }

   private static void arrow(ImDrawList dl, float x, float y, float size, float angle, int abgr) {
      float cos = (float)Math.cos(angle);
      float sin = (float)Math.sin(angle);
      float cx = x - size * 1.26795F / 3.0F;
      if (MobilePerformance.active()) {
         float dx0 = x - size - cx;
         float dy0 = y - size - y;
         float dx1 = x + size * 0.73205F - cx;
         float dy1 = y - y;
         float dx2 = x - size - cx;
         float dy2 = y + size - y;
         dl.addTriangleFilled(
            cx + dx0 * cos - dy0 * sin,
            y + dx0 * sin + dy0 * cos,
            cx + dx1 * cos - dy1 * sin,
            y + dx1 * sin + dy1 * cos,
            cx + dx2 * cos - dy2 * sin,
            y + dx2 * sin + dy2 * cos,
            abgr
         );
      } else {
         float[] px = new float[]{x - size, x + size * 0.73205F, x - size};
         float[] py = new float[]{y - size, y, y + size};

         for (int i = 0; i < 3; i++) {
            float dx = px[i] - cx;
            float dy = py[i] - y;
            px[i] = cx + dx * cos - dy * sin;
            py[i] = y + dx * sin + dy * cos;
         }

         dl.addTriangleFilled(px[0], py[0], px[1], py[1], px[2], py[2], abgr);
      }
   }
}
