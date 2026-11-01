package net.anubis.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.StringListSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.ItemEnchants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
final class ItemPickerEditor {
   private static final int INLINE_MAX_ROWS = 8;
   private static final float HEADER_H = 40.0F;
   private static final float INLINE_ROW_H = 38.0F;
   private static final float LIST_PAD = 4.0F;
   private static final float EMPTY_H = 44.0F;
   private static final float LIST_W = 820.0F;
   private static final float SINGLE_W = 560.0F;
   private static final float ENCHANT_W = 560.0F;
   private static final float PICKER_MAX_H = 760.0F;
   private static final float ENCHANT_MAX_H = 640.0F;
   private static final float PICKER_ROW_H = 42.0F;
   private static final float COLUMN_GAP = 28.0F;
   private static final float TAB_BAR_ROOM = 86.0F;
   private static final float ICON_SLOT = 32.0F;
   private static final float PRESET_ICON = 48.0F;
   private static final float PRESET_GAP = 8.0F;
   private static final int LIST_BG = AnubisWidgets.withAlpha(-13226454, 0.5F);
   private static final int ROW_HOVER = 318767103;
   private static final int SCRIM = 1929379840;
   private static final String SELECT_LABEL = "Select";
   private static final String EDIT_LABEL = "Edit";
   private static final String NO_ITEMS = "No items yet";
   private static final String NO_ITEM = "No item selected";
   private static final String ANY_ENCHANTS = "Any enchantments";
   private static final String LIST_TITLE = "Select Items";
   private static final String SINGLE_TITLE = "Select Item";
   private static final String ENCHANT_TITLE = "Enchantments";
   private static final String AVAILABLE_TITLE = "All items";
   private static final String CHOSEN_TITLE = "Selected";
   private static final String NO_MATCHES = "No items match";
   private static final String NONE_CHOSEN = "Nothing selected yet";
   private static final String SEARCH_HINT = "Search items...";
   private static final String CLEAR_LABEL = "Clear";
   private static final String DONE_LABEL = "Done";
   private static final String TEXT_HINT = "text match";
   private static final String UNKNOWN_HINT = "unknown item";
   private static final String ENCHANT_HELP = "Pick what the item must have. Clashing enchantments turn off on their own.";
   private static final String NO_WORLD = "Join a world to load enchantments.";
   private static final String NO_ENCHANTS = "This item takes no enchantments.";
   private static final String FULL_LIST = "The list is full";
   private static final String OTHER_ITEMS = "Other items";
   private static final int PICKER_FLAGS = 8639;
   private static final int LEV_INSERT = 1;
   private static final int LEV_SUBSTITUTE = 8;
   private static final int LEV_DELETE = 8;
   private static final List<ItemPickerEditor.ItemInfo> ITEMS = new ArrayList<>();
   private static final Reference2ObjectOpenHashMap<Item, ItemPickerEditor.ItemInfo> BY_ITEM = new Reference2ObjectOpenHashMap();
   private static final Map<StringSetting.Preset, ItemStack> PRESET_ICONS = new HashMap<>();
   private static String language;
   private static int namesGeneration;
   private static int[] levPrev = new int[64];
   private static int[] levCur = new int[64];
   private final Reference2ObjectOpenHashMap<StringListSetting, float[]> inlineScroll = new Reference2ObjectOpenHashMap();
   private final Reference2ObjectOpenHashMap<StringSetting, ItemPickerEditor.Summary> summaries = new Reference2ObjectOpenHashMap();
   private ClientModule pickerModule;
   private StringListSetting listTarget;
   private StringSetting itemTarget;
   private String pickerSubtitle;
   private final ImString search = new ImString(64);
   private boolean focusSearch;
   private boolean searchActive;
   private boolean pickerJustOpened;
   private long pickerOpenedAt;
   private final float[] availableScroll = new float[2];
   private final float[] chosenScroll = new float[2];
   private ItemPickerEditor.ItemInfo[] available = new ItemPickerEditor.ItemInfo[0];
   private ItemPickerEditor.ItemInfo[] chosen = new ItemPickerEditor.ItemInfo[0];
   private long[] sortKeys = new long[0];
   private int availableCount;
   private int chosenCount;
   private Object columnsTarget;
   private long columnsRevision = -1L;
   private String columnsKey;
   private int columnsGeneration = -1;
   private StringSetting enchantTarget;
   private Item enchantItem;
   private String enchantSubtitle;
   private final List<ItemPickerEditor.EnchantRow> enchantRows = new ArrayList<>();
   private boolean enchantsLoaded;
   private long enchantOpenedAt;
   private boolean enchantJustOpened;
   private final float[] enchantScroll = new float[2];

   float renderList(ClientModule module, StringListSetting setting, float pad, float contentW, float startY) {
      beginFrame();
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGuiManager.getInstance().moduleFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      float cardX0 = ImGui.getWindowPosX();
      float originY = ImGui.getWindowPosY() - ImGui.getScrollY();
      List<String> values = setting.get();
      ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
      ImGui.textUnformatted(setting.name());
      float labelCy = originY + startY + AnubisWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F;
      countPill(dl, small, ImGui.getItemRectMaxX() + AnubisWidgets.s(8.0F), labelCy, Integer.toString(values.size()));
      if (headerChip("##pick_items", "Select", cardX0 + pad + contentW, labelCy, font)) {
         this.openList(module, setting);
      }

      float listY = startY + AnubisWidgets.s(40.0F);
      float step = AnubisWidgets.s(38.0F);
      int n = values.size();
      float boxH = n == 0 ? AnubisWidgets.s(44.0F) : Math.min(n, 8) * step + AnubisWidgets.s(4.0F) * 2.0F;
      float bx = cardX0 + pad;
      float by = originY + listY;
      dl.addRectFilled(bx, by, bx + contentW, by + boxH, AnubisWidgets.c(LIST_BG), AnubisWidgets.s(10.0F));
      String removed = null;
      if (n == 0) {
         AnubisWidgets.text(dl, font, bx + AnubisWidgets.s(14.0F), by + (boxH - AnubisWidgets.lineHeight(font)) * 0.5F, -4542808, "No items yet");
      } else if (n <= 8) {
         removed = this.drawEntries(dl, values, pad, listY + AnubisWidgets.s(4.0F), contentW, step, 0, n, font, small);
      } else {
         ImGui.setCursorPos(pad, listY + AnubisWidgets.s(4.0F));
         if (ImGui.beginChild("##chosen_items", contentW, boxH - AnubisWidgets.s(4.0F) * 2.0F, false, 24)) {
            ImGui.setCursorPos(0.0F, 0.0F);
            ImGui.dummy(1.0F, n * step);
            float scroll = ImGui.getScrollY();
            int first = Math.max(0, (int)(scroll / step));
            int last = Math.min(n, (int)Math.ceil((scroll + ImGui.getWindowHeight()) / step));
            removed = this.drawEntries(ImGui.getWindowDrawList(), values, 0.0F, 0.0F, contentW, step, first, last, font, small);
            ClickGuiRenderer.smoothScroll((float[])this.inlineScroll.computeIfAbsent(setting, k -> new float[2]), frameDelta());
         }

         ImGui.endChild();
      }

      if (removed != null) {
         setting.removeValue(removed);
      }

      return AnubisWidgets.s(40.0F) + boxH + AnubisWidgets.s(12.0F);
   }

   float renderItem(ClientModule module, StringSetting setting, float pad, float contentW, float startY) {
      beginFrame();
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGuiManager.getInstance().moduleFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      float cardX0 = ImGui.getWindowPosX();
      float originY = ImGui.getWindowPosY() - ImGui.getScrollY();
      ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
      ImGui.textUnformatted(setting.name());
      float labelCy = originY + startY + AnubisWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F;
      boolean open = headerChip("##pick_item", "Select", cardX0 + pad + contentW, labelCy, font);
      float boxY = startY + AnubisWidgets.s(40.0F);
      float boxH = AnubisWidgets.s(38.0F) + AnubisWidgets.s(4.0F) * 2.0F;
      float bx = cardX0 + pad;
      float by = originY + boxY;
      float cy = by + boxH * 0.5F;
      float lineH = AnubisWidgets.lineHeight(font);
      float smallH = AnubisWidgets.lineHeight(small);
      String raw = setting.get().trim();
      float btn = AnubisWidgets.s(22.0F);
      float right = bx + contentW - AnubisWidgets.s(10.0F);
      boolean clear = false;
      boolean clearHovered = false;
      if (!raw.isEmpty()) {
         ImGui.setCursorScreenPos(right - btn, cy - btn * 0.5F);
         clear = ImGui.invisibleButton("##clear_item", btn, btn);
         clearHovered = ImGui.isItemHovered();
      }

      float boxButtonW = raw.isEmpty() ? contentW : Math.max(1.0F, contentW - btn - AnubisWidgets.s(14.0F));
      ImGui.setCursorScreenPos(bx, by);
      if (ImGui.invisibleButton("##item_box", boxButtonW, boxH)) {
         open = true;
      }

      boolean boxHovered = ImGui.isItemHovered();
      dl.addRectFilled(
         bx, by, bx + contentW, by + boxH, AnubisWidgets.c(boxHovered ? AnubisWidgets.withAlpha(-13226454, 0.8F) : LIST_BG), AnubisWidgets.s(10.0F)
      );
      if (raw.isEmpty()) {
         AnubisWidgets.text(dl, font, bx + AnubisWidgets.s(14.0F), cy - lineH * 0.5F, -4542808, "No item selected");
      } else {
         if (clearHovered) {
            dl.addCircleFilled(right - btn * 0.5F, cy, btn * 0.5F, AnubisWidgets.c(-12240588), 20);
         }

         AnubisWidgets.cross(dl, right - btn * 0.5F, cy, AnubisWidgets.s(3.5F), clearHovered ? -10856974 : -4542808);
         float textRight = right - btn - AnubisWidgets.s(8.0F);
         Item item = ItemEnchants.resolve(raw);
         float ix = bx + AnubisWidgets.s(8.0F);
         if (item != null) {
            ItemPickerEditor.ItemInfo info = info(item);
            float icon = ItemIcons.pixels(AnubisWidgets.s(32.0F));
            ItemIcons.draw(dl, item, ix, cy - icon * 0.5F, icon, 1.0F);
            float tx = ix + icon + AnubisWidgets.s(10.0F);
            drawLabel(dl, font, small, tx, cy, textRight - tx, info.name, info.stored, -461582, lineH, smallH);
         } else {
            drawLabel(dl, font, small, ix + AnubisWidgets.s(6.0F), cy, textRight - ix - AnubisWidgets.s(6.0F), raw, "unknown item", -10856974, lineH, smallH);
         }
      }

      if (clear) {
         setting.set("");
         if (setting.enchantSetting() != null) {
            setting.enchantSetting().set("");
         }
      } else if (open) {
         this.openItem(module, setting);
      }

      return AnubisWidgets.s(40.0F) + boxH + AnubisWidgets.s(12.0F);
   }

   float renderPresets(ClientModule module, StringSetting setting, float pad, float contentW, float startY) {
      beginFrame();
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGuiManager.getInstance().moduleFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      float cardX0 = ImGui.getWindowPosX();
      float originY = ImGui.getWindowPosY() - ImGui.getScrollY();
      ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
      ImGui.textUnformatted(setting.name());
      List<StringSetting.Preset> presets = setting.presets();
      String value = setting.get().trim();
      String key = value.toLowerCase(Locale.ROOT).replace(' ', '_');
      StringSetting.Preset chosen = null;

      for (StringSetting.Preset preset : presets) {
         if (preset.value().equalsIgnoreCase(key)) {
            chosen = preset;
         }
      }

      int accent = GuiPalette.accent();
      float gap = AnubisWidgets.s(8.0F);
      float lineH = AnubisWidgets.lineHeight(font);
      float smallH = AnubisWidgets.lineHeight(small);
      float icon = ItemIcons.pixels(AnubisWidgets.s(48.0F));
      float tileW = (contentW - gap * (presets.size() - 1)) / presets.size();
      float tileH = icon + AnubisWidgets.s(30.0F) + smallH;
      float top = originY + startY + AnubisWidgets.s(40.0F);

      for (int i = 0; i < presets.size(); i++) {
         StringSetting.Preset preset = presets.get(i);
         boolean selected = preset == chosen;
         float x0 = cardX0 + pad + i * (tileW + gap);
         ImGui.setCursorScreenPos(x0, top);
         ImGui.pushID(i);
         boolean clicked = ImGui.invisibleButton("##preset", tileW, tileH);
         boolean hovered = ImGui.isItemHovered();
         ImGui.popID();
         presetTile(dl, x0, top, tileW, tileH, selected, hovered, accent);
         ItemIcons.draw(dl, presetIcon(preset), x0 + (tileW - icon) * 0.5F, top + AnubisWidgets.s(12.0F), icon, 1.0F);
         String shown = AnubisWidgets.ellipsize(small, preset.label(), tileW - AnubisWidgets.s(10.0F));
         float labelW = AnubisWidgets.textWidth(small, shown);
         AnubisWidgets.text(
            dl, small, x0 + (tileW - labelW) * 0.5F, top + tileH - AnubisWidgets.s(12.0F) - smallH, !selected && !hovered ? -2042416 : -461582, shown
         );
         if (hovered && !shown.equals(preset.label())) {
            ClickGuiRenderer.showTooltip(preset.label());
         }

         if (clicked) {
            setting.set(preset.value());
         }
      }

      boolean otherChosen = chosen == null && !value.isEmpty();
      Item other = otherChosen ? ItemEnchants.resolve(value) : null;
      float rowY = top + tileH + gap;
      float rowH = AnubisWidgets.s(38.0F) + AnubisWidgets.s(4.0F) * 2.0F;
      float bx = cardX0 + pad;
      float cy = rowY + rowH * 0.5F;
      ImGui.setCursorScreenPos(bx, rowY);
      boolean open = ImGui.invisibleButton("##other_items", contentW, rowH);
      boolean hovered = ImGui.isItemHovered();
      presetTile(dl, bx, rowY, contentW, rowH, otherChosen, hovered, accent);
      float slot = ItemIcons.pixels(AnubisWidgets.s(32.0F));
      float ix = bx + AnubisWidgets.s(10.0F);
      float tx = ix + slot + AnubisWidgets.s(10.0F);
      float avail = bx + contentW - AnubisWidgets.s(14.0F) - tx;
      if (other != null) {
         ItemIcons.draw(dl, other, ix, cy - slot * 0.5F, slot, 1.0F);
         drawLabel(dl, font, small, tx, cy, avail, info(other).name, "Other items", -461582, lineH, smallH);
      } else {
         float gx = ix + slot * 0.5F;
         float r = slot * 0.34F;
         int glyph = AnubisWidgets.c(hovered ? -461582 : -4542808);
         dl.addCircle(gx, cy, r, glyph, 24, AnubisWidgets.s(1.5F));
         dl.addLine(gx - r * 0.5F, cy, gx + r * 0.5F, cy, glyph, AnubisWidgets.s(1.5F));
         dl.addLine(gx, cy - r * 0.5F, gx, cy + r * 0.5F, glyph, AnubisWidgets.s(1.5F));
         drawLabel(
            dl,
            font,
            small,
            tx,
            cy,
            avail,
            otherChosen ? value : "Other items",
            otherChosen ? "Other items" : null,
            !hovered && !otherChosen ? -2042416 : -461582,
            lineH,
            smallH
         );
      }

      if (open) {
         this.openItem(module, setting);
      }

      return AnubisWidgets.s(40.0F) + tileH + gap + rowH + AnubisWidgets.s(12.0F);
   }

   private static void presetTile(ImDrawList dl, float x, float y, float w, float h, boolean selected, boolean hovered, int accent) {
      int fill = selected ? AnubisWidgets.withAlpha(accent, 0.18F) : (hovered ? AnubisWidgets.withAlpha(-13226454, 0.8F) : LIST_BG);
      dl.addRectFilled(x, y, x + w, y + h, AnubisWidgets.c(fill), AnubisWidgets.s(10.0F));
      if (selected) {
         dl.addRect(x, y, x + w, y + h, AnubisWidgets.c(AnubisWidgets.withAlpha(accent, 0.9F)), AnubisWidgets.s(10.0F), 0, AnubisWidgets.s(1.5F));
      }
   }

   private static ItemStack presetIcon(StringSetting.Preset preset) {
      ItemStack stack = PRESET_ICONS.get(preset);
      if (stack != null) {
         return stack;
      }

      Item item = ItemEnchants.resolve(preset.icon());
      stack = new ItemStack(item == null ? Items.BARRIER : item);
      if (preset.glint()) {
         stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
      }

      PRESET_ICONS.put(preset, stack);
      return stack;
   }

   float renderEnchants(ClientModule module, StringSetting setting, float pad, float contentW, float startY) {
      beginFrame();
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont font = ImGuiManager.getInstance().moduleFont();
      float cardX0 = ImGui.getWindowPosX();
      float originY = ImGui.getWindowPosY() - ImGui.getScrollY();
      ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
      ImGui.textUnformatted(setting.name());
      float labelCy = originY + startY + AnubisWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F;
      boolean open = headerChip("##edit_enchants", "Edit", cardX0 + pad + contentW, labelCy, font);
      float boxY = startY + AnubisWidgets.s(40.0F);
      float boxH = AnubisWidgets.s(38.0F) + AnubisWidgets.s(4.0F) * 2.0F;
      float bx = cardX0 + pad;
      float by = originY + boxY;
      ImGui.setCursorScreenPos(bx, by);
      if (ImGui.invisibleButton("##enchant_box", contentW, boxH)) {
         open = true;
      }

      boolean hovered = ImGui.isItemHovered();
      dl.addRectFilled(bx, by, bx + contentW, by + boxH, AnubisWidgets.c(hovered ? AnubisWidgets.withAlpha(-13226454, 0.8F) : LIST_BG), AnubisWidgets.s(10.0F));
      String summary = this.summary(setting);
      float lineH = AnubisWidgets.lineHeight(font);
      float tx = bx + AnubisWidgets.s(14.0F);
      float avail = contentW - AnubisWidgets.s(28.0F);
      if (summary.isEmpty()) {
         AnubisWidgets.text(dl, font, tx, by + (boxH - lineH) * 0.5F, -4542808, "Any enchantments");
      } else {
         String shown = AnubisWidgets.ellipsize(font, summary, avail);
         AnubisWidgets.text(dl, font, tx, by + (boxH - lineH) * 0.5F, -461582, shown);
         if (hovered && !shown.equals(summary)) {
            ClickGuiRenderer.showTooltip(summary);
         }
      }

      if (open) {
         this.openEnchants(module, setting);
      }

      return AnubisWidgets.s(40.0F) + boxH + AnubisWidgets.s(12.0F);
   }

   void renderWindows() {
      if (this.listTarget != null || this.itemTarget != null) {
         this.renderPicker();
      } else if (this.enchantTarget != null) {
         this.renderEnchantWindow();
      }
   }

   void close() {
      this.closePicker();
      this.closeEnchants();
   }

   boolean closeWindow() {
      if (this.listTarget != null || this.itemTarget != null) {
         this.closePicker();
         return true;
      } else if (this.enchantTarget != null) {
         this.closeEnchants();
         return true;
      } else {
         return false;
      }
   }

   private String drawEntries(ImDrawList dl, List<String> values, float x, float y, float w, float step, int first, int last, ImFont font, ImFont small) {
      boolean windowHovered = ImGui.isWindowHovered();
      float icon = ItemIcons.pixels(AnubisWidgets.s(32.0F));
      float btn = AnubisWidgets.s(22.0F);
      float lineH = AnubisWidgets.lineHeight(font);
      float smallH = AnubisWidgets.lineHeight(small);
      String removed = null;

      for (int i = first; i < last; i++) {
         String value = values.get(i);
         ImGui.setCursorPos(x, y + i * step);
         float x0 = ImGui.getCursorScreenPosX();
         float y0 = ImGui.getCursorScreenPosY();
         float cy = y0 + step * 0.5F;
         if (windowHovered && ImGui.isMouseHoveringRect(x0, y0, x0 + w, y0 + step)) {
            dl.addRectFilled(
               x0 + AnubisWidgets.s(4.0F),
               y0 + AnubisWidgets.s(1.0F),
               x0 + w - AnubisWidgets.s(4.0F),
               y0 + step - AnubisWidgets.s(1.0F),
               AnubisWidgets.c(318767103),
               AnubisWidgets.s(7.0F)
            );
         }

         ImGui.pushID(i);
         float right = x0 + w - AnubisWidgets.s(10.0F);
         ImGui.setCursorScreenPos(right - btn, cy - btn * 0.5F);
         if (ImGui.invisibleButton("##remove", btn, btn)) {
            removed = value;
         }

         boolean hovered = ImGui.isItemHovered();
         if (hovered) {
            dl.addCircleFilled(right - btn * 0.5F, cy, btn * 0.5F, AnubisWidgets.c(-12240588), 20);
         }

         AnubisWidgets.cross(dl, right - btn * 0.5F, cy, AnubisWidgets.s(3.5F), hovered ? -10856974 : -4542808);
         ImGui.popID();
         right -= btn + AnubisWidgets.s(8.0F);
         float ix = x0 + AnubisWidgets.s(8.0F);
         Item item = ItemEnchants.resolve(value);
         if (item != null) {
            ItemPickerEditor.ItemInfo info = info(item);
            ItemIcons.draw(dl, item, ix, cy - icon * 0.5F, icon, 1.0F);
            float tx = ix + icon + AnubisWidgets.s(10.0F);
            drawLabel(dl, font, small, tx, cy, right - tx, info.name, info.stored, -461582, lineH, smallH);
         } else {
            float tx = ix + AnubisWidgets.s(6.0F);
            drawLabel(dl, font, small, tx, cy, right - tx, value, "text match", -2042416, lineH, smallH);
         }
      }

      return removed;
   }

   private static void drawLabel(
      ImDrawList dl, ImFont font, ImFont small, float x, float cy, float avail, String text, String hint, int color, float lineH, float smallH
   ) {
      if (!(avail <= 0.0F)) {
         String shown = AnubisWidgets.ellipsize(font, text, avail);
         AnubisWidgets.text(dl, font, x, cy - lineH * 0.5F, color, shown);
         float used = AnubisWidgets.textWidth(font, shown);
         float room = avail - used - AnubisWidgets.s(6.0F);
         if (hint != null && room >= AnubisWidgets.s(40.0F)) {
            AnubisWidgets.text(dl, small, x + used + AnubisWidgets.s(6.0F), cy - smallH * 0.5F, -4542808, AnubisWidgets.ellipsize(small, hint, room));
         }
      }
   }

   private void openList(ClientModule module, StringListSetting setting) {
      this.closeEnchants();
      this.listTarget = setting;
      this.itemTarget = null;
      this.startPicker(module, setting.name());
   }

   private void openItem(ClientModule module, StringSetting setting) {
      this.closeEnchants();
      this.itemTarget = setting;
      this.listTarget = null;
      this.startPicker(module, setting.name());
   }

   private void startPicker(ClientModule module, String settingName) {
      this.pickerModule = module;
      this.pickerSubtitle = module.name() + " / " + settingName;
      this.search.set("");
      this.focusSearch = true;
      this.searchActive = false;
      this.pickerOpenedAt = System.nanoTime();
      this.pickerJustOpened = true;
      this.availableScroll[0] = this.availableScroll[1] = this.chosenScroll[0] = this.chosenScroll[1] = 0.0F;
      this.columnsTarget = null;
   }

   private void closePicker() {
      this.listTarget = null;
      this.itemTarget = null;
      this.columnsTarget = null;
   }

   private void renderPicker() {
      beginFrame();
      boolean list = this.listTarget != null;
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float margin = AnubisWidgets.s(16.0F);
      float w = Math.min(sw - margin * 2.0F, AnubisWidgets.s(list ? 820.0F : 560.0F));
      float h = Math.min(sh - AnubisWidgets.s(86.0F) - AnubisWidgets.s(32.0F), AnubisWidgets.s(760.0F));
      float open = ClickGuiRenderer.easeOut(ClickGuiRenderer.elapsed(this.pickerOpenedAt, 200.0F));
      float x = Math.round((sw - w) * 0.5F);
      float y = Math.round(Math.max(margin, (sh - AnubisWidgets.s(86.0F) - h) * 0.5F + AnubisWidgets.s(8.0F)) + AnubisWidgets.s(14.0F) * (1.0F - open));
      float rounding = ClickGuiRenderer.cardRounding();
      ImGui.setNextWindowPos(0.0F, 0.0F, 1);
      ImGui.setNextWindowSize(sw, sh, 1);
      if (this.pickerJustOpened) {
         ImGui.setNextWindowFocus();
      }

      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(4, 0.0F);
      boolean closeClicked = false;
      ItemPickerEditor.ItemInfo picked = null;
      ItemPickerEditor.ItemInfo dropped = null;
      boolean visible = ImGui.begin("##item_picker", 8639);
      if (this.pickerJustOpened) {
         imgui.internal.ImGui.bringWindowToDisplayFront(imgui.internal.ImGui.getCurrentWindow());
      }

      if (visible) {
         ImDrawList dl = ImGui.getWindowDrawList();
         dl.addRectFilled(0.0F, 0.0F, sw, sh, AnubisWidgets.c(1929379840));
         shadow(dl, x, y, w, h, rounding);
         dl.addRectFilled(x, y, x + w, y + h, AnubisWidgets.c(GuiPalette.abgr(ThemeStore.get().cards.color.argbWithAlpha(255))), rounding);
         dl.addRect(x, y, x + w, y + h, AnubisWidgets.c(318767103), rounding, 0, 1.0F);
         ImFont font = ImGuiManager.getInstance().moduleFont();
         ImFont small = ImGuiManager.getInstance().smallFont();
         float pad = AnubisWidgets.s(24.0F);
         float top = y + AnubisWidgets.s(22.0F);
         float btn = AnubisWidgets.s(28.0F);
         ClickGuiRenderer.drawCardTitle(list ? "Select Items" : "Select Item", x + pad, top + btn * 0.5F);
         ImGui.setCursorPos(x + w - pad - btn, top);
         if (AnubisWidgets.iconButton("##close", btn)) {
            closeClicked = true;
         }

         AnubisWidgets.cross(dl, x + w - pad - btn * 0.5F, top + btn * 0.5F, AnubisWidgets.s(4.5F), -461582);
         if (list
            && chip(
               "##clear_items",
               "Clear",
               this.listTarget.get().isEmpty(),
               x + w - pad - btn - AnubisWidgets.s(10.0F),
               top + btn * 0.5F,
               small,
               "Takes every item off the list"
            )) {
            this.listTarget.clear();
         }

         float subY = top + btn + AnubisWidgets.s(2.0F);
         AnubisWidgets.text(dl, small, x + pad, subY, -4542808, this.pickerSubtitle);
         ImGui.setCursorPos(x + pad, subY + AnubisWidgets.lineHeight(small) + AnubisWidgets.s(14.0F));
         ImGui.pushStyleVar(11, AnubisWidgets.s(12.0F), AnubisWidgets.s(8.0F));
         ImGui.setNextItemWidth(w - pad * 2.0F);
         if (this.focusSearch) {
            ImGui.setKeyboardFocusHere();
            this.focusSearch = false;
         }

         boolean enter = this.searchActive && (ImGui.isKeyPressed(525, false) || ImGui.isKeyPressed(627, false));
         ImGui.inputTextWithHint("##item_search", "Search items...", this.search);
         this.searchActive = ImGui.isItemActive();
         ImGui.popStyleVar();
         float headerY = ImGui.getItemRectMaxY() + AnubisWidgets.s(18.0F);
         String before = this.columnsKey;
         this.refreshColumns();
         if (before != null && !before.equals(this.columnsKey)) {
            this.availableScroll[0] = this.availableScroll[1] = this.chosenScroll[0] = this.chosenScroll[1] = 0.0F;
         }

         if (enter && !this.search.get().isBlank() && this.availableCount > 0) {
            picked = this.available[0];
            this.focusSearch = list;
         }

         float headerH = AnubisWidgets.s(28.0F);
         float listTop = headerY + headerH + AnubisWidgets.s(10.0F);
         float listBottom = y + h - AnubisWidgets.s(18.0F);
         float listH = Math.max(1.0F, listBottom - listTop);
         String emptyLeft = this.search.get().isBlank() ? "Nothing selected yet" : "No items match";
         if (list) {
            float colW = (w - pad * 2.0F - AnubisWidgets.s(28.0F)) * 0.5F;
            float leftX = x + pad;
            float rightX = leftX + colW + AnubisWidgets.s(28.0F);
            columnHeader(
               dl,
               font,
               small,
               leftX,
               headerY,
               headerH,
               colW,
               "All items",
               this.listTarget.canAdd() ? Integer.toString(this.availableCount) : "The list is full"
            );
            columnHeader(dl, font, small, rightX, headerY, headerH, colW, "Selected", Integer.toString(this.chosenCount));
            float dividerX = Math.round(leftX + colW + AnubisWidgets.s(28.0F) * 0.5F);
            dl.addLine(dividerX, headerY, dividerX, listBottom, AnubisWidgets.c(268435455), 1.0F);
            ItemPickerEditor.ItemInfo add = this.column(
               "##available", this.available, this.availableCount, true, null, leftX, listTop, colW, listH, this.availableScroll, "No items match", font, small
            );
            ItemPickerEditor.ItemInfo remove = this.column(
               "##chosen", this.chosen, this.chosenCount, false, null, rightX, listTop, colW, listH, this.chosenScroll, emptyLeft, font, small
            );
            if (add != null) {
               picked = add;
            }

            if (remove != null) {
               dropped = remove;
            }
         } else {
            float colW = w - pad * 2.0F;
            columnHeader(dl, font, small, x + pad, headerY, headerH, colW, "All items", Integer.toString(this.availableCount));
            Item current = ItemEnchants.resolve(this.itemTarget.get());
            ItemPickerEditor.ItemInfo choice = this.column(
               "##single",
               this.available,
               this.availableCount,
               true,
               current,
               x + pad,
               listTop,
               colW,
               listH,
               this.availableScroll,
               "No items match",
               font,
               small
            );
            if (choice != null) {
               picked = choice;
            }
         }

         if (!this.pickerJustOpened && ImGui.isMouseClicked(0) && ImGui.isWindowHovered() && !ImGui.isMouseHoveringRect(x, y, x + w, y + h)) {
            closeClicked = true;
         }
      }

      ImGui.end();
      ImGui.popStyleVar(4);
      this.pickerJustOpened = false;
      if (dropped != null && this.listTarget != null) {
         removeFromList(this.listTarget, dropped.item);
      }

      if (picked != null) {
         if (this.listTarget != null) {
            this.listTarget.addValue(picked.stored);
         } else if (this.itemTarget != null) {
            this.pickItem(picked);
            return;
         }
      }

      if (closeClicked) {
         this.closePicker();
      }
   }

   private void pickItem(ItemPickerEditor.ItemInfo picked) {
      StringSetting setting = this.itemTarget;
      ClientModule module = this.pickerModule;
      Item previous = ItemEnchants.resolve(setting.get());
      setting.set(picked.stored);
      this.closePicker();
      StringSetting enchants = setting.enchantSetting();
      if (enchants != null) {
         if (previous != picked.item) {
            enchants.set("");
         }

         if (module != null && ItemEnchants.supports(picked.item)) {
            this.openEnchants(module, enchants);
         }
      }
   }

   private static void removeFromList(StringListSetting setting, Item item) {
      for (String value : setting.get()) {
         if (ItemEnchants.resolve(value) == item) {
            setting.removeValue(value);
            return;
         }
      }
   }

   private ItemPickerEditor.ItemInfo column(
      String id,
      ItemPickerEditor.ItemInfo[] rows,
      int count,
      boolean adding,
      Item current,
      float x,
      float y,
      float w,
      float h,
      float[] scroll,
      String emptyText,
      ImFont font,
      ImFont small
   ) {
      ItemPickerEditor.ItemInfo clicked = null;
      ImGui.setCursorPos(x, y);
      if (ImGui.beginChild(id, w, h, false, 24)) {
         float step = AnubisWidgets.s(42.0F);
         float rowW = w - AnubisWidgets.s(6.0F);
         float rowH = step - AnubisWidgets.s(2.0F);
         ImGui.setCursorPos(0.0F, 0.0F);
         ImGui.dummy(1.0F, count * step + AnubisWidgets.s(6.0F));
         ImDrawList dl = ImGui.getWindowDrawList();
         if (count == 0) {
            AnubisWidgets.text(dl, font, ImGui.getWindowPosX() + AnubisWidgets.s(8.0F), ImGui.getWindowPosY() + AnubisWidgets.s(12.0F), -4542808, emptyText);
         } else {
            float lineH = AnubisWidgets.lineHeight(font);
            float smallH = AnubisWidgets.lineHeight(small);
            float icon = ItemIcons.pixels(AnubisWidgets.s(32.0F));
            float btnR = AnubisWidgets.s(11.0F);
            float scrollY = ImGui.getScrollY();
            int first = Math.max(0, (int)(scrollY / step));
            int last = Math.min(count, (int)Math.ceil((scrollY + ImGui.getWindowHeight()) / step));
            int accent = GuiPalette.accent();

            for (int i = first; i < last; i++) {
               ItemPickerEditor.ItemInfo row = rows[i];
               ImGui.setCursorPos(0.0F, i * step);
               float x0 = ImGui.getCursorScreenPosX();
               float y0 = ImGui.getCursorScreenPosY();
               float cy = y0 + rowH * 0.5F;
               ImGui.pushID(row.rawId);
               if (ImGui.invisibleButton("##row", rowW, rowH)) {
                  clicked = row;
               }

               boolean hovered = ImGui.isItemHovered();
               ImGui.popID();
               boolean selected = current != null && row.item == current;
               if (selected) {
                  dl.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, AnubisWidgets.c(AnubisWidgets.withAlpha(accent, 0.18F)), AnubisWidgets.s(8.0F));
               } else if (hovered) {
                  dl.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, AnubisWidgets.c(318767103), AnubisWidgets.s(8.0F));
               }

               float ix = x0 + AnubisWidgets.s(6.0F);
               ItemIcons.draw(dl, row.item, ix, cy - icon * 0.5F, icon, 1.0F);
               float bx = x0 + rowW - AnubisWidgets.s(8.0F) - btnR;
               float tx = ix + icon + AnubisWidgets.s(12.0F);
               drawLabel(
                  dl,
                  font,
                  small,
                  tx,
                  cy,
                  bx - btnR - AnubisWidgets.s(10.0F) - tx,
                  row.name,
                  row.stored,
                  !hovered && !selected ? -2042416 : -461582,
                  lineH,
                  smallH
               );
               if (selected) {
                  dl.addCircleFilled(bx, cy, btnR, AnubisWidgets.c(accent), 24);
                  AnubisWidgets.check(dl, bx, cy, btnR * 0.5F, -1);
               } else {
                  int fill = !hovered ? -13226454 : (adding ? accent : -10856974);
                  int glyph = hovered ? -1 : -4542808;
                  dl.addCircleFilled(bx, cy, btnR, AnubisWidgets.c(fill), 24);
                  dl.addLine(bx - btnR * 0.45F, cy, bx + btnR * 0.45F, cy, AnubisWidgets.c(glyph), AnubisWidgets.s(1.6F));
                  if (adding) {
                     dl.addLine(bx, cy - btnR * 0.45F, bx, cy + btnR * 0.45F, AnubisWidgets.c(glyph), AnubisWidgets.s(1.6F));
                  }
               }
            }
         }

         ClickGuiRenderer.smoothScroll(scroll, frameDelta());
      }

      ImGui.endChild();
      return clicked;
   }

   private void refreshColumns() {
      Object target = this.listTarget != null ? this.listTarget : this.itemTarget;
      long revision = this.listTarget != null ? this.listTarget.revision() : this.itemTarget.revision();
      String key = this.search.get();
      if (target != this.columnsTarget || revision != this.columnsRevision || !key.equals(this.columnsKey) || this.columnsGeneration != namesGeneration) {
         this.columnsTarget = target;
         this.columnsRevision = revision;
         this.columnsKey = key;
         this.columnsGeneration = namesGeneration;
         ReferenceOpenHashSet<Item> picked = new ReferenceOpenHashSet();
         if (this.listTarget != null) {
            for (String value : this.listTarget.get()) {
               Item item = ItemEnchants.resolve(value);
               if (item != null) {
                  picked.add(item);
               }
            }
         }

         String filter = key.trim().toLowerCase(Locale.ROOT);
         String[] words = filter.split(" ");
         int size = ITEMS.size();
         if (this.available.length < size) {
            this.available = new ItemPickerEditor.ItemInfo[size];
            this.chosen = new ItemPickerEditor.ItemInfo[size];
            this.sortKeys = new long[size];
         }

         int count = 0;

         for (int i = 0; i < size; i++) {
            ItemPickerEditor.ItemInfo info = ITEMS.get(i);
            if (!picked.contains(info.item)) {
               if (filter.isEmpty()) {
                  this.sortKeys[count++] = i;
               } else {
                  int distance = matchDistance(info, filter, words);
                  if (distance >= 0) {
                     this.sortKeys[count++] = (long)distance << 32 | i;
                  }
               }
            }
         }

         Arrays.sort(this.sortKeys, 0, count);

         for (int k = 0; k < count; k++) {
            this.available[k] = ITEMS.get((int)this.sortKeys[k]);
         }

         this.availableCount = count;
         int shown = 0;
         if (this.listTarget != null) {
            for (String value : this.listTarget.get()) {
               Item item = ItemEnchants.resolve(value);
               if (item != null) {
                  ItemPickerEditor.ItemInfo info = info(item);
                  if (filter.isEmpty() || matchDistance(info, filter, words) >= 0) {
                     this.chosen[shown++] = info;
                  }
               }
            }
         }

         this.chosenCount = shown;
      }
   }

   private void openEnchants(ClientModule module, StringSetting setting) {
      this.closePicker();
      this.enchantTarget = setting;
      this.enchantItem = setting.enchantItem() == null ? null : ItemEnchants.resolve(setting.enchantItem().get());
      String itemName = this.enchantItem == null ? "No item" : info(this.enchantItem).name;
      this.enchantSubtitle = module.name() + " / " + itemName;
      this.loadEnchantRows();
      this.enchantOpenedAt = System.nanoTime();
      this.enchantJustOpened = true;
      this.enchantScroll[0] = this.enchantScroll[1] = 0.0F;
   }

   private void closeEnchants() {
      this.enchantTarget = null;
      this.enchantItem = null;
      this.enchantRows.clear();
   }

   private void loadEnchantRows() {
      this.enchantRows.clear();
      this.enchantsLoaded = MinecraftClient.getInstance().world != null;
      if (this.enchantsLoaded && this.enchantItem != null) {
         Map<String, Integer> current = ItemEnchants.parse(this.enchantTarget.get());

         for (RegistryEntry<Enchantment> holder : ItemEnchants.applicable(this.enchantItem)) {
            ItemPickerEditor.EnchantRow row = new ItemPickerEditor.EnchantRow(holder);
            Integer level = current.get(row.key);
            if (level == null) {
               level = current.get(ItemEnchants.key(row.name));
            }

            if (level != null) {
               row.on = true;
               row.level = Math.max(1, Math.min(row.max, level));
            }

            this.enchantRows.add(row);
         }
      }
   }

   private void writeEnchants() {
      List<String> parts = new ArrayList<>();

      for (ItemPickerEditor.EnchantRow row : this.enchantRows) {
         if (row.on) {
            parts.add(row.max > 1 ? row.path + " " + row.level : row.path);
         }
      }

      this.enchantTarget.set(String.join(", ", parts));
   }

   private void toggle(ItemPickerEditor.EnchantRow row) {
      row.on = !row.on;
      if (row.on) {
         for (ItemPickerEditor.EnchantRow other : this.enchantRows) {
            if (other != row && other.on && !Enchantment.canBeCombined(row.holder, other.holder)) {
               other.on = false;
            }
         }
      }

      this.writeEnchants();
   }

   private void renderEnchantWindow() {
      beginFrame();
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float margin = AnubisWidgets.s(16.0F);
      float w = Math.min(sw - margin * 2.0F, AnubisWidgets.s(560.0F));
      float h = Math.min(sh - AnubisWidgets.s(86.0F) - AnubisWidgets.s(32.0F), AnubisWidgets.s(640.0F));
      float open = ClickGuiRenderer.easeOut(ClickGuiRenderer.elapsed(this.enchantOpenedAt, 200.0F));
      float x = Math.round((sw - w) * 0.5F);
      float y = Math.round(Math.max(margin, (sh - AnubisWidgets.s(86.0F) - h) * 0.5F + AnubisWidgets.s(8.0F)) + AnubisWidgets.s(14.0F) * (1.0F - open));
      float rounding = ClickGuiRenderer.cardRounding();
      ImGui.setNextWindowPos(0.0F, 0.0F, 1);
      ImGui.setNextWindowSize(sw, sh, 1);
      if (this.enchantJustOpened) {
         ImGui.setNextWindowFocus();
      }

      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(4, 0.0F);
      boolean closeClicked = false;
      ItemPickerEditor.EnchantRow toggled = null;
      boolean visible = ImGui.begin("##enchant_picker", 8639);
      if (this.enchantJustOpened) {
         imgui.internal.ImGui.bringWindowToDisplayFront(imgui.internal.ImGui.getCurrentWindow());
      }

      if (visible) {
         ImDrawList dl = ImGui.getWindowDrawList();
         dl.addRectFilled(0.0F, 0.0F, sw, sh, AnubisWidgets.c(1929379840));
         shadow(dl, x, y, w, h, rounding);
         dl.addRectFilled(x, y, x + w, y + h, AnubisWidgets.c(GuiPalette.abgr(ThemeStore.get().cards.color.argbWithAlpha(255))), rounding);
         dl.addRect(x, y, x + w, y + h, AnubisWidgets.c(318767103), rounding, 0, 1.0F);
         ImFont font = ImGuiManager.getInstance().moduleFont();
         ImFont small = ImGuiManager.getInstance().smallFont();
         float pad = AnubisWidgets.s(24.0F);
         float top = y + AnubisWidgets.s(22.0F);
         float btn = AnubisWidgets.s(28.0F);
         ClickGuiRenderer.drawCardTitle("Enchantments", x + pad, top + btn * 0.5F);
         ImGui.setCursorPos(x + w - pad - btn, top);
         if (AnubisWidgets.iconButton("##close", btn)) {
            closeClicked = true;
         }

         AnubisWidgets.cross(dl, x + w - pad - btn * 0.5F, top + btn * 0.5F, AnubisWidgets.s(4.5F), -461582);
         float subY = top + btn + AnubisWidgets.s(2.0F);
         float smallH = AnubisWidgets.lineHeight(small);
         float lineH = AnubisWidgets.lineHeight(font);
         float subX = x + pad;
         if (this.enchantItem != null) {
            float icon = ItemIcons.pixels(AnubisWidgets.s(20.0F));
            ItemIcons.draw(dl, this.enchantItem, subX, subY + smallH * 0.5F - icon * 0.5F, icon, 1.0F);
            subX += icon + AnubisWidgets.s(8.0F);
         }

         AnubisWidgets.text(dl, small, subX, subY, -4542808, this.enchantSubtitle);
         float helpY = subY + smallH + AnubisWidgets.s(8.0F);
         AnubisWidgets.text(
            dl,
            small,
            x + pad,
            helpY,
            -4542808,
            AnubisWidgets.ellipsize(small, "Pick what the item must have. Clashing enchantments turn off on their own.", w - pad * 2.0F)
         );
         float footerH = AnubisWidgets.s(32.0F);
         float listTop = helpY + smallH + AnubisWidgets.s(14.0F);
         float listBottom = y + h - AnubisWidgets.s(20.0F) - footerH - AnubisWidgets.s(12.0F);
         float listH = Math.max(1.0F, listBottom - listTop);
         float colW = w - pad * 2.0F;
         dl.addLine(x + pad, listTop - AnubisWidgets.s(6.0F), x + pad + colW, listTop - AnubisWidgets.s(6.0F), AnubisWidgets.c(268435455), 1.0F);
         if (this.enchantsLoaded && !this.enchantRows.isEmpty()) {
            ImGui.setCursorPos(x + pad, listTop);
            if (ImGui.beginChild("##enchant_rows", colW, listH, false, 24)) {
               float step = AnubisWidgets.s(42.0F);
               float rowW = colW - AnubisWidgets.s(6.0F);
               float rowH = step - AnubisWidgets.s(2.0F);
               ImGui.setCursorPos(0.0F, 0.0F);
               ImGui.dummy(1.0F, this.enchantRows.size() * step + AnubisWidgets.s(6.0F));
               ImDrawList rows = ImGui.getWindowDrawList();
               int accent = GuiPalette.accent();
               float scrollY = ImGui.getScrollY();
               int first = Math.max(0, (int)(scrollY / step));
               int last = Math.min(this.enchantRows.size(), (int)Math.ceil((scrollY + ImGui.getWindowHeight()) / step));

               for (int i = first; i < last; i++) {
                  ItemPickerEditor.EnchantRow row = this.enchantRows.get(i);
                  ImGui.setCursorPos(0.0F, i * step);
                  float x0 = ImGui.getCursorScreenPosX();
                  float y0 = ImGui.getCursorScreenPosY();
                  float cy = y0 + rowH * 0.5F;
                  float stepperW = row.max > 1 ? AnubisWidgets.s(112.0F) : 0.0F;
                  ImGui.pushID(i);
                  if (ImGui.invisibleButton("##toggle", Math.max(1.0F, rowW - stepperW), rowH)) {
                     toggled = row;
                  }

                  boolean hovered = ImGui.isItemHovered();
                  if (row.on) {
                     rows.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, AnubisWidgets.c(AnubisWidgets.withAlpha(accent, 0.14F)), AnubisWidgets.s(8.0F));
                  } else if (hovered) {
                     rows.addRectFilled(x0, y0, x0 + rowW, y0 + rowH, AnubisWidgets.c(318767103), AnubisWidgets.s(8.0F));
                  }

                  float box = AnubisWidgets.s(18.0F);
                  float bx = x0 + AnubisWidgets.s(10.0F);
                  rows.addRectFilled(bx, cy - box * 0.5F, bx + box, cy + box * 0.5F, AnubisWidgets.c(-13226454), AnubisWidgets.s(4.0F));
                  rows.addRect(
                     bx + 0.5F,
                     cy - box * 0.5F + 0.5F,
                     bx + box - 0.5F,
                     cy + box * 0.5F - 0.5F,
                     AnubisWidgets.c(row.on ? accent : (hovered ? 1090519039 : 452984831)),
                     AnubisWidgets.s(4.0F),
                     0,
                     1.0F
                  );
                  if (row.on) {
                     AnubisWidgets.check(rows, bx + box * 0.5F, cy, box * 0.28F, accent);
                  }

                  float tx = bx + box + AnubisWidgets.s(12.0F);
                  String label = row.max > 1 ? row.name + "  (max " + ItemEnchants.roman(row.max) + ")" : row.name;
                  AnubisWidgets.text(
                     rows,
                     font,
                     tx,
                     cy - lineH * 0.5F,
                     !row.on && !hovered ? -2042416 : -461582,
                     AnubisWidgets.ellipsize(font, label, x0 + rowW - stepperW - AnubisWidgets.s(10.0F) - tx)
                  );
                  if (row.max > 1) {
                     float chipW = AnubisWidgets.s(28.0F);
                     float chipH = AnubisWidgets.s(26.0F);
                     float right = x0 + rowW - AnubisWidgets.s(8.0F);
                     ImGui.setCursorScreenPos(right - chipW, cy - chipH * 0.5F);
                     if (AnubisWidgets.chipButton("##up", "+", chipW, chipH, false) && row.level < row.max) {
                        row.level++;
                        if (!row.on) {
                           toggled = row;
                        } else {
                           this.writeEnchants();
                        }
                     }

                     String levelText = ItemEnchants.roman(row.level);
                     float levelW = AnubisWidgets.s(40.0F);
                     float lx = right - chipW - levelW;
                     AnubisWidgets.text(
                        rows, font, lx + (levelW - AnubisWidgets.textWidth(font, levelText)) * 0.5F, cy - lineH * 0.5F, row.on ? -461582 : -4542808, levelText
                     );
                     ImGui.setCursorScreenPos(lx - chipW, cy - chipH * 0.5F);
                     if (AnubisWidgets.chipButton("##down", "-", chipW, chipH, false) && row.level > 1) {
                        row.level--;
                        if (!row.on) {
                           toggled = row;
                        } else {
                           this.writeEnchants();
                        }
                     }
                  }

                  ImGui.popID();
               }

               ClickGuiRenderer.smoothScroll(this.enchantScroll, frameDelta());
            }

            ImGui.endChild();
         } else {
            AnubisWidgets.text(
               dl,
               font,
               x + pad + AnubisWidgets.s(8.0F),
               listTop + AnubisWidgets.s(10.0F),
               -4542808,
               this.enchantsLoaded ? "This item takes no enchantments." : "Join a world to load enchantments."
            );
         }

         float footerY = y + h - AnubisWidgets.s(20.0F) - footerH;
         float doneW = AnubisWidgets.textWidth(font, "Done") + AnubisWidgets.s(36.0F);
         ImGui.setCursorPos(x + w - pad - doneW, footerY);
         if (AnubisWidgets.chipButton("##done", "Done", doneW, footerH, true)) {
            closeClicked = true;
         }

         float clearW = AnubisWidgets.textWidth(font, "Clear") + AnubisWidgets.s(36.0F);
         ImGui.setCursorPos(x + pad, footerY);
         boolean any = false;

         for (ItemPickerEditor.EnchantRow row : this.enchantRows) {
            any |= row.on;
         }

         ImGui.beginDisabled(!any);
         if (AnubisWidgets.chipButton("##clear_enchants", "Clear", clearW, footerH, false)) {
            for (ItemPickerEditor.EnchantRow row : this.enchantRows) {
               row.on = false;
            }

            this.writeEnchants();
         }

         ImGui.endDisabled();
         if (!this.enchantJustOpened && ImGui.isMouseClicked(0) && ImGui.isWindowHovered() && !ImGui.isMouseHoveringRect(x, y, x + w, y + h)) {
            closeClicked = true;
         }
      }

      ImGui.end();
      ImGui.popStyleVar(4);
      this.enchantJustOpened = false;
      if (toggled != null) {
         this.toggle(toggled);
      }

      if (closeClicked) {
         this.closeEnchants();
      }
   }

   private String summary(StringSetting setting) {
      ItemPickerEditor.Summary cached = (ItemPickerEditor.Summary)this.summaries.get(setting);
      if (cached != null && cached.revision() == setting.revision() && cached.names() == namesGeneration) {
         return cached.text();
      }

      Map<String, Integer> parsed = ItemEnchants.parse(setting.get());
      List<String> parts = new ArrayList<>();
      List<RegistryEntry<Enchantment>> all = ItemEnchants.all();

      for (Entry<String, Integer> entry : parsed.entrySet()) {
         String name = null;
         int max = 0;

         for (RegistryEntry<Enchantment> holder : all) {
            if (ItemEnchants.key(ItemEnchants.path(holder)).equals(entry.getKey())) {
               name = holder.value().description().getString();
               max = holder.value().getMaxLevel();
               break;
            }
         }

         if (name == null) {
            name = entry.getKey();
         }

         parts.add(max == 1 ? name : name + " " + ItemEnchants.roman(entry.getValue()));
      }

      String text = String.join(", ", parts);
      this.summaries.put(setting, new ItemPickerEditor.Summary(setting.revision(), namesGeneration, text));
      return text;
   }

   private static boolean headerChip(String id, String label, float right, float cy, ImFont font) {
      float chipW = AnubisWidgets.textWidth(font, label) + AnubisWidgets.s(28.0F);
      float chipH = AnubisWidgets.s(28.0F);
      ImGui.setCursorScreenPos(right - chipW, cy - chipH * 0.5F);
      return AnubisWidgets.chipButton(id, label, chipW, chipH, false);
   }

   private static boolean chip(String id, String label, boolean disabled, float right, float cy, ImFont small, String tooltip) {
      boolean pushed = ClickGuiRenderer.pushFont(small);
      float chipW = AnubisWidgets.textWidth(small, label) + AnubisWidgets.s(20.0F);
      float chipH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(12.0F);
      ImGui.setCursorPos(right - chipW, cy - chipH * 0.5F);
      ImGui.beginDisabled(disabled);
      boolean clicked = AnubisWidgets.chipButton(id, label, chipW, chipH, false);
      ImGui.endDisabled();
      if (ImGui.isItemHovered(65536)) {
         ClickGuiRenderer.showTooltip(tooltip);
      }

      if (pushed) {
         ImGui.popFont();
      }

      return clicked;
   }

   private static void columnHeader(ImDrawList dl, ImFont font, ImFont small, float x, float y, float h, float w, String title, String count) {
      float cy = y + h * 0.5F;
      AnubisWidgets.text(dl, font, x + AnubisWidgets.s(4.0F), cy - AnubisWidgets.lineHeight(font) * 0.5F, -461582, title);
      countPill(dl, small, x + AnubisWidgets.s(4.0F) + AnubisWidgets.textWidth(font, title) + AnubisWidgets.s(8.0F), cy, count);
      dl.addLine(x, y + h + AnubisWidgets.s(4.0F), x + w, y + h + AnubisWidgets.s(4.0F), AnubisWidgets.c(268435455), 1.0F);
   }

   private static void countPill(ImDrawList dl, ImFont small, float x, float cy, String count) {
      float w = AnubisWidgets.textWidth(small, count) + AnubisWidgets.s(14.0F);
      float h = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(6.0F);
      float y = cy - h * 0.5F;
      dl.addRectFilled(x, y, x + w, y + h, AnubisWidgets.c(-13226454), h * 0.5F);
      AnubisWidgets.text(dl, small, x + AnubisWidgets.s(7.0F), y + AnubisWidgets.s(3.0F), -4542808, count);
   }

   private static void shadow(ImDrawList dl, float x, float y, float w, float h, float rounding) {
      for (int i = 1; i <= 6; i++) {
         float e = AnubisWidgets.s(2.5F) * i;
         dl.addRectFilled(x - e, y - e + AnubisWidgets.s(4.0F), x + w + e, y + h + e + AnubisWidgets.s(4.0F), AnubisWidgets.c(335544320), rounding + e);
      }
   }

   private static void beginFrame() {
      refreshNames();
   }

   private static ItemPickerEditor.ItemInfo info(Item item) {
      refreshNames();
      ItemPickerEditor.ItemInfo info = (ItemPickerEditor.ItemInfo)BY_ITEM.get(item);
      if (info == null) {
         info = new ItemPickerEditor.ItemInfo(item);
         info.name = item.getDefaultStack().getName().getString();
         info.nameLower = info.name.toLowerCase(Locale.ROOT);
         BY_ITEM.put(item, info);
      }

      return info;
   }

   private static void refreshNames() {
      String selected = MinecraftClient.getInstance().getLanguageManager().getLanguage();
      if (!selected.equals(language) || ITEMS.isEmpty()) {
         language = selected;
         if (ITEMS.isEmpty()) {
            for (Item item : Registries.ITEM) {
               if (item != Items.AIR) {
                  ItemPickerEditor.ItemInfo info = new ItemPickerEditor.ItemInfo(item);
                  ITEMS.add(info);
                  BY_ITEM.put(item, info);
               }
            }
         }

         for (ItemPickerEditor.ItemInfo info : ITEMS) {
            info.name = info.item.getDefaultStack().getName().getString();
            info.nameLower = info.name.toLowerCase(Locale.ROOT);
         }

         ITEMS.sort(Comparator.<ItemPickerEditor.ItemInfo, String>comparing(infox -> infox.nameLower).thenComparing(infox -> infox.id));
         namesGeneration++;
      }
   }

   private static int matchDistance(ItemPickerEditor.ItemInfo info, String filter, String[] words) {
      int found = Math.max(wordsFound(info.nameLower, words), wordsFound(info.idLower, words));
      int nameDistance = levenshtein(filter, info.nameLower);
      int idDistance = levenshtein(filter, info.idLower);
      float relevancy = Math.max(
         1.0F - (float)nameDistance / Math.max(1, info.nameLower.length()), 1.0F - (float)idDistance / Math.max(1, info.idLower.length())
      );
      return found <= 0 && !(relevancy >= 0.5F) ? -1 : Math.min(nameDistance, idDistance);
   }

   private static int wordsFound(String text, String[] words) {
      int found = 0;

      for (String word : words) {
         if (!text.contains(word)) {
            return 0;
         }

         if (!word.isEmpty()) {
            for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + word.length())) {
               found++;
            }
         }
      }

      return found;
   }

   private static int levenshtein(String from, String to) {
      int n = from.length();
      int m = to.length();
      if (n == 0) {
         return m * 1;
      }

      if (m == 0) {
         return n * 8;
      }

      if (levPrev.length <= m) {
         levPrev = new int[m + 1];
         levCur = new int[m + 1];
      }

      int[] prev = levPrev;
      int[] cur = levCur;

      for (int j = 0; j <= m; j++) {
         prev[j] = j * 1;
      }

      for (int i = 1; i <= n; i++) {
         cur[0] = i * 8;
         char typed = from.charAt(i - 1);

         for (int j = 1; j <= m; j++) {
            int substitute = prev[j - 1] + (typed == to.charAt(j - 1) ? 0 : 8);
            cur[j] = Math.min(Math.min(prev[j] + 8, cur[j - 1] + 1), substitute);
         }

         int[] swap = prev;
         prev = cur;
         cur = swap;
      }

      return prev[m];
   }

   private static float frameDelta() {
      return ClickGuiRenderer.animationDelta(Math.max(ImGui.getIO().getDeltaTime(), 0.001F));
   }

   @Environment(EnvType.CLIENT)
   private static final class EnchantRow {
      final RegistryEntry<Enchantment> holder;
      final String path;
      final String key;
      final String name;
      final int max;
      int level;
      boolean on;

      EnchantRow(RegistryEntry<Enchantment> holder) {
         this.holder = holder;
         this.path = ItemEnchants.path(holder);
         this.key = ItemEnchants.key(this.path);
         this.name = holder.value().description().getString();
         this.max = Math.max(1, holder.value().getMaxLevel());
         this.level = this.max;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class ItemInfo {
      final Item item;
      final int rawId;
      final String id;
      final String stored;
      final String idLower;
      String name;
      String nameLower;

      ItemInfo(Item item) {
         this.item = item;
         this.rawId = Registries.ITEM.getRawId(item);
         Identifier key = Registries.ITEM.getId(item);
         this.id = key.toString();
         this.stored = ItemEnchants.stored(item);
         this.idLower = this.id.toLowerCase(Locale.ROOT);
      }
   }

   @Environment(EnvType.CLIENT)
   private record Summary(long revision, int names, String text) {
   }
}
