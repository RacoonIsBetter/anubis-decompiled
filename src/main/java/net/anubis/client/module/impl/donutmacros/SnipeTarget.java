package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

@Environment(EnvType.CLIENT)
final class SnipeTarget {
   private static final Pattern DURATION = Pattern.compile("(?<!\\d)(\\d{1,5})\\s*([dhms])");
   final Item item;
   final boolean shard;
   final SnipeTarget.Preset preset;
   final String displayName;
   final String search;
   final List<Product.Enchant> enchants;
   final boolean exactEnchants;
   final long maxPrice;
   final int minMinutes;
   private final Predicate<ItemStack> filter;

   private SnipeTarget(
      Item item,
      boolean shard,
      SnipeTarget.Preset preset,
      String displayName,
      String search,
      List<Product.Enchant> enchants,
      boolean exactEnchants,
      long maxPrice,
      int minMinutes,
      Predicate<ItemStack> filter
   ) {
      this.item = item;
      this.shard = shard;
      this.preset = preset;
      this.displayName = displayName;
      this.search = search;
      this.enchants = enchants;
      this.exactEnchants = exactEnchants;
      this.maxPrice = maxPrice;
      this.minMinutes = minMinutes;
      this.filter = filter;
   }

   static SnipeTarget matching(Item shown, String name, String search, Predicate<ItemStack> filter, long maxPrice) {
      return new SnipeTarget(shown, false, null, name, search, List.of(), false, maxPrice, 0, filter);
   }

   static SnipeTarget parse(String rawItem, String rawEnchants, boolean exact, long maxPrice, int minMinutes) {
      List<Product.Enchant> enchants = Product.parseEnchants(rawEnchants);
      SnipeTarget.Preset preset = SnipeTarget.Preset.of(rawItem);
      if (preset != null) {
         return new SnipeTarget(preset.item, true, preset, preset.label, preset.search, enchants, exact, maxPrice, minMinutes, null);
      }

      String text = rawItem == null ? "" : rawItem.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.startsWith("minecraft:")) {
         text = text.substring("minecraft:".length());
      }

      boolean shard = text.startsWith("shard_");
      Item item = Product.resolveItem(shard ? "netherite_" + text.substring("shard_".length()) : text);
      if (item == null) {
         return null;
      }

      String name = shard ? "Shard " + capitalize(text.substring("shard_".length())) : new ItemStack(item).getName().getString();
      return new SnipeTarget(item, shard, null, name, Product.searchText(item), enchants, exact, maxPrice, shard ? minMinutes : 0, null);
   }

   private static String capitalize(String path) {
      StringBuilder out = new StringBuilder();

      for (String word : path.split("_")) {
         if (!word.isEmpty()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return out.toString();
   }

   boolean sameKind(ItemStack stack) {
      if (this.filter != null) {
         return !stack.isEmpty() && this.filter.test(stack);
      }

      if (!stack.isEmpty() && stack.isOf(this.item)) {
         String name = ServerText.key(stack.getName().getString());
         if (this.preset != null) {
            if (!name.contains(this.preset.keyword)) {
               return false;
            }

            if (!this.preset.exclude.isEmpty() && name.contains(this.preset.exclude)) {
               return false;
            }
         } else if (name.contains("shard") != this.shard) {
            return false;
         }

         return this.enchants.isEmpty() || Product.enchantsMatch(stack, this.enchants, this.exactEnchants);
      } else {
         return false;
      }
   }

   boolean timed() {
      return this.preset != null || this.shard;
   }

   boolean matches(ItemStack stack) {
      if (!this.sameKind(stack)) {
         return false;
      } else {
         return this.preset != null ? minutesLeft(stack) >= Math.max(0, this.minMinutes) : this.minMinutes <= 0 || minutesLeft(stack) >= this.minMinutes;
      }
   }

   static int minutesLeft(ItemStack stack) {
      List<String> lines = new ArrayList<>();

      for (String line : ServerText.lore(stack)) {
         lines.add(ServerText.normalize(line));
      }

      List<Integer> listing = new ArrayList<>();

      for (int i = 0; i < lines.size(); i++) {
         if (lines.get(i).contains("$") || lines.get(i).contains("seller")) {
            listing.add(i);
         }
      }

      int best = -1;
      int bestGap = -1;
      int timers = 0;

      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);
         boolean destruct = line.contains("destruct");
         if (destruct || line.contains("expire")) {
            int minutes = duration(line);
            if (minutes >= 0) {
               if (destruct) {
                  return minutes;
               }

               timers++;
               int gap = Integer.MAX_VALUE;

               for (int index : listing) {
                  gap = Math.min(gap, Math.abs(index - i));
               }

               if (gap > bestGap) {
                  bestGap = gap;
                  best = minutes;
               }
            }
         }
      }

      if (best < 0) {
         return -1;
      } else {
         return !listing.isEmpty() && timers < 2 && bestGap < 2 ? -1 : best;
      }
   }

   private static int duration(String line) {
      Matcher matcher = DURATION.matcher(line);
      int minutes = 0;
      boolean found = false;

      while (matcher.find()) {
         found = true;
         int value = Integer.parseInt(matcher.group(1));
         switch (matcher.group(2)) {
            case "d":
               minutes += value * 1440;
               break;
            case "h":
               minutes += value * 60;
               break;
            case "m":
               minutes += value;
         }
      }

      return found ? minutes : -1;
   }

   Product product(long sellPrice) {
      return new Product(
         this.item,
         Registries.ITEM.getId(this.item).getPath(),
         this.search,
         this.displayName,
         sellPrice,
         this.enchants,
         false,
         1,
         false,
         1L,
         1L,
         0L,
         this::sameKind
      );
   }

   String describe() {
      StringBuilder text = new StringBuilder(this.displayName);
      if (!this.enchants.isEmpty()) {
         StringBuilder names = new StringBuilder();

         for (Product.Enchant enchant : this.enchants) {
            if (names.length() > 0) {
               names.append(", ");
            }

            names.append(enchant.label());
         }

         text.append(" (").append(this.exactEnchants ? "exactly " : "at least ").append(names).append(')');
      }

      return text.toString();
   }

   @Environment(EnvType.CLIENT)
   enum Preset {
      SELL_AXE("sell_axe", "Sell Axe", Items.NETHERITE_AXE, "sell axe", "sell", ""),
      DRILL("drill", "Drill", Items.NETHERITE_PICKAXE, "drill", "drill", ""),
      SHARD_AXE("shard_axe", "Shard Axe", Items.NETHERITE_AXE, "shard axe", "shard", "sell");

      final String value;
      final String label;
      final Item item;
      final String search;
      final String keyword;
      final String exclude;

      Preset(String value, String label, Item item, String search, String keyword, String exclude) {
         this.value = value;
         this.label = label;
         this.item = item;
         this.search = search;
         this.keyword = keyword;
         this.exclude = exclude;
      }

      static SnipeTarget.Preset of(String raw) {
         String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');

         for (SnipeTarget.Preset preset : values()) {
            if (preset.value.equals(text)) {
               return preset;
            }
         }

         return null;
      }

      static List<StringSetting.Preset> choices() {
         List<StringSetting.Preset> out = new ArrayList<>();

         for (SnipeTarget.Preset preset : values()) {
            out.add(new StringSetting.Preset(preset.value, preset.label, Registries.ITEM.getId(preset.item).getPath(), true));
         }

         return out;
      }
   }
}
