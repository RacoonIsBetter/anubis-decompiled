package net.anubis.client.security;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Desktop.Action;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TamperWindow {
   private static final Color BACKGROUND = new Color(1118483);
   private static final Color ACCENT = new Color(14721072);
   private static final Color WHITE = new Color(15592941);
   private static final Color GREY = new Color(9211020);
   private static final int TEXT_WIDTH = 470;
   private static final String TITLE = "This isn't the official Anubis";
   private static final String EXPLAIN = "This file was changed after it was released, so Anubis didn't start it. Changed copies can contain viruses or account stealers.";
   private static final String[] STEPS = new String[]{
      "Delete this file and download Anubis from discord.gg/anubis.", "If you played with this file before, scan your PC and change your Microsoft password."
   };
   private static final String QUIET = "Please don't tell whoever gave you this file that Anubis noticed it.";

   private TamperWindow() {
   }

   public static void main(String[] args) throws Exception {
      String jar = args.length > 0 ? args[0] : "";
      CountDownLatch closed = new CountDownLatch(1);
      SwingUtilities.invokeAndWait(() -> open(jar, closed));
      closed.await();
      System.exit(0);
   }

   static String plainText(String jar) {
      StringBuilder text = new StringBuilder(
         "This isn't the official Anubis\n\nThis file was changed after it was released, so Anubis didn't start it. Changed copies can contain viruses or account stealers.\n\nWhat to do:"
      );

      for (int i = 0; i < STEPS.length; i++) {
         text.append("\n").append(i + 1).append(". ").append(STEPS[i]);
      }

      text.append("\n\n").append("Please don't tell whoever gave you this file that Anubis noticed it.");
      if (!jar.isEmpty()) {
         text.append("\n\nFile: ").append(jar);
      }

      return text.toString();
   }

   private static void open(String jar, CountDownLatch closed) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception var10) {
      }

      JFrame frame = new JFrame("Anubis");
      frame.setDefaultCloseOperation(2);

      try (InputStream icon = TamperWindow.class.getResourceAsStream("/assets/anubis/icons/icon_128.png")) {
         if (icon != null) {
            frame.setIconImage(ImageIO.read(icon));
         }
      } catch (Exception var12) {
      }

      frame.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosed(WindowEvent event) {
            closed.countDown();
         }
      });
      JPanel body = new JPanel();
      body.setLayout(new BoxLayout(body, 1));
      body.setBackground(BACKGROUND);
      body.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, ACCENT), BorderFactory.createEmptyBorder(20, 24, 18, 24)));
      body.add(label("This isn't the official Anubis", ACCENT, 19.0F, true));
      body.add(Box.createVerticalStrut(10));
      body.add(
         label(
            "This file was changed after it was released, so Anubis didn't start it. Changed copies can contain viruses or account stealers.",
            WHITE,
            14.0F,
            false
         )
      );
      body.add(Box.createVerticalStrut(14));
      body.add(label("What to do:", WHITE, 14.0F, true));
      StringBuilder steps = new StringBuilder("<ol style='margin-left:20px;margin-top:4px'>");

      for (String step : STEPS) {
         steps.append("<li style='margin-bottom:4px'>").append(escape(step)).append("</li>");
      }

      body.add(html(steps.append("</ol>").toString(), WHITE, 14.0F, false));
      body.add(Box.createVerticalStrut(4));
      body.add(label("Please don't tell whoever gave you this file that Anubis noticed it.", GREY, 12.0F, false));
      if (!jar.isEmpty()) {
         body.add(label("File: " + jar, GREY, 12.0F, false));
      }

      body.add(Box.createVerticalStrut(16));
      JButton download = new JButton("Download Official Release");
      download.setFont(download.getFont().deriveFont(1, 14.0F));
      download.addActionListener(event -> {
         if (!browse()) {
            download.setText("Open discord.gg/anubis in your browser");
         }
      });
      JButton close = new JButton("Close");
      close.setFont(close.getFont().deriveFont(14.0F));
      close.addActionListener(event -> frame.dispose());
      JPanel buttons = new JPanel(new FlowLayout(0, 0, 0));
      buttons.setBackground(BACKGROUND);
      buttons.setAlignmentX(0.0F);
      buttons.add(download);
      buttons.add(Box.createHorizontalStrut(10));
      buttons.add(close);
      body.add(buttons);
      frame.setContentPane(body);
      frame.pack();
      frame.setResizable(false);
      frame.setLocationRelativeTo(null);
      frame.setAlwaysOnTop(true);
      frame.setVisible(true);
      frame.toFront();
      download.requestFocusInWindow();
   }

   private static boolean browse() {
      try {
         if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI("https://discord.gg/anubis"));
            return true;
         } else {
            return false;
         }
      } catch (Exception e) {
         return false;
      }
   }

   private static JLabel label(String text, Color color, float size, boolean bold) {
      return html(escape(text), color, size, bold);
   }

   private static JLabel html(String content, Color color, float size, boolean bold) {
      JLabel label = new JLabel("<html><div style='width:470px'>" + content + "</div></html>");
      label.setForeground(color);
      label.setFont(label.getFont().deriveFont(bold ? 1 : 0, size));
      label.setAlignmentX(0.0F);
      return label;
   }

   private static String escape(String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
   }
}
