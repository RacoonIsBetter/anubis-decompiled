package net.anubis.client.module.impl.donutmacros;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.Identifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import net.minecraft.component.type.ItemEnchantmentsComponent;

@Environment(EnvType.CLIENT)
record Product(
   Item item,
   String id,
   String search,
   String displayName,
   long minPrice,
   List<Product.Enchant> enchants,
   boolean restock,
   int orderAmount,
   boolean beatTopOrder,
   long fixedPrice,
   long bidIncrement,
   long maxPrice,
   Predicate<ItemStack> filter
) {
   Product(
      Item item,
      String id,
      String search,
      String displayName,
      long minPrice,
      List<Product.Enchant> enchants,
      boolean restock,
      int orderAmount,
      boolean beatTopOrder,
      long fixedPrice,
      long bidIncrement,
      long maxPrice
   ) {
      this(item, id, search, displayName, minPrice, enchants, restock, orderAmount, beatTopOrder, fixedPrice, bidIncrement, maxPrice, null);
   }

   static Item resolveItem(String raw) {
      String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      if (text.isEmpty()) {
         return null;
      }

      if (!text.contains(":")) {
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

   static String searchText(Item item) {
      return Registries.ITEM.getId(item).getPath().replace('_', ' ');
   }

   static List<Product.Enchant> parseEnchants(String raw) {
      List<Product.Enchant> out = new ArrayList<>();
      if (raw == null) {
         return out;
      }

      for (String part : raw.split("[,;]")) {
         String text = part.trim();
         if (!text.isEmpty()) {
            int level = 1;
            int space = text.lastIndexOf(32);
            if (space > 0) {
               String last = text.substring(space + 1).trim();
               int parsed = parseLevel(last);
               if (parsed > 0) {
                  level = parsed;
                  text = text.substring(0, space).trim();
               }
            }

            String key = ServerText.key(text);
            if (!key.isEmpty()) {
               out.add(new Product.Enchant(key, text, level));
            }
         }
      }

      return out;
   }

   private static int parseLevel(String raw) {
      try {
         int value = Integer.parseInt(raw);
         return value >= 1 && value <= 10 ? value : 0;
      } catch (NumberFormatException e) {
         return ServerText.roman(raw);
      }
   }

   boolean matches(ItemStack stack) {
      if (!stack.isEmpty() && stack.isOf(this.item)) {
         if (this.filter != null) {
            return this.filter.test(stack);
         }

         if (this.enchants.isEmpty()) {
            return true;
         }

         ItemEnchantmentsComponent present = EnchantmentHelper.getEnchantments(stack);

         for (Product.Enchant wanted : this.enchants) {
            if (levelOf(present, wanted.key()) < wanted.level()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean enchantsMatch(ItemStack stack, List<Product.Enchant> wanted, boolean exact) {
      ItemEnchantmentsComponent present = EnchantmentHelper.getEnchantments(stack);
      if (exact && present.getSize() != wanted.size()) {
         return false;
      }

      for (Product.Enchant enchant : wanted) {
         int level = levelOf(present, enchant.key());
         if (exact ? level != enchant.level() : level < enchant.level()) {
            return false;
         }
      }

      return true;
   }

   private static int levelOf(ItemEnchantmentsComponent present, String key) {
      for (Entry<RegistryEntry<Enchantment>> entry : present.getEnchantmentEntries()) {
         String path = entry.getKey().getKey().map(k -> k.getValue().getPath()).orElse("");
         if (ServerText.key(path).equals(key)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   boolean nameMatches(String label, boolean loose) {
      String key = ServerText.key(label);
      String byId = ServerText.key(Registries.ITEM.getId(this.item).getPath());
      String byName = ServerText.key(this.displayName);
      return !key.equals(byId) && !key.equals(byName) ? loose && (key.endsWith(byId) || !byName.isEmpty() && key.endsWith(byName)) : true;
   }

   String describe() {
      StringBuilder text = new StringBuilder(this.displayName);
      if (!this.enchants.isEmpty()) {
         List<String> names = new ArrayList<>();

         for (Product.Enchant enchant : this.enchants) {
            names.add(enchant.label());
         }

         text.append(" (").append(String.join(", ", names)).append(')');
      }

      return text.toString();
   }

   @Environment(EnvType.CLIENT)
   record Enchant(String key, String name, int level) {
      String label() {
         return this.name + (this.level > 1 ? " " + this.level : "");
      }
   }
}
