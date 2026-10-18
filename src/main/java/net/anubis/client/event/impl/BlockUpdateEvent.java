package net.anubis.client.event.impl;

import net.anubis.client.event.Event;
import net.anubis.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

@Environment(EnvType.CLIENT)
public final class BlockUpdateEvent extends Event {
   public static final EventType<BlockUpdateEvent> TYPE = new EventType<>("BlockUpdateEvent");
   public BlockPos pos;
   public BlockState oldState;
   public BlockState newState;
   private static final BlockUpdateEvent INSTANCE = new BlockUpdateEvent();

   private BlockUpdateEvent() {
   }

   public static void dispatch(BlockPos pos, BlockState oldState, BlockState newState) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.pos = pos;
         INSTANCE.oldState = oldState;
         INSTANCE.newState = newState;
         TYPE.dispatch(INSTANCE);
         INSTANCE.pos = null;
         INSTANCE.oldState = null;
         INSTANCE.newState = null;
      }
   }
}
