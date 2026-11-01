package net.anubis.client.easyplace.sync;

import net.anubis.client.easyplace.schematic.SchematicTarget;
import net.anubis.client.easyplace.util.BlockStateUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public final class ServerAckManager {
   private PlacementConfirmation pending;

   public void begin(SchematicTarget target, long tick, int timeoutTicks, int retry, int sequence, BlockState predicted) {
      this.pending = new PlacementConfirmation(target.pos(), target.expected(), tick, tick + timeoutTicks, retry, sequence, predicted);
   }

   public ServerAckManager.Result poll(ClientWorld level, long tick) {
      if (this.pending == null) {
         return ServerAckManager.Result.IDLE;
      }

      if (!BlockAcks.answered(level, this.pending.sequence())) {
         return tick >= this.pending.deadlineTick() ? ServerAckManager.Result.TIMEOUT : ServerAckManager.Result.WAITING;
      }

      BlockState wanted = this.pending.predicted() != null && !this.pending.predicted().isAir() ? this.pending.predicted() : this.pending.expected().state();
      return BlockStateUtil.statesMatch(wanted, level.getBlockState(this.pending.pos())) ? ServerAckManager.Result.CONFIRMED : ServerAckManager.Result.REFUSED;
   }

   public PlacementConfirmation getPending() {
      return this.pending;
   }

   public void clear() {
      this.pending = null;
   }

   @Environment(EnvType.CLIENT)
   public enum Result {
      IDLE,
      WAITING,
      CONFIRMED,
      REFUSED,
      TIMEOUT;
   }
}
