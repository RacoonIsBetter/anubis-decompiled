package net.anubis.client.module.impl.donutmacros;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

@Environment(EnvType.CLIENT)
final class CompletionRequests {
   private static final int FIRST_ID = 10000;
   private static final int LAST_ID = 29999;
   private static final long FORGET_MS = 15000L;
   private final Map<Integer, CompletionRequests.Request> requests = new ConcurrentHashMap<>();
   private int nextId = 10000;
   private CompletionRequests.Form form = CompletionRequests.Form.BOTH;

   CompletionRequests.Form form() {
      return this.form;
   }

   void form(CompletionRequests.Form form) {
      this.form = form;
   }

   int fire(String partial) {
      return this.fire(partial, this.form);
   }

   int fire(String partial, CompletionRequests.Form using) {
      ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
      if (connection == null) {
         return -1;
      }

      int id = this.nextId++;
      if (this.nextId > 29999) {
         this.nextId = 10000;
      }

      this.requests.put(id, new CompletionRequests.Request(System.currentTimeMillis()));

      try {
         if (using != CompletionRequests.Form.BARE) {
            connection.sendPacket(new RequestCommandCompletionsC2SPacket(id, "/" + partial));
         }

         if (using != CompletionRequests.Form.SLASH) {
            connection.sendPacket(new RequestCommandCompletionsC2SPacket(id, partial));
         }

         return id;
      } catch (RuntimeException e) {
         this.requests.remove(id);
         return -1;
      }
   }

   List<String> poll(int id) {
      CompletionRequests.Request request = this.requests.get(id);
      return request != null && !request.done ? request.names : null;
   }

   void forget(int id) {
      CompletionRequests.Request request = this.requests.get(id);
      if (request != null) {
         request.done = true;
      }
   }

   void prune(long now) {
      this.requests.values().removeIf(request -> now - request.at > 15000L);
   }

   void clear() {
      this.requests.clear();
      this.form = CompletionRequests.Form.BOTH;
   }

   boolean ingest(Packet<?> packet) {
      if (packet instanceof CommandSuggestionsS2CPacket suggestions) {
         CompletionRequests.Request request = this.requests.get(suggestions.id());
         if (request == null) {
            return false;
         }

         if (request.names == null) {
            List<String> names = new ArrayList<>();

            for (Suggestion suggestion : suggestions.getSuggestions().getList()) {
               String text = suggestion.getText();
               if (text != null && !text.isBlank()) {
                  names.add(text.trim());
               }
            }

            request.names = List.copyOf(names);
         }

         return true;
      } else {
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   enum Form {
      BOTH,
      SLASH,
      BARE;
   }

   @Environment(EnvType.CLIENT)
   private static final class Request {
      final long at;
      volatile List<String> names;
      volatile boolean done;

      Request(long at) {
         this.at = at;
      }
   }
}
