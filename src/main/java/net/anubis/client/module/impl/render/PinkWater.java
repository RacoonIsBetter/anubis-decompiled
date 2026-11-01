package net.anubis.client.module.impl.render;

import com.example.client.mixin.AccessorPinkWaterLevelRenderer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLongArray;
import net.anubis.client.event.EventPriority;
import net.anubis.client.event.ListenerHandle;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRenderData;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class PinkWater {
   public static final String OFF = "Off";
   static final int WARM = 4445678;
   static final int LUKEWARM = 4566514;
   static final int CHERRY_GROVE = 6141935;
   static final int OCEAN = 4159204;
   static final int PALE_GARDEN = 7768221;
   static final int SWAMP = 6388580;
   static final int MANGROVE = 3832426;
   static final int COLD = 4020182;
   static final int MEADOW = 937679;
   static final int FROZEN = 3750089;
   static final int[] BIOMES = new int[]{4445678, 4566514, 6141935, 4159204, 7768221, 6388580, 3832426, 4020182, 937679, 3750089};
   private static final double[][] BIOME_LAB = new double[BIOMES.length][];
   private static final double FOG_HUE = 240.0;
   private static final double FOG_SATURATION = 0.902;
   private static final int SWITCH_ON_TICKS = 60;
   private static final int SWITCH_OFF_TICKS = 40;
   private static final int CHANGE_TICKS = 15;
   private static final float FOG_STEP = 0.05F;
   private static final int UNSEEN_BATCH = 64;
   private static final int UNSEEN_WAIT = 2;
   private static final int UNSEEN_PATIENCE = 20;
   private static final int UNSEEN_TRICKLE = 8;
   private static final double BLEND_POWER = 3.0;
   private static final double LIGHT_CARRY = 0.8;
   private static volatile PinkWater.Design shown;
   private static volatile PinkWater.Design fogDesign;
   private static volatile float fogShare;
   private static PinkWater.Design chosen;
   private static PinkWater.Design pending;
   private static int pendingTicks;
   private static float fogLevel;
   private static final List<PinkWater.Unseen> UNSEEN;
   private static int unseenNext;
   private static ClientWorld unseenLevel;
   private static BuiltChunkStorage unseenArea;
   private static int unseenWait;
   private static int unseenStalled;
   private static final Object UNSEEN_OWNER;
   private static ListenerHandle<TickEvent> unseenHook;
   private static final int CACHE_SIZE = 64;
   private static final AtomicLongArray CACHE;

   private PinkWater() {
   }

   public static String[] modes() {
      PinkWater.Design[] designs = PinkWater.Design.values();
      String[] modes = new String[designs.length + 1];
      modes[0] = "Off";

      for (int i = 0; i < designs.length; i++) {
         modes[i + 1] = designs[i].label;
      }

      return modes;
   }

   public static boolean shows() {
      return shown != null;
   }

   public static int water(int rgb, boolean cave) {
      PinkWater.Design design = shown;
      return design == null ? rgb : cached(rgb, cave, design);
   }

   public static byte caveBiome(Biome biome) {
      ClientWorld level = MinecraftClient.getInstance().world;
      if (level == null) {
         return 0;
      }

      Optional<Registry<Biome>> registry = level.getRegistryManager().getOptional(RegistryKeys.BIOME);
      if (registry.isEmpty()) {
         return 0;
      }

      Optional<RegistryKey<Biome>> key = registry.get().getKey(biome);
      if (key.isEmpty()) {
         return 1;
      }

      RegistryKey<Biome> k = key.get();
      return (byte)(!k.equals(BiomeKeys.LUSH_CAVES) && !k.equals(BiomeKeys.DRIPSTONE_CAVES) && !k.equals(BiomeKeys.DEEP_DARK) ? 1 : 2);
   }

   public static int waterFog(int argb) {
      float share = fogShare;
      PinkWater.Design design = fogDesign;
      return !(share <= 0.0F) && design != null ? mix(argb, waterFog(argb, design), share) : argb;
   }

   public static void underwater(Vector4f color) {
      float share = fogShare;
      PinkWater.Design design = fogDesign;
      if (!(share <= 0.0F) && design != null) {
         float k = 1.0F + ((float)design.fog.shade - 1.0F) * share;
         color.x *= k;
         color.y *= k;
         color.z *= k;
      }
   }

   public static int light(int packed) {
      PinkWater.Design design = shown;
      return design == null ? packed : light(packed, design);
   }

   public static void tick(MinecraftClient mc, boolean peep, PinkWater.Design design) {
      PinkWater.Design wanted = peep ? design : null;
      if (wanted == chosen) {
         pending = null;
         pendingTicks = 0;
      } else {
         if (wanted != pending) {
            pending = wanted;
            pendingTicks = 0;
         }

         int needed = chosen == null ? 60 : (design != null && wanted == null ? 40 : 15);
         if (++pendingTicks >= needed) {
            chosen = wanted;
            pending = null;
            pendingTicks = 0;
         }
      }

      ClientWorld level = mc.world;
      boolean allowed = level != null && level.getRegistryKey() != World.NETHER && level.getRegistryKey() != World.END;
      show(mc, allowed ? chosen : null);
      fogLevel = Math.max(0.0F, Math.min(1.0F, fogLevel + (shown != null ? 0.05F : -0.05F)));
      fogShare = fogLevel * fogLevel * (3.0F - 2.0F * fogLevel);
   }

   public static void reset(MinecraftClient mc) {
      chosen = null;
      pending = null;
      pendingTicks = 0;
      fogLevel = 0.0F;
      fogShare = 0.0F;
      show(mc, null);
   }

   private static void show(MinecraftClient mc, PinkWater.Design next) {
      if (next != shown) {
         shown = next;
         if (next != null) {
            fogDesign = next;
         }

         ClientWorld level = mc.world;
         if (level != null && mc.worldRenderer != null) {
            level.reloadColor();
            refresh(mc, level);
         } else {
            clearUnseen();
         }
      }
   }

   private static void refresh(MinecraftClient mc, ClientWorld level) {
      WorldRenderer renderer = mc.worldRenderer;
      ChunkSectionPos at = ChunkSectionPos.from(mc.gameRenderer.getCamera().getCameraPos());
      int radius = mc.options.getClampedViewDistance() + 1;
      LongOpenHashSet water = new LongOpenHashSet();

      for (int x = at.getSectionX() - radius; x <= at.getSectionX() + radius; x++) {
         for (int z = at.getSectionZ() - radius; z <= at.getSectionZ() + radius; z++) {
            WorldChunk chunk = level.getChunkManager().getWorldChunk(x, z);
            if (chunk != null) {
               ChunkSection[] sections = chunk.getSectionArray();

               for (int i = 0; i < sections.length; i++) {
                  ChunkSection section = sections[i];
                  if (!section.isEmpty() && section.hasAny(PinkWater::tinted)) {
                     water.add(ChunkSectionPos.asLong(x, chunk.sectionIndexToCoord(i), z));
                  }
               }
            }
         }
      }

      queueUnseen(renderer, level, at, water);
      LongIterator it = water.iterator();

      while (it.hasNext()) {
         long node = it.nextLong();
         renderer.scheduleChunkRender(ChunkSectionPos.unpackX(node), ChunkSectionPos.unpackY(node), ChunkSectionPos.unpackZ(node));
      }
   }

   private static boolean tinted(BlockState state) {
      return Fluids.WATER.matchesType(state.getFluidState().getFluid()) || state.isOf(Blocks.WATER_CAULDRON);
   }

   private static void queueUnseen(WorldRenderer renderer, ClientWorld level, ChunkSectionPos at, LongOpenHashSet water) {
      clearUnseen();
      BuiltChunkStorage area = ((AccessorPinkWaterLevelRenderer)renderer).anubis$pinkWaterViewArea();
      if (area != null && area.chunks != null && !water.isEmpty()) {
         for (BuiltChunk section : area.chunks) {
            if (section != null) {
               long node = section.getSectionPos();
               if (water.contains(node)) {
                  boolean firstBuild = section.getCurrentRenderData() == ChunkRenderData.HIDDEN && !section.needsRebuild();
                  UNSEEN.add(new PinkWater.Unseen(section, node, firstBuild));
               }
            }
         }

         if (!UNSEEN.isEmpty()) {
            UNSEEN.sort(Comparator.comparingLong(unseen -> distance(unseen.node, at)));
            unseenLevel = level;
            unseenArea = area;
            unseenWait = 2;
            unseenStalled = 0;
            if (unseenHook == null) {
               unseenHook = TickEvent.TYPE.listen(UNSEEN_OWNER, Stage.POST, EventPriority.NORMAL, event -> rebuildUnseen(MinecraftClient.getInstance()));
            }
         }
      }
   }

   private static void rebuildUnseen(MinecraftClient mc) {
      if (unseenNext >= UNSEEN.size()) {
         clearUnseen();
         unhookUnseen();
      } else {
         WorldRenderer renderer = mc.worldRenderer;
         ChunkBuilder dispatcher = renderer == null ? null : renderer.getChunkBuilder();
         if (mc.world != unseenLevel || dispatcher == null || ((AccessorPinkWaterLevelRenderer)renderer).anubis$pinkWaterViewArea() != unseenArea) {
            clearUnseen();
            unhookUnseen();
         } else if (unseenWait > 0) {
            unseenWait--;
         } else {
            int budget;
            if (dispatcher.getScheduledTaskCount() == 0) {
               budget = 64;
               unseenStalled = 0;
            } else {
               if (++unseenStalled < 20) {
                  return;
               }

               budget = 8;
               unseenStalled = 0;
            }

            ChunkRendererRegionBuilder cache = null;

            while (budget > 0 && unseenNext < UNSEEN.size()) {
               PinkWater.Unseen next = UNSEEN.get(unseenNext++);
               BuiltChunk section = next.section;
               if (section.getSectionPos() == next.node
                  && section.needsRebuild()
                  && (section.getCurrentRenderData() != ChunkRenderData.HIDDEN || next.firstBuild && section.shouldBuild())) {
                  if (cache == null) {
                     cache = new ChunkRendererRegionBuilder();
                  }

                  section.scheduleRebuild(cache);
                  section.cancelRebuild();
                  budget--;
               }
            }
         }
      }
   }

   private static void clearUnseen() {
      UNSEEN.clear();
      unseenNext = 0;
      unseenLevel = null;
      unseenArea = null;
   }

   private static void unhookUnseen() {
      if (unseenHook != null) {
         unseenHook.unregister();
         unseenHook = null;
      }
   }

   private static long distance(long node, ChunkSectionPos at) {
      long dx = ChunkSectionPos.unpackX(node) - at.getSectionX();
      long dy = ChunkSectionPos.unpackY(node) - at.getSectionY();
      long dz = ChunkSectionPos.unpackZ(node) - at.getSectionZ();
      return dx * dx + dy * dy + dz * dz;
   }

   public static int water(int rgb, PinkWater.Design design) {
      return water(rgb, false, design);
   }

   public static int water(int rgb, boolean cave, PinkWater.Design design) {
      int known = index(rgb & 16777215);
      double[] lab = known >= 0 ? (double[])design.water[known].clone() : between(oklab(rgb), design);
      if (cave) {
         for (int i = 0; i < 3; i++) {
            lab[i] += design.cave[i];
         }
      }

      return fromOklab(lab[0], lab[1], lab[2]);
   }

   private static double[] between(double[] in, PinkWater.Design design) {
      double weights = 0.0;
      double l = 0.0;
      double a = 0.0;
      double b = 0.0;
      double inL = 0.0;
      double inC = 0.0;

      for (int i = 0; i < BIOMES.length; i++) {
         double[] at = BIOME_LAB[i];
         double[] out = design.water[i];
         double dl = in[0] - at[0];
         double da = in[1] - at[1];
         double db = in[2] - at[2];
         double w = 1.0 / Math.pow(Math.max(dl * dl + da * da + db * db, 1.0E-12), 1.5);
         weights += w;
         l += w * out[0];
         a += w * out[1];
         b += w * out[2];
         inL += w * at[0];
         inC += w * Math.hypot(at[1], at[2]);
      }

      l /= weights;
      a /= weights;
      b /= weights;
      l = clamp(l + (in[0] - inL / weights) * 0.8, 0.3, 0.92);
      double vivid = Math.sqrt(clamp(Math.hypot(in[1], in[2]) / Math.max(inC / weights, 1.0E-6), 0.4, 1.6));
      return new double[]{l, a * vivid, b * vivid};
   }

   static int index(int rgb) {
      for (int i = 0; i < BIOMES.length; i++) {
         if (BIOMES[i] == rgb) {
            return i;
         }
      }

      return -1;
   }

   public static int waterFog(int argb, PinkWater.Design design) {
      PinkWater.Fog fog = design.fog;
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      double value = max / 255.0;
      double saturation = max == 0 ? 0.0 : (double)(max - min) / max;
      double hue = 0.0;
      if (max != min) {
         double span = max - min;
         hue = max == r ? 60.0 * ((g - b) / span) : (max == g ? 60.0 * ((b - r) / span + 2.0) : 60.0 * ((r - g) / span + 4.0));
      }

      double follow = fog.hueFollow * smooth(0.1, 0.3, saturation);
      double outHue = fog.hue + clamp(-follow * wrap(hue - 240.0), -fog.maxShift, fog.maxShift);
      double outSaturation = clamp(fog.saturation * Math.pow(clamp(saturation / 0.902, 0.25, 1.1), 0.7), 0.0, 1.0);
      double outValue = clamp(value * fog.value, 0.0, 1.0);
      return argb & 0xFF000000 | hsv(outHue, outSaturation, outValue);
   }

   public static int underwater(int rgb, PinkWater.Design design) {
      double k = design.fog.shade;
      return srgbChannel((rgb >> 16 & 0xFF) / 255.0 * k) << 16 | srgbChannel((rgb >> 8 & 0xFF) / 255.0 * k) << 8 | srgbChannel((rgb & 0xFF) / 255.0 * k);
   }

   public static int light(int packed, PinkWater.Design design) {
      int sky = packed >>> 20 & 15;
      int glow = (design.glow * sky + 7) / 15 << 4;
      return glow > 0 && (packed & 0xFF) < glow ? packed & -256 | glow : packed;
   }

   private static int cached(int rgb, boolean cave, PinkWater.Design design) {
      int key = rgb & 16777215 | design.ordinal() + 1 << 24 | (cave ? 1073741824 : 0);
      int slot = key * -1640531535 >>> 26 & 63;
      long entry = CACHE.getOpaque(slot);
      if ((int)(entry >>> 32) == key) {
         return (int)entry;
      }

      int out = water(rgb, cave, design);
      CACHE.setOpaque(slot, (long)key << 32 | out & 4294967295L);
      return out;
   }

   static double[] oklab(int rgb) {
      double r = linear((rgb >> 16 & 0xFF) / 255.0);
      double g = linear((rgb >> 8 & 0xFF) / 255.0);
      double b = linear((rgb & 0xFF) / 255.0);
      double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
      double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
      double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
      return new double[]{
         0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
         1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
         0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
      };
   }

   private static int fromOklab(double l, double a, double b) {
      double[] out = new double[3];
      if (!fromOklab(l, a, b, out)) {
         double low = 0.0;
         double high = 1.0;

         for (int i = 0; i < 14; i++) {
            double mid = (low + high) * 0.5;
            if (fromOklab(l, a * mid, b * mid, out)) {
               low = mid;
            } else {
               high = mid;
            }
         }

         fromOklab(l, a * low, b * low, out);
      }

      return channel(out[0]) << 16 | channel(out[1]) << 8 | channel(out[2]);
   }

   private static boolean fromOklab(double l, double a, double b, double[] out) {
      double l1 = l + 0.3963377774 * a + 0.2158037573 * b;
      double m1 = l - 0.1055613458 * a - 0.0638541728 * b;
      double s1 = l - 0.0894841775 * a - 1.291485548 * b;
      l1 = l1 * l1 * l1;
      m1 = m1 * m1 * m1;
      s1 = s1 * s1 * s1;
      out[0] = 4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1;
      out[1] = -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1;
      out[2] = -0.0041960863 * l1 - 0.7034186147 * m1 + 1.707614701 * s1;
      double e = 1.0E-4;
      return out[0] >= -e && out[0] <= 1.0 + e && out[1] >= -e && out[1] <= 1.0 + e && out[2] >= -e && out[2] <= 1.0 + e;
   }

   private static int hsv(double hueDegrees, double saturation, double value) {
      double h = (hueDegrees % 360.0 + 360.0) % 360.0 / 60.0;
      int sector = (int)Math.floor(h) % 6;
      double f = h - Math.floor(h);
      double p = value * (1.0 - saturation);
      double q = value * (1.0 - saturation * f);
      double t = value * (1.0 - saturation * (1.0 - f));
      double r;
      double g;
      double b;
      switch (sector) {
         case 0:
            r = value;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = value;
            b = p;
            break;
         case 2:
            r = p;
            g = value;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = value;
            break;
         case 4:
            r = t;
            g = p;
            b = value;
            break;
         default:
            r = value;
            g = p;
            b = q;
      }

      return srgbChannel(r) << 16 | srgbChannel(g) << 8 | srgbChannel(b);
   }

   private static double linear(double c) {
      return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
   }

   private static int channel(double linear) {
      double c = clamp(linear, 0.0, 1.0);
      double s = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 0.4166666666666667) - 0.055;
      return srgbChannel(s);
   }

   private static int srgbChannel(double c) {
      return (int)Math.round(clamp(c, 0.0, 1.0) * 255.0);
   }

   private static int mix(int from, int to, float t) {
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return from & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static double wrap(double degrees) {
      double d = degrees % 360.0;
      if (d > 180.0) {
         d -= 360.0;
      }

      if (d <= -180.0) {
         d += 360.0;
      }

      return d;
   }

   private static double smooth(double from, double to, double x) {
      double t = clamp((x - from) / (to - from), 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }

   private static double clamp(double v, double min, double max) {
      return v < min ? min : (v > max ? max : v);
   }

   static {
      for (int i = 0; i < BIOMES.length; i++) {
         BIOME_LAB[i] = oklab(BIOMES[i]);
      }

      UNSEEN = new ArrayList<>();
      UNSEEN_OWNER = new Object();
      CACHE = new AtomicLongArray(64);
   }

   @Environment(EnvType.CLIENT)
   public enum Design {
      ROSE_QUARTZ(
         "Rose Quartz",
         new int[]{16742585, 15429040, 15626161, 13392801, 12357803, 11165580, 10508930, 12476315, 12146838, 11817105},
         11862992,
         new PinkWater.Fog(338.0, 0.25, 15.0, 0.47, 1.0, 0.84),
         6
      ),
      PINK_SALT_LAKE(
         "Pink Salt Lake",
         new int[]{16759265, 16361428, 16427734, 15633086, 14923972, 14781610, 14452650, 15762095, 14908074, 14119845},
         12279295,
         new PinkWater.Fog(340.0, 0.15, 10.0, 0.36, 1.6, 0.77),
         7
      ),
      SUNSET_LAGOON(
         "Sunset Lagoon",
         new int[]{16751278, 16746659, 16747428, 16737936, 14130594, 14444925, 13789560, 15753090, 14900600, 14113390},
         16732671,
         new PinkWater.Fog(352.0, 0.3, 15.0, 0.46, 1.15, 0.81),
         2
      ),
      NEON_NIGHT(
         "Neon Night",
         new int[]{16740085, 15753959, 15885545, 14175952, 13076403, 13459385, 12802735, 12466090, 11808155, 11150220},
         15466751,
         new PinkWater.Fog(322.0, 0.2, 12.0, 0.5, 1.0, 0.82),
         8
      ),
      ORCHID_DEPTHS(
         "Orchid Depths",
         new int[]{13459425, 12538831, 12670418, 11026355, 10317461, 9193110, 9518220, 9515665, 8531337, 7547010},
         6815936,
         new PinkWater.Fog(300.0, 0.3, 15.0, 0.47, 0.9, 0.76),
         1
      ),
      COTTON_CANDY(
         "Cotton Candy",
         new int[]{16425215, 15569654, 15701240, 14253288, 14595804, 12157650, 12156370, 12485350, 11959777, 11499740},
         8402175,
         new PinkWater.Fog(300.0, 0.3, 20.0, 0.38, 1.5, 0.77),
         3
      );

      public static final PinkWater.Design DEFAULT = ROSE_QUARTZ;
      private final String label;
      final double[][] water;
      final double[] cave;
      final PinkWater.Fog fog;
      final int glow;

      Design(String label, int[] water, int cave, PinkWater.Fog fog, int glow) {
         if (water.length != PinkWater.BIOMES.length) {
            throw new IllegalArgumentException(label + ": one colour per biome");
         }

         this.label = label;
         this.water = new double[water.length][];

         for (int i = 0; i < water.length; i++) {
            this.water[i] = PinkWater.oklab(water[i]);
         }

         double[] ocean = this.water[PinkWater.index(4159204)];
         double[] lab = PinkWater.oklab(cave);
         this.cave = new double[]{lab[0] - ocean[0], lab[1] - ocean[1], lab[2] - ocean[2]};
         this.fog = fog;
         this.glow = glow;
      }

      public String label() {
         return this.label;
      }

      public static PinkWater.Design byLabel(String label) {
         for (PinkWater.Design design : values()) {
            if (design.label.equals(label)) {
               return design;
            }
         }

         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   record Fog(double hue, double hueFollow, double maxShift, double saturation, double value, double shade) {
   }

   @Environment(EnvType.CLIENT)
   private record Unseen(BuiltChunk section, long node, boolean firstBuild) {
   }
}
