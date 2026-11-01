package net.anubis.client.module.impl.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Ellipse2D.Float;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
enum SkyEmoji {
   BROKEN_HEART(Set.of("heartbreak", "heartbroken", "brokenhearted"), Set.of("kırık", "kirik", "yıkıl", "paramparça")),
   HEART(Set.of("heart", "hearts"), Set.of("kalp", "kalb")),
   KISS(Set.of("kiss", "kisses", "kissing", "kissed"), Set.of("öp")),
   HUG(Set.of("hug", "hugs", "hold", "holding", "embrace", "cuddle"), Set.of("sarıl", "kucak")),
   LOVE(Set.of("love", "loved", "lover", "lovers", "loving", "baby", "darling", "honey"), Set.of("aşk", "sevgi", "seviyor", "sevdi")),
   SHY(Set.of("shy", "blush", "blushing", "crush", "cute"), Set.of("utan", "tatlı")),
   CRY(Set.of("cry", "cried", "crying", "cries", "tears", "tear", "weep"), Set.of("ağla", "gözyaş")),
   SAD(Set.of("sad", "lonely", "alone", "pain", "hurt", "hurts", "sorrow", "goodbye"), Set.of("üzgün", "yalnız", "acı", "hüzün", "keder", "elveda")),
   LAUGH(Set.of("laugh", "laughing", "haha", "funny", "lol", "smile", "smiling"), Set.of("gülüyor", "gülerim", "kahkaha", "gülümse")),
   DEVIL(
      Set.of("sin", "sins", "sinner", "sinners", "devil", "hell", "evil", "demon", "danger", "dangerous", "wicked"),
      Set.of("şeytan", "günah", "cehennem", "tehlike")
   ),
   FIRE(Set.of("fire", "burn", "burning", "flame", "flames", "lit", "blaze"), Set.of("ateş", "alev", "yanıyor", "yakıyor")),
   ANGRY(Set.of("angry", "mad", "hate", "rage", "fury"), Set.of("kızgın", "nefret", "öfke", "sinir")),
   SHOCK(Set.of("wow", "omg", "shock", "shocked", "crazy", "insane"), Set.of("şok", "inanılmaz", "çılgın")),
   COOL(Set.of("cool", "swag", "drip", "boss", "flex", "fresh"), Set.of("havalı", "efsane")),
   MONEY(Set.of("money", "cash", "rich", "dollar", "dollars", "millionaire", "paid"), Set.of("zengin", "dolar", "parayı", "paralar")),
   STAR(Set.of("star", "stars", "shine", "shining", "sparkle"), Set.of("yıldız", "parla")),
   MOON(Set.of("moon", "night", "midnight", "tonight", "dream", "dreams", "dreaming"), Set.of("gece", "rüya", "mehtap")),
   SLEEP(Set.of("sleep", "sleeping", "tired", "asleep"), Set.of("uyku", "uyuyor", "uyudu", "yorgun")),
   THINK(Set.of("think", "thinking", "thought", "wonder", "why", "maybe", "guess"), Set.of("düşün", "neden", "belki", "acaba")),
   MUSIC(Set.of("dance", "dancing", "song", "songs", "music", "sing", "singing", "melody", "beat"), Set.of("dans", "şarkı", "müzik", "melodi"));

   static final int SIZE = 256;
   private static final String IMAGES = "/assets/anubis/textures/sky_emoji/";
   private static final int CANVAS = 192;
   private static final Pattern WORD = Pattern.compile("\\p{L}+");
   private static final String[][] PHRASES = new String[][]{{"broken heart", "broken heart"}, {"break my heart", "heart"}, {"kalbim kırık", "kırık"}};
   private static final Color INK = new Color(662080);
   private static final Color TEAR = new Color(10479359);
   private static final Color RED = new Color(16726876);
   private final Set<String> words;
   private final Set<String> stems;

   SkyEmoji(Set<String> words, Set<String> stems) {
      this.words = words;
      this.stems = stems;
   }

   static SkyEmoji.Match match(String line) {
      for (String[] phrase : PHRASES) {
         Matcher found = Pattern.compile(Pattern.quote(phrase[0]), 66).matcher(line);
         if (found.find()) {
            int start = found.start() + phrase[0].indexOf(phrase[1]);
            return new SkyEmoji.Match(BROKEN_HEART, start, start + phrase[1].length());
         }
      }

      List<int[]> spans = new ArrayList<>();
      List<String> tokens = new ArrayList<>();
      Matcher word = WORD.matcher(line);

      while (word.find()) {
         spans.add(new int[]{word.start(), word.end()});
         tokens.add(word.group().toLowerCase(Locale.ROOT).replace("i̇", "i"));
      }

      for (SkyEmoji emoji : values()) {
         for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            boolean hit = emoji.words.contains(token);

            for (String stem : emoji.stems) {
               hit |= token.startsWith(stem);
            }

            if (hit) {
               return new SkyEmoji.Match(emoji, spans.get(i)[0], spans.get(i)[1]);
            }
         }
      }

      return null;
   }

   int[] draw() {
      BufferedImage picture = this.picture();
      if (picture != null || this != HUG && this != SHY && this != THINK) {
         BufferedImage image = new BufferedImage(256, 256, 2);
         Graphics2D g = image.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
         g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         if (picture != null) {
            g.drawImage(picture, 0, 0, 256, 256, null);
            g.dispose();
            return image.getRGB(0, 0, 256, 256, null, 0, 256);
         }

         g.scale(1.3333333333333333, 1.3333333333333333);
         switch (this) {
            case BROKEN_HEART:
               heart(g, 96.0F, 100.0F, 56.0F, true);
               break;
            case HEART:
               heart(g, 96.0F, 100.0F, 64.0F, false);
               break;
            case KISS:
            case HUG:
            case LOVE:
            case SHY:
            case CRY:
            case SAD:
            case LAUGH:
            case DEVIL:
            case ANGRY:
            case SHOCK:
            case COOL:
            case MONEY:
            case SLEEP:
            case THINK:
            default:
               this.face(g);
               break;
            case FIRE:
               flame(g);
               break;
            case STAR:
               star(g);
               break;
            case MOON:
               moon(g);
               break;
            case MUSIC:
               notes(g);
         }

         g.dispose();
         return image.getRGB(0, 0, 256, 256, null, 0, 256);
      } else {
         return null;
      }
   }

   private BufferedImage picture() {
      try (InputStream in = SkyEmoji.class.getResourceAsStream("/assets/anubis/textures/sky_emoji/" + this.name().toLowerCase(Locale.ROOT) + ".png")) {
         return in == null ? null : ImageIO.read(in);
      } catch (IOException e) {
         return null;
      }
   }

   private void face(Graphics2D g) {
      if (this == DEVIL) {
         horns(g);
      }

      sphere(g, 96.0F, 100.0F, 72.0F);
      switch (this) {
         case KISS:
            closedEye(g, 72.0F, 92.0F, 13.0F, true);
            eye(g, 120.0F, 90.0F, 11.0F, 14.0F, 0.0F, 0.0F);
            kissLips(g, 102.0F, 128.0F);
            heart(g, 150.0F, 62.0F, 12.0F, false);
            break;
         case HUG:
         case SHY:
         case FIRE:
         case STAR:
         case MOON:
         default:
            eye(g, 72.0F, 92.0F, 11.0F, 14.0F, 0.0F, 0.0F);
            eye(g, 120.0F, 92.0F, 11.0F, 14.0F, 0.0F, 0.0F);
            break;
         case LOVE:
            heart(g, 70.0F, 90.0F, 20.0F, false);
            heart(g, 122.0F, 90.0F, 20.0F, false);
            openSmile(g, 96.0F, 122.0F, 30.0F, 18.0F, true);
            break;
         case CRY:
            tears(g, 72.0F, 98.0F);
            tears(g, 120.0F, 98.0F);
            closedEye(g, 72.0F, 92.0F, 13.0F, false);
            closedEye(g, 120.0F, 92.0F, 13.0F, false);
            brows(g, 72.0F, 72.0F, 120.0F, 72.0F, -0.35F);
            openFrown(g, 96.0F, 134.0F, 22.0F, 13.0F);
            break;
         case SAD:
            droopyEye(g, 72.0F, 94.0F);
            droopyEye(g, 120.0F, 94.0F);
            brows(g, 72.0F, 74.0F, 120.0F, 74.0F, -0.3F);
            frown(g, 96.0F, 134.0F, 18.0F);
            drop(g, 62.0F, 118.0F, 7.0F);
            break;
         case LAUGH:
            happyEye(g, 72.0F, 90.0F);
            happyEye(g, 120.0F, 90.0F);
            laughMouth(g, 96.0F, 120.0F);
            drop(g, 44.0F, 98.0F, 8.0F);
            drop(g, 148.0F, 98.0F, 8.0F);
            break;
         case DEVIL:
            eye(g, 72.0F, 94.0F, 11.0F, 9.0F, 0.2F, 0.0F);
            eye(g, 120.0F, 94.0F, 11.0F, 9.0F, -0.2F, 0.0F);
            brows(g, 72.0F, 78.0F, 120.0F, 78.0F, 0.45F);
            smirk(g, 96.0F, 126.0F, true);
            break;
         case ANGRY:
            eye(g, 72.0F, 96.0F, 11.0F, 9.0F, 0.15F, 0.0F);
            eye(g, 120.0F, 96.0F, 11.0F, 9.0F, -0.15F, 0.0F);
            brows(g, 72.0F, 78.0F, 120.0F, 78.0F, 0.6F);
            teeth(g, 96.0F, 130.0F);
            vein(g, 146.0F, 56.0F);
            break;
         case SHOCK:
            eye(g, 72.0F, 90.0F, 15.0F, 18.0F, 0.0F, 0.0F);
            eye(g, 120.0F, 90.0F, 15.0F, 18.0F, 0.0F, 0.0F);
            brows(g, 72.0F, 64.0F, 120.0F, 64.0F, -0.15F);
            ring(g, 96.0F, 134.0F, 13.0F, 16.0F);
            break;
         case COOL:
            shades(g);
            smirk(g, 98.0F, 128.0F, false);
            break;
         case MONEY:
            dollarEyes(g);
            openSmile(g, 96.0F, 122.0F, 30.0F, 18.0F, false);
            banknoteTongue(g, 96.0F, 138.0F);
            break;
         case SLEEP:
            closedEye(g, 72.0F, 94.0F, 13.0F, true);
            closedEye(g, 120.0F, 94.0F, 13.0F, true);
            ring(g, 96.0F, 130.0F, 6.0F, 7.0F);
            zz(g);
      }
   }

   private static void sphere(Graphics2D g, float cx, float cy, float r) {
      for (int i = 6; i >= 1; i--) {
         float grow = i * 2.2F;
         g.setColor(new Color(0, 10, 40, 12));
         g.fill(new Float(cx - r - grow * 0.5F, cy - r + 6.0F - grow * 0.3F, (r + grow * 0.5F) * 2.0F, (r + grow * 0.4F) * 2.0F));
      }

      Float ball = new Float(cx - r, cy - r, r * 2.0F, r * 2.0F);
      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Float(cx - r * 0.35F, cy - r * 0.45F),
            r * 1.55F,
            new float[]{0.0F, 0.35F, 0.75F, 1.0F},
            new Color[]{new Color(11132159), new Color(4101621), new Color(1791689), new Color(798078)}
         )
      );
      g.fill(ball);
      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Float(cx, cy + r * 0.9F), r * 1.1F, new float[]{0.0F, 1.0F}, new Color[]{new Color(7327999), new Color(7327999, true)}
         )
      );
      g.fill(ball);
      g.setStroke(new BasicStroke(2.5F));
      g.setColor(new Color(666222));
      g.draw(ball);
      AffineTransform keep = g.getTransform();
      g.rotate(-0.5, cx - r * 0.42F, cy - r * 0.55F);
      g.setPaint(new GradientPaint(0.0F, cy - r * 0.8F, new Color(255, 255, 255, 190), 0.0F, cy - r * 0.25F, new Color(255, 255, 255, 0)));
      g.fill(new Float(cx - r * 0.75F, cy - r * 0.8F, r * 0.7F, r * 0.42F));
      g.setTransform(keep);
   }

   private static void eye(Graphics2D g, float cx, float cy, float rx, float ry, float tilt, float look) {
      AffineTransform keep = g.getTransform();
      g.rotate(tilt, cx, cy);
      g.setColor(Color.WHITE);
      g.fill(new Float(cx - rx, cy - ry, rx * 2.0F, ry * 2.0F));
      g.setColor(INK);
      float pr = Math.min(rx, ry) * 0.62F;
      g.fill(new Float(cx - pr + look, cy - pr + ry * 0.15F, pr * 2.0F, pr * 2.0F));
      g.setColor(Color.WHITE);
      g.fill(new Float(cx - pr * 0.25F + look, cy - pr * 0.55F + ry * 0.15F, pr * 0.6F, pr * 0.6F));
      g.setTransform(keep);
   }

   private static void closedEye(Graphics2D g, float cx, float cy, float w, boolean smile) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(5.0F, 1, 1));
      float bend = smile ? 8.0F : -8.0F;
      g.draw(new java.awt.geom.QuadCurve2D.Float(cx - w, cy, cx, cy + bend, cx + w, cy));
   }

   private static void happyEye(Graphics2D g, float cx, float cy) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(6.0F, 1, 1));
      g.draw(new java.awt.geom.QuadCurve2D.Float(cx - 13.0F, cy + 6.0F, cx, cy - 14.0F, cx + 13.0F, cy + 6.0F));
   }

   private static void droopyEye(Graphics2D g, float cx, float cy) {
      Area eye = new Area(new Float(cx - 11.0F, cy - 13.0F, 22.0F, 26.0F));
      eye.subtract(new Area(new Float(cx - 16.0F, cy - 26.0F, 32.0F, 22.0F)));
      g.setColor(Color.WHITE);
      g.fill(eye);
      g.setColor(INK);
      g.fill(new Float(cx - 6.0F, cy - 2.0F, 12.0F, 12.0F));
      g.setStroke(new BasicStroke(3.5F, 1, 1));
      g.draw(new java.awt.geom.QuadCurve2D.Float(cx - 12.0F, cy - 5.0F, cx, cy - 1.0F, cx + 12.0F, cy - 5.0F));
   }

   private static void brows(Graphics2D g, float lx, float ly, float rx, float ry, float angle) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(6.0F, 1, 1));
      float dx = 12.0F;
      float dy = 12.0F * angle;
      g.draw(new java.awt.geom.QuadCurve2D.Float(lx - dx, ly - dy, lx, ly - 3.0F, lx + dx, ly + dy));
      g.draw(new java.awt.geom.QuadCurve2D.Float(rx - dx, ry + dy, rx, ry - 3.0F, rx + dx, ry - dy));
   }

   private static void openSmile(Graphics2D g, float cx, float cy, float w, float h, boolean tongue) {
      GeneralPath mouth = new GeneralPath();
      mouth.moveTo(cx - w, cy);
      mouth.quadTo(cx, cy + h * 2.2F, cx + w, cy);
      mouth.quadTo(cx, cy + h * 0.4F, cx - w, cy);
      mouth.closePath();
      g.setColor(new Color(2755102));
      g.fill(mouth);
      if (tongue) {
         Shape keep = g.getClip();
         g.clip(mouth);
         g.setColor(new Color(16740241));
         g.fill(new Float(cx - w * 0.5F, cy + h * 0.7F, w, h * 1.4F));
         g.setClip(keep);
      }

      g.setColor(INK);
      g.setStroke(new BasicStroke(3.0F, 1, 1));
      g.draw(mouth);
   }

   private static void openFrown(Graphics2D g, float cx, float cy, float w, float h) {
      GeneralPath mouth = new GeneralPath();
      mouth.moveTo(cx - w, cy + h * 0.6F);
      mouth.quadTo(cx, cy - h * 1.4F, cx + w, cy + h * 0.6F);
      mouth.quadTo(cx, cy + h * 0.2F, cx - w, cy + h * 0.6F);
      mouth.closePath();
      g.setColor(new Color(2755102));
      g.fill(mouth);
      g.setColor(INK);
      g.setStroke(new BasicStroke(3.0F, 1, 1));
      g.draw(mouth);
   }

   private static void frown(Graphics2D g, float cx, float cy, float w) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(6.0F, 1, 1));
      g.draw(new java.awt.geom.QuadCurve2D.Float(cx - w, cy + 4.0F, cx, cy - 10.0F, cx + w, cy + 4.0F));
   }

   private static void smirk(Graphics2D g, float cx, float cy, boolean wide) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(6.0F, 1, 1));
      float w = wide ? 30.0F : 22.0F;
      GeneralPath path = new GeneralPath();
      path.moveTo(cx - w, cy - 2.0F);
      path.curveTo(cx - w * 0.3F, cy + 10.0F, cx + w * 0.5F, cy + 8.0F, cx + w, cy - 10.0F);
      g.draw(path);
   }

   private static void laughMouth(Graphics2D g, float cx, float cy) {
      GeneralPath mouth = new GeneralPath();
      mouth.moveTo(cx - 34.0F, cy - 4.0F);
      mouth.lineTo(cx + 34.0F, cy - 4.0F);
      mouth.quadTo(cx + 30.0F, cy + 38.0F, cx, cy + 38.0F);
      mouth.quadTo(cx - 30.0F, cy + 38.0F, cx - 34.0F, cy - 4.0F);
      mouth.closePath();
      g.setColor(new Color(2755102));
      g.fill(mouth);
      Shape keep = g.getClip();
      g.clip(mouth);
      g.setColor(Color.WHITE);
      g.fill(new java.awt.geom.RoundRectangle2D.Float(cx - 34.0F, cy - 6.0F, 68.0F, 13.0F, 6.0F, 6.0F));
      g.setColor(new Color(16740241));
      g.fill(new Float(cx - 18.0F, cy + 18.0F, 36.0F, 26.0F));
      g.setClip(keep);
      g.setColor(INK);
      g.setStroke(new BasicStroke(3.0F, 1, 1));
      g.draw(mouth);
   }

   private static void teeth(Graphics2D g, float cx, float cy) {
      java.awt.geom.RoundRectangle2D.Float mouth = new java.awt.geom.RoundRectangle2D.Float(cx - 26.0F, cy - 10.0F, 52.0F, 20.0F, 12.0F, 12.0F);
      g.setColor(Color.WHITE);
      g.fill(mouth);
      g.setColor(INK);
      g.setStroke(new BasicStroke(2.5F));
      g.draw(mouth);
      g.drawLine((int)(cx - 26.0F), (int)cy, (int)(cx + 26.0F), (int)cy);

      for (int i = -2; i <= 2; i++) {
         g.drawLine((int)(cx + i * 9.0F), (int)(cy - 10.0F), (int)(cx + i * 9.0F), (int)(cy + 10.0F));
      }
   }

   private static void ring(Graphics2D g, float cx, float cy, float rx, float ry) {
      g.setColor(new Color(2755102));
      g.fill(new Float(cx - rx, cy - ry, rx * 2.0F, ry * 2.0F));
      g.setColor(INK);
      g.setStroke(new BasicStroke(3.0F));
      g.draw(new Float(cx - rx, cy - ry, rx * 2.0F, ry * 2.0F));
   }

   private static void kissLips(Graphics2D g, float cx, float cy) {
      g.setColor(INK);
      g.setStroke(new BasicStroke(5.5F, 1, 1));
      GeneralPath lips = new GeneralPath();
      lips.moveTo(cx - 6.0F, cy - 12.0F);
      lips.quadTo(cx + 10.0F, cy - 10.0F, cx + 2.0F, cy - 2.0F);
      lips.quadTo(cx + 12.0F, cy + 4.0F, cx - 6.0F, cy + 10.0F);
      g.draw(lips);
   }

   private static void tears(Graphics2D g, float cx, float cy) {
      g.setPaint(new GradientPaint(0.0F, cy, new Color(13235199), 0.0F, cy + 70.0F, new Color(5227519)));
      g.fill(new java.awt.geom.RoundRectangle2D.Float(cx - 7.0F, cy, 14.0F, 66.0F, 14.0F, 14.0F));
   }

   private static void drop(Graphics2D g, float cx, float cy, float r) {
      GeneralPath drop = new GeneralPath();
      drop.moveTo(cx, cy - r * 2.2F);
      drop.curveTo(cx + r * 1.2F, cy - r * 0.6F, cx + r, cy + r, cx, cy + r);
      drop.curveTo(cx - r, cy + r, cx - r * 1.2F, cy - r * 0.6F, cx, cy - r * 2.2F);
      g.setColor(TEAR);
      g.fill(drop);
      g.setColor(new Color(255, 255, 255, 200));
      g.fill(new Float(cx - r * 0.45F, cy - r * 0.4F, r * 0.5F, r * 0.6F));
   }

   private static void shades(Graphics2D g) {
      GeneralPath lens = new GeneralPath();
      lens.moveTo(42.0F, 80.0F);
      lens.lineTo(90.0F, 80.0F);
      lens.quadTo(90.0F, 108.0F, 68.0F, 108.0F);
      lens.quadTo(44.0F, 108.0F, 42.0F, 80.0F);
      lens.closePath();
      GeneralPath right = (GeneralPath)lens.clone();
      right.transform(AffineTransform.getTranslateInstance(60.0, 0.0));
      g.setColor(new Color(724764));
      g.fill(lens);
      g.fill(right);
      g.setStroke(new BasicStroke(6.0F, 1, 1));
      g.draw(new java.awt.geom.Line2D.Float(88.0F, 84.0F, 104.0F, 84.0F));
      g.setColor(new Color(255, 255, 255, 120));
      g.setStroke(new BasicStroke(4.0F, 1, 1));
      g.drawLine(52, 86, 62, 96);
      g.drawLine(112, 86, 122, 96);
   }

   private static void dollarEyes(Graphics2D g) {
      g.setFont(new Font("SansSerif", 1, 40));
      g.setColor(new Color(2347360));
      g.drawString("$", 60, 106);
      g.drawString("$", 108, 106);
   }

   private static void banknoteTongue(Graphics2D g, float cx, float cy) {
      g.setColor(new Color(4054148));
      g.fill(new java.awt.geom.RoundRectangle2D.Float(cx - 14.0F, cy - 4.0F, 28.0F, 26.0F, 6.0F, 6.0F));
      g.setColor(new Color(2002510));
      g.setStroke(new BasicStroke(2.0F));
      g.draw(new java.awt.geom.RoundRectangle2D.Float(cx - 14.0F, cy - 4.0F, 28.0F, 26.0F, 6.0F, 6.0F));
   }

   private static void zz(Graphics2D g) {
      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", 1, 30));
      g.drawString("Z", 140, 52);
      g.setFont(new Font("SansSerif", 1, 20));
      g.drawString("z", 162, 30);
   }

   private static void vein(Graphics2D g, float cx, float cy) {
      g.setColor(RED);
      g.setStroke(new BasicStroke(5.0F, 1, 1));

      for (int i = 0; i < 4; i++) {
         AffineTransform keep = g.getTransform();
         g.rotate(i * Math.PI / 2.0, cx, cy);
         g.draw(new java.awt.geom.QuadCurve2D.Float(cx - 4.0F, cy - 14.0F, cx - 4.0F, cy - 4.0F, cx - 14.0F, cy - 4.0F));
         g.setTransform(keep);
      }
   }

   private static void horns(Graphics2D g) {
      for (int side = -1; side <= 1; side += 2) {
         GeneralPath horn = new GeneralPath();
         float base = 96.0F + side * 40.0F;
         horn.moveTo(base - side * 16.0F, 48.0F);
         horn.quadTo(base + side * 12.0F, 34.0F, base + side * 18.0F, 10.0F);
         horn.quadTo(base + side * 26.0F, 40.0F, base + side * 14.0F, 62.0F);
         horn.closePath();
         g.setPaint(new GradientPaint(0.0F, 10.0F, new Color(14183423), 0.0F, 62.0F, new Color(6954936)));
         g.fill(horn);
         g.setColor(new Color(4001138));
         g.setStroke(new BasicStroke(2.5F));
         g.draw(horn);
      }
   }

   private static Shape heartShape(float cx, float cy, float r) {
      GeneralPath heart = new GeneralPath();
      heart.moveTo(cx, cy + r * 0.95F);
      heart.curveTo(cx - r * 1.35F, cy + r * 0.05F, cx - r * 1.1F, cy - r * 1.05F, cx, cy - r * 0.45F);
      heart.curveTo(cx + r * 1.1F, cy - r * 1.05F, cx + r * 1.35F, cy + r * 0.05F, cx, cy + r * 0.95F);
      heart.closePath();
      return heart;
   }

   private static void heart(Graphics2D g, float cx, float cy, float r, boolean broken) {
      Shape shape = heartShape(cx, cy, r);
      SkyEmoji.HeartPaint paint = new SkyEmoji.HeartPaint(cx, cy, r);
      if (!broken) {
         paint.fill(g, shape);
      } else {
         GeneralPath crack = new GeneralPath();
         crack.moveTo(cx, cy - r);
         crack.lineTo(cx - r * 0.18F, cy - r * 0.2F);
         crack.lineTo(cx + r * 0.15F, cy + r * 0.15F);
         crack.lineTo(cx - r * 0.1F, cy + r * 0.6F);
         crack.lineTo(cx, cy + r * 1.2F);
         crack.lineTo(cx - r * 2.0F, cy + r * 1.2F);
         crack.lineTo(cx - r * 2.0F, cy - r * 1.2F);
         crack.closePath();
         Area left = new Area(shape);
         left.intersect(new Area(crack));
         Area right = new Area(shape);
         right.subtract(new Area(crack));
         left.transform(AffineTransform.getRotateInstance(-0.1, cx, cy + r));
         left.transform(AffineTransform.getTranslateInstance(-5.0, 0.0));
         right.transform(AffineTransform.getRotateInstance(0.1, cx, cy + r));
         right.transform(AffineTransform.getTranslateInstance(5.0, 0.0));
         paint.fill(g, left);
         paint.fill(g, right);
      }
   }

   private static void flame(Graphics2D g) {
      GeneralPath outer = new GeneralPath();
      outer.moveTo(96.0F, 176.0F);
      outer.curveTo(40.0F, 176.0F, 30.0F, 122.0F, 58.0F, 86.0F);
      outer.curveTo(66.0F, 110.0F, 76.0F, 112.0F, 80.0F, 108.0F);
      outer.curveTo(70.0F, 74.0F, 92.0F, 42.0F, 110.0F, 16.0F);
      outer.curveTo(112.0F, 58.0F, 150.0F, 76.0F, 152.0F, 120.0F);
      outer.curveTo(154.0F, 156.0F, 130.0F, 176.0F, 96.0F, 176.0F);
      outer.closePath();
      g.setPaint(new GradientPaint(0.0F, 16.0F, new Color(8382719), 0.0F, 176.0F, new Color(1791689)));
      g.fill(outer);
      g.setColor(new Color(666222));
      g.setStroke(new BasicStroke(2.5F));
      g.draw(outer);
      GeneralPath inner = new GeneralPath();
      inner.moveTo(98.0F, 168.0F);
      inner.curveTo(70.0F, 168.0F, 66.0F, 136.0F, 82.0F, 116.0F);
      inner.curveTo(88.0F, 132.0F, 96.0F, 130.0F, 98.0F, 122.0F);
      inner.curveTo(96.0F, 102.0F, 108.0F, 86.0F, 116.0F, 76.0F);
      inner.curveTo(118.0F, 104.0F, 132.0F, 118.0F, 128.0F, 142.0F);
      inner.curveTo(126.0F, 160.0F, 114.0F, 168.0F, 98.0F, 168.0F);
      inner.closePath();
      g.setPaint(new GradientPaint(0.0F, 76.0F, Color.WHITE, 0.0F, 168.0F, new Color(11132159)));
      g.fill(inner);
   }

   private static void star(Graphics2D g) {
      GeneralPath star = new GeneralPath();

      for (int i = 0; i < 10; i++) {
         double angle = (-Math.PI / 2) + i * Math.PI / 5.0;
         double radius = i % 2 == 0 ? 80.0 : 34.0;
         float x = (float)(96.0 + Math.cos(angle) * radius);
         float y = (float)(100.0 + Math.sin(angle) * radius);
         if (i == 0) {
            star.moveTo(x, y);
         } else {
            star.lineTo(x, y);
         }
      }

      star.closePath();
      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Float(80.0F, 80.0F),
            100.0F,
            new float[]{0.0F, 0.5F, 1.0F},
            new Color[]{new Color(16774832), new Color(16762941), new Color(14255642)},
            CycleMethod.NO_CYCLE
         )
      );
      g.fill(star);
      g.setColor(new Color(9064202));
      g.setStroke(new BasicStroke(2.5F, 1, 1));
      g.draw(star);
      g.setColor(new Color(255, 255, 255, 170));
      g.fill(new Float(72.0F, 62.0F, 22.0F, 12.0F));
   }

   private static void moon(Graphics2D g) {
      Area crescent = new Area(new Float(30.0F, 26.0F, 140.0F, 140.0F));
      crescent.subtract(new Area(new Float(70.0F, 8.0F, 130.0F, 130.0F)));
      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Float(60.0F, 70.0F), 120.0F, new float[]{0.0F, 1.0F}, new Color[]{new Color(16775112), new Color(15909198)}
         )
      );
      g.fill(crescent);
      g.setColor(new Color(10119954));
      g.setStroke(new BasicStroke(2.5F));
      g.draw(crescent);
      g.setColor(new Color(16775112));
      sparkle(g, 150.0F, 140.0F, 12.0F);
      sparkle(g, 168.0F, 104.0F, 7.0F);
   }

   private static void sparkle(Graphics2D g, float cx, float cy, float r) {
      GeneralPath spark = new GeneralPath();
      spark.moveTo(cx, cy - r);
      spark.quadTo(cx, cy, cx + r, cy);
      spark.quadTo(cx, cy, cx, cy + r);
      spark.quadTo(cx, cy, cx - r, cy);
      spark.quadTo(cx, cy, cx, cy - r);
      g.fill(spark);
   }

   private static void notes(Graphics2D g) {
      g.setPaint(new GradientPaint(0.0F, 30.0F, new Color(9425919), 0.0F, 170.0F, new Color(1791689)));
      g.fill(new Float(36.0F, 132.0F, 44.0F, 32.0F));
      g.fill(new Float(112.0F, 116.0F, 44.0F, 32.0F));
      g.fill(new java.awt.geom.Rectangle2D.Float(70.0F, 42.0F, 10.0F, 104.0F));
      g.fill(new java.awt.geom.Rectangle2D.Float(146.0F, 26.0F, 10.0F, 104.0F));
      GeneralPath beam = new GeneralPath();
      beam.moveTo(70.0F, 42.0F);
      beam.lineTo(156.0F, 26.0F);
      beam.lineTo(156.0F, 50.0F);
      beam.lineTo(70.0F, 66.0F);
      beam.closePath();
      g.fill(beam);
      g.setColor(new Color(666222));
      g.setStroke(new BasicStroke(2.5F));
      g.draw(new Float(36.0F, 132.0F, 44.0F, 32.0F));
      g.draw(new Float(112.0F, 116.0F, 44.0F, 32.0F));
      g.setColor(new Color(255, 255, 255, 160));
      g.fill(new Float(46.0F, 138.0F, 14.0F, 8.0F));
      g.fill(new Float(122.0F, 122.0F, 14.0F, 8.0F));
   }

   @Environment(EnvType.CLIENT)
   private record HeartPaint(float cx, float cy, float r) {
      void fill(Graphics2D g, Shape shape) {
         g.setPaint(
            new RadialGradientPaint(
               new java.awt.geom.Point2D.Float(this.cx - this.r * 0.4F, this.cy - this.r * 0.5F),
               this.r * 1.7F,
               new float[]{0.0F, 0.45F, 1.0F},
               new Color[]{new Color(16752050), new Color(16726876), new Color(11014190)}
            )
         );
         g.fill(shape);
         g.setColor(new Color(7211552));
         g.setStroke(new BasicStroke(Math.max(1.5F, this.r * 0.05F)));
         g.draw(shape);
         Shape keep = g.getClip();
         g.clip(shape);
         g.setColor(new Color(255, 255, 255, 150));
         g.fill(new Float(this.cx - this.r * 0.85F, this.cy - this.r * 0.75F, this.r * 0.55F, this.r * 0.35F));
         g.setClip(keep);
      }
   }

   @Environment(EnvType.CLIENT)
   record Match(SkyEmoji emoji, int start, int end) {
   }
}
