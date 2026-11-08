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
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.GeneratorOptions;

/**
 * Seeded xray: with the world seed it works out where the overworld ores were generated and boxes
 * them, so ores still show where anti-xray sends the underground as plain deepslate. Once the real
 * blocks around a prediction are visible, mined-out ores and ones the seed model got wrong (caves it
 * doesn't model) are hidden.
 */
@Environment(EnvType.CLIENT)
public final class SeedBlockEspModule extends ClientModule {
   private static final int SEED_SETTLE_TICKS = 20;
   private static final int REQUEST_EVERY_TICKS = 20;
   private static final int RECHECK_EVERY_TICKS = 40;

   private final StringSetting seed = this.addSetting("seed", new StringSetting("Seed", "").hint("World seed"));
   private final NumberSetting radius = this.addSetting("radius", new NumberSetting("Chunk Radius", 6.0F, 1.0F, 12.0F).step(1.0F));
   private final Map<SeedOres.Type, BooleanSetting> shown = new EnumMap<>(SeedOres.Type.class);
   private final BooleanSetting uncertain = this.addSetting("uncertain", new BooleanSetting("Show Uncertain", true));
   private final BooleanSetting hideMissing = this.addSetting("hide-missing", new BooleanSetting("Hide Mined / Missing", true));
   private final BooleanSetting tracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final EspStyle style = new EspStyle(this::addSetting, "Outline", 0.25F, 256.0F);
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         SeedBlockEspModule.Shown shown = (SeedBlockEspModule.Shown)kind;
         return shown.certain() ? shown.type().color : shown.type().color & 0x00FFFFFF | 0x70000000;
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
   private int ticks;

   public SeedBlockEspModule() {
      super("seed-block-esp", "Seed Block ESP", "Seeded xray: shows the ores the world seed generated, even where anti-xray hides them.", ModuleCategory.RENDER);
      this.seed.privateValue();

      for (SeedOres.Type type : SeedOres.Type.values()) {
         boolean on = type == SeedOres.Type.DIAMOND || type == SeedOres.Type.EMERALD || type == SeedOres.Type.GOLD || type == SeedOres.Type.REDSTONE;
         BooleanSetting setting = this.addSetting(type.name().toLowerCase(), new BooleanSetting(SeedBlockEspModule.Look.of(type).label, on));
         setting.onChange(this::redraw);
         this.shown.put(type, setting);
      }

      this.uncertain.onChange(this::redraw);
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
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientWorld level = mc.world;
      if (level != this.lastLevel) {
         this.reset(level);
      }

      if (level == null || mc.player == null || level.getRegistryKey() != World.OVERWORLD || !this.seedReady(level)) {
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
      if (this.ores == null || this.ores.seed() != wanted.getAsLong()) {
         this.reset(level);
         this.ores = SeedOres.start(wanted.getAsLong());
      }

      if (!this.seedChecked) {
         this.seedChecked = true;
         OptionalLong hashed = SeedOres.hashedSeed(level);
         if (hashed.isPresent() && !SeedOres.matches(wanted.getAsLong(), hashed.getAsLong())) {
            ModuleChat.send(this, ModuleChat.body().warn("This seed does not match the world").text(": the ores shown will be wrong."));
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

   /** Puts every prediction that passes the settings and the visible-block check into the layer. */
   private void redraw() {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return;
      }

      LongOpenHashSet keep = new LongOpenHashSet();
      Long2ObjectOpenHashMap<Boolean> masked = new Long2ObjectOpenHashMap<>();

      for (List<SeedOres.Ore> ores : this.predicted.values()) {
         for (SeedOres.Ore ore : ores) {
            if (this.shown.get(ore.type()).get() && (ore.certain() || this.uncertain.get()) && this.stillThere(level, ore, masked)) {
               keep.add(ore.pos());
               this.layer.put(ore.pos(), new SeedBlockEspModule.Shown(SeedBlockEspModule.Look.of(ore.type()), ore.certain()));
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
    * Unloaded or still hidden by anti-xray (a section of nothing but deepslate): trust the seed.
    * Visible: keep it only if the ore is really there, so mined ores and wrong guesses drop out.
    */
   private boolean stillThere(ClientWorld level, SeedOres.Ore ore, Long2ObjectOpenHashMap<Boolean> masked) {
      if (!this.hideMissing.get()) {
         return true;
      }

      BlockPos pos = BlockPos.fromLong(ore.pos());
      int cx = pos.getX() >> 4;
      int cz = pos.getZ() >> 4;
      if (!LevelUtil.isLoaded(level, cx, cz)) {
         return true;
      }

      WorldChunk chunk = level.getChunkManager().getWorldChunk(cx, cz);
      if (chunk == null) {
         return true;
      }

      BlockState state = chunk.getBlockState(pos);
      if (SeedBlockEspModule.Look.of(ore.type()).matches(state.getBlock())) {
         return true;
      }

      if (!state.isOf(Blocks.DEEPSLATE) || pos.getY() >= 0) {
         return false;
      }

      long sectionKey = BlockPos.asLong(cx, pos.getY() >> 4, cz);
      Boolean hidden = masked.get(sectionKey);
      if (hidden == null) {
         ChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
         hidden = section != null && !section.hasAny(s -> !s.isAir() && !s.isOf(Blocks.DEEPSLATE));
         masked.put(sectionKey, hidden);
      }

      return hidden;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.tracers.get()) {
         this.layer.renderTracers(this.style);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Shown(SeedBlockEspModule.Look type, boolean certain) {
   }

   /** Colour, setting label and the real blocks of each ore (stone and deepslate variants). */
   @Environment(EnvType.CLIENT)
   private enum Look {
      DIAMOND("Diamond", 0xFF35E8F0, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
      EMERALD("Emerald", 0xFF2EE66B, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
      GOLD("Gold", 0xFFFFC928, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
      REDSTONE("Redstone", 0xFFFF3030, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
      LAPIS("Lapis", 0xFF3060FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
      IRON("Iron", 0xFFD9A982, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
      COPPER("Copper", 0xFFE0753E, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
      COAL("Coal", 0xFF404040, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);

      final String label;
      final int color;
      private final Block stone;
      private final Block deepslate;

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
