package net.anubis.client.util.seedscan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.structure.rule.RuleTest;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;

import net.minecraft.world.gen.feature.OreFeatureConfig.Target;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures;

@Environment(EnvType.CLIENT)
public record OreSpec(
   String id,
   int step,
   int index,
   int order,
   int countMin,
   int countMax,
   int rarity,
   boolean trapezoid,
   int yMin,
   int yMax,
   int plateau,
   int size,
   float discard,
   byte kind,
   Set<RegistryEntry<Biome>> biomes
) {
   private static final int[] STEPS = new int[]{net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES.ordinal(), net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_DECORATION.ordinal()};

   public int reach() {
      return MathHelper.ceil((this.size / 16.0F * 2.0F + 1.0F) / 2.0F);
   }

   public int halfWidth() {
      return MathHelper.ceil(this.size / 8.0F) + this.reach();
   }

   public boolean drawsOnTerrain() {
      return this.discard > 0.0F && this.discard < 1.0F;
   }

   int sampleCount(WgRandom random) {
      if (this.rarity > 0) {
         return random.nextFloat() < 1.0F / this.rarity ? 1 : 0;
      } else {
         return this.countMin == this.countMax ? this.countMin : random.between(this.countMin, this.countMax);
      }
   }

   int sampleY(WgRandom random) {
      if (this.yMin > this.yMax) {
         return this.yMin;
      }

      if (!this.trapezoid) {
         return random.between(this.yMin, this.yMax);
      }

      int k = this.yMax - this.yMin;
      if (this.plateau >= k) {
         return random.between(this.yMin, this.yMax);
      }

      int l = (k - this.plateau) / 2;
      int m = k - l;
      return this.yMin + random.between(0, m) + random.between(0, l);
   }

   boolean inBiome(RegistryEntry<Biome> biome) {
      return this.biomes == null || this.biomes.contains(biome);
   }

   @Override
   public String toString() {
      return this.id + "(" + this.step + ":" + this.index + ")";
   }

   static List<OreSpec> derive(
      List<IndexedFeatures> steps, List<RegistryEntry<Biome>> biomes, ChunkGenerator generator, Function<PlacedFeature, String> names, int minY, int maxY
   ) {
      checkTags();
      List<OreSpec> specs = new ArrayList<>();

      for (int step : STEPS) {
         if (step < steps.size()) {
            List<PlacedFeature> features = steps.get(step).features();

            for (int index = 0; index < features.size(); index++) {
               PlacedFeature placed = features.get(index);
               ConfiguredFeature<?, ?> configured = (ConfiguredFeature<?, ?>)placed.feature().value();
               if (configured.feature() == net.minecraft.world.gen.feature.Feature.ORE) {
                  OreFeatureConfig config = (OreFeatureConfig)configured.config();
                  String id = names.apply(placed);
                  Set<RegistryEntry<Biome>> carriers = Collections.newSetFromMap(new IdentityHashMap<>());

                  for (RegistryEntry<Biome> biome : biomes) {
                     if (generator.getGenerationSettings(biome).isFeatureAllowed(placed)) {
                        carriers.add(biome);
                     }
                  }

                  specs.add(read(id, step, index, specs.size(), placed, config, carriers.size() == biomes.size() ? null : Set.copyOf(carriers), minY, maxY));
               }
            }
         }
      }

      return List.copyOf(specs);
   }

   private static OreSpec read(
      String id, int step, int index, int order, PlacedFeature placed, OreFeatureConfig config, Set<RegistryEntry<Biome>> biomes, int minY, int maxY
   ) {
      List<PlacementModifier> mods = placed.placementModifiers();
      if (mods.size() != 4) {
         throw unsupported(id, "placement " + mods.size() + " modifiers");
      }

      JsonObject first = json(id, mods.get(0));
      int rarity = 0;
      int countMin;
      int countMax;
      switch (type(first)) {
         case "minecraft:count":
            JsonElement count = first.get("count");
            if (count.isJsonPrimitive()) {
               countMin = countMax = count.getAsInt();
            } else {
               JsonObject provider = count.getAsJsonObject();
               if (!type(provider).equals("minecraft:uniform")) {
                  throw unsupported(id, "count " + provider);
               }

               countMin = provider.get("min_inclusive").getAsInt();
               countMax = provider.get("max_inclusive").getAsInt();
            }
            break;
         case "minecraft:rarity_filter":
            countMax = 1;
            countMin = 1;
            rarity = first.get("chance").getAsInt();
            break;
         default:
            throw unsupported(id, "first modifier " + first);
      }

      if (!type(json(id, mods.get(1))).equals("minecraft:in_square")) {
         throw unsupported(id, "second modifier");
      }

      JsonObject range = json(id, mods.get(2));
      if (!type(range).equals("minecraft:height_range")) {
         throw unsupported(id, "third modifier");
      }

      if (!type(json(id, mods.get(3))).equals("minecraft:biome")) {
         throw unsupported(id, "fourth modifier");
      }

      JsonObject height = range.getAsJsonObject("height");

      boolean trapezoid = switch (type(height)) {
         case "minecraft:uniform" -> false;
         case "minecraft:trapezoid" -> true;
         default -> throw unsupported(id, "height " + height);
      };
      int plateau = height.has("plateau") ? height.get("plateau").getAsInt() : 0;
      int yMin = anchor(id, height.getAsJsonObject("min_inclusive"), minY, maxY);
      int yMax = anchor(id, height.getAsJsonObject("max_inclusive"), minY, maxY);
      return new OreSpec(
         id, step, index, order, countMin, countMax, rarity, trapezoid, yMin, yMax, plateau, config.size, config.discardOnAirChance, kindOf(id, config), biomes
      );
   }

   private static int anchor(String id, JsonObject anchor, int minY, int maxY) {
      if (anchor.has("absolute")) {
         return anchor.get("absolute").getAsInt();
      } else if (anchor.has("above_bottom")) {
         return minY + anchor.get("above_bottom").getAsInt();
      } else if (anchor.has("below_top")) {
         return maxY - anchor.get("below_top").getAsInt();
      } else {
         throw unsupported(id, "anchor " + anchor);
      }
   }

   private static byte kindOf(String id, OreFeatureConfig config) {
      Set<String> tags = new HashSet<>();
      byte kind = -1;

      for (Target target : config.targets) {
         JsonObject test = ((JsonElement)RuleTest.TYPE_CODEC.encodeStart(JsonOps.INSTANCE, target.target).getOrThrow()).getAsJsonObject();
         if (!test.get("predicate_type").getAsString().equals("minecraft:tag_match")) {
            throw unsupported(id, "target " + test);
         }

         tags.add(test.get("tag").getAsString());
         byte written = VBlock.of(target.state);
         if (written == 12 || written == 0 || kind != -1 && kind != written) {
            throw unsupported(id, "writes " + target.state);
         }

         kind = written;
      }

      boolean covered = tags.equals(Set.of("minecraft:base_stone_overworld"))
         || tags.equals(Set.of("minecraft:stone_ore_replaceables", "minecraft:deepslate_ore_replaceables"));
      if (covered && kind != -1) {
         return kind;
      } else {
         throw unsupported(id, "targets " + tags);
      }
   }

   static void checkTags() {
      expect(
         BlockTags.BASE_STONE_OVERWORLD,
         Blocks.STONE,
         Blocks.GRANITE,
         Blocks.DIORITE,
         Blocks.ANDESITE,
         Blocks.TUFF,
         Blocks.DEEPSLATE
      );
      expect(BlockTags.STONE_ORE_REPLACEABLES, Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE);
      expect(BlockTags.DEEPSLATE_ORE_REPLACEABLES, Blocks.DEEPSLATE, Blocks.TUFF);
   }

   private static void expect(TagKey<Block> tag, Block... blocks) {
      Set<Block> want = Collections.newSetFromMap(new IdentityHashMap<>());
      Collections.addAll(want, blocks);
      Set<Block> have = Collections.newSetFromMap(new IdentityHashMap<>());

      for (RegistryEntry<Block> holder : Registries.BLOCK.iterateEntries(tag)) {
         have.add((Block)holder.value());
      }

      if (!have.equals(want)) {
         throw new IllegalStateException("block tag " + tag + " is not vanilla's: " + have);
      }
   }

   private static JsonObject json(String id, PlacementModifier modifier) {
      try {
         return ((JsonElement)PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier).getOrThrow()).getAsJsonObject();
      } catch (RuntimeException e) {
         throw unsupported(id, "modifier " + modifier + ": " + e.getMessage());
      }
   }

   private static String type(JsonObject object) {
      JsonElement type = object.get("type");
      return type == null ? "" : type.getAsString();
   }

   private static IllegalStateException unsupported(String id, String what) {
      return new IllegalStateException("feature " + id + " is not placed like a vanilla ore: " + what);
   }
}
