package net.anubis.client.gui;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.imgui.ImGuiManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Atlases;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.item.ItemDisplayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
final class BlockIcons {
   private static final Logger LOGGER = LoggerFactory.getLogger("Anubis");
   private static final float SPRITE_TEXELS = 16.0F;
   private static final int PAGE_SIDE = 1024;
   private static final int RENDERS_PER_FRAME = 48;
   private static final int MAX_SHEETS = 4;
   private static final int MAX_LOSSES = 3;
   private static final int WATER_RGB = 4159204;
   private static final Int2ObjectOpenHashMap<BlockIcons.Sheet> SHEETS = new Int2ObjectOpenHashMap();
   private static final ReferenceOpenHashSet<GpuTexture> CRISP = new ReferenceOpenHashSet();
   private static GpuTexture atlas;
   private static final ItemRenderState ITEM = new ItemRenderState();
   private static int budgetFrame = -1;
   private static int rendersLeft;
   private static boolean itemsFailed;
   private static int auditedFrame;
   private static int losses;

   private BlockIcons() {
   }

   static void validate() {
      GpuTexture current;
      try {
         current = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.BLOCKS).getGlTexture();
      } catch (RuntimeException notLoaded) {
         current = null;
      }

      if (current != atlas) {
         atlas = current;
         clearSheets();
      } else {
         int frame = ImGuiManager.getInstance().frameCount();
         if (frame - auditedFrame >= 30) {
            auditedFrame = frame;
            if (!intact()) {
               clearSheets();
               if (++losses < 3) {
                  LOGGER.warn("Block icon pages were lost; drawing them again");
               } else {
                  itemsFailed = true;
                  LOGGER.warn("Block icon pages keep getting lost; using block textures instead");
               }
            }
         }
      }
   }

   private static boolean intact() {
      ObjectIterator var0 = SHEETS.values().iterator();

      while (var0.hasNext()) {
         BlockIcons.Sheet sheet = (BlockIcons.Sheet)var0.next();
         if (sheet.depth != null && !ItemIcons.alive(sheet.depth)) {
            return false;
         }

         for (GpuTexture page : sheet.pages) {
            if (!ItemIcons.alive(page)) {
               return false;
            }
         }
      }

      return true;
   }

   static void forget() {
      clearSheets();
   }

   static float size(float target) {
      return Math.max(1, (int)(target / 16.0F)) * 16.0F;
   }

   static void draw(ImDrawList dl, Block block, float x, float y, float size) {
      int px = Math.round(size);
      BlockIcons.Sheet sheet = sheet(px);
      BlockIcons.Icon icon = (BlockIcons.Icon)sheet.icons.get(block);
      if (icon == null) {
         icon = resolve(sheet, block);
         if (icon == null) {
            return;
         }

         sheet.icons.put(block, icon);
      }

      float x0 = Math.round(x);
      float y0 = Math.round(y);
      if (icon.texture != 0) {
         dl.addImage(icon.texture, x0, y0, x0 + px, y0 + px, icon.u0, icon.v0, icon.u1, icon.v1, AnubisWidgets.c(icon.tint));
      } else {
         float r = px * 0.2F;
         dl.addRectFilled(x0, y0, x0 + px, y0 + px, AnubisWidgets.c(-12240588), r);
         dl.addRect(x0, y0, x0 + px, y0 + px, AnubisWidgets.c(536870911), r, 0, 1.0F);
      }
   }

   private static BlockIcons.Sheet sheet(int px) {
      BlockIcons.Sheet sheet = (BlockIcons.Sheet)SHEETS.get(px);
      if (sheet == null) {
         if (SHEETS.size() >= 4) {
            clearSheets();
         }

         sheet = new BlockIcons.Sheet(px);
         SHEETS.put(px, sheet);
      }

      return sheet;
   }

   private static void clearSheets() {
      ObjectIterator var0 = SHEETS.values().iterator();

      while (var0.hasNext()) {
         BlockIcons.Sheet sheet = (BlockIcons.Sheet)var0.next();
         sheet.close();
      }

      SHEETS.clear();
      CRISP.clear();
      ItemIcons.forgetVerdict();
   }

   private static BlockIcons.Icon resolve(BlockIcons.Sheet sheet, Block block) {
      Item item = block.asItem();
      Block shown = block;
      if (item == Items.AIR) {
         item = standIn(block);
         if (item instanceof BlockItem placed) {
            shown = placed.getBlock();
         }
      }

      if (item == Items.AIR || itemsFailed || sheet.cell > 256 || !ItemIcons.works()) {
         return sprite(block);
      }

      if (!takeRender()) {
         return null;
      }

      MinecraftClient mc = MinecraftClient.getInstance();

      try {
         mc.getItemModelManager().clearAndUpdate(ITEM, new ItemStack(item), ItemDisplayContext.GUI, mc.world, null, 0);
      } catch (RuntimeException brokenModel) {
         ITEM.clear();
         return sprite(block);
      }

      if (ITEM.isEmpty()) {
         return sprite(block);
      }

      BlockIcons.Icon icon = new BlockIcons.Icon();
      boolean seeThrough = BlockRenderLayers.getBlockLayer(shown.getDefaultState()) == BlockRenderLayer.TRANSLUCENT;

      try {
         renderItem(sheet, icon, seeThrough);
         return icon;
      } catch (RuntimeException e) {
         itemsFailed = true;
         LOGGER.warn("Block item icons failed, using block textures instead", e);
         return sprite(block);
      }
   }

   private static Item standIn(Block block) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         try {
            ItemStack picked = block.getDefaultState().getPickStack(mc.world, BlockPos.ORIGIN, false);
            if (!picked.isEmpty()) {
               return picked.getItem();
            }
         } catch (RuntimeException var3) {
         }
      }

      if (block == Blocks.MOVING_PISTON) {
         return Items.PISTON;
      } else if (block == Blocks.END_PORTAL) {
         return Items.ENDER_EYE;
      } else {
         return block == Blocks.END_GATEWAY ? Items.ENDER_PEARL : Items.AIR;
      }
   }

   private static boolean takeRender() {
      int frame = ImGuiManager.getInstance().frameCount();
      if (frame != budgetFrame) {
         budgetFrame = frame;
         rendersLeft = 48;
      }

      if (rendersLeft <= 0) {
         return false;
      }

      rendersLeft--;
      return true;
   }

   private static void renderItem(BlockIcons.Sheet sheet, BlockIcons.Icon icon, boolean seeThrough) {
      int cell = sheet.cell;
      int perRow = 1024 / cell;
      if (sheet.pages.isEmpty() || sheet.used >= perRow * perRow) {
         addPage(sheet);
      }

      int slot = sheet.used++;
      int x = slot % perRow * cell;
      int y = slot / perRow * cell;
      int glY = 1024 - y - cell;
      GpuTexture page = sheet.pages.get(sheet.pages.size() - 1);
      GpuTextureView view = sheet.views.get(sheet.views.size() - 1);

      try {
         ItemIcons.render(ITEM, page, view, sheet.depth, sheet.depthView, x, y, cell);
      } finally {
         ITEM.clear();
      }

      if (seeThrough) {
         ItemIcons.straighten(page, x, glY, cell);
      }

      icon.texture = ((GlTexture)page).getGlId();
      icon.u0 = x / 1024.0F;
      icon.u1 = (x + cell) / 1024.0F;
      icon.v0 = (1024 - y) / 1024.0F;
      icon.v1 = glY / 1024.0F;
   }

   private static void addPage(BlockIcons.Sheet sheet) {
      GpuDevice device = RenderSystem.getDevice();
      if (sheet.depth == null) {
         sheet.depth = ItemIcons.create("Anubis block icons depth", 8, TextureFormat.DEPTH32, 1024);
         sheet.depthView = device.createTextureView(sheet.depth);
      }

      GpuTexture page = ItemIcons.create("Anubis block icons " + sheet.cell + "px", 15, TextureFormat.RGBA8, 1024);
      sheet.pages.add(page);
      sheet.views.add(device.createTextureView(page));
      sheet.used = 0;
      device.createCommandEncoder().clearColorAndDepthTextures(page, 0, sheet.depth, 1.0);
   }

   private static BlockIcons.Icon sprite(Block block) {
      BlockIcons.Icon icon = new BlockIcons.Icon();
      MinecraftClient mc = MinecraftClient.getInstance();
      BlockState state = block.getDefaultState();

      try {
         Sprite sprite = mc.getBakedModelManager().getBlockModels().getModelParticleSprite(state);
         if (MissingSprite.getMissingSpriteId().equals(sprite.getContents().getId())) {
            return icon;
         }

         if (!(mc.getTextureManager().getTexture(sprite.getAtlasId()).getGlTexture() instanceof GlTexture gl && !gl.isClosed())) {
            return icon;
         }

         if (CRISP.add(gl)) {
            ItemIcons.crisp(gl);
         }

         icon.texture = gl.getGlId();
         icon.u0 = sprite.getMinU();
         icon.v0 = sprite.getMinV();
         icon.u1 = sprite.getMaxU();
         icon.v1 = sprite.getMaxV();
      } catch (RuntimeException unavailable) {
         return icon;
      }

      if (!state.isOf(Blocks.GRASS_BLOCK)) {
         int rgb = -1;

         try {
            rgb = mc.getBlockColors().getColor(state, null, null, 0);
         } catch (RuntimeException var7) {
         }

         if (rgb == -1 && (state.isOf(Blocks.WATER) || state.isOf(Blocks.BUBBLE_COLUMN))) {
            rgb = 4159204;
         }

         if (rgb != -1) {
            icon.tint = 0xFF000000 | (rgb & 0xFF) << 16 | rgb & 0xFF00 | rgb >> 16 & 0xFF;
         }
      }

      return icon;
   }

   @Environment(EnvType.CLIENT)
   private static final class Icon {
      int texture;
      float u0;
      float v0;
      float u1;
      float v1;
      int tint = -1;
   }

   @Environment(EnvType.CLIENT)
   private static final class Sheet {
      final int cell;
      final Reference2ObjectOpenHashMap<Block, BlockIcons.Icon> icons = new Reference2ObjectOpenHashMap();
      final List<GpuTexture> pages = new ArrayList<>();
      final List<GpuTextureView> views = new ArrayList<>();
      GpuTexture depth;
      GpuTextureView depthView;
      int used;

      Sheet(int cell) {
         this.cell = cell;
      }

      void close() {
         for (GpuTextureView view : this.views) {
            view.close();
         }

         for (GpuTexture page : this.pages) {
            ItemIcons.dispose(page);
         }

         if (this.depthView != null) {
            this.depthView.close();
         }

         ItemIcons.dispose(this.depth);
         this.views.clear();
         this.pages.clear();
         this.depth = null;
         this.depthView = null;
      }
   }
}
