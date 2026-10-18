package net.anubis.client.module.impl.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.anubis.client.event.Stage;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.scoreboard.ScoreboardCriterion.RenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class FakeStats extends ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String OBJECTIVE = "anubis_fs";
   private static final String SHOWN_BY_DEFAULT = ".gg/anubis";
   private static final int LINES = 15;
   private static final Comparator<ScoreboardEntry> ORDER = Comparator.<ScoreboardEntry>comparingInt(ScoreboardEntry::value)
      .reversed()
      .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
   private static final Pattern MONEY = Pattern.compile("\\$\\s?(\\d[\\d.,]*[KkMmBbTtQq]?)");
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
   private static volatile FakeStats active;
   private final StringSetting playerName = this.addSetting("name", new StringSetting("Name", ".gg/anubis"));
   private final FakeStats.Stat money = this.stat("Money", "money", "balance", "$");
   private final List<FakeStats.Stat> stats = List.of(
      this.money,
      this.stat("Shards", "shards", "★"),
      this.stat("Kills", "kills", "\ud83d\udde1", "⚔"),
      this.stat("Deaths", "deaths", "☠"),
      this.stat("Playtime", "playtime", "⌚"),
      this.stat("Team", "team")
   );
   private long seed;
   private boolean applying;
   private Scoreboard board;
   private ScoreboardObjective fake;
   private ScoreboardObjective serverSidebar;
   private String sidebarMoney;
   private boolean nameReported;

   public FakeStats() {
      super("fake-stats", "Fake Stats", "Shows your own name and stats on the sidebar and tab list. Blank stats get scrambled.", ModuleCategory.DONUT);
   }

   private FakeStats.Stat stat(String name, String... markers) {
      return new FakeStats.Stat(this.addSetting(name.toLowerCase(Locale.ROOT), new StringSetting(name, ".gg/anubis")), markers);
   }

   @Override
   public boolean enabledByDefault() {
      return true;
   }

   public static boolean redirect(Scoreboard board, ScoreboardDisplaySlot slot, ScoreboardObjective objective) {
      FakeStats module = active;
      if (module == null || module.applying || slot != ScoreboardDisplaySlot.SIDEBAR) {
         return false;
      }

      if (objective != null && "anubis_fs".equals(objective.getName())) {
         return false;
      }

      module.serverSidebar = objective;
      if (objective == null) {
         return false;
      }

      ScoreboardObjective shown = module.install(board);
      if (shown == null) {
         return false;
      }

      module.applying = true;

      try {
         board.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, shown);
      } finally {
         module.applying = false;
      }

      return true;
   }

   public static Text tabName(UUID player, Text name) {
      FakeStats module = active;
      return module != null && name != null && isSelf(player) ? module.fakeTabMoney(name) : name;
   }

   public static StringVisitable tabText(StringVisitable text) {
      FakeStats module = active;
      return (StringVisitable)(module != null && text instanceof Text line ? module.fakeTabMoney(line) : text);
   }

   public static ReadableScoreboardScore tabScore(ScoreHolder holder, ReadableScoreboardScore real) {
      final FakeStats module = active;
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return module != null && real != null && player != null && player.getNameForScoreboard().equals(holder.getNameForScoreboard()) ? new ReadableScoreboardScore() {
         public int getScore() {
            return real.getScore();
         }

         public boolean isLocked() {
            return real.isLocked();
         }

         public NumberFormat getNumberFormat() {
            return real.getNumberFormat();
         }

         public MutableText getFormattedScore(NumberFormat fallback) {
            return Text.empty().append(module.fakeTabMoney(real.getFormattedScore(fallback)));
         }
      } : real;
   }

   @Override
   protected void onEnable() {
      this.seed = ThreadLocalRandom.current().nextLong();
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, event -> this.refresh());
      this.refresh();
   }

   @Override
   protected void onDisable() {
      active = null;
      this.restore();
   }

   private void refresh() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         this.board = null;
         this.fake = null;
         this.serverSidebar = null;
         this.sidebarMoney = null;
      } else {
         Scoreboard current = mc.world.getScoreboard();
         if (current != this.board) {
            this.board = current;
            this.fake = null;
            this.serverSidebar = null;
         }

         ScoreboardObjective shown = current.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (this.serverSidebar == null || this.serverSidebar.getScoreboard() != current) {
            this.serverSidebar = shown != null && !"anubis_fs".equals(shown.getName()) ? shown : null;
         }

         if (this.serverSidebar == null) {
            this.sidebarMoney = null;
            if (this.fake != null && shown == this.fake) {
               this.applying = true;

               try {
                  current.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
               } finally {
                  this.applying = false;
               }
            }
         } else {
            ScoreboardObjective installed = this.install(current);
            if (installed != null) {
               if (current.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != installed) {
                  this.applying = true;

                  try {
                     current.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, installed);
                  } finally {
                     this.applying = false;
                  }
               }
            }
         }
      }
   }

   private ScoreboardObjective install(Scoreboard current) {
      ScoreboardObjective server = this.serverSidebar;
      if (server != null && server.getScoreboard() == current) {
         ScoreboardObjective objective = current.getNullableObjective("anubis_fs");
         if (objective == null) {
            objective = current.addObjective("anubis_fs", ScoreboardCriterion.DUMMY, Text.empty(), RenderType.INTEGER, false, server.getNumberFormat());
         }

         this.fake = objective;
         this.board = current;
         objective.setNumberFormat(server.getNumberFormat());
         this.writeRows(current, objective, server);
         return objective;
      } else {
         return this.fake;
      }
   }

   private void writeRows(Scoreboard current, ScoreboardObjective objective, ScoreboardObjective server) {
      List<ScoreboardEntry> rows = new ArrayList<>();

      for (ScoreboardEntry entry : current.getScoreboardEntries(server)) {
         if (!entry.hidden()) {
            rows.add(entry);
         }
      }

      rows.sort(ORDER);
      if (rows.size() > 15) {
         rows.subList(15, rows.size()).clear();
      }

      Text title = visible(server.getDisplayName());
      Text renamedTitle = this.renamed(title);
      objective.setDisplayName(renamedTitle != null ? renamedTitle : this.mask(title, 0));
      boolean nameShown = renamedTitle != null;
      NumberFormat format = server.getNumberFormatOr(StyledNumberFormat.RED);
      this.sidebarMoney = null;

      for (int i = 0; i < rows.size(); i++) {
         ScoreboardEntry row = rows.get(i);
         Text name = visible(Team.decorateName(current.getScoreHolderTeam(row.owner()), row.name()));
         Text number = visible(row.formatted(format));
         Text renamed = this.renamed(name);
         FakeStats.Hit hit = renamed == null ? this.find(name.getString(), number.getString()) : null;
         String custom = hit != null ? hit.stat().value().get().strip() : "";
         String shown = null;
         if (renamed != null) {
            name = renamed;
            nameShown = true;
         } else if (custom.isEmpty()) {
            name = this.mask(name, i + 1);
            number = this.mask(number, i + 1);
            if (hit != null) {
               shown = (hit.inNumber() ? number : name).getString().substring(hit.start(), hit.end());
            }
         } else {
            Text target = hit.inNumber() ? number : name;
            shown = fit(target.getString().substring(hit.start(), hit.end()), custom);
            Text swapped = replace(target, List.of(new int[]{hit.start(), hit.end()}), shown);
            name = hit.inNumber() ? this.mask(name, i + 1) : swapped;
            number = hit.inNumber() ? swapped : this.mask(number, i + 1);
         }

         if (hit != null && hit.stat() == this.money) {
            this.sidebarMoney = shown;
         }

         ScoreHolder holder = ScoreHolder.fromName(holderName(i));
         current.clearTeam(holder.getNameForScoreboard());
         ScoreAccess access = current.getOrCreateScore(holder, objective);
         access.setScore(row.value());
         access.setDisplayText(name);
         access.setNumberFormat(new FixedNumberFormat(number));
      }

      for (int i = rows.size(); i < 15; i++) {
         ScoreHolder holder = ScoreHolder.fromName(holderName(i));
         if (current.getScore(holder, objective) != null) {
            current.removeScore(holder, objective);
         }
      }

      if (!nameShown && !this.playerName.get().isBlank() && !this.nameReported) {
         this.nameReported = true;
         this.reportMissingName(current, rows, format, title);
      }
   }

   private void reportMissingName(Scoreboard current, List<ScoreboardEntry> rows, NumberFormat format, Text title) {
      StringBuilder lines = new StringBuilder("title: ").append(escaped(title.getString()));

      for (ScoreboardEntry row : rows) {
         Text name = visible(Team.decorateName(current.getScoreHolderTeam(row.owner()), row.name()));
         lines.append("\n  ").append(escaped(name.getString())).append(" | ").append(escaped(visible(row.formatted(format)).getString()));
      }

      LOGGER.info("Fake Stats: no sidebar line shows the name {}\n{}", selfName(), lines);
   }

   private static String escaped(String text) {
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= ' ' && c < 127) {
            out.append(c);
         } else {
            out.append(String.format("\\u%04X", Integer.valueOf(c)));
         }
      }

      return out.toString();
   }

   private void restore() {
      Scoreboard current = this.board;
      ScoreboardObjective server = this.serverSidebar;
      ScoreboardObjective shown = this.fake;
      this.board = null;
      this.fake = null;
      this.serverSidebar = null;
      this.sidebarMoney = null;
      if (current != null) {
         if (current.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) == shown) {
            ScoreboardObjective back = server != null && server.getScoreboard() == current ? server : null;
            current.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, back);
         }

         ScoreboardObjective leftover = current.getNullableObjective("anubis_fs");
         if (leftover != null) {
            current.removeObjective(leftover);
         }
      }
   }

   private FakeStats.Hit find(String name, String number) {
      for (FakeStats.Stat stat : this.stats) {
         int[] value = valueAfter(name, stat);
         if (value != null && value[0] < value[1]) {
            return new FakeStats.Hit(stat, false, value[0], value[1]);
         }

         if (value != null) {
            return new FakeStats.Hit(stat, true, 0, number.length());
         }

         value = valueAfter(number, stat);
         if (value != null) {
            return new FakeStats.Hit(stat, true, value[0], value[1]);
         }
      }

      return null;
   }

   private static int[] valueAfter(String line, FakeStats.Stat stat) {
      for (String marker : stat.markers()) {
         int at = markerAt(line, marker);
         if (at >= 0) {
            int start = at + marker.length();
            int end = line.length();

            while (start < end && (Character.isWhitespace(line.charAt(start)) || line.charAt(start) == ':')) {
               start++;
            }

            while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
               end--;
            }

            return new int[]{start, end};
         }
      }

      return null;
   }

   private static int markerAt(String line, String marker) {
      String text = folded(line);
      boolean wordStart = wordChar(marker.charAt(0));
      boolean wordEnd = wordChar(marker.charAt(marker.length() - 1));

      for (int at = 0; at + marker.length() <= text.length(); at++) {
         int end = at + marker.length();
         if (text.regionMatches(true, at, marker, 0, marker.length())
            && (!wordStart || at <= 0 || !wordChar(text.charAt(at - 1)))
            && (!wordEnd || end >= text.length() || !wordChar(text.charAt(end)))) {
            return at;
         }
      }

      return -1;
   }

   private static String folded(String line) {
      char[] out = line.toCharArray();

      for (int i = 0; i < out.length; i++) {
         int letter = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".indexOf(out[i]);
         if (letter >= 0) {
            out[i] = (char)(97 + letter);
         }
      }

      return new String(out);
   }

   private static boolean wordChar(char c) {
      return Character.isLetterOrDigit(c) || c == '_';
   }

   private static String selfName() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      return player != null ? player.getGameProfile().name() : null;
   }

   private Text renamed(Text line) {
      String self = selfName();
      int at = self != null ? markerAt(line.getString(), self) : -1;
      if (at < 0) {
         return null;
      }

      String custom = this.playerName.get().strip();
      return (Text)(custom.isEmpty() ? line : replace(line, List.of(new int[]{at, at + self.length()}), custom));
   }

   private static String fit(String real, String custom) {
      String bare = custom.startsWith("$") ? custom.substring(1).strip() : custom;
      return real.startsWith("$") ? "$" + bare : bare;
   }

   private String tabMoney() {
      String custom = this.money.value().get().strip();
      String shown = custom.isEmpty() ? this.sidebarMoney : custom;
      if (shown == null) {
         return null;
      }

      shown = shown.startsWith("$") ? shown.substring(1).strip() : shown.strip();
      return shown.isEmpty() ? null : shown;
   }

   private Text fakeTabMoney(Text line) {
      String shown = this.tabMoney();
      if (shown != null && line.getString().indexOf(36) >= 0) {
         Text clean = visible(line);
         Matcher matcher = MONEY.matcher(clean.getString());
         List<int[]> ranges = new ArrayList<>();

         while (matcher.find()) {
            ranges.add(new int[]{matcher.start(1), matcher.end(1)});
         }

         return (Text)(ranges.isEmpty() ? line : replace(clean, ranges, shown));
      } else {
         return line;
      }
   }

   private static boolean isSelf(UUID player) {
      ClientPlayerEntity self = MinecraftClient.getInstance().player;
      return self != null && self.getUuid().equals(player);
   }

   private static Text visible(Text line) {
      if (line.getString().indexOf(167) < 0) {
         return line;
      }

      MutableText out = Text.empty();
      StringBuilder run = new StringBuilder();
      Style[] runStyle = new Style[]{null};
      TextVisitFactory.visitFormatted(line, Style.EMPTY, (index, style, codePoint) -> {
         if (runStyle[0] != null && !style.equals(runStyle[0])) {
            out.append(Text.literal(run.toString()).setStyle(runStyle[0]));
            run.setLength(0);
         }

         runStyle[0] = style;
         run.appendCodePoint(codePoint);
         return true;
      });
      if (!run.isEmpty()) {
         out.append(Text.literal(run.toString()).setStyle(runStyle[0]));
      }

      return out;
   }

   private static MutableText replace(Text line, List<int[]> ranges, String text) {
      if (line.getString().isEmpty()) {
         return Text.literal(text);
      }

      MutableText out = Text.empty();
      int[] pos = new int[]{0};
      int[] next = new int[]{0};
      line.visit((style, piece) -> {
         int start = pos[0];
         int end = start + piece.length();
         int cursor = start;

         while (cursor < end) {
            int[] range = next[0] < ranges.size() ? ranges.get(next[0]) : null;
            if (range == null || range[0] >= end) {
               out.append(Text.literal(piece.substring(cursor - start)).setStyle(style));
               cursor = end;
            } else if (cursor < range[0]) {
               out.append(Text.literal(piece.substring(cursor - start, range[0] - start)).setStyle(style));
               cursor = range[0];
            } else {
               if (cursor == range[0]) {
                  out.append(Text.literal(text).setStyle(style));
               }

               cursor = Math.min(end, range[1]);
               if (cursor == range[1]) {
                  next[0]++;
               }
            }
         }

         pos[0] = end;
         return Optional.empty();
      }, Style.EMPTY);
      return out;
   }

   private static String holderName(int row) {
      return new String(new char[]{'\ue000', (char)(57360 + row)});
   }

   private Text mask(Text line, int row) {
      if (line == null) {
         return Text.empty();
      }

      MutableText out = Text.empty();
      boolean[] changed = new boolean[]{false};
      int[] number = new int[]{0};
      line.visit((style, text) -> {
         String shown = this.maskText(text, row, number);
         if (!shown.equals(text)) {
            changed[0] = true;
         }

         out.append(Text.literal(shown).setStyle(style));
         return Optional.empty();
      }, Style.EMPTY);
      return (Text)(changed[0] ? out : line);
   }

   private String maskText(String text, int row, int[] number) {
      StringBuilder out = null;
      int i = 0;

      while (i < text.length()) {
         int cp = text.codePointAt(i);
         if (!Character.isDigit(cp)) {
            if (out != null) {
               out.appendCodePoint(cp);
            }

            i += Character.charCount(cp);
         } else {
            int start = i;
            int end = i;

            while (true) {
               if (end < text.length()) {
                  int c = text.codePointAt(end);
                  int width = Character.charCount(c);
                  if (Character.isDigit(c)) {
                     end += width;
                     continue;
                  }

                  if ((c == 44 || c == 46) && end + width < text.length() && Character.isDigit(text.codePointAt(end + width))) {
                     end += width;
                     continue;
                  }
               }

               String raw = text.substring(start, end);
               String fake = this.fakeNumber(row, (int)(number[0]++), raw);
               if (out == null) {
                  if (!fake.equals(raw)) {
                     out = new StringBuilder(text.length()).append(text, 0, start).append(fake);
                  }
               } else {
                  out.append(fake);
               }

               i = end;
               break;
            }
         }
      }

      return out == null ? text : out.toString();
   }

   private String fakeNumber(int row, int index, String raw) {
      int digits = 0;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            digits++;
         }
      }

      if (digits == 0) {
         return raw;
      }

      if (digits > 15) {
         return this.shiftWide(row, index, raw);
      }

      long value = 0L;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            value = value * 10L + (c - '0');
         }
      }

      long span = pow10(digits);
      long offset = Long.remainderUnsigned(mix(this.seed ^ row * 1315423911L ^ index * 2654435761L ^ digits), span);
      if (offset == 0L) {
         offset = 1L;
      }

      boolean leadingZero = raw.charAt(0) == '0' && digits > 1;
      long fake = shifted(value, offset, span, digits, leadingZero);
      if (fake == value) {
         fake = shifted(value, offset == span - 1L ? 1L : offset + 1L, span, digits, leadingZero);
      }

      String rendered = Long.toString(fake);
      if (rendered.length() < digits) {
         rendered = "0".repeat(digits - rendered.length()) + rendered;
      }

      StringBuilder result = new StringBuilder(raw.length());
      int cursor = 0;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            result.append(rendered.charAt(cursor++));
         } else {
            result.append(c);
         }
      }

      return result.toString();
   }

   private static long shifted(long value, long offset, long span, int digits, boolean leadingZero) {
      long fake = Math.floorMod(value + offset, span);
      if (!leadingZero && digits > 1 && fake < span / 10L) {
         fake += span / 10L;
      }

      return fake;
   }

   private String shiftWide(int row, int index, String raw) {
      long salt = mix(this.seed ^ row * 1315423911L ^ index);
      StringBuilder result = new StringBuilder(raw.length());
      int pos = 0;
      boolean first = true;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c >= '0' && c <= '9') {
            int shift = (int)Long.remainderUnsigned(mix(salt + pos), 9L) + 1;
            int shown = (c - '0' + shift) % 10;
            if (first && shown == 0) {
               shown = shift;
            }

            first = false;
            result.append((char)(48 + shown));
            pos++;
         } else {
            result.append(c);
         }
      }

      return result.toString();
   }

   private static long pow10(int digits) {
      long value = 1L;

      for (int i = 0; i < digits; i++) {
         value *= 10L;
      }

      return value;
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }

   @Environment(EnvType.CLIENT)
   private record Hit(FakeStats.Stat stat, boolean inNumber, int start, int end) {
   }

   @Environment(EnvType.CLIENT)
   private record Stat(StringSetting value, String... markers) {
   }
}
