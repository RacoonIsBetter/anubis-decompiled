package net.anubis.client.webhook;

import java.net.URI;
import java.net.URISyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record WebhookConfig(String url, String username, String avatarUrl) {
   public static final String DEFAULT_USERNAME = "Anubis";
   public static final String DEFAULT_AVATAR_URL = "https://i.imgur.com/W3cTqvc.png";

   public WebhookConfig {
      url = normalizeRequired(url, "url");
      username = normalizeOptional(username, "Anubis");
      avatarUrl = normalizeOptional(avatarUrl, "https://i.imgur.com/W3cTqvc.png");
   }

   public WebhookConfig(String url) {
      this(url, "Anubis", "https://i.imgur.com/W3cTqvc.png");
   }

   public static boolean isValidUrl(String url) {
      try {
         URI uri = new URI(url);
         String scheme = uri.getScheme();
         return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
      } catch (URISyntaxException e) {
         return false;
      }
   }

   private static String normalizeRequired(String value, String fieldName) {
      String normalized = normalizeOptional(value, null);
      if (normalized == null) {
         throw new IllegalArgumentException(fieldName + " cannot be blank");
      } else {
         return normalized;
      }
   }

   private static String normalizeOptional(String value, String fallback) {
      if (value == null) {
         return fallback;
      }

      String normalized = value.trim();
      return normalized.isEmpty() ? fallback : normalized;
   }
}
