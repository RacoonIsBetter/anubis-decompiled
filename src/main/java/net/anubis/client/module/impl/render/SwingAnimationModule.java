package net.anubis.client.module.impl.render;

import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.combat.CombatTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public final class SwingAnimationModule extends ClientModule {
   public static volatile SwingAnimationModule active;
   private static final String KHOPESH = "Slash";
   private static final String ANKH = "Chop";
   private static final String SICKLE = "Hook";
   private static final String SCARAB = "Spin";
   private static final String HORUS = "Double Tap";
   private static final String SPHINX = "Push";
   private static final long COMBAT_WINDOW_MS = 3000L;
   private static final float PI = (float) Math.PI;
   private static final float TAU = (float) (Math.PI * 2);
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Slash", "Slash", "Chop", "Hook", "Spin", "Double Tap", "Push"));
   private final NumberSetting tempo = this.addSetting("tempo", new NumberSetting("Tempo", 1.0F, 0.4F, 2.5F).step(0.05F));
   private final NumberSetting intensity = this.addSetting("intensity", new NumberSetting("Intensity", 1.0F, 0.2F, 2.0F).step(0.05F));
   private final BooleanSetting offhand = this.addSetting("offhand", new BooleanSetting("Offhand Too", true));
   private final BooleanSetting combatOnly = this.addSetting("combat-only", new BooleanSetting("Combat Only", false));
   private int styleIndex;
   private boolean styled;

   public SwingAnimationModule() {
      super("swing-animation", "Swing Animation", "Changes your first-person swing animation.", ModuleCategory.VISUALS);
      this.style.onChange(this::cacheStyle);
      this.cacheStyle();
   }

   @Override
   protected void onEnable() {
      this.latch();
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
   }

   private void cacheStyle() {
      this.styleIndex = Math.max(0, this.style.modes().indexOf(this.style.get()));
   }

   public void latch() {
      this.styled = !this.combatOnly.get() || inCombat();
   }

   private static boolean inCombat() {
      if (CombatTarget.lastHit(3000L) != null) {
         return true;
      }

      Entity looked = MinecraftClient.getInstance().targetedEntity;
      return (looked instanceof PlayerEntity || looked instanceof Monster) && looked.isAlive();
   }

   public boolean covers(boolean mainHand) {
      return (mainHand || this.offhand.get()) && this.styled;
   }

   public int swingTicks(int ticks) {
      return !this.styled ? ticks : Math.max(1, Math.round(ticks / this.tempo.get()));
   }

   public void pose(MatrixStack pose, float progress, int side) {
      if (!(progress <= 0.0F) && !(progress >= 1.0F)) {
         float k = this.intensity.get();
         switch (this.styleIndex) {
            case 1:
               ankh(pose, progress, side, k);
               break;
            case 2:
               sickle(pose, progress, side, k);
               break;
            case 3:
               scarab(pose, progress, side, k);
               break;
            case 4:
               horus(pose, progress, side, k);
               break;
            case 5:
               sphinx(pose, progress, side, k);
               break;
            default:
               khopesh(pose, progress, side, k);
         }
      }
   }

   private static void khopesh(MatrixStack pose, float t, int side, float k) {
      float a = crest(t, 0.3F) * k;
      pose.translate(side * -0.3F * a, 0.05F * MathHelper.sin((float) Math.PI * t) * k, -0.1F * a);
      pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 64.0F * a));
      pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 48.0F * a));
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-14.0F * a));
   }

   private static void ankh(MatrixStack pose, float t, int side, float k) {
      float raise = t < 0.28F ? MathHelper.sin((float) Math.PI * t / 0.28F) * k : 0.0F;
      float strike = t <= 0.18F ? 0.0F : crest((t - 0.18F) / 0.82F, 0.28F) * k;
      pose.translate(side * -0.06F * strike, 0.16F * raise - 0.1F * strike, 0.06F * raise - 0.24F * strike);
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30.0F * raise - 105.0F * strike));
      pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 10.0F * strike));
   }

   private static void sickle(MatrixStack pose, float t, int side, float k) {
      float a = crest(t, 0.42F);
      float hook = a * a * k;
      a *= k;
      pose.translate(side * -0.1F * a, -0.24F * a, -0.06F * a);
      pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -100.0F * a));
      pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 30.0F * hook));
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25.0F * a));
   }

   private static void scarab(MatrixStack pose, float t, int side, float k) {
      float turn = (float) (Math.PI * 2) * t;
      float cos = MathHelper.cos(turn);
      float sin = MathHelper.sin(turn);
      float body = MathHelper.sin((float) Math.PI * t) * k;
      float r = 0.17F * k;
      pose.translate(side * r * 0.6F * (cos - 1.0F), r * sin, -0.12F * body);
      pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 40.0F * sin * k));
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40.0F * body));
   }

   private static void horus(MatrixStack pose, float t, int side, float k) {
      float beat = MathHelper.sin((float) (Math.PI * 2) * t);
      float w = beat * beat * (t < 0.5F ? 1.0F : 0.65F) * k;
      pose.translate(side * -0.08F * w, 0.06F * w, -0.1F * w);
      pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 26.0F * w));
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-50.0F * w));
      pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -22.0F * w));
   }

   private static void sphinx(MatrixStack pose, float t, int side, float k) {
      float a = (0.5F - 0.5F * MathHelper.cos((float) (Math.PI * 2) * t)) * k;
      pose.translate(0.0F, -0.06F * a, -0.1F * a);
      pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24.0F * a));
      pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 10.0F * a));
   }

   private static float crest(float t, float peak) {
      if (t <= 0.0F || t >= 1.0F) {
         return 0.0F;
      } else if (t < peak) {
         float u = 1.0F - t / peak;
         return 1.0F - u * u * u;
      } else {
         float u = (t - peak) / (1.0F - peak);
         return 1.0F - u * u * (3.0F - 2.0F * u);
      }
   }
}
