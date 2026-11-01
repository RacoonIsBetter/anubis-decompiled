package net.anubis.client.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import net.anubis.client.BuildConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class OfficialBuild {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final String MOD_ID = "anubisplus";
   private static final Pattern SIG_FILE = Pattern.compile("SIG-[^.]*(\\.[A-Z0-9]{1,3})?");
   private static volatile OfficialBuild.State state = OfficialBuild.State.PENDING;
   private static volatile String reason = "";
   private static final AtomicBoolean tickChecked = new AtomicBoolean();

   private OfficialBuild() {
   }

   public static OfficialBuild.State state() {
      return state;
   }

   public static void checkBeforeLaunch() {
      run();
      stopIfModified();
   }

   public static void enforceIfPending() {
      if (state == OfficialBuild.State.PENDING) {
         run();
         stopIfModified();
      }
   }

   public static void enforceOnFirstTick() {
      if (!tickChecked.getAndSet(true)) {
         enforceIfPending();
      }
   }

   private static void stopIfModified() {
      if (state == OfficialBuild.State.MODIFIED) {
         TamperStop.stopGame(reason, ownJar());
      }
   }

   private static void run() {
      OfficialBuild.Result result;
      try {
         result = check();
      } catch (RuntimeException e) {
         result = new OfficialBuild.Result(OfficialBuild.State.NOT_CHECKED, "the check could not run (" + e.getClass().getSimpleName() + ")");
      }

      reason = result.reason();
      state = result.state();
      switch (result.state()) {
         case VERIFIED:
            LOGGER.info("[Anubis] Build check: this jar matches the signed release ({})", result.reason());
            break;
         case MODIFIED:
            LOGGER.warn(
               "[Anubis] Build check: this jar differs from the signed Anubis release ({}). Download Anubis only from the official Discord.", result.reason()
            );
            break;
         default:
            LOGGER.info("[Anubis] Build check skipped: {}", result.reason());
      }
   }

   private static OfficialBuild.Result check() {
      if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
         return new OfficialBuild.Result(OfficialBuild.State.NOT_CHECKED, "development environment");
      }

      String expected = String.join("", BuildConfig.SIGN_CERT_PARTS);
      if (expected.isEmpty()) {
         return new OfficialBuild.Result(OfficialBuild.State.NOT_CHECKED, "unsigned local build");
      }

      Path jar = ownJar();
      if (jar == null) {
         return new OfficialBuild.Result(OfficialBuild.State.NOT_CHECKED, "the Anubis jar file could not be located");
      }

      try (JarFile file = new JarFile(jar.toFile(), true)) {
         int signed = 0;
         Enumeration<JarEntry> entries = file.entries();

         while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory()) {
               if (entry.getSize() > 0L) {
                  return modified("unexpected data in " + name);
               }
            } else if (!signingRelated(name)) {
               try (InputStream in = file.getInputStream(entry)) {
                  in.transferTo(OutputStream.nullOutputStream());
               }

               CodeSigner[] signers = entry.getCodeSigners();
               if (signers == null) {
                  return modified(signed == 0 ? "the signature is missing" : "a file was added: " + name);
               }

               if (!signedBy(signers, expected)) {
                  return modified("signed with a different key");
               }

               signed++;
            }
         }

         return signed == 0 ? modified("the signature is missing") : new OfficialBuild.Result(OfficialBuild.State.VERIFIED, signed + " files checked");
      } catch (SecurityException e) {
         return modified("a file does not match the signature: " + e.getMessage());
      } catch (IOException e) {
         return new OfficialBuild.Result(OfficialBuild.State.NOT_CHECKED, "the jar could not be read (" + e.getClass().getSimpleName() + ")");
      }
   }

   private static OfficialBuild.Result modified(String reason) {
      return new OfficialBuild.Result(OfficialBuild.State.MODIFIED, reason);
   }

   private static Path ownJar() {
      ModContainer mod = (ModContainer)FabricLoader.getInstance().getModContainer("anubisplus").orElse(null);
      if (mod != null && mod.getOrigin().getKind() == Kind.PATH) {
         List<Path> paths = mod.getOrigin().getPaths();
         if (paths.size() != 1) {
            return null;
         }

         Path path = paths.get(0);
         boolean jar = path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
         return jar && Files.isRegularFile(path) ? path : null;
      } else {
         return null;
      }
   }

   private static boolean signingRelated(String name) {
      String upper = name.toUpperCase(Locale.ROOT);
      if (upper.startsWith("META-INF/") && upper.indexOf(47, 9) < 0) {
         String file = upper.substring(9);
         return file.equals("MANIFEST.MF")
            || file.endsWith(".SF")
            || file.endsWith(".DSA")
            || file.endsWith(".RSA")
            || file.endsWith(".EC")
            || SIG_FILE.matcher(file).matches();
      } else {
         return false;
      }
   }

   private static boolean signedBy(CodeSigner[] signers, String expected) {
      for (CodeSigner signer : signers) {
         List<? extends Certificate> chain = signer.getSignerCertPath().getCertificates();
         if (!chain.isEmpty() && expected.equalsIgnoreCase(fingerprint(chain.get(0)))) {
            return true;
         }
      }

      return false;
   }

   private static String fingerprint(Certificate certificate) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
      } catch (GeneralSecurityException e) {
         return "";
      }
   }

   @Environment(EnvType.CLIENT)
   private record Result(OfficialBuild.State state, String reason) {
   }

   @Environment(EnvType.CLIENT)
   public enum State {
      PENDING,
      VERIFIED,
      MODIFIED,
      NOT_CHECKED;
   }
}
