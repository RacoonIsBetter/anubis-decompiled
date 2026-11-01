package net.anubis.client.module.impl.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.LightSnapshot;
import net.anubis.client.util.SessionWorker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;

@Environment(EnvType.CLIENT)
public class LightEspModule extends ClientModule {
   private static final long SCAN_COOLDOWN_MS = 1000L;
   private static final int MAX_BOXES = 12000;
   private static final float LINE_WIDTH = 1.5F;
   private final SessionWorker<Map<Integer, List<Box>>> worker = new SessionWorker<>("Anubis-Light");
   private ClientWorld sessionLevel;
   private LightSnapshot collecting;
   private long lastScanTime;
   private Map<Integer, List<Box>> renderData = Map.of();
   private final NumberSetting sRadius = this.addSetting("render-radius", new NumberSetting("Render Radius", 8.0F, 1.0F, 16.0F).step(1.0F));
   private final NumberSetting sMinLight = this.addSetting("min-light", new NumberSetting("Min Light", 5.0F, 1.0F, 15.0F).step(1.0F));
   private final BooleanSetting sDenoise = this.addSetting("denoise", new BooleanSetting("Denoise", true));
   private final ColorSetting sColor = this.addSetting("color", new ColorSetting("Color", -2130719744));

   public LightEspModule() {
      super("light-debug", "Light Debug", "Highlights artificial light underground.", ModuleCategory.BASE_RAID);
      List.of(this.sRadius, this.sMinLight, this.sDenoise).forEach(setting -> setting.onChange(this::resetScan));
   }

   @Override
   protected void onEnable() {
      this.resetScan();
   }

   @Override
   protected void onDisable() {
      this.resetScan();
   }

   private void resetScan() {
      this.worker.close();
      this.sessionLevel = null;
      this.collecting = null;
      this.renderData = Map.of();
      this.lastScanTime = 0L;
   }

   @EventHandler
   public void onTick(TickEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.sessionLevel != mc.world) {
         this.resetScan();
         this.sessionLevel = mc.world;
      }

      if (mc.world != null && mc.player != null) {
         this.worker.poll(result -> this.renderData = result);
         if (!this.worker.busy()) {
            long now = System.currentTimeMillis();
            if (this.collecting == null) {
               if (now - this.lastScanTime < 1000L) {
                  return;
               }

               this.collecting = new LightSnapshot(mc.player.getChunkPos(), clampInt(num(this.sRadius, 8), 1, 16), clampInt(num(this.sMinLight, 5), 1, 15));
            }

            if (this.collecting.collect(mc.world)) {
               Map<BlockPos, Integer> hits = this.collecting.hits();
               boolean denoise = this.isDenoise();
               int minLight = clampInt(num(this.sMinLight, 5), 1, 15);
               this.collecting = null;
               this.lastScanTime = now;
               this.worker.submit(() -> buildBoxes(hits, denoise, minLight));
            }
         }
      }
   }

   static Map<Integer, List<Box>> buildBoxes(Map<BlockPos, Integer> hits, boolean denoise, int minLight) {
      Set<BlockPos> skip = new HashSet<>();
      if (denoise) {
         for (Entry<BlockPos, Integer> entry : hits.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
               throw new CancellationException();
            }

            BlockPos source = entry.getKey();
            int light = entry.getValue();
            if (light >= minLight + 1 && light <= minLight + 2 && !skip.contains(source)) {
               boolean stronger = false;

               for (int x = -1; x <= 1 && !stronger; x++) {
                  for (int y = -1; y <= 1 && !stronger; y++) {
                     for (int z = -1; z <= 1; z++) {
                        if (hits.getOrDefault(source.add(x, y, z), 0) > light) {
                           stronger = true;
                           break;
                        }
                     }
                  }
               }

               if (!stronger) {
                  ArrayDeque<BlockPos> queue = new ArrayDeque<>();
                  queue.add(source);
                  skip.add(source);

                  while (!queue.isEmpty()) {
                     if (Thread.currentThread().isInterrupted()) {
                        throw new CancellationException();
                     }

                     BlockPos pos = queue.removeFirst();

                     for (Direction dir : Direction.values()) {
                        BlockPos next = pos.offset(dir);
                        int value = hits.getOrDefault(next, 0);
                        if (value >= minLight && value <= light && skip.add(next)) {
                           queue.addLast(next);
                        }
                     }
                  }
               }
            }
         }
      }

      Map<Integer, List<Box>> built = new HashMap<>();
      int count = 0;

      for (Entry<BlockPos, Integer> entry : hits.entrySet()) {
         if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
         }

         BlockPos pos = entry.getKey();
         if (!skip.contains(pos)) {
            boolean exposed = false;

            for (Direction dir : Direction.values()) {
               BlockPos next = pos.offset(dir);
               if (!hits.containsKey(next) || skip.contains(next)) {
                  exposed = true;
                  break;
               }
            }

            if (exposed) {
               built.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(new Box(pos));
               if (++count >= 12000) {
                  break;
               }
            }
         }
      }

      built.replaceAll((light, boxes) -> List.copyOf((Collection<? extends Box>)boxes));
      return Map.copyOf(built);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null && mc.world == this.sessionLevel) {
         Map<Integer, List<Box>> data = this.renderData;
         if (!data.isEmpty() && this.sColor != null) {
            int base = this.sColor.argb();
            int baseA = base >>> 24 & 0xFF;
            int baseR = base >> 16 & 0xFF;
            int baseG = base >> 8 & 0xFF;
            int baseB = base & 0xFF;

            for (Entry<Integer, List<Box>> entry : data.entrySet()) {
               float t = clamp01(entry.getKey().intValue() / 15.0F);
               int r = lerp(baseR, 255, t);
               int g = lerp(baseG, 255, t);
               int b = lerp(baseB, 255, t);
               int fillA = clampByte(Math.round(baseA * (0.5F + 0.5F * t)));
               int fill = fillA << 24 | r << 16 | g << 8 | b;
               int stroke = 0xFF000000 | Math.round(r * 0.45F) << 16 | Math.round(g * 0.45F) << 8 | Math.round(b * 0.45F);
               DrawStyle style = DrawStyle.filledAndStroked(stroke, 1.5F, fill);

               for (Box box : entry.getValue()) {
                  int cx = (int)box.minX >> 4;
                  int cz = (int)box.minZ >> 4;
                  if (mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                     VisibilityConfigurable props = GizmoDrawing.box(box, style);
                     props.ignoreOcclusion();
                  }
               }
            }
         }
      }
   }

   private boolean isDenoise() {
      return this.sDenoise != null && this.sDenoise.value().get();
   }

   private static int num(NumberSetting s, int fallback) {
      return s == null ? fallback : Math.round(s.value()[0]);
   }

   private static int clampInt(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }

   private static int clampByte(int v) {
      return Math.max(0, Math.min(255, v));
   }

   private static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static int lerp(int a, int b, float t) {
      return Math.round(a + (b - a) * t);
   }
}
