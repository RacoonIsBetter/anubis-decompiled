package net.anubis.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.anubis.client.chat.ModuleChat;
import net.anubis.client.esp.BlockLayer;
import net.anubis.client.esp.EspStyle;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.LevelUtil;
import net.anubis.client.util.seedscan.SeedOres;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;

/**
 * Seeded xray: with the world seed, Minecraft's own world generator (run privately on a background
 * thread, see {@link SeedOres}) produces the overworld ores exactly where the server generated
 * them, and they are boxed even where anti-xray sends the underground as plain deepslate. A box
 * only goes away when its block is air (mined, cave) or exposed to air and still not the ore.
 */
@Environment(EnvType.CLIENT)
public final class SeedBlockEspModule extends ClientModule {
   private static final int SEED_SETTLE_TICKS = 20;
   private static final int REQUEST_EVERY_TICKS = 20;
   private static final int RECHECK_EVERY_TICKS = 40;
   /** Visible ores needed before the seed is judged; a wrong seed matches almost none, the right one most. */
   private static final int VERIFY_SAMPLES = 30;
   private static final float VERIFY_PASS = 0.5F;
   private static final float VERIFY_FAIL = 0.2F;

   private final StringSetting seed = this.addSetting("seed", new StringSetting("Seed", "").hint("World seed"));
   private final NumberSetting radius = this.addSetting("radius", new NumberSetting("Chunk Radius", 6.0F, 1.0F, 12.0F).step(1.0F));
   private final Map<SeedOres.Type, BooleanSetting> shown = new EnumMap<>(SeedOres.Type.class);
   private final Map<SeedOres.Type, ColorSetting> colors = new EnumMap<>(SeedOres.Type.class);
   private final BooleanSetting stoneOres = this.addSetting("stone-ores", new BooleanSetting("Stone Ores", true));
   private final BooleanSetting deepslateOres = this.addSetting("deepslate-ores", new BooleanSetting("Deepslate Ores", true));
   private final BooleanSetting hideMissing = this.addSetting("hide-missing", new BooleanSetting("Hide Mined / Missing", true));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.25F, 512.0F);
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         SeedBlockEspModule.Shown shown = (SeedBlockEspModule.Shown)kind;
         int color = SeedBlockEspModule.this.colors.get(shown.ore()).argb();
         return color;
      }

      @Override
      public int tracerColor(Object kind) {
         return this.color(kind);
      }
   }, () -> MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getRegistryKey());
   private final Long2ObjectOpenHashMap<List<SeedOres.Ore>> predicted = new Long2ObjectOpenHashMap<>();
   private final LongOpenHashSet requested = new LongOpenHashSet();
   private final LongOpenHashSet drawn = new LongOpenHashSet();
   private SeedOres ores;
   private ClientWorld lastLevel;
   private String seedText;
   private int seedStill;
   private boolean seedAsked;
   private boolean seedChecked;
   private boolean hashMismatch;
   /** 1 confirmed by visible ores, -1 rejected, 0 not decided yet. */
   private int verdict;
   private int ticks;

   public SeedBlockEspModule() {
      super("seed-block-esp", "Seed Block ESP", "Seeded xray: shows the ores the world seed generated, even where anti-xray hides them.", ModuleCategory.RENDER);
      this.seed.privateValue();

      for (SeedOres.Type type : SeedOres.Type.values()) {
         boolean on = type == SeedOres.Type.DIAMOND || type == SeedOres.Type.EMERALD || type == SeedOres.Type.GOLD || type == SeedOres.Type.REDSTONE
            || type == SeedOres.Type.ANCIENT_DEBRIS;
         SeedBlockEspModule.Look look = SeedBlockEspModule.Look.of(type);
         BooleanSetting setting = this.addSetting(type.name().toLowerCase(), new BooleanSetting(look.label, on));
         ColorSetting color = this.addSetting(type.name().toLowerCase() + "-color", new ColorSetting(look.label + " Colour", look.color));
         setting.onChange(this::redraw);
         color.visibleWhen(setting::get);
         // Boxes keep the colour they were built with; rebuild them all.
         color.onChange(this::recolor);
         this.shown.put(type, setting);
         this.colors.put(type, color);
      }

      this.stoneOres.onChange(this::redraw);
      this.deepslateOres.onChange(this::redraw);
      this.hideMissing.onChange(this::redraw);
   }

   @Override
   protected void onEnable() {
      this.reset(MinecraftClient.getInstance().world);
   }

   @Override
   protected void onDisable() {
      this.reset(null);
      this.seedText = null;
   }

   private void reset(ClientWorld level) {
      if (this.ores != null) {
         this.ores.close();
         this.ores = null;
      }

      this.predicted.clear();
      this.requested.clear();
      this.drawn.clear();
      this.layer.clear();
      this.lastLevel = level;
      this.seedChecked = false;
      this.hashMismatch = false;
      this.verdict = 0;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.reset(level);
      }

      if (level == null || mc.player == null || level.getRegistryKey() != World.OVERWORLD && level.getRegistryKey() != World.NETHER || !this.seedReady(level)) {
         return;
      }

      this.ticks++;
      SeedOres.Result result;
      boolean fresh = false;

      while ((result = this.ores.poll()) != null) {
         this.predicted.put(result.chunk(), result.ores());
         fresh = true;
      }

      ChunkPos center = mc.player.getChunkPos();
      int radius = this.radius.getInt();
      if (this.ticks % REQUEST_EVERY_TICKS == 0) {
         this.requestAround(level, center, radius);
         this.forgetFar(center, radius + 2);
      }

      if (fresh || this.ticks % RECHECK_EVERY_TICKS == 0) {
         this.redraw();
         if (this.verdict == 0) {
            this.verify(level);
         }
      }
   }

   private boolean seedReady(ClientWorld level) {
      String text = this.seed.get();
      if (!text.equals(this.seedText)) {
         this.seedStill = this.seedText == null ? SEED_SETTLE_TICKS : 0;
         this.seedText = text;
      } else if (this.seedStill < SEED_SETTLE_TICKS) {
         this.seedStill++;
      }

      if (this.seedStill < SEED_SETTLE_TICKS) {
         return this.ores != null;
      }

      OptionalLong wanted = GeneratorOptions.parseSeed(text);
      if (wanted.isEmpty()) {
         if (this.ores != null) {
            this.reset(level);
         }

         if (!this.seedAsked) {
            this.seedAsked = true;
            ModuleChat.send(this, ModuleChat.body().text("Seed Block ESP needs the world seed: ").warn("set Seed"));
         }

         return false;
      }

      this.seedAsked = false;
      if (this.ores == null || this.ores.seed() != wanted.getAsLong() || this.ores.dimension() != level.getRegistryKey()) {
         this.reset(level);
         this.ores = SeedOres.start(wanted.getAsLong(), level.getRegistryKey());
      }

      if (!this.seedChecked) {
         this.seedChecked = true;
         OptionalLong hashed = SeedOres.hashedSeed(level);
         // Only a hint: servers can send a fake hash to stop seed cracking. The visible ores decide.
         this.hashMismatch = hashed.isPresent() && !SeedOres.matches(wanted.getAsLong(), hashed.getAsLong());
         if (this.hashMismatch) {
            ModuleChat.send(this, ModuleChat.body().text("The server's seed hash doesn't match this seed (some servers send a fake one); checking it against ores you can see..."));
         }
      }

      if (this.ores.failure() != null) {
         ModuleChat.send(this, ModuleChat.body().warn("Seed Block ESP stopped").text(": " + this.ores.failure()));
         this.reset(level);
         this.toggle();
         return false;
      }

      return true;
   }

   private void requestAround(ClientWorld level, ChunkPos center, int radius) {
      for (int ring = 0; ring <= radius; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                  long key = ChunkPos.toLong(center.x + dx, center.z + dz);
                  if (this.requested.add(key)) {
                     this.ores.request(key);
                  }
               }
            }
         }
      }
   }

   private void forgetFar(ChunkPos center, int keep) {
      LongOpenHashSet far = new LongOpenHashSet();
      var it = this.requested.iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (Math.max(Math.abs(ChunkPos.getPackedX(key) - center.x), Math.abs(ChunkPos.getPackedZ(key) - center.z)) > keep) {
            far.add(key);
         }
      }

      var gone = far.iterator();

      while (gone.hasNext()) {
         long key = gone.nextLong();
         this.requested.remove(key);
         List<SeedOres.Ore> ores = this.predicted.remove(key);
         if (ores != null) {
            for (SeedOres.Ore ore : ores) {
               if (this.drawn.remove(ore.pos())) {
                  this.layer.remove(ore.pos());
               }
            }
         }
      }
   }

   private void recolor() {
      this.layer.clear();
      this.drawn.clear();
      this.redraw();
   }

   /** Puts every prediction that passes the settings and the visible-block check into the layer. */
   private void redraw() {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return;
      }

      LongOpenHashSet keep = new LongOpenHashSet();

      for (List<SeedOres.Ore> ores : this.predicted.values()) {
         for (SeedOres.Ore ore : ores) {
            if (this.shown.get(ore.type()).get() && (ore.type().nether || (ore.deepslate() ? this.deepslateOres.get() : this.stoneOres.get())) && this.stillThere(level, ore)) {
               keep.add(ore.pos());
               this.layer.put(ore.pos(), new SeedBlockEspModule.Shown(ore.type()));
            }
         }
      }

      var it = this.drawn.iterator();

      while (it.hasNext()) {
         long pos = it.nextLong();
         if (!keep.contains(pos)) {
            this.layer.remove(pos);
            it.remove();
         }
      }

      this.drawn.addAll(keep);
   }

   /**
    * Judges the seed by the world itself: predicted ores that touch air are real blocks anti-xray
    * leaves alone (it only hides buried ores). With the right seed most are there, with a wrong one
    * almost none are.
    */
   private void verify(ClientWorld level) {
      int seen = 0;
      int there = 0;
      BlockPos.Mutable next = new BlockPos.Mutable();

      for (List<SeedOres.Ore> ores : this.predicted.values()) {
         for (SeedOres.Ore ore : ores) {
            BlockPos pos = BlockPos.fromLong(ore.pos());
            if (!LevelUtil.isLoaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
               continue;
            }

            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.DEEPSLATE) && pos.getY() < 0) {
               continue;
            }

            boolean exposed = false;

            for (net.minecraft.util.math.Direction side : net.minecraft.util.math.Direction.values()) {
               if (level.getBlockState(next.set(pos, side)).isAir()) {
                  exposed = true;
                  break;
               }
            }

            if (exposed) {
               seen++;
               if (SeedBlockEspModule.Look.of(ore.type()).matches(state.getBlock())) {
                  there++;
               }
            }
         }
      }

      if (seen < VERIFY_SAMPLES) {
         return;
      }

      float ratio = (float)there / seen;
      if (ratio >= VERIFY_PASS) {
         this.verdict = 1;
         ModuleChat.Body body = ModuleChat.body().good("Seed confirmed").text(": " + there + " of " + seen + " ores you can see are where it puts them.");
         if (this.hashMismatch) {
            body.text(" The server sends a fake seed hash.");
         }

         ModuleChat.send(this, body);
      } else if (ratio <= VERIFY_FAIL) {
         this.verdict = -1;
         ModuleChat.send(this, ModuleChat.body().warn("Seed looks wrong").text(": only " + there + " of " + seen + " ores you can see are where it puts them."));
      }
   }

   /**
    * Trust the seed unless the world clearly says otherwise. Ore anti-xray keeps showing buried ores
    * as stone or deepslate even in revealed chunks, so a buried non-ore proves nothing; only air
    * (mined out, or a cave the seed model lacks) or an exposed block that still isn't the ore
    * (anti-xray has to show exposed ores) drops a prediction.
    */
   private boolean stillThere(ClientWorld level, SeedOres.Ore ore) {
      if (!this.hideMissing.get()) {
         return true;
      }

      BlockPos pos = BlockPos.fromLong(ore.pos());
      if (!LevelUtil.isLoaded(level, pos.getX() >> 4, pos.getZ() >> 4)) {
         return true;
      }

      BlockState state = level.getBlockState(pos);
      if (SeedBlockEspModule.Look.of(ore.type()).matches(state.getBlock())) {
         return true;
      }

      return !state.isAir() && !exposed(level, pos);
   }

   private static boolean exposed(ClientWorld level, BlockPos pos) {
      BlockPos.Mutable next = new BlockPos.Mutable();

      for (Direction side : Direction.values()) {
         if (level.getBlockState(next.set(pos, side)).isAir()) {
            return true;
         }
      }

      return false;
   }


   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.tracers.get()) {
         this.layer.renderTracers(this.style);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Shown(SeedOres.Type ore) {
   }

   /** Default colour, setting label and the real blocks of each ore: both the stone and the deepslate variant count. */
   @Environment(EnvType.CLIENT)
   private enum Look {
      DIAMOND("Diamond", 0xFF35E8F0, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
      EMERALD("Emerald", 0xFF2EE66B, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
      GOLD("Gold", 0xFFFFC928, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
      REDSTONE("Redstone", 0xFFFF3030, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
      LAPIS("Lapis", 0xFF3060FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
      IRON("Iron", 0xFFD9A982, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
      COPPER("Copper", 0xFFE0753E, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
      COAL("Coal", 0xFF404040, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
      ANCIENT_DEBRIS("Ancient Debris (Netherite)", 0xFFB4583A, Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS),
      NETHER_GOLD("Nether Gold", 0xFFFFD84A, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
      NETHER_QUARTZ("Nether Quartz", 0xFFEDE6DA, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE);

      final String label;
      final int color;
      private final Block stone;
      final Block deepslate;

      Look(String label, int color, Block stone, Block deepslate) {
         this.label = label;
         this.color = color;
         this.stone = stone;
         this.deepslate = deepslate;
      }

      static SeedBlockEspModule.Look of(SeedOres.Type type) {
         return valueOf(type.name());
      }

      boolean matches(Block block) {
         return block == this.stone || block == this.deepslate;
      }
   }
}
