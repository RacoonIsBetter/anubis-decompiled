package net.anubis.client.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class FileDialogs {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String PATTERN = "*.json";
   private static final String DESCRIPTION = "Anubis profile (*.json)";
   private static volatile boolean open;
   private static volatile Path lastFolder;

   private FileDialogs() {
   }

   static boolean busy() {
      return open;
   }

   static void openProfile(String title, Consumer<Path> picked) {
      show(stack -> TinyFileDialogs.tinyfd_openFileDialog(title, startIn(null), filters(stack), "Anubis profile (*.json)", false), picked);
   }

   static void saveProfile(String title, String fileName, Consumer<Path> picked) {
      show(stack -> TinyFileDialogs.tinyfd_saveFileDialog(title, startIn(fileName), filters(stack), "Anubis profile (*.json)"), picked);
   }

   private static PointerBuffer filters(MemoryStack stack) {
      return (PointerBuffer)stack.mallocPointer(1).put(stack.UTF8("*.json")).flip();
   }

   private static String startIn(String fileName) {
      Path folder = lastFolder;
      if (folder == null || !Files.isDirectory(folder)) {
         folder = defaultFolder();
      }

      return fileName == null ? folder + File.separator : folder.resolve(fileName).toString();
   }

   private static Path defaultFolder() {
      try {
         Path home = Path.of(System.getProperty("user.home", "."));
         Path downloads = home.resolve("Downloads");
         return Files.isDirectory(downloads) ? downloads : home;
      } catch (InvalidPathException e) {
         return Path.of(".").toAbsolutePath();
      }
   }

   private static void show(Function<MemoryStack, String> dialog, Consumer<Path> picked) {
      if (!open) {
         open = true;
         Thread thread = new Thread(() -> {
            String result = null;

            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  result = dialog.apply(stack);
               } catch (Throwable var14) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var13) {
                        var14.addSuppressed(var13);
                     }
                  }

                  throw var14;
               }

               if (stack != null) {
                  stack.close();
               }
            } catch (Throwable t) {
               LOGGER.warn("[Anubis] File dialog failed", t);
            } finally {
               open = false;
            }

            if (result != null && !result.isBlank()) {
               Path path;
               try {
                  path = Path.of(result).toAbsolutePath();
               } catch (InvalidPathException e) {
                  return;
               }

               if (path.getParent() != null) {
                  lastFolder = path.getParent();
               }

               MinecraftClient.getInstance().execute(() -> picked.accept(path));
            }
         }, "Anubis file dialog");
         thread.setDaemon(true);
         thread.start();
      }
   }
}
