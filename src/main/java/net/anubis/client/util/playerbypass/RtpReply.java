package net.anubis.client.util.playerbypass;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record RtpReply(RtpReply.Kind kind, long millis, long nextUseMs) {
   public static final RtpReply NONE = new RtpReply(RtpReply.Kind.NONE, 0L);
   private static final Pattern FORMATTING = Pattern.compile("§.");
   private static final Pattern DURATION = Pattern.compile(
      "(\\d+(?:[.,]\\d+)?)\\s*(hours?|hrs?|saat|h|minutes?|mins?|dakika|dk|m|seconds?|secs?|saniye|sn|s)(?![a-z])"
   );
   private static final Pattern CLOCK = Pattern.compile("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])");
   private static final Pattern BRACKET_TIME = Pattern.compile("[\\[(<]\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[ap]m)?\\s*[\\])>]");
   private static final Pattern LEADING_TIME = Pattern.compile("^\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s?[ap]m)?(?![\\d:])(?!\\s*(?:left|remaining|kaldi|kalan))");
   private static final Pattern SENTENCE = Pattern.compile("\\.(?!\\d)|[!?|;]");
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕꜱᴛᴜᴠᴡʏᴢ";
   private static final String SMALL_CAPS_PLAIN = "abcdefghijklmnopqrsstuvwyz";
   private static final String TURKISH = "ıİşŞğĞüÜöÖçÇ";
   private static final String TURKISH_PLAIN = "iissgguuoocc";
   private static final String CHAT_SEPARATORS = ":>»›≫➤➜→⇒|";
   private static final String[] UNKNOWN = new String[]{
      "unknown command", "unknown or incomplete command", "bilinmeyen komut", "komut bulunamadi", "boyle bir komut yok"
   };
   private static final String[] NO_PERMISSION = new String[]{
      "no permission",
      "don't have permission",
      "dont have permission",
      "do not have permission",
      "not have permission",
      "insufficient permission",
      "not allowed to use",
      "yetkin yok",
      "yetkiniz yok",
      "iznin yok",
      "izniniz yok",
      "yetkiniz bulunmuyor",
      "izniniz bulunmuyor"
   };
   private static final String[] COMBAT = new String[]{"combat", "savasta", "savas modunda", "pvp modunda", "pvp'de", "pvpde", "kombat"};
   private static final String[] COMBAT_STRONG = new String[]{
      "in combat", "combat tag", "combat-tag", "combattag", "savasta", "savas modunda", "pvp modunda", "pvp'de", "pvpde"
   };
   private static final String[] COMBAT_OVER = new String[]{
      "no longer in combat", "out of combat", "left combat", "not in combat", "savastan ciktin", "artik savasta degil"
   };
   private static final String[] COOLDOWN_STRONG = new String[]{
      "cooldown", "cool down", "again", "another", "next teleport", "next rtp", "next use", "tekrar", "yeniden", "bekleme suresi"
   };
   private static final String[] COOLDOWN_WEAK = new String[]{"wait", "bekle", "before", "until", "remaining", "kalan", "sonra"};
   private static final String[] WARMUP = new String[]{
      "teleporting in",
      "teleporting you in",
      "teleport in",
      "teleported in",
      "teleportation in",
      "teleportation will",
      "teleport will",
      "will be teleported",
      "will teleport",
      "before teleporting",
      "before you teleport",
      "before being teleported",
      "don't move",
      "dont move",
      "do not move",
      "stand still",
      "isinlaniyor",
      "isinlanacak",
      "isinlanmaya",
      "hareket etme"
   };
   private static final String[] PROGRESS = new String[]{"teleporting", "searching", "looking for a", "finding a", "araniyor", "konum bul"};
   private static final String[] CANCEL = new String[]{"cancel", "iptal", "you moved", "hareket ettin", "aborted"};

   public RtpReply(RtpReply.Kind kind, long millis) {
      this(kind, millis, -1L);
   }

   public static RtpReply parse(String raw) {
      String text = normalize(raw);
      if (text.isEmpty()) {
         return NONE;
      }

      if (containsAny(text, UNKNOWN)) {
         return new RtpReply(RtpReply.Kind.UNKNOWN_COMMAND, 0L);
      }

      if (containsAny(text, NO_PERMISSION)) {
         return new RtpReply(RtpReply.Kind.NO_PERMISSION, 0L);
      }

      text = LEADING_TIME.matcher(BRACKET_TIME.matcher(text).replaceAll(" ").trim()).replaceFirst("").trim();
      String[] sentences = SENTENCE.split(text);
      long any = -1L;
      long strong = -1L;
      long warm = -1L;
      long weak = -1L;

      for (String sentence : sentences) {
         long ms = durationMs(sentence);
         if (ms >= 0L) {
            if (any < 0L) {
               any = ms;
            }

            if (containsAny(sentence, COOLDOWN_STRONG)) {
               if (strong < 0L) {
                  strong = ms;
               }
            } else if (containsAny(sentence, WARMUP)) {
               if (warm < 0L) {
                  warm = ms;
               }
            } else if (containsAny(sentence, COOLDOWN_WEAK) && weak < 0L) {
               weak = ms;
            }
         }
      }

      boolean warmupWords = containsAny(text, WARMUP);
      if (!containsAny(text, COMBAT) || containsAny(text, COMBAT_OVER) || warmupWords && (warm >= 0L || !containsAny(text, COMBAT_STRONG))) {
         boolean cancel = containsAny(text, CANCEL);
         if (any < 0L) {
            if (cancel) {
               return new RtpReply(RtpReply.Kind.CANCELLED, 0L);
            } else {
               return !warmupWords && !containsAny(text, PROGRESS) ? NONE : new RtpReply(RtpReply.Kind.WARMUP, 0L);
            }
         } else if (warm >= 0L && strong >= 0L) {
            return new RtpReply(RtpReply.Kind.WARMUP, warm, strong);
         } else if (containsAny(text, COOLDOWN_STRONG)) {
            return new RtpReply(RtpReply.Kind.COOLDOWN, strong >= 0L ? strong : any);
         } else if (warmupWords) {
            return new RtpReply(RtpReply.Kind.WARMUP, warm >= 0L ? warm : any);
         } else if (cancel) {
            return new RtpReply(RtpReply.Kind.CANCELLED, 0L);
         } else {
            return containsAny(text, COOLDOWN_WEAK) ? new RtpReply(RtpReply.Kind.COOLDOWN, weak >= 0L ? weak : any) : NONE;
         }
      } else {
         return new RtpReply(RtpReply.Kind.COMBAT, 0L);
      }
   }

   public static long durationMs(String normalized) {
      Matcher matcher = DURATION.matcher(normalized);
      double total = 0.0;
      boolean found = false;

      while (matcher.find()) {
         double value;
         try {
            value = Double.parseDouble(matcher.group(1).replace(',', '.'));
         } catch (NumberFormatException e) {
            continue;
         }

         total += value * unitMs(matcher.group(2));
         found = true;
      }

      if (found) {
         return Math.round(total);
      }

      Matcher clock = CLOCK.matcher(normalized);
      return clock.find() ? (Integer.parseInt(clock.group(1)) * 60L + Integer.parseInt(clock.group(2))) * 1000L : -1L;
   }

   private static long unitMs(String unit) {
      return switch (unit) {
         case "h", "hr", "hrs", "hour", "hours", "saat" -> 3600000L;
         case "m", "min", "mins", "minute", "minutes", "dakika", "dk" -> 60000L;
         default -> 1000L;
      };
   }

   public static boolean looksLikePlayerChat(String raw, Iterable<String> names) {
      String text = normalize(raw);
      if (text.isEmpty()) {
         return false;
      }

      for (String name : names) {
         if (name != null && !name.isBlank()) {
            String needle = name.toLowerCase(Locale.ROOT);

            for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
               int end = at + needle.length();
               if ((at <= 0 || !nameChar(text.charAt(at - 1))) && (end >= text.length() || !nameChar(text.charAt(end))) && separatorAfter(text, end)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean nameChar(char c) {
      return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
   }

   private static boolean separatorAfter(String text, int from) {
      int i = from;

      while (i < text.length() && i - from < 3 && " ])}".indexOf(text.charAt(i)) >= 0) {
         i++;
      }

      if (i >= text.length()) {
         return false;
      }

      char c = text.charAt(i);
      return ":>»›≫➤➜→⇒|".indexOf(c) >= 0 ? true : (c == '-' || c == '=') && i + 1 < text.length() && text.charAt(i + 1) == '>';
   }

   public static String normalize(String raw) {
      if (raw == null) {
         return "";
      }

      String text = FORMATTING.matcher(raw).replaceAll("");
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         int small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕꜱᴛᴜᴠᴡʏᴢ".indexOf(c);
         int turkish = "ıİşŞğĞüÜöÖçÇ".indexOf(c);
         if (small >= 0) {
            out.append("abcdefghijklmnopqrsstuvwyz".charAt(small));
         } else if (turkish >= 0) {
            out.append("iissgguuoocc".charAt(turkish));
         } else if (c == 8217 || c == 8216) {
            out.append('\'');
         } else if (c != 775) {
            out.append(c);
         }
      }

      return out.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
   }

   private static boolean containsAny(String text, String[] words) {
      for (String word : words) {
         if (text.contains(word)) {
            return true;
         }
      }

      return false;
   }

   @Environment(EnvType.CLIENT)
   public enum Kind {
      NONE,
      COOLDOWN,
      WARMUP,
      CANCELLED,
      UNKNOWN_COMMAND,
      NO_PERMISSION,
      COMBAT;
   }
}
