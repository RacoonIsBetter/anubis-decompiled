package net.anubis.client.easyplace.builder;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Type;

@Environment(EnvType.CLIENT)
final class CellFiller {
   private static final int ANSWER_TICKS = 12;
   private static final int HOLD_TICKS = 20;
   private static final int TURN_TICKS = 30;
   private static final int TRIES = 2;
   private final BlockPos cell;
   private final CellFiller.Kind kind;
   private Aim.Shot shot;
   private int phase;
   private int phaseTicks;
   private int onAim;
   private int tries;

   CellFiller(BlockPos cell, CellFiller.Kind kind) {
      this.cell = cell;
      this.kind = kind;
   }

   BlockPos cell() {
      return this.cell;
   }

   CellFiller.Kind kind() {
      return this.kind;
   }

   static boolean filled(MinecraftClient mc, BlockPos cell, CellFiller.Kind kind) {
      BlockState state = mc.world.getBlockState(cell);

      return switch (kind) {
         case SUPPORT_LEAF -> Scaffold.block(state);
         case WATER_SOURCE -> state.getFluidState().isStill() && state.getFluidState().isIn(FluidTags.WATER);
         case WATERLOG -> state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
         case LAVA_SOURCE -> state.getFluidState().isStill() && state.getFluidState().isIn(FluidTags.LAVA);
         case REFILL -> false;
      };
   }

   static boolean reachable(MinecraftClient mc, BlockPos cell, CellFiller.Kind kind) {
      return aim(mc, cell, kind) != null;
   }

   CellFiller.Result tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      if (player == null || mc.world == null || mc.interactionManager == null) {
         return CellFiller.Result.FAILED;
      }

      if (filled(mc, this.cell, this.kind)) {
         return CellFiller.Result.DONE;
      }

      this.phaseTicks++;
      switch (this.phase) {
         case 0:
            if (this.kind == CellFiller.Kind.SUPPORT_LEAF ? !Scaffold.hold(mc, player) : !Hands.hold(mc, player, stack -> stack.isOf(this.kind.item))) {
               if (this.phaseTicks > 20) {
                  return CellFiller.Result.FAILED;
               }

               return CellFiller.Result.RUNNING;
            }

            this.shot = aim(mc, this.cell, this.kind);
            if (this.shot == null) {
               return CellFiller.Result.FAILED;
            }

            this.next(1);
            break;
         case 1:
            Aim.turn(mc, this.shot.yaw(), this.shot.pitch(), 22.0F);
            if (!Aim.looking(player, this.shot.yaw(), this.shot.pitch())) {
               this.onAim = 0;
               if (this.phaseTicks > 30) {
                  return CellFiller.Result.FAILED;
               }

               return CellFiller.Result.RUNNING;
            }

            if (++this.onAim < 2) {
               return CellFiller.Result.RUNNING;
            }

            boolean used = this.kind == CellFiller.Kind.SUPPORT_LEAF
               ? mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, this.shot.hit()).isAccepted()
               : mc.interactionManager.interactItem(player, Hand.MAIN_HAND).isAccepted();
            if (used) {
               player.swingHand(Hand.MAIN_HAND);
            }

            this.next(2);
            break;
         case 2:
            if (this.kind == CellFiller.Kind.REFILL && player.getMainHandStack().isOf(Items.WATER_BUCKET)) {
               return CellFiller.Result.DONE;
            }

            if (this.phaseTicks > 12) {
               if (++this.tries >= 2) {
                  return CellFiller.Result.FAILED;
               }

               this.next(0);
            }
            break;
         default:
            return CellFiller.Result.FAILED;
      }

      return CellFiller.Result.RUNNING;
   }

   private void next(int value) {
      this.phase = value;
      this.phaseTicks = 0;
      this.onAim = 0;
   }

   private static Aim.Shot aim(MinecraftClient mc, BlockPos cell, CellFiller.Kind kind) {
      return switch (kind) {
         case SUPPORT_LEAF -> Aim.against(mc, cell, null);
         case WATER_SOURCE -> Aim.against(mc, cell, Fluids.WATER);
         case WATERLOG -> Aim.at(mc, cell);
         case LAVA_SOURCE -> Aim.against(mc, cell, Fluids.LAVA);
         case REFILL -> Aim.water(mc, cell);
      };
   }

   static boolean endless(ClientWorld level, BlockPos pos) {
      if (level.getBlockState(pos).isOf(Blocks.WATER) && level.getFluidState(pos).isStill()) {
         int sources = 0;

         for (Direction direction : Type.HORIZONTAL) {
            FluidState next = level.getFluidState(pos.offset(direction));
            if (next.isStill() && next.isIn(FluidTags.WATER)) {
               sources++;
            }
         }

         if (sources < 2) {
            return false;
         }

         BlockState below = level.getBlockState(pos.down());
         return below.isSolid() || below.getFluidState().isStill() && below.getFluidState().isIn(FluidTags.WATER);
      } else {
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   enum Kind {
      SUPPORT_LEAF(Items.OAK_LEAVES),
      WATER_SOURCE(Items.WATER_BUCKET),
      WATERLOG(Items.WATER_BUCKET),
      LAVA_SOURCE(Items.LAVA_BUCKET),
      REFILL(Items.BUCKET);

      final Item item;

      Kind(Item item) {
         this.item = item;
      }
   }

   @Environment(EnvType.CLIENT)
   enum Result {
      RUNNING,
      DONE,
      FAILED;
   }
}
