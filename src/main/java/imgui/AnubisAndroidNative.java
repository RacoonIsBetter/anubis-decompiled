package imgui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class AnubisAndroidNative {
   static final String RESOURCE = "/io/imgui/java/native-bin/android-arm64/libimgui-java64.so";
   static final String LIBRARY_PATH = "imgui.library.path";
   static final String LIBRARY_NAME = "imgui.library.name";
   static final String DEFAULT_NAME = "libimgui-java64.so";

   private AnubisAndroidNative() {
   }

   public static boolean isAndroidRuntime() {
      return isAndroid(System.getProperties(), System.getenv(), androidFilesystem());
   }

   public static synchronized void prepare() {
      prepare(
         System.getProperties(),
         System.getenv(),
         androidFilesystem(),
         () -> AnubisAndroidNative.class.getResourceAsStream("/io/imgui/java/native-bin/android-arm64/libimgui-java64.so")
      );
   }

   static void prepare(Properties properties, Map<String, String> environment, boolean androidFilesystem, AnubisAndroidNative.NativeSource bundledNative) {
      if (isAndroid(properties, environment, androidFilesystem)) {
         String arch = lower(properties.getProperty("os.arch"));
         if ((arch.equals("aarch64") || arch.equals("arm64") || arch.equals("arm64-v8a")) && !"32".equals(properties.getProperty("sun.arch.data.model"))) {
            try {
               String name = properties.getProperty("imgui.library.name", "libimgui-java64.so");
               requireFilename(name);
               String override = properties.getProperty("imgui.library.path");
               if (override != null) {
                  validateArm64(Paths.get(override).resolve(name));
               } else {
                  AnubisAndroidNative.NativeSource source = bundledNative;
                  if (properties.containsKey("imgui.library.name")) {
                     Path custom = findNamedOverride(name, properties);
                     validateArm64(custom);
                     if (custom.getFileName().toString().equals(name)) {
                        properties.setProperty("imgui.library.path", custom.getParent().toString());
                        return;
                     }

                     source = () -> Files.newInputStream(custom);
                  }

                  Path directory = createPrivateDirectory(cacheCandidates(properties, environment));
                  Path nativeFile = directory.resolve(name);

                  try {
                     try (InputStream input = source.open()) {
                        if (input == null) {
                           throw new IOException("Bundled Android ARM64 native is missing: /io/imgui/java/native-bin/android-arm64/libimgui-java64.so");
                        }

                        Files.copy(input, nativeFile);
                     }

                     validateArm64(nativeFile);
                     if (Files.getFileStore(nativeFile).supportsFileAttributeView("posix")) {
                        Files.setPosixFilePermissions(nativeFile, PosixFilePermissions.fromString("r-x------"));
                     }

                     directory.toFile().deleteOnExit();
                     nativeFile.toFile().deleteOnExit();
                     properties.setProperty("imgui.library.path", directory.toString());
                  } catch (IOException | RuntimeException exception) {
                     try {
                        Files.deleteIfExists(nativeFile);
                        Files.deleteIfExists(directory);
                     } catch (IOException cleanup) {
                        exception.addSuppressed(cleanup);
                     }

                     throw exception;
                  }
               }
            } catch (IOException | RuntimeException exception) {
               throw failure("Cannot prepare Android ARM64 ImGui: " + exception.getMessage(), exception);
            }
         } else {
            throw failure("Unsupported Android architecture '" + arch + "'. This build requires an ARM64 device and a 64-bit Java runtime.", null);
         }
      }
   }

   static boolean isAndroid(Properties properties, Map<String, String> environment, boolean androidFilesystem) {
      String os = lower(properties.getProperty("os.name"));
      String vm = lower(properties.getProperty("java.vm.name"));
      return !os.contains("android")
            && !lower(properties.getProperty("java.runtime.name")).contains("android")
            && !vm.contains("android")
            && !vm.contains("dalvik")
         ? os.contains("linux")
            && (
               androidFilesystem
                  || lower(properties.getProperty("os.version")).contains("android")
                  || nonempty(environment.get("POJAV_NATIVEDIR"))
                  || nonempty(environment.get("MOJO_RENDERER"))
                  || nonempty(environment.get("MOD_ANDROID_RUNTIME"))
            )
         : true;
   }

   private static boolean androidFilesystem() {
      return (Files.exists(Paths.get("/system/bin/linker64")) || Files.exists(Paths.get("/system/bin/linker"))) && Files.isDirectory(Paths.get("/system/etc"));
   }

   static List<Path> cacheCandidates(Properties properties, Map<String, String> environment) {
      List<Path> paths = new ArrayList<>();
      addCandidate(paths, environment.get("MOD_ANDROID_RUNTIME"));
      addCandidate(paths, environment.get("TMPDIR"));
      addCandidate(paths, properties.getProperty("java.io.tmpdir"));
      return paths;
   }

   private static void addCandidate(List<Path> paths, String value) {
      if (nonempty(value)) {
         Path path = Paths.get(value).toAbsolutePath().normalize();
         if (!paths.contains(path)) {
            paths.add(path);
         }
      }
   }

   static Path createPrivateDirectory(List<Path> candidates) throws IOException {
      IOException failures = new IOException(
         "No writable internal native cache. Use the launcher's private MOD_ANDROID_RUNTIME, TMPDIR or -Djava.io.tmpdir; shared storage cannot load .so files."
      );

      for (Path candidate : candidates) {
         try {
            if (isSharedStorage(candidate)) {
               throw new IOException("Shared Android storage cannot load native libraries: " + candidate);
            }

            Files.createDirectories(candidate);
            Path real = candidate.toRealPath();
            if (isSharedStorage(real)) {
               throw new IOException("Native cache resolves to shared Android storage: " + real);
            }

            if (Files.getFileStore(real).supportsFileAttributeView("posix")) {
               return Files.createTempDirectory(real, "anubis-imgui-arm64-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            }

            return Files.createTempDirectory(real, "anubis-imgui-arm64-");
         } catch (IOException | SecurityException exception) {
            failures.addSuppressed(exception);
         }
      }

      throw failures;
   }

   static boolean isSharedStorage(Path path) {
      String value = path.toAbsolutePath().normalize().toString().replace('\\', '/');
      if (value.length() > 2 && value.charAt(1) == ':') {
         value = value.substring(2);
      }

      for (String prefix : new String[]{"/sdcard", "/storage", "/mnt/sdcard", "/mnt/media_rw", "/data/media"}) {
         if (value.equals(prefix) || value.startsWith(prefix + "/")) {
            return true;
         }
      }

      return false;
   }

   private static Path findNamedOverride(String name, Properties properties) throws IOException {
      String mapped = "lib" + name + ".so";

      for (String entry : properties.getProperty("java.library.path", "").split(File.pathSeparator)) {
         if (!entry.isEmpty()) {
            Path directory = Paths.get(entry).toAbsolutePath().normalize();
            Path exact = directory.resolve(name);
            if (Files.isRegularFile(exact)) {
               return exact;
            }

            Path conventional = directory.resolve(mapped);
            if (Files.isRegularFile(conventional)) {
               return conventional;
            }
         }
      }

      throw new IOException(
         "Explicit -Dimgui.library.name="
            + name
            + " was not found in java.library.path. Set -Dimgui.library.path to the folder containing the Android ARM64 .so and use its full filename as imgui.library.name."
      );
   }

   private static void requireFilename(String name) throws IOException {
      if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf(47) >= 0 || name.indexOf(92) >= 0 || name.indexOf(58) >= 0) {
         throw new IOException("imgui.library.name must be a filename, not a path: " + name);
      }
   }

   static void validateArm64(Path file) throws IOException {
      byte[] header = new byte[64];

      try (InputStream input = Files.newInputStream(file)) {
         int offset = 0;

         while (offset < header.length) {
            int count = input.read(header, offset, header.length - offset);
            if (count < 0) {
               throw new IOException("Truncated ELF native library: " + file);
            }

            offset += count;
         }
      }

      if (header[0] == 127 && header[1] == 69 && header[2] == 76 && header[3] == 70 && header[4] == 2 && header[5] == 1 && header[6] == 1) {
         int machine = header[18] & 255 | (header[19] & 255) << 8;
         if (machine != 183) {
            throw new IOException(
               "Native architecture mismatch: ELF e_machine="
                  + machine
                  + ", expected EM_AARCH64 (183), in "
                  + file
                  + ". Desktop Linux x86_64 libraries cannot run on Android ARM64."
            );
         }
      } else {
         throw new IOException("Expected a little-endian ELF64 Android ARM64 native library: " + file);
      }
   }

   private static String lower(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }

   private static boolean nonempty(String value) {
      return value != null && !value.trim().isEmpty();
   }

   private static UnsatisfiedLinkError failure(String message, Throwable cause) {
      UnsatisfiedLinkError error = new UnsatisfiedLinkError("[Anubis ImGui] " + message);
      if (cause != null) {
         error.initCause(cause);
      }

      return error;
   }

   interface NativeSource {
      InputStream open() throws IOException;
   }
}
