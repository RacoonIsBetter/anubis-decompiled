package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

@Environment(EnvType.CLIENT)
final class ServerText {
   private static final Pattern FORMATTING = Pattern.compile("§.");
   private static final Pattern MONEY_EXACT = Pattern.compile("^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?$");
   private static final Pattern MONEY_ANY = Pattern.compile("\\$?[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmb])?(?![a-z])");
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ";
   private static final String SMALL_CAPS_PLAIN = "abcdefghijklmnoprsstuvwyz";
   private static final String[] ROMAN = new String[]{"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};

   private ServerText() {
   }

   static String normalize(String raw) {
      if (raw == null) {
         return "";
      }

      String text = FORMATTING.matcher(raw).replaceAll("");
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         int small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ".indexOf(c);
         if (small >= 0) {
            out.append("abcdefghijklmnoprsstuvwyz".charAt(small));
         } else if (c != 10140 && c != 8594) {
            out.append(c);
         } else {
            out.append('-');
         }
      }

      return out.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
   }

   static String normalize(Text component) {
      return component == null ? "" : normalize(component.getString());
   }

   static String key(String raw) {
      String text = normalize(raw);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   static long parseMoney(String raw) {
      if (raw == null) {
         return -1L;
      }

      Matcher matcher = MONEY_EXACT.matcher(raw.trim().toLowerCase(Locale.ROOT));
      return !matcher.matches() ? -1L : Math.round(scale(matcher.group(1), matcher.group(2)));
   }

   static List<Double> moneyAll(String raw) {
      List<Double> out = new ArrayList<>();
      if (raw == null) {
         return out;
      }

      Matcher matcher = MONEY_ANY.matcher(normalize(raw));

      while (matcher.find()) {
         double value = scale(matcher.group(1), matcher.group(2));
         if (value > 0.0) {
            out.add(value);
         }
      }

      return out;
   }

   static String moneyToken(String raw) {
      Matcher matcher = MONEY_ANY.matcher(normalize(raw));
      if (!matcher.find()) {
         return null;
      }

      String suffix = matcher.group(2);
      return matcher.group(1).replace(",", "") + (suffix == null ? "" : suffix);
   }

   static double displayStep(String raw) {
      Matcher matcher = MONEY_ANY.matcher(normalize(raw));
      if (!matcher.find()) {
         return 1.0;
      }

      String number = matcher.group(1);
      int dot = number.indexOf(46);
      int decimals = dot < 0 ? 0 : number.length() - dot - 1;
      return Math.max(1.0, scale("1", matcher.group(2)) / Math.pow(10.0, decimals));
   }

   private static double scale(String number, String suffix) {
      double value;
      try {
         value = Double.parseDouble(number.replace(",", ""));
      } catch (NumberFormatException e) {
         return -1.0;
      }

      if (suffix == null) {
         return value;
      }

      return switch (suffix) {
         case "k" -> value * 1000.0;
         case "m" -> value * 1000000.0;
         case "b" -> value * 1.0E9;
         default -> value;
      };
   }

   static String exactMoney(long value) {
      if (value >= 1000000000L && value % 1000000000L == 0L) {
         return value / 1000000000L + "b";
      } else if (value >= 1000000L && value % 1000000L == 0L) {
         return value / 1000000L + "m";
      } else if (value >= 1000000L && value % 100000L == 0L) {
         return value / 1000000L + "." + value % 1000000L / 100000L + "m";
      } else {
         return value >= 1000L && value % 1000L == 0L ? value / 1000L + "k" : Long.toString(value);
      }
   }

   static String shortMoney(double value) {
      if (value >= 1.0E9) {
         return trim(value / 1.0E9) + "b";
      } else if (value >= 1000000.0) {
         return trim(value / 1000000.0) + "m";
      } else {
         return value >= 1000.0 ? trim(value / 1000.0) + "k" : Long.toString(Math.round(value));
      }
   }

   private static String trim(double value) {
      String text = String.format(Locale.ROOT, "%.2f", value);
      return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
   }

   static int roman(String raw) {
      String text = normalize(raw);

      for (int i = 0; i < ROMAN.length; i++) {
         if (ROMAN[i].equals(text)) {
            return i + 1;
         }
      }

      return 0;
   }

   static String withoutRoman(String raw) {
      String text = normalize(raw);
      int space = text.lastIndexOf(32);
      return space > 0 && roman(text.substring(space + 1)) > 0 ? text.substring(0, space) : text;
   }

   static List<String> lore(ItemStack stack) {
      List<String> out = new ArrayList<>();

      for (Text line : ((LoreComponent)stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)).lines()) {
         out.add(line.getString());
      }

      return out;
   }

   static double listingPrice(ItemStack stack) {
      List<String> lore = lore(stack);
      double best = -1.0;

      for (String line : lore) {
         String clean = normalize(line);
         if (clean.contains("price") || clean.contains("cost") || clean.contains("buy")) {
            for (double value : moneyAll(line)) {
               if (value >= 100.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      }

      for (String line : lore) {
         if (line.contains("$")) {
            for (double value : moneyAll(line)) {
               if (value >= 100.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      return best;
   }

   static double orderPrice(ItemStack stack) {
      List<String> lore = lore(stack);
      double best = -1.0;

      for (String line : lore) {
         String clean = normalize(line);
         if (line.contains("$") && (clean.contains("price") || clean.contains("each") || clean.contains("per item") || clean.contains("pay"))) {
            for (double value : moneyAll(line)) {
               if (value > 0.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      }

      for (String line : lore) {
         if (line.contains("$")) {
            List<Double> values = moneyAll(line);
            if (!values.isEmpty()) {
               return values.get(0);
            }
         }
      }

      return -1.0;
   }
}
