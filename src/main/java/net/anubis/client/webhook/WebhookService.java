package net.anubis.client.webhook;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import net.anubis.client.AnubisClientMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class WebhookService {
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   public boolean send(WebhookConfig config, WebhookPayloadBuilder payloadBuilder) {
      JsonObject payload;
      try {
         payload = payloadBuilder.build(config);
      } catch (RuntimeException exception) {
         AnubisClientMod.LOGGER.warn("[Anubis] Failed to build webhook payload", exception);
         return false;
      }

      HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
         .timeout(Duration.ofSeconds(10L))
         .header("Content-Type", "application/json")
         .POST(BodyPublishers.ofString(payload.toString()))
         .build();

      try {
         HttpResponse<Void> response = this.httpClient.send(request, BodyHandlers.discarding());
         int status = response.statusCode();
         if (status >= 200 && status < 300) {
            return true;
         }

         AnubisClientMod.LOGGER.warn("[Anubis] Webhook request failed with status {}", status);
      } catch (IOException | InterruptedException exception) {
         if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
         }

         AnubisClientMod.LOGGER.warn("[Anubis] Webhook request failed", exception);
      }

      return false;
   }
}
