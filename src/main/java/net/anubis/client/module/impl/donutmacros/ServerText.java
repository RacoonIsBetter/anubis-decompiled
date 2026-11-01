package net.anubis.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
final class ServerText {
   private static final Pattern FORMATTING = Pattern.compile("§.");
   private static final Pattern MONEY_EXACT = Pattern.compile("^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmb])?$");
   private static final Pattern MONEY_ANY = Pattern.compile("\\$?[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmb])?(?![a-z])");
   private static final Pattern DOLLARS = Pattern.compile("\\$[ \\t]*([0-9][0-9,]*(?:\\.[0-9]+)?)[ \\t]*([kmb])?(?![a-z])");
   private static final String[] NOT_PRICES = new String[]{"balance", "you have", "your money", "wallet", "bank"};
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

   static List<Double> dollars(String raw) {
      List<Double> out = new ArrayList<>();
      if (raw == null) {
         return out;
      }

      Matcher matcher = DOLLARS.matcher(normalize(raw));

      while (matcher.find()) {
         double value = scale(matcher.group(1), matcher.group(2));
         if (value > 0.0) {
            out.add(value);
         }
      }

      return out;
   }

   static double askingPrice(List<String> lines) {
      double worst = -1.0;

      for (String line : lines) {
         String clean = normalize(line);
         boolean skip = false;

         for (String word : NOT_PRICES) {
            if (clean.contains(word)) {
               skip = true;
               break;
            }
         }

         if (!skip) {
            for (double value : clean.contains("$") ? dollars(clean) : (!clean.contains("price") && !clean.contains("cost") ? List.<Double>of() : moneyAll(clean))) {
               if (value > worst) {
                  worst = value;
               }
            }
         }
      }

      return worst;
   }

   static double askingPrice(ItemStack stack) {
      return askingPrice(lore(stack));
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

      for (Text line : stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).lines()) {
         out.add(line.getString());
      }

      return out;
   }

   static double listingPrice(ItemStack stack) {
      List<String> lore = lore(stack);

      for (String line : lore) {
         String clean = withoutBullet(normalize(line));
         if (clean.startsWith("price")) {
            List<Double> values = clean.contains("$") ? dollars(clean) : moneyAll(clean.substring("price".length()));
            return values.isEmpty() ? -1.0 : values.get(0);
         }
      }

      double found = -1.0;

      for (String line : lore) {
         String clean = normalize(line);
         if (clean.contains("$") && !clean.contains("balance")) {
            for (double value : dollars(clean)) {
               if (found > 0.0 && Math.abs(found - value) >= 1.0) {
                  return -1.0;
               }

               found = value;
            }
         }
      }

      return found;
   }

   private static String withoutBullet(String text) {
      int start = 0;

      while (start < text.length() && !Character.isLetterOrDigit(text.charAt(start))) {
         start++;
      }

      return text.substring(start);
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
