package net.anubis.client.module.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.config.FriendStore;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.MobilePerformance;
import net.anubis.client.util.StableTopK;
import net.anubis.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class EspModule extends ClientModule {
   private static EspModule instance;
   private static final int ENTITY_CAP = 96;
   private static final Comparator<EspModule.Target> PRIORITY = Comparator.<EspModule.Target, Boolean>comparing(
         target -> target.group() != EspModule.Group.PLAYER
      )
      .thenComparingDouble(EspModule.Target::distanceSq);
   private final BooleanSetting sPlayers = this.addSetting("players", new BooleanSetting("Players", true));
   private final BooleanSetting sMobs = this.addSetting("mobs", new BooleanSetting("Hostile Mobs", true));
   private final BooleanSetting sAnimals = this.addSetting("animals", new BooleanSetting("Passive Mobs", false));
   private final BooleanSetting sIgnoreSelf = this.addSetting("ignore-self", new BooleanSetting("Ignore Self", true));
   private final BooleanSetting sBox = this.addSetting("box", new BooleanSetting("Box", true));
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sCustomTracerColor = this.addSetting("custom-tracer-color", new BooleanSetting("Custom Tracer Color", false));
   private final ColorSetting sTracerColor = this.addSetting("tracer-color", new ColorSetting("Tracer Color", -1));
   private final ModeSetting sShape = this.addSetting("shape", new ModeSetting("Shape", "Glow", "Glow", "Box", "Fill", "Both"));
   private final NumberSetting sRange = this.addSetting("range", new NumberSetting("Range", 96.0F, 16.0F, 256.0F).step(1.0F));
   private final NumberSetting sMaxEntities = this.addSetting("max-entities", new NumberSetting("Max Entities", 48.0F, 8.0F, 96.0F).step(1.0F));
   private final ColorSetting sPlayerColor = this.addSetting("player-color", new ColorSetting("Player Color", -1));
   private final BooleanSetting sFriends = this.addSetting("friends", new BooleanSetting("Highlight Friends", true));
   private final ColorSetting sFriendColor = this.addSetting("friend-color", new ColorSetting("Friend Color", -10169601));
   private final ColorSetting sMobColor = this.addSetting("mob-color", new ColorSetting("Hostile Color", -47814));
   private final ColorSetting sAnimalColor = this.addSetting("animal-color", new ColorSetting("Passive Color", -13577896));
   private final NumberSetting sFillAlpha = this.addSetting("fill-alpha", new NumberSetting("Fill Alpha", 0.3F, 0.0F, 1.0F).step(0.05F));
   private final NumberSetting sLineWidth = this.addSetting("line-width", new NumberSetting("Line Width", 1.5F, 0.5F, 5.0F).step(0.5F));
   private final Map<Entity, Integer> glowing = new IdentityHashMap<>();
   private final StableTopK<EspModule.Target> mobileTargets = new StableTopK<>(PRIORITY);
   private final Map<Integer, DrawStyle> mobileStyles = new HashMap<>();
   private long glowFrame = -1L;
   private ClientWorld glowLevel;

   public EspModule() {
      super("esp", "ESP", "Highlights players, hostile and passive mobs with outlines, boxes and tracers.", ModuleCategory.RENDER);
      instance = this;
      this.sBox.visibleWhen(() -> !this.sShape.is("Glow"));
      this.sFillAlpha.visibleWhen(() -> this.sShape.is("Fill") || this.sShape.is("Both"));
      this.sLineWidth.visibleWhen(() -> this.sShape.is("Box") || this.sShape.is("Both"));
      this.sCustomTracerColor.visibleWhen(this.sTracers::get);
      this.sTracerColor.visibleWhen(() -> this.sTracers.get() && this.sCustomTracerColor.get());
      this.sPlayerColor.visibleWhen(this.sPlayers::get);
      this.sFriends.visibleWhen(this.sPlayers::get);
      this.sFriendColor.visibleWhen(() -> this.sPlayers.get() && this.sFriends.get());
      this.sMobColor.visibleWhen(this.sMobs::get);
      this.sAnimalColor.visibleWhen(this.sAnimals::get);
   }

   @Override
   protected void onDisable() {
      this.resetGlow();
   }

   public static boolean shouldGlow(Entity entity) {
      EspModule module = instance;
      return module != null && module.isEnabled() && module.sShape.is("Glow") ? module.glowing().containsKey(entity) : false;
   }

   public static int glowColor(Entity entity) {
      EspModule module = instance;
      Integer color = module == null ? null : module.glowing().get(entity);
      return (color == null ? 16777215 : color) & 16777215;
   }

   private Map<Entity, Integer> glowing() {
      long frame = Render3DEvent.frame();
      if (frame != this.glowFrame) {
         this.glowFrame = frame;
         this.glowing.clear();

         for (EspModule.Target target : this.targets(1.0F)) {
            this.glowing.put(target.entity(), this.colorFor(target));
         }
      }

      return this.glowing;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level != this.glowLevel || !this.sShape.is("Glow")) {
         this.resetGlow();
      }

      this.glowLevel = level;
   }

   private void resetGlow() {
      this.glowing.clear();
      this.mobileTargets.reset(0);
      this.mobileStyles.clear();
      this.glowFrame = -1L;
      this.glowLevel = null;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      boolean box = this.sBox.get() && !this.sShape.is("Glow");
      boolean tracers = this.sTracers.get();
      if (box || tracers) {
         String shape = this.sShape.get();
         float lineWidth = this.sLineWidth.get();
         int fillAlpha = Math.round(this.sFillAlpha.get() * 255.0F);
         boolean mobile = MobilePerformance.active();
         if (mobile) {
            this.mobileStyles.clear();
         }

         for (EspModule.Target target : this.targets(event.partialTick)) {
            int color = this.colorFor(target);
            Box bounds = mobile ? interpolatedBox(target.entity(), target.interpolated()) : interpolatedBox(target.entity(), event.partialTick);
            if (box) {
               int fill = color & 16777215 | fillAlpha << 24;
               DrawStyle style = mobile ? this.mobileStyles.get(color) : null;
               if (style == null) {
                  style = switch (shape) {
                     case "Fill" -> DrawStyle.filled(fill);
                     case "Both" -> DrawStyle.filledAndStroked(color, lineWidth, fill);
                     default -> DrawStyle.stroked(color, lineWidth);
                  };
                  if (mobile) {
                     this.mobileStyles.put(color, style);
                  }
               }

               GizmoDrawing.box(bounds, style).ignoreOcclusion();
            }

            if (tracers) {
               TracerEngine.draw(bounds.getCenter(), this.sCustomTracerColor.get() ? this.sTracerColor.argb() : color);
            }
         }
      }
   }

   private List<EspModule.Target> targets(float partialTick) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null) {
         int max = Math.max(1, Math.min(96, this.sMaxEntities.getInt()));
         float range = Math.max(1.0F, this.sRange.get());
         double rangeSq = (double)range * range;
         Vec3d eye = mc.player.getCameraPosVec(partialTick);
         boolean mobile = MobilePerformance.active();
         if (mobile) {
            this.mobileTargets.reset(max);
         }

         List<EspModule.Target> result = mobile ? null : new ArrayList<>();

         for (Entity entity : mc.world.getEntities()) {
            EspModule.Group group = groupOf(entity);
            if (group != null
               && this.shown(group)
               && (entity != mc.player || !this.sIgnoreSelf.get())
               && !isGone(entity)
               && !(entity instanceof PlayerEntity player && player.isSpectator())) {
               Vec3d interpolated = entity.getLerpedPos(partialTick);
               double distanceSq = interpolated.squaredDistanceTo(eye);
               if (!(distanceSq > rangeSq)) {
                  EspModule.Target target = new EspModule.Target(entity, group, distanceSq, mobile ? interpolated : null);
                  if (mobile) {
                     this.mobileTargets.offer(target);
                  } else {
                     result.add(target);
                  }
               }
            }
         }

         if (mobile) {
            return this.mobileTargets.result();
         }

         if (result.size() <= max) {
            return result;
         }

         result.sort(PRIORITY);
         return result.subList(0, max);
      } else {
         return List.of();
      }
   }

   private static boolean isGone(Entity entity) {
      return entity.isRemoved() || entity instanceof LivingEntity living && living.isDead();
   }

   private static EspModule.Group groupOf(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return EspModule.Group.PLAYER;
      } else if (entity instanceof Monster) {
         return EspModule.Group.HOSTILE;
      } else {
         return entity instanceof MobEntity ? EspModule.Group.PASSIVE : null;
      }
   }

   private boolean shown(EspModule.Group group) {
      return switch (group) {
         case PLAYER -> this.sPlayers.get();
         case HOSTILE -> this.sMobs.get();
         case PASSIVE -> this.sAnimals.get();
      };
   }

   private int colorFor(EspModule.Target target) {
      return switch (target.group()) {
         case PLAYER -> {
            if (this.sFriends.get() && target.entity() instanceof PlayerEntity player && FriendStore.getInstance().isFriend(player.getGameProfile().name())) {
               yield this.sFriendColor.argb();
            }

            yield this.sPlayerColor.argb();
         }
         case HOSTILE -> this.sMobColor.argb();
         case PASSIVE -> this.sAnimalColor.argb();
      };
   }

   private static Box interpolatedBox(Entity entity, float partialTick) {
      Vec3d current = entity.getEntityPos();
      Vec3d interpolated = entity.getLerpedPos(partialTick);
      return entity.getBoundingBox()
         .offset(interpolated.x - current.x, interpolated.y - current.y, interpolated.z - current.z);
   }

   private static Box interpolatedBox(Entity entity, Vec3d interpolated) {
      Vec3d current = entity.getEntityPos();
      return entity.getBoundingBox()
         .offset(interpolated.x - current.x, interpolated.y - current.y, interpolated.z - current.z);
   }

   @Environment(EnvType.CLIENT)
   private enum Group {
      PLAYER,
      HOSTILE,
      PASSIVE;
   }

   @Environment(EnvType.CLIENT)
   private record Target(Entity entity, EspModule.Group group, double distanceSq, Vec3d interpolated) {
   }
}
