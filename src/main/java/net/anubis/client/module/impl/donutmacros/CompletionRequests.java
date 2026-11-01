package net.anubis.client.module.impl.donutmacros;

import java.util.List;
import net.anubis.client.util.CompletionChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class CompletionRequests {
   private CompletionChannel.Form form = CompletionChannel.Form.BOTH;

   CompletionChannel.Form form() {
      return this.form;
   }

   void form(CompletionChannel.Form form) {
      this.form = form;
   }

   int fire(String partial) {
      return this.fire(partial, this.form);
   }

   int fire(String partial, CompletionChannel.Form using) {
      return CompletionChannel.INSTANCE.fire(this, partial, using);
   }

   List<String> poll(int id) {
      return CompletionChannel.INSTANCE.poll(id);
   }

   void forget(int id) {
      CompletionChannel.INSTANCE.forget(id);
   }

   void prune(long now) {
      CompletionChannel.INSTANCE.prune(now);
   }

   void clear() {
      CompletionChannel.INSTANCE.clear(this);
      this.form = CompletionChannel.Form.BOTH;
   }
}
