package net.anubis.client.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
public final class ItemEnchants {
   private static final String[] ROMAN = new String[]{"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
   private static DynamicRegistryManager cachedFor;
   private static List<RegistryEntry<Enchantment>> cached = List.of();

   private ItemEnchants() {
   }

   public static Item resolve(String raw) {
      String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.isEmpty()) {
         return null;
      }

      if (text.indexOf(58) < 0) {
         text = "minecraft:" + text;
      }

      Identifier id = Identifier.tryParse(text);
      if (id != null && Registries.ITEM.containsId(id)) {
         Item item = (Item)Registries.ITEM.get(id);
         return item == Items.AIR ? null : item;
      } else {
         return null;
      }
   }

   public static String stored(Item item) {
      Identifier key = Registries.ITEM.getId(item);
      return "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString();
   }

   public static boolean supports(Item item) {
      if (item == null) {
         return false;
      }

      if (item == Items.ENCHANTED_BOOK) {
         return true;
      }

      List<RegistryEntry<Enchantment>> all = all();
      ItemStack stack = item.getDefaultStack();
      if (all.isEmpty()) {
         return stack.contains(DataComponentTypes.ENCHANTABLE);
      }

      for (RegistryEntry<Enchantment> holder : all) {
         if (((Enchantment)holder.value()).isSupportedItem(stack)) {
            return true;
         }
      }

      return false;
   }

   public static List<RegistryEntry<Enchantment>> applicable(Item item) {
      List<RegistryEntry<Enchantment>> out = new ArrayList<>();
      if (item == null) {
         return out;
      }

      ItemStack stack = item.getDefaultStack();
      boolean book = item == Items.ENCHANTED_BOOK;

      for (RegistryEntry<Enchantment> holder : all()) {
         if (book || ((Enchantment)holder.value()).isSupportedItem(stack)) {
            out.add(holder);
         }
      }

      out.sort(Comparator.comparing(holderx -> ((Enchantment)holderx.value()).description().getString(), String.CASE_INSENSITIVE_ORDER));
      return out;
   }

   public static List<RegistryEntry<Enchantment>> all() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return List.of();
      }

      DynamicRegistryManager access = mc.world.getRegistryManager();
      if (access != cachedFor) {
         List<RegistryEntry<Enchantment>> found = new ArrayList<>();
         access.getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().forEach(found::add);
         cached = List.copyOf(found);
         cachedFor = access;
      }

      return cached;
   }

   public static String path(RegistryEntry<Enchantment> holder) {
      return holder.getKey().map(key -> key.getValue().getPath()).orElse("");
   }

   public static String key(String raw) {
      String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   public static Map<String, Integer> parse(String raw) {
      Map<String, Integer> out = new LinkedHashMap<>();
      if (raw == null) {
         return out;
      }

      for (String part : raw.split("[,;]")) {
         String text = part.trim();
         if (!text.isEmpty()) {
            int level = 1;
            int space = text.lastIndexOf(32);
            if (space > 0) {
               int parsed = level(text.substring(space + 1).trim());
               if (parsed > 0) {
                  level = parsed;
                  text = text.substring(0, space).trim();
               }
            }

            String key = key(text);
            if (!key.isEmpty()) {
               out.put(key, level);
            }
         }
      }

      return out;
   }

   private static int level(String raw) {
      try {
         int value = Integer.parseInt(raw);
         return value >= 1 && value <= 10 ? value : 0;
      } catch (NumberFormatException e) {
         String text = raw.toLowerCase(Locale.ROOT);

         for (int i = 0; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(text)) {
               return i + 1;
            }
         }

         return 0;
      }
   }

   public static String roman(int level) {
      return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1].toUpperCase(Locale.ROOT) : Integer.toString(level);
   }
}
