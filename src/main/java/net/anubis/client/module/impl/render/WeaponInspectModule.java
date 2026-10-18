package net.anubis.client.module.impl.render;

import net.anubis.client.event.EventPriority;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.KeyInputEvent;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.input.AnubisKeyPolling;
import net.anubis.client.input.ModuleKeybinds;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class WeaponInspectModule extends ClientModule {
   public static volatile WeaponInspectModule active;
   private static final String SWORDS = "Swords";
   private static final String WEAPONS = "Weapons";
   private static final String ANY_ITEM = "Any Item";
   private static final Identifier KNIFE_TEXTURE = Identifier.of("anubis", "textures/misc/knife_base.png");
   private static final float EASE_OUT_SECONDS = 0.15F;
   private static final int IDLE = 0;
   private static final int INSPECT = 1;
   private static final int DEPLOY = 2;
   private static final float[] REST = new float[16];
   private static final float SPARKLE_SIZE = 0.07F;
   private final KeySetting key = this.addSetting("key", new KeySetting("Inspect Key", 70));
   private final ModeSetting skin = this.addSetting("skin", new ModeSetting("Knife Skin", "Butterfly", "None", "Butterfly", "Karambit", "Katana"));
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Classic", "Classic", "Twirl"));
   private final ModeSetting items = this.addSetting("items", new ModeSetting("Items", "Swords", "Swords", "Weapons", "Any Item"));
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 1.0F, 0.5F, 2.0F).step(0.05F));
   private final NumberSetting knifeSize = this.addSetting("knife-size", new NumberSetting("Knife Size", 1.0F, 0.5F, 2.0F).step(0.05F));
   private final BooleanSetting showArm = this.addSetting("show-arm", new BooleanSetting("Show Arm", true));
   private final BooleanSetting deploy = this.addSetting("deploy", new BooleanSetting("Deploy Animation", true));
   private final BooleanSetting slashTrail = this.addSetting("slash-trail", new BooleanSetting("Slash Trail", true));
   private final NumberSetting trailLength = this.addSetting("trail-length", new NumberSetting("Trail Length", 0.14F, 0.05F, 0.35F).step(0.01F));
   private final BooleanSetting sparkles = this.addSetting("sparkles", new BooleanSetting("Blade Sparkle", true));
   private final BooleanSetting sneakSkips = this.addSetting("sneak-skips", new BooleanSetting("Sneak Skips Inspect", true));
   private int mode = 0;
   private InspectAnimation.Clip clip;
   private KnifeSkin clipKnife;
   private long startedAt;
   private float seconds;
   private boolean easingOut;
   private long easeStartedAt;
   private float easeFrom;
   private int slot;
   private Item item;
   private int heldSlot = -1;
   private Item heldItem;
   private final float[] state = new float[16];
   private final float[] trailSample = new float[12];
   private final SlashTrail[] trails = new SlashTrail[2];
   private final Matrix4f base = new Matrix4f();
   private final Matrix4f baseInverse = new Matrix4f();
   private final Matrix3f baseNormal = new Matrix3f();
   private boolean baseKnown;

   public WeaponInspectModule() {
      super(
         "weapon-inspect",
         "CS Knife",
         "Knife skins for your sword and CS-style inspects with a key of your choice (F by default); hold the key to keep the knife spinning.",
         ModuleCategory.VISUALS
      );

      for (int i = 0; i < this.trails.length; i++) {
         this.trails[i] = new SlashTrail();
      }

      this.knifeSize.visibleWhen(() -> !this.skin.is("None"));
      this.deploy.visibleWhen(() -> !this.skin.is("None"));
      this.sparkles.visibleWhen(() -> !this.skin.is("None"));
      this.trailLength.visibleWhen(this.slashTrail::get);
   }

   @Override
   protected void onEnable() {
      this.mode = 0;
      this.heldItem = null;
      this.clearTrails();
      active = this;
      this.on(KeyInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onKey);
      this.on(MouseInputEvent.TYPE, Stage.PRE, EventPriority.HIGH, this::onMouse);
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      active = null;
      this.mode = 0;
      this.clearTrails();
   }

   public static void handsBegin(MatrixStack pose) {
      WeaponInspectModule module = active;
      if (module != null) {
         module.base.set(pose.peek().getPositionMatrix());
         module.base.invert(module.baseInverse);
         module.baseNormal.set(pose.peek().getNormalMatrix());
         module.baseKnown = true;
      }
   }

   public static boolean renderItem(MatrixStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, OrderedRenderCommandQueue collector, int light, boolean mainHand) {
      WeaponInspectModule module = active;
      return module != null && module.render(pose, holder, stack, context, collector, light, mainHand);
   }

   private void onKey(KeyInputEvent event) {
      if (event.action != 0 && this.pressed(event.key, event.action == 2)) {
         event.setCancelled(true);
      }
   }

   private void onMouse(MouseInputEvent event) {
      if (event.action == 1 && this.pressed(1000 + event.button, false)) {
         event.setCancelled(true);
      }
   }

   private boolean pressed(int pressed, boolean repeat) {
      int bound = this.key.value();
      if (ModuleKeybinds.isBindable(bound) && pressed == bound) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientPlayerEntity player = mc.player;
         if (mc.currentScreen != null || player == null || player.isSpectator()) {
            return false;
         }

         if (this.sneakSkips.get() && player.isSneaking()) {
            return false;
         }

         ItemStack held = player.getMainHandStack();
         if (!this.inspectable(held)) {
            return false;
         }

         if (!repeat && !player.isUsingItem()) {
            this.start(1, player.getInventory().getSelectedSlot(), held);
         }

         return true;
      } else {
         return false;
      }
   }

   private boolean held() {
      int bound = this.key.value();
      MinecraftClient mc = MinecraftClient.getInstance();
      if (ModuleKeybinds.isBindable(bound) && mc.currentScreen == null) {
         return bound >= 1000
            ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), bound - 1000) == 1
            : AnubisKeyPolling.isKeyDown(mc.getWindow(), bound);
      } else {
         return false;
      }
   }

   private boolean inspectable(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return !this.items.is("Any Item") && !isSword(stack)
            ? this.items.is("Weapons")
               && (
                  stack.isIn(ItemTags.AXES)
                     || stack.isIn(ItemTags.SPEARS)
                     || stack.isOf(Items.MACE)
                     || stack.isOf(Items.TRIDENT)
               )
            : true;
      }
   }

   private static boolean isSword(ItemStack stack) {
      return !stack.isEmpty()
         && (stack.isIn(ItemTags.SWORDS) || Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword"));
   }

   private static boolean handheld(ItemStack stack) {
      return isSword(stack)
         || stack.isIn(ItemTags.AXES)
         || stack.isIn(ItemTags.PICKAXES)
         || stack.isIn(ItemTags.SHOVELS)
         || stack.isIn(ItemTags.HOES);
   }

   private void start(int next, int selected, ItemStack held) {
      KnifeSkin knife = isSword(held) ? KnifeSkin.of(this.skin.get()) : null;
      this.clip = next == 2 ? knife.deploy : (knife != null ? knife.inspect : InspectAnimation.clip(this.style.get()));
      this.clipKnife = knife;
      this.mode = next;
      this.seconds = this.clip.seconds;
      this.easingOut = false;
      this.startedAt = System.nanoTime();
      this.slot = selected;
      this.item = held.getItem();
   }

   private void easeOut() {
      long now = System.nanoTime();
      this.easeFrom = Math.min(this.progress(now), 1.0F);
      this.easeStartedAt = now;
      this.easingOut = true;
   }

   private void onTick(TickEvent event) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         this.mode = 0;
         this.heldItem = null;
      } else {
         ItemStack held = player.getMainHandStack();
         int selected = player.getInventory().getSelectedSlot();
         if (selected != this.heldSlot || !held.isOf(this.heldItem)) {
            this.heldSlot = selected;
            this.heldItem = held.getItem();
            if (this.deploy.get() && isSword(held) && KnifeSkin.of(this.skin.get()) != null) {
               this.start(2, selected, held);
            } else if (this.mode != 0 && !this.easingOut) {
               this.easeOut();
            }
         } else if (this.mode != 0 && !this.easingOut) {
            if (player.handSwinging) {
               this.mode = 0;
            } else if (player.isUsingItem()) {
               this.easeOut();
            } else if (this.loopedProgress(System.nanoTime()) >= 1.0F) {
               this.mode = 0;
            }
         }
      }
   }

   private float progress(long now) {
      return (float)(now - this.startedAt) / 1.0E9F / (this.seconds / this.speed.get());
   }

   private float loopedProgress(long now) {
      float progress = this.progress(now);
      if (this.mode == 1 && this.clip.loops() && !(progress < this.clip.loopExit) && this.held()) {
         float duration = this.seconds / this.speed.get();
         long cycle = (long)((this.clip.loopExit - this.clip.loopEnter) * duration * 1.0E9F);
         if (cycle <= 0L) {
            return progress;
         }

         long over = (long)((progress - this.clip.loopExit) * duration * 1.0E9F);
         this.startedAt += (over / cycle + 1L) * cycle;
         return this.progress(now);
      } else {
         return progress;
      }
   }

   private float animate(ItemStack stack, KnifeSkin knife, long now) {
      if (this.mode == 0 || !stack.isOf(this.item)) {
         return 0.0F;
      }

      if (knife != this.clipKnife) {
         this.mode = 0;
         return 0.0F;
      }

      float weight = 1.0F;
      float progress;
      if (this.easingOut) {
         float t = (float)(now - this.easeStartedAt) / 1.0E9F / 0.15F;
         if (t >= 1.0F) {
            this.mode = 0;
            return 0.0F;
         }

         progress = this.easeFrom;
         weight = 1.0F - t * t * (3.0F - 2.0F * t);
      } else {
         progress = this.loopedProgress(now);
         if (progress >= 1.0F) {
            this.mode = 0;
            return 0.0F;
         }
      }

      this.clip.sample(progress, this.state);
      return weight;
   }

   private boolean render(MatrixStack pose, LivingEntity holder, ItemStack stack, ItemDisplayContext context, OrderedRenderCommandQueue collector, int light, boolean mainHand) {
      int side = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;
      boolean sword = isSword(stack);
      KnifeSkin knife = sword ? KnifeSkin.of(this.skin.get()) : null;
      long now = System.nanoTime();
      float weight = mainHand ? this.animate(stack, knife, now) : 0.0F;
      float[] at = weight > 0.0F ? this.state : REST;
      boolean trailing = mainHand && sword && this.slashTrail.get() && this.baseKnown;
      if (mainHand && !trailing) {
         this.clearTrails();
      }

      if (knife == null) {
         if (this.showArm.get() && handheld(stack) || HandModelModule.covers(stack, mainHand)) {
            FirstPersonArm.Arm arm = FirstPersonArm.arm();
            if (arm != null) {
               FirstPersonArm.hold(pose, arm, stack, context, holder, collector, light, at[0] * weight, at[1] * weight, at[2] * weight);
            }
         }

         if (weight > 0.0F) {
            InspectAnimation.place(pose, at, weight, side, 0.07F, 0.2F, 0.07F);
         }

         if (trailing) {
            this.swordTrail(pose.peek().getPositionMatrix(), side);
            this.record(now, 1);
            this.submitGlow(collector, now, KnifeMaterial.of(stack).glow, 0.0F, null);
         }

         return false;
      } else {
         KnifeMaterial material = KnifeMaterial.of(stack);
         FirstPersonArm.Arm arm = !this.showArm.get() && !HandModelModule.wants(mainHand) ? null : FirstPersonArm.arm();
         boolean slim = arm != null && arm.slim();
         pose.push();
         if (side < 0) {
            pose.scale(-1.0F, 1.0F, 1.0F);
         }

         knife.draw(
            pose,
            at,
            weight,
            slim,
            this.knifeSize.get(),
            sink(collector, light, side, arm, mainHand && offHandFree(), material, stack.hasGlint()),
            trailing ? this.trailSample : null
         );
         pose.pop();
         if (trailing) {
            this.record(now, knife.streakCount());
            float flash = this.sparkles.get() && weight > 0.0F ? knife.sparkle(at, weight) : 0.0F;
            this.submitGlow(collector, now, material.glow, flash, knife);
         }

         return true;
      }
   }

   private static KnifeSkin.Sink sink(
      OrderedRenderCommandQueue collector, int light, int side, FirstPersonArm.Arm arm, boolean otherFree, KnifeMaterial material, boolean foil
   ) {
      final RenderLayer solid = RenderLayers.entityCutoutNoCull(KNIFE_TEXTURE);
      final int[] palette = material.palette;
      return new KnifeSkin.Sink() {
         @Override
         public void part(MatrixStack part, KnifeMesh mesh, KnifeMesh.Shine shine) {
            collector.submitCustom(part, solid, (p, out) -> mesh.emit(p, out, palette, light, shine));
            if (foil) {
               collector.getBatchingQueue(1).submitCustom(part, RenderLayers.entityGlint(), (p, out) -> mesh.emit(p, out, palette, light, KnifeMesh.Shine.NONE));
            }
         }

         @Override
         public void glow(MatrixStack part, KnifeMesh mesh, KnifeMesh.Shine shine) {
            collector.getBatchingQueue(1)
               .submitCustom(part, RenderLayers.eyes(WeaponInspectModule.KNIFE_TEXTURE), (p, out) -> mesh.emitGlow(p, out, material.glow, shine));
         }

         @Override
         public void arm(MatrixStack hand, boolean other) {
            if (arm != null && (!other || otherFree)) {
               arm.draw(hand, side < 0 != other, collector, light);
            }
         }
      };
   }

   private static boolean offHandFree() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player != null && player.getOffHandStack().isEmpty();
   }

   private void swordTrail(Matrix4f pose, int side) {
      Matrix4f m = new Matrix4f(pose)
         .translate(side * 1.13F / 16.0F, 0.2F, 0.070625F)
         .rotate(new Quaternionf().rotationXYZ(0.0F, (float) (-Math.PI / 2), 0.43633232F))
         .scale(0.68F)
         .translate(-0.5F, -0.5F, -0.5F);
      Vector3f p = m.transformPosition(0.3125F, 0.3125F, 0.5F, new Vector3f());
      this.trailSample[0] = p.x;
      this.trailSample[1] = p.y;
      this.trailSample[2] = p.z;
      m.transformPosition(0.96875F, 0.96875F, 0.5F, p);
      this.trailSample[3] = p.x;
      this.trailSample[4] = p.y;
      this.trailSample[5] = p.z;
   }

   private void record(long now, int streaks) {
      Vector3f p = new Vector3f();

      for (int s = 0; s < this.trails.length; s++) {
         if (s >= streaks) {
            this.trails[s].clear();
         } else {
            for (int i = s * 6; i < s * 6 + 6; i += 3) {
               this.baseInverse.transformPosition(this.trailSample[i], this.trailSample[i + 1], this.trailSample[i + 2], p);
               this.trailSample[i] = p.x;
               this.trailSample[i + 1] = p.y;
               this.trailSample[i + 2] = p.z;
            }

            this.trails[s].add(now, this.trailSample, s * 6);
         }
      }
   }

   private void clearTrails() {
      for (SlashTrail trail : this.trails) {
         trail.clear();
      }
   }

   private void submitGlow(OrderedRenderCommandQueue collector, long now, int glow, float flash, KnifeSkin knife) {
      long lifetime = (long)(this.trailLength.get() * 1.0E9F);
      int streaks = knife != null ? knife.streakCount() : 1;
      boolean showing = flash > 0.0F;

      for (int s = 0; s < streaks && !showing; s++) {
         showing = !this.trails[s].isEmpty(now, lifetime);
      }

      if (showing) {
         float tipX = this.trailSample[3];
         float tipY = this.trailSample[4];
         float tipZ = this.trailSample[5];
         float turn = (float)(now % 6283185307L) / 1.0E9F * 1.5F;
         MatrixStack hand = new MatrixStack();
         hand.peek().getPositionMatrix().set(this.base);
         hand.peek().getNormalMatrix().set(this.baseNormal);
         collector.getBatchingQueue(1).submitCustom(hand, RenderLayers.eyes(KNIFE_TEXTURE), (p, out) -> {
            for (int s = 0; s < streaks; s++) {
               this.trails[s].emit(p, out, now, lifetime, glow, knife != null ? knife.streakStrength(s) : 1.0F);
            }

            if (flash > 0.0F) {
               SlashTrail.sparkle(p, out, tipX, tipY, tipZ, 0.07F * flash, turn, glow, flash);
            }
         });
      }
   }
}
