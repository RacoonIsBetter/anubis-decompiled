package net.anubis.client.module.impl.render.character;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.config.FriendStore;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.misc.FreecamModule;
import net.anubis.client.module.impl.render.PharaohHeaddressLayer;
import net.anubis.client.module.impl.render.PharaohHeaddressModule;
import net.anubis.client.module.impl.render.character.kit.CharacterDesign;
import net.anubis.client.module.impl.render.character.kit.CharacterModel;
import net.anubis.client.module.impl.render.character.kit.Detail;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.StuckObjectsFeatureRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.Deadmau5FeatureRenderer;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.feature.ShoulderParrotFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;

@Environment(EnvType.CLIENT)
public final class CharacterModule extends ClientModule {
   private static final long FRIEND_REFRESH_NANOS = 1000000000L;
   private static final float MODEL_PIXEL = 0.05859375F;
   private static volatile CharacterModule active;
   private final ModeSetting character = this.addSetting("character", characterSetting());
   private final BooleanSetting friends = this.addSetting("friends", new BooleanSetting("On Friends", false));
   private final ModeSetting detail = this.addSetting(
      "detail", new ModeSetting("Detail", Detail.MEDIUM.label, Detail.LOW.label, Detail.MEDIUM.label, Detail.HIGH.label)
   );
   private final BooleanSetting heldItems = this.addSetting("held-items", new BooleanSetting("Held Items", false));
   private final Int2BooleanOpenHashMap friendCache = new Int2BooleanOpenHashMap();
   private long friendsCheckedAt;
   private int scanTicks;
   private WeakReference<ClientWorld> level = new WeakReference<>(null);

   public CharacterModule() {
      super("character-3d", "3D Character", "Replaces your player model with a 3D character.", ModuleCategory.VISUALS);
      this.addSetting("characters-folder", new ActionSetting("Open Characters Folder", this::openFolder));
      this.character.onChange(this::prepare);
      this.detail.onChange(this::prepare);
   }

   private static ModeSetting characterSetting() {
      CharacterCatalog.refresh();
      List<String> names = CharacterCatalog.names();
      return new ModeSetting("Character", names.get(0), names.toArray(new String[0]));
   }

   private void refreshCharacters() {
      if (CharacterCatalog.refresh()) {
         this.character.setModes(CharacterCatalog.names());
      }
   }

   private void openFolder() {
      Path dir = CustomCharacters.folder();

      try {
         Files.createDirectories(dir);
      } catch (IOException e) {
         ModuleChat.send(this, ModuleChat.body().danger("Couldn't create ").text(dir.toString()));
         return;
      }

      this.refreshCharacters();
      Util.getOperatingSystem().open(dir);
   }

   private void prepare() {
      if (this.isEnabled()) {
         CharacterCache.prepare(CharacterCatalog.byName(this.character.get()), this.effectiveDetail());
      }
   }

   private Detail effectiveDetail() {
      return MobilePerformance.active() ? Detail.LOW : Detail.byLabel(this.detail.get());
   }

   @Override
   protected void onEnable() {
      PharaohHeaddressModule headdress = ModuleRegistry.get(PharaohHeaddressModule.class);
      if (headdress != null) {
         headdress.setEnabled(false);
      }

      CharacterCache.listen();
      this.reset();
      this.refreshCharacters();
      this.prepare();
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, event -> {
         this.watchLevel();
         if (++this.scanTicks % 40 == 0) {
            this.refreshCharacters();
         }
      });
   }

   @Override
   protected void onDisable() {
      if (active == this) {
         active = null;
      }

      this.reset();
      CharacterCache.clear();
   }

   private void reset() {
      this.friendCache.clear();
      CharacterMotion.clear();
      this.level = new WeakReference<>(MinecraftClient.getInstance().world);
   }

   private void watchLevel() {
      ClientWorld current = MinecraftClient.getInstance().world;
      if (this.level.get() != current) {
         this.reset();
         CharacterCache.releaseTextures();
      }
   }

   public static void extract(Entity entity, PlayerEntityRenderState state) {
      CharacterModule module = active;
      WornCharacter worn = module == null ? null : module.resolve(state);
      CharacterHolder holder = (CharacterHolder)state;
      holder.anubis$setCharacter(worn);
      if (worn != null) {
         Entity vehicle = entity.getVehicle();
         holder.anubis$setSeat(vehicle == null ? 0.0F : (vehicle instanceof LivingEntity ? 1.35F : 0.3F));
         if (state.nameLabelPos != null) {
            CharacterModel model = worn.model;
            float over = model.maxY * model.scale - 32.0F;
            if (over > 0.0F) {
               state.nameLabelPos = state.nameLabelPos.add(0.0, over * 0.05859375F * state.baseScale, 0.0);
            }
         }
      }
   }

   static void baking(String file) {
      CharacterModule module = active;
      if (module != null) {
         ModuleChat.send(module, ModuleChat.body().text("Preparing ").name(file).text(", big models take a few seconds the first time"));
      }
   }

   static void notice(String file, List<String> limbs) {
      CharacterModule module = active;
      if (module != null) {
         ModuleChat.Body body = ModuleChat.body().name(file);
         if (limbs.isEmpty()) {
            body.text(" has no skeleton the client recognises, so it moves as one piece");
         } else {
            body.text(" only moves its ").value(String.join(" and ", limbs)).text(", the rest of its skeleton wasn't recognised");
         }

         ModuleChat.send(module, body);
      }
   }

   static void failed(CharacterDesign design, Throwable failure) {
      CharacterModule module = active;
      if (module != null) {
         Throwable cause = failure instanceof UncheckedIOException && failure.getCause() != null ? failure.getCause() : failure;
         String reason = !(cause instanceof IOException) && !(cause instanceof IllegalStateException)
            ? "the file couldn't be read, see the log"
            : cause.getMessage();
         ModuleChat.send(module, ModuleChat.body().text("Couldn't load ").name(design.name()).text(": ").danger(reason));
      }
   }

   static WornCharacter worn(EntityRenderState state) {
      return state instanceof CharacterHolder holder ? holder.anubis$character() : null;
   }

   static float seat(EntityRenderState state) {
      return state instanceof CharacterHolder holder ? holder.anubis$seat() : 0.0F;
   }

   public static boolean hidesModel(EntityRenderState state) {
      return worn(state) != null;
   }

   public static boolean hidesLayer(FeatureRenderer<?, ?> layer, EntityRenderState state) {
      return worn(state) == null
         ? false
         : layer instanceof ArmorFeatureRenderer
            || layer instanceof CapeFeatureRenderer
            || layer instanceof ElytraFeatureRenderer
            || layer instanceof StuckObjectsFeatureRenderer
            || layer instanceof HeadFeatureRenderer
            || layer instanceof ShoulderParrotFeatureRenderer
            || layer instanceof Deadmau5FeatureRenderer
            || layer instanceof PharaohHeaddressLayer
            || layer instanceof HeldItemFeatureRenderer && !showsHeldItems();
   }

   private static boolean showsHeldItems() {
      CharacterModule module = active;
      return module != null && module.heldItems.get();
   }

   static WornCharacter firstPerson() {
      CharacterModule module = active;
      if (module == null) {
         return null;
      } else {
         ClientPlayerEntity self = MinecraftClient.getInstance().player;
         if (self != null && !self.isSpectator()) {
            WornCharacter worn = CharacterCache.ready(CharacterCatalog.byName(module.character.get()), module.effectiveDetail());
            return worn != null && worn.model.firstPersonArms ? worn : null;
         } else {
            return null;
         }
      }
   }

   private WornCharacter resolve(PlayerEntityRenderState state) {
      if (state.spectator) {
         return null;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      return !this.wears(mc, state) ? null : CharacterCache.ready(CharacterCatalog.byName(this.character.get()), this.effectiveDetail());
   }

   private boolean wears(MinecraftClient mc, PlayerEntityRenderState state) {
      ClientPlayerEntity self = mc.player;
      if (self != null && mc.world != null) {
         if (state.id != self.getId()) {
            return this.friends.get() && this.isFriend(mc, state.id);
         } else {
            return FreecamModule.active == null && mc.options.getPerspective().isFirstPerson() ? mc.currentScreen != null && !self.isSleeping() : true;
         }
      } else {
         return false;
      }
   }

   private boolean isFriend(MinecraftClient mc, int id) {
      long now = System.nanoTime();
      if (now - this.friendsCheckedAt > 1000000000L) {
         this.friendCache.clear();
         this.friendsCheckedAt = now;
      }

      if (this.friendCache.containsKey(id)) {
         return this.friendCache.get(id);
      }

      boolean friend = mc.world.getEntityById(id) instanceof PlayerEntity player && FriendStore.getInstance().isFriend(player.getGameProfile().name());
      this.friendCache.put(id, friend);
      return friend;
   }
}
