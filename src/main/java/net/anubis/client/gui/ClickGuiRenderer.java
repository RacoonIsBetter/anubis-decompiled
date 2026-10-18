package net.anubis.client.gui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.glfw.AnubisKeys;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.anubis.client.config.BundledDefaults;
import net.anubis.client.config.ConfigProfiles;
import net.anubis.client.config.FriendStore;
import net.anubis.client.config.HudTheme;
import net.anubis.client.config.ModuleCodes;
import net.anubis.client.config.ModuleConfigStore;
import net.anubis.client.config.ProfileException;
import net.anubis.client.config.Surface;
import net.anubis.client.config.ThemeStore;
import net.anubis.client.imgui.ImGuiManager;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.client.HotkeysModule;
import net.anubis.client.module.impl.client.HudModule;
import net.anubis.client.module.impl.client.PerformanceModule;
import net.anubis.client.module.impl.client.RadarModule;
import net.anubis.client.module.impl.client.RegionMapModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BlockColorListSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ItemColorListSetting;
import net.anubis.client.module.setting.KeySetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.Setting;
import net.anubis.client.module.setting.StringListSetting;
import net.anubis.client.module.setting.StringSetting;
import net.anubis.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class ClickGuiRenderer {
   private static final float PANEL_W = 212.0F;
   private static final float PANEL_GAP = 14.0F;
   private static final float PANEL_TOP = 28.0F;
   private static final float HEADER_H = 42.0F;
   private static final float ROW_H = 30.0F;
   private static final float ROW_GAP = 2.0F;
   private static final float ROW_INSET = 6.0F;
   private static final int HEADER_BAND = 251658239;
   private static final int ROW_RULE = 620756991;
   private static final float CARD_W = 460.0F;
   private static final float CARD_MAX_H = 760.0F;
   private static final float CARD_PAD = 24.0F;
   private static final float SETTING_ROW_H = 46.0F;
   private static final float TAB_BAR_H = 44.0F;
   private static final float TAB_BAR_BOTTOM = 26.0F;
   private static final float INSPECTOR_W = 360.0F;
   private static final float SMALL_TOG_W = 16.0F;
   private static final float SMALL_TOG_H = 16.0F;
   public static final int ERROR_TEXT = -10856974;
   private static final int[] AVATAR_COLORS = new int[]{-624588, -10893516, -16080897, -2207057, -10536961, -3690448, -2730408, -16724737};
   private static final int PANEL_FLAGS = 123;
   private static final int CARD_FLAGS = 8511;
   public static final int CHILD_FLAGS = 24;
   private static final String PANEL_ID_PREFIX = "##panel_";
   private static final String SEARCH_PANEL_ID = "##panel_search";
   private static final int STYLE_VAR_COUNT = 11;
   private static final int DYNAMIC_COLOR_COUNT = 10;
   private static final int[] THEME_COLORS = new int[]{
      0,
      -461582,
      1,
      -4542808,
      3,
      0,
      4,
      -14015199,
      5,
      352321535,
      6,
      0,
      7,
      -13226454,
      8,
      -12240588,
      9,
      -12240588,
      21,
      -13226454,
      22,
      -12240588,
      24,
      -13226454,
      25,
      -12240588,
      27,
      268435455,
      14,
      0,
      15,
      822083583,
      16,
      1358954495,
      17,
      1895825407,
      30,
      0,
      31,
      0,
      32,
      0,
      53,
      0
   };
   private ClickGuiRenderer.Tab tab = ClickGuiRenderer.Tab.MODULES;
   private final long guiOpenedAt = System.nanoTime();
   private long viewChangedAt = this.guiOpenedAt;
   private float tabIndicatorX = -1.0F;
   private float tabIndicatorW;
   private ClientModule settingsModule;
   private boolean cardJustOpened;
   private boolean dividerPending;
   private boolean panelGuard;
   private boolean tabBarGuard;
   private boolean cardGuard;
   private boolean cardTextLift;
   private final HashMap<String, float[]> scroll = new HashMap<>();
   private static float scrollDragFrom;
   private static final ImBoolean SCROLLBAR_HOVERED = new ImBoolean();
   private static final ImBoolean SCROLLBAR_HELD = new ImBoolean();
   private ClientModule listeningModule = null;
   private boolean listeningFirstFrame = false;
   private KeySetting listeningKeySetting = null;
   private boolean listeningKeyFirstFrame = false;
   private final ImBoolean colorPickerOpen = new ImBoolean(false);
   private ClientModule activeColorModule = null;
   private ColorSetting activeColorSetting = null;
   private final ImString globalSearch = new ImString(128);
   private boolean focusSearch;
   private float searchBodyH;
   private final Map<String, float[]> panelSpots = new HashMap<>();
   private ClickGuiRenderer.CategoryPanel[] panelCache = new ClickGuiRenderer.CategoryPanel[0];
   private int panelCacheRevision = -1;
   private final Map<String, float[]> panelFold = new HashMap<>();
   private final Map<String, Motion.Rows> rowMotion = new HashMap<>();
   private final Int2ObjectOpenHashMap<String> keyLabels = new Int2ObjectOpenHashMap();
   private int cachedRegistryRevision = -1;
   private String cachedGlobalSearch = "";
   private List<ClientModule> cachedGlobalResults = List.of();
   private final ImString friendInput = new ImString(32);
   private boolean focusFriendInput;
   private String friendMessage;
   private long friendMessageAt;
   private final ImString profileInput = new ImString(64);
   private boolean focusProfileInput;
   private String configMessage;
   private boolean configMessageError;
   private long configMessageAt;
   private String moduleCodeFor;
   private String moduleCodeMessage;
   private boolean moduleCodeError;
   private long moduleCodeAt;
   private final ProfileList profileList = new ProfileList(this::setConfigMessage);
   private String pendingConfirm;
   private long pendingConfirmAt;
   private String themeMessage;
   private boolean themeMessageError;
   private long themeMessageAt;
   private Surface themeSurface;
   private boolean inspectMode;
   private final List<float[]> panelRects = new ArrayList<>();
   private float[] cardRect;
   private float[] tabBarRect;
   private float[] inspectorRect;
   private Surface hoveredSurface;
   private boolean rowDividers;
   private float rowAboveEmphasis;
   private boolean focusInspector;
   private long previewChangedAt;
   private String hudElementId;
   private boolean focusHudInspector;
   private String hudMessage;
   private boolean hudMessageError;
   private long hudMessageAt;
   private final BlockListEditor blockLists = new BlockListEditor();
   private final ItemPickerEditor itemPickers = new ItemPickerEditor();
   private ItemColorListSetting activeItemColorPalette = null;
   private boolean itemColorWindowOpen = false;
   private final ImString itemSearchBuf = new ImString(256);
   private ItemColorListSetting.ItemEntry activeItemColorEntry = null;
   private boolean editingItemDefaultColor = false;
   private final HashMap<ItemColorListSetting.ItemEntry, Float> itemEntryTogAnim = new HashMap<>();
   private final ImBoolean itemColorPickerOpen = new ImBoolean(false);

   public void render() {
      float dt = animationDelta(Math.max(ImGui.getIO().getDeltaTime(), 0.001F));
      ThemeStore theme = ThemeStore.get();
      this.panelGuard = HudChrome.needsTextGuard(theme.panels);
      this.tabBarGuard = HudChrome.needsTextGuard(theme.tabBar);
      this.cardGuard = HudChrome.needsTextGuard(theme.cards);
      this.cardTextLift = theme.cards.isImage() && !theme.cards.isBanner();
      applyTheme();
      boolean moduleFontPushed = pushFont(ImGuiManager.getInstance().moduleFont());

      try {
         float sw = ImGui.getIO().getDisplaySizeX();
         float sh = ImGui.getIO().getDisplaySizeY();
         float open = easeOut(elapsed(this.guiOpenedAt, 240.0F));
         float view = easeOut(elapsed(this.viewChangedAt, 260.0F));
         float alpha = Math.max(0.01F, open * view);
         ImGui.getBackgroundDrawList().addRectFilled(0.0F, 0.0F, sw, sh, AnubisWidgets.withAlpha(-16777216, theme.backgroundDim.get() * open));
         Path picked = ThemeImages.get().takePendingImport();
         if (picked != null) {
            this.importHeaderImage(picked);
         }

         this.handleShortcuts();
         switch (this.tab) {
            case MODULES:
               if (this.settingsModule == null) {
                  this.renderModulePanels(dt, alpha);
               } else {
                  this.renderModuleCard(this.settingsModule, dt, alpha, view);
               }
               break;
            case HUD:
               this.renderHudStudio(dt, alpha, view);
               break;
            case FRIENDS:
               this.renderFriendsCard(dt, alpha, view);
               break;
            case THEME:
               this.renderThemeStudio(dt, alpha, view);
               break;
            case CONFIG:
               this.renderConfigCard(dt, alpha, view);
         }

         this.renderTabBar(dt, open);
         this.renderColorPickerWindow();
         this.blockLists.renderWindows();
         this.itemPickers.renderWindows();
         this.renderItemColorPaletteWindow();
         this.closeCardOnOutsideClick();
      } finally {
         if (moduleFontPushed) {
            ImGui.popFont();
         }

         removeTheme();
      }

      AndroidSystemPicker.render();
   }

   private void handleShortcuts() {
      if (this.tab == ClickGuiRenderer.Tab.MODULES && this.settingsModule == null && ImGui.getIO().getKeyCtrl() && ImGui.isKeyPressed(551)) {
         this.focusSearch = true;
      }
   }

   private void switchTab(ClickGuiRenderer.Tab next) {
      if (next != this.tab) {
         this.closeSettings();
         this.tab = next;
         this.viewChangedAt = System.nanoTime();
         this.pendingConfirm = null;
         this.profileList.disarm();
         if (next == ClickGuiRenderer.Tab.CONFIG) {
            ConfigProfiles.getInstance().refresh();
         }
      }
   }

   private void renderModulePanels(float dt, float alpha) {
      ClickGuiRenderer.CategoryPanel[] panels = this.visiblePanels();
      boolean placed = this.panelSpots.containsKey("##panel_search");

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         placed &= this.panelSpots.containsKey(panel.id());
      }

      if (!placed) {
         this.placeNewPanels(panels);
      }

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         this.renderCategoryPanel(panel, dt, alpha);
      }

      this.renderSearchPanel(dt, alpha);
   }

   private ClickGuiRenderer.CategoryPanel[] visiblePanels() {
      int revision = ModuleRegistry.revision();
      if (revision != this.panelCacheRevision) {
         List<ClickGuiRenderer.CategoryPanel> panels = new ArrayList<>();

         for (ModuleCategory category : ModuleCategory.values()) {
            if (!this.modulesFor(category).isEmpty()) {
               panels.add(new ClickGuiRenderer.CategoryPanel(category, "##panel_" + category.name(), "##rows_" + category.name()));
            }
         }

         this.panelCache = panels.toArray(ClickGuiRenderer.CategoryPanel[]::new);
         this.panelCacheRevision = revision;
      }

      return this.panelCache;
   }

   private void renderCategoryPanel(ClickGuiRenderer.CategoryPanel panel, float dt, float alpha) {
      List<ClientModule> modules = this.modulesFor(panel.category());
      float listH = Math.min(rowListHeight(modules.size()), maxListHeight());
      float open = this.unfolded(panel, dt);
      if (open < 1.0F) {
         ImGui.setNextWindowSize(AnubisWidgets.s(212.0F), AnubisWidgets.s(42.0F) + listH * open, 1);
      }

      if (this.beginPanel(panel.id(), alpha)) {
         String title = panel.category().displayName();
         this.drawPanelChrome(title, panel.category());
         this.recordPanelRect();
         this.renderFoldControl(panel.category(), open);
         if (open > 0.0F) {
            this.renderRowList(panel.rowsId(), modules, listH, false, dt);
         }
      }

      endPanel();
   }

   private float unfolded(ClickGuiRenderer.CategoryPanel panel, float dt) {
      float target = PanelStateStore.getInstance().isCollapsed(panel.category()) ? 0.0F : 1.0F;
      float[] open = this.panelFold.get(panel.id());
      if (open == null) {
         this.panelFold.put(panel.id(), open = new float[]{target});
      }

      open[0] = smooth(open[0], target, dt, 14.0F);
      if (Math.abs(open[0] - target) < 0.002F) {
         open[0] = target;
      }

      return open[0];
   }

   private void renderFoldControl(ModuleCategory category, float open) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float headerH = AnubisWidgets.s(42.0F);
      float hit = AnubisWidgets.s(22.0F);
      float cx = wx + ww - AnubisWidgets.s(20.0F);
      float cy = wy + headerH * 0.5F;
      boolean overHeader = !this.inspectMode && ImGui.isWindowHovered() && ImGui.isMouseHoveringRect(wx, wy, wx + ww, wy + headerH);
      ImGui.setCursorScreenPos(cx - hit * 0.5F, cy - hit * 0.5F);
      boolean clicked = ImGui.invisibleButton("##fold", hit, hit) && !this.inspectMode;
      float hover = AnubisWidgets.animate(ImGui.getItemID(), overHeader, 18.0F);
      ImGui.setCursorPos(0.0F, headerH);
      if (clicked || overHeader && ImGui.isMouseClicked(1)) {
         PanelStateStore store = PanelStateStore.getInstance();
         store.setCollapsed(category, !store.isCollapsed(category));
      }

      AnubisWidgets.chevronRotated(
         ImGui.getWindowDrawList(), cx, cy, AnubisWidgets.s(4.0F), AnubisTheme.lerpColor(-4542808, -461582, hover), open * (float) (Math.PI / 2)
      );
   }

   private void renderSearchPanel(float dt, float alpha) {
      if (this.beginPanel("##panel_search", alpha)) {
         this.drawPanelChrome("Search", null);
         this.recordPanelRect();
         float inset = AnubisWidgets.s(6.0F) + AnubisWidgets.s(2.0F);
         ImGui.setCursorPos(inset, AnubisWidgets.s(42.0F) - AnubisWidgets.s(4.0F));
         ImGui.pushStyleVar(11, AnubisWidgets.s(10.0F), AnubisWidgets.s(7.0F));
         ImGui.setNextItemWidth(AnubisWidgets.s(212.0F) - inset * 2.0F);
         if (this.focusSearch) {
            ImGui.setKeyboardFocusHere();
            this.focusSearch = false;
         }

         ImGui.inputTextWithHint("##globalsearch", "Search modules...", this.globalSearch);
         ImGui.popStyleVar();
         ImGui.dummy(0.0F, AnubisWidgets.s(8.0F));
         String query = normalizeSearch(this.globalSearch.get());
         List<ClientModule> results = query.isEmpty() ? List.of() : this.searchModules(query);
         float target = query.isEmpty() ? 0.0F : (results.isEmpty() ? AnubisWidgets.s(34.0F) : Math.min(rowListHeight(results.size()), maxListHeight()));
         this.searchBodyH = smooth(this.searchBodyH, target, dt, 14.0F);
         if (Math.abs(this.searchBodyH - target) < 0.5F) {
            this.searchBodyH = target;
         }

         if (this.searchBodyH >= 1.0F) {
            if (results.isEmpty()) {
               ImGui.beginChild("##search_empty", AnubisWidgets.s(212.0F), this.searchBodyH, false, 24);
               String empty = "No results";
               ImFont font = ImGui.getFont();
               AnubisWidgets.text(
                  ImGui.getWindowDrawList(),
                  font,
                  ImGui.getWindowPosX() + (AnubisWidgets.s(212.0F) - AnubisWidgets.textWidth(font, empty)) * 0.5F,
                  ImGui.getWindowPosY() + AnubisWidgets.s(4.0F),
                  -4542808,
                  empty,
                  this.panelGuard
               );
               ImGui.endChild();
            } else {
               this.renderRowList("##search_rows", results, this.searchBodyH, true, dt);
            }
         }
      }

      endPanel();
   }

   private boolean beginPanel(String id, float alpha) {
      float[] spot = this.panelSpots.get(id);
      ImGui.setNextWindowPos(spot[0], spot[1], 4);
      ImGui.setNextWindowSizeConstraints(AnubisWidgets.s(212.0F), 0.0F, AnubisWidgets.s(212.0F), Float.MAX_VALUE);
      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, panelRounding());
      boolean open = ImGui.begin(id, 123);
      spot[0] = ImGui.getWindowPosX();
      spot[1] = ImGui.getWindowPosY();
      return open;
   }

   private static void endPanel() {
      ImGui.end();
      ImGui.popStyleVar(4);
   }

   private void drawPanelChrome(String title, ModuleCategory category) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float wh = ImGui.getWindowHeight();
      drawShadow(wx, wy, ww, wh, panelRounding(), 4);
      ImDrawList dl = ImGui.getWindowDrawList();
      SurfaceBackground.draw(dl, ThemeStore.get().panels, wx, wy, ww, wh, panelRounding());
      ImFont font = ImGuiManager.getInstance().categoryFont();
      float headerH = AnubisWidgets.s(42.0F);
      drawHeaderBand(dl, wx, wy, ww, headerH);
      float r = AnubisWidgets.s(7.0F);
      float cx = wx + AnubisWidgets.s(14.0F) + r;
      float cy = wy + headerH * 0.5F;
      if (category == null) {
         AnubisWidgets.magnifier(dl, cx, cy, r, -4542808);
      } else {
         drawCategoryIcon(dl, category, cx, cy, r, -4542808);
      }

      float tx = cx + r + AnubisWidgets.s(9.0F);
      float ty = wy + (headerH - AnubisWidgets.lineHeight(font)) * 0.5F;
      AnubisWidgets.text(dl, font, tx, ty, -461582, title, this.panelGuard);
      ImGui.dummy(ww, headerH);
   }

   private static void drawHeaderBand(ImDrawList dl, float x, float y, float w, float h) {
      float rounding = panelRounding();
      float cap = Math.min(h, rounding + AnubisWidgets.s(2.0F));
      int top = AnubisWidgets.c(AnubisWidgets.withAlpha(251658239, 1.0F - cap / (2.0F * h)));
      int below = AnubisWidgets.c(AnubisWidgets.withAlpha(251658239, 1.0F - cap / h));
      int clear = AnubisWidgets.c(16777215);
      dl.addRectFilled(x, y, x + w, y + cap, top, rounding, 48);
      dl.addRectFilledMultiColor(x, y + cap, x + w, y + h, below, below, clear, clear);
   }

   private static void softRule(ImDrawList dl, float x0, float x1, float y, int peak) {
      float mid = (x0 + x1) * 0.5F;
      int solid = AnubisWidgets.c(peak);
      int clear = AnubisWidgets.c(peak & 16777215);
      dl.addRectFilledMultiColor(x0, y, mid, y + 1.0F, clear, solid, solid, clear);
      dl.addRectFilledMultiColor(mid, y, x1, y + 1.0F, solid, clear, clear, solid);
   }

   private static void drawCategoryIcon(ImDrawList dl, ModuleCategory category, float cx, float cy, float r, int color) {
      switch (category.icon()) {
         case CROSSHAIR:
            AnubisWidgets.crosshair(dl, cx, cy, r, color);
            break;
         case CHEVRON:
            AnubisWidgets.chevron(dl, cx, cy, r, color, true);
            break;
         case MOVE:
            AnubisWidgets.move(dl, cx, cy, r, color);
            break;
         case GRID:
            AnubisWidgets.grid(dl, cx, cy, r, color);
            break;
         case RING:
            AnubisWidgets.ring(dl, cx, cy, r, color);
            break;
         case EYE:
            AnubisWidgets.eye(dl, cx, cy, r, color);
            break;
         case MONITOR:
            AnubisWidgets.monitor(dl, cx, cy, r, color);
            break;
         case HOUSE:
            AnubisWidgets.house(dl, cx, cy, r, color);
            break;
         case PAPYRUS:
            AnubisWidgets.papyrus(dl, cx, cy, r, color);
            break;
         case ANKH:
            AnubisWidgets.ankh(dl, cx, cy, r, color);
      }
   }

   public static void drawShadow(float x, float y, float w, float h, float rounding, int layers) {
      ImDrawList bg = ImGui.getBackgroundDrawList();

      for (int i = 1; i <= layers; i++) {
         float e = AnubisWidgets.s(2.5F) * i;
         bg.addRectFilled(x - e, y - e + AnubisWidgets.s(4.0F), x + w + e, y + h + e + AnubisWidgets.s(4.0F), AnubisWidgets.c(335544320), rounding + e);
      }
   }

   private static float rowListHeight(int rows) {
      return rows * AnubisWidgets.s(32.0F) - AnubisWidgets.s(2.0F) + AnubisWidgets.s(6.0F);
   }

   private static float maxListHeight() {
      return Math.max(AnubisWidgets.s(120.0F), ImGui.getIO().getDisplaySizeY() * 0.62F - AnubisWidgets.s(42.0F));
   }

   private void renderRowList(String id, List<ClientModule> modules, float height, boolean showCategory, float dt) {
      if (ImGui.beginChild(id, AnubisWidgets.s(212.0F), Math.max(1.0F, height), false, 24)) {
         Motion.Rows motion = this.rowMotion.computeIfAbsent(id, key -> new Motion.Rows());
         motion.bind(modules);
         this.rowDividers = ThemeStore.get().moduleDividers.get();

         for (int i = 0; i < modules.size(); i++) {
            this.renderModuleRow(modules.get(i), showCategory, motion, i, dt);
         }

         ImGui.dummy(1.0F, AnubisWidgets.s(6.0F) - AnubisWidgets.s(2.0F));
         this.applySmoothScroll(id, dt);
      }

      ImGui.endChild();
   }

   private void renderModuleRow(ClientModule module, boolean showCategory, Motion.Rows motion, int row, float dt) {
      ImGui.pushID(module.id());
      float inset = AnubisWidgets.s(6.0F);
      float rowH = AnubisWidgets.s(30.0F);
      float localY = ImGui.getCursorPosY();
      ImGui.setCursorPosX(inset);
      float x0 = ImGui.getCursorScreenPosX();
      float y0 = ImGui.getCursorScreenPosY();
      float w = ImGui.getWindowWidth() - inset * 2.0F;
      if (ImGui.invisibleButton("##row", w, rowH) && !this.inspectMode) {
         if (module.isAction()) {
            module.onClick();
         } else {
            module.toggle();
         }
      }

      boolean hovered = ImGui.isItemHovered();
      if (!this.inspectMode && ImGui.isItemClicked(1)) {
         this.openSettings(module);
      }

      if (!this.inspectMode && ImGui.isItemHovered(65536)) {
         String description = module.description();
         showTooltip(description != null && !description.isBlank() ? description + "\nRight-click for settings" : "Right-click for settings");
      }

      float hover = motion.hover(row, hovered, dt, 18.0F);
      float on = motion.on(row, module.isEnabled(), dt, 14.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      float emphasis = Math.max(hover, on);
      if (this.rowDividers && row > 0) {
         float fade = 1.0F - Math.max(emphasis, this.rowAboveEmphasis);
         if (fade > 0.01F) {
            float y = (float)Math.floor(y0 - AnubisWidgets.s(2.0F) * 0.5F);
            softRule(dl, x0 + AnubisWidgets.s(8.0F), x0 + w - AnubisWidgets.s(8.0F), y, AnubisWidgets.withAlpha(620756991, fade));
         }
      }

      this.rowAboveEmphasis = emphasis;
      float r = AnubisWidgets.s(6.0F);
      if (hover > 0.01F) {
         dl.addRectFilled(x0, y0, x0 + w, y0 + rowH, AnubisWidgets.c(AnubisWidgets.withAlpha(318767103, hover)), r);
      }

      if (on > 0.01F) {
         dl.addRectFilled(
            x0,
            y0,
            x0 + w,
            y0 + rowH,
            AnubisWidgets.c(AnubisWidgets.withAlpha(AnubisTheme.lerpColor(GuiPalette.accent(), GuiPalette.accentHover(), hover * 0.3F), on)),
            r
         );
      }

      ImFont font = ImGui.getFont();
      ImFont small = ImGuiManager.getInstance().smallFont();
      String trailing = showCategory ? module.category().displayName() : (module.keybind() == -1 ? null : this.keyLabel(module.keybind()));
      float tw = trailing == null ? 0.0F : AnubisWidgets.textWidth(small, trailing);
      int textColor = AnubisTheme.lerpColor(AnubisTheme.lerpColor(-2042416, -461582, hover), -1, on);
      float room = w - AnubisWidgets.s(trailing == null && !module.isAction() ? 20.0F : 28.0F) - tw;
      String name = AnubisWidgets.ellipsize(font, module.name(), room);
      AnubisWidgets.text(dl, font, x0 + AnubisWidgets.s(10.0F), y0 + (rowH - AnubisWidgets.lineHeight(font)) * 0.5F, textColor, name, this.panelGuard);
      if (trailing != null) {
         int trailColor = AnubisTheme.lerpColor(-4542808, -419430401, on);
         AnubisWidgets.text(
            dl, small, x0 + w - AnubisWidgets.s(10.0F) - tw, y0 + (rowH - AnubisWidgets.lineHeight(small)) * 0.5F, trailColor, trailing, this.panelGuard
         );
      } else if (module.isAction()) {
         AnubisWidgets.chevronRight(
            dl, x0 + w - AnubisWidgets.s(14.0F), y0 + rowH * 0.5F, AnubisWidgets.s(4.0F), AnubisTheme.lerpColor(-4542808, -461582, hover)
         );
      }

      ImGui.setCursorPos(0.0F, localY + rowH + AnubisWidgets.s(2.0F));
      ImGui.popID();
   }

   public static void showTooltip(String text) {
      ImGui.pushStyleVar(2, AnubisWidgets.s(10.0F), AnubisWidgets.s(8.0F));
      ImGui.beginTooltip();
      ImGui.pushTextWrapPos(AnubisWidgets.s(280.0F));
      ImGui.textUnformatted(text);
      ImGui.popTextWrapPos();
      ImGui.endTooltip();
      ImGui.popStyleVar();
   }

   private void applySmoothScroll(String key, float dt) {
      smoothScroll(this.scroll.computeIfAbsent(key, k -> new float[2]), dt);
   }

   public static void smoothScroll(float[] state, float dt) {
      float max = ImGui.getScrollMaxY();
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float ww = ImGui.getWindowWidth();
      float wh = ImGui.getWindowHeight();
      float barH = Math.max(AnubisWidgets.s(24.0F), wh * wh / (wh + max));
      float travel = Math.max(1.0F, wh - barH);
      boolean held = false;
      boolean hot = false;
      if (max > 0.5F) {
         int id = ImGui.getID("##scrollbar");
         imgui.internal.ImGui.keepAliveID(id);
         imgui.internal.ImGui.buttonBehavior(wx + ww - AnubisWidgets.s(8.0F), wy, wx + ww, wy + wh, id, SCROLLBAR_HOVERED, SCROLLBAR_HELD);
         held = SCROLLBAR_HELD.get();
         hot = held || SCROLLBAR_HOVERED.get();
         if (held && ImGui.isMouseClicked(0)) {
            float barY = wy + travel * (state[0] / max);
            float mouseY = ImGui.getMousePosY();
            state[1] = mouseY >= barY && mouseY <= barY + barH ? state[0] : (mouseY - wy - barH * 0.5F) / travel * max;
            scrollDragFrom = state[1];
         } else if (held) {
            state[1] = scrollDragFrom + ImGui.getMouseDragDeltaY(0, 0.0F) * max / travel;
         }
      }

      if (!held && ImGui.isWindowHovered()) {
         float wheel = ImGui.getIO().getMouseWheel();
         if (wheel != 0.0F) {
            state[1] -= wheel * AnubisWidgets.s(56.0F);
         }
      }

      state[1] = Math.max(0.0F, Math.min(max, state[1]));
      state[0] = held ? state[1] : smooth(state[0], state[1], dt, 16.0F);
      if (Math.abs(state[0] - state[1]) < 0.3F) {
         state[0] = state[1];
      }

      ImGui.setScrollY(state[0]);
      if (max > 0.5F) {
         float barY = wy + travel * (state[0] / max);
         ImGui.getWindowDrawList()
            .addRectFilled(
               wx + ww - (hot ? AnubisWidgets.s(6.0F) : AnubisWidgets.s(4.0F)),
               barY,
               wx + ww - AnubisWidgets.s(1.5F),
               barY + barH,
               AnubisWidgets.c(hot ? 1728053247 : 872415231),
               AnubisWidgets.s(2.0F)
            );
      }
   }

   private void placeNewPanels(ClickGuiRenderer.CategoryPanel[] panels) {
      List<String> ids = new ArrayList<>();
      List<String> keys = new ArrayList<>();
      List<Float> heights = new ArrayList<>();
      PanelStateStore folds = PanelStateStore.getInstance();

      for (ClickGuiRenderer.CategoryPanel panel : panels) {
         ids.add(panel.id());
         keys.add(panel.category().name());
         heights.add(
            folds.isCollapsed(panel.category())
               ? AnubisWidgets.s(42.0F)
               : AnubisWidgets.s(42.0F) + Math.min(rowListHeight(this.modulesFor(panel.category()).size()), maxListHeight())
         );
      }

      ids.add("##panel_search");
      keys.add("search");
      heights.add(AnubisWidgets.s(42.0F) + ImGui.getFontSize() + AnubisWidgets.s(18.0F) + maxListHeight());
      Map<String, float[]> saved = savedPanelSpots();
      List<float[]> taken = new ArrayList<>();

      for (int i = 0; i < ids.size(); i++) {
         float[] spot = this.panelSpots.get(ids.get(i));
         if (spot == null && saved.containsKey(ids.get(i))) {
            spot = saved.get(ids.get(i));
            this.panelSpots.put(ids.get(i), spot);
         }

         if (spot != null) {
            taken.add(new float[]{spot[0], spot[1], AnubisWidgets.s(212.0F), heights.get(i)});
         }
      }

      boolean firstRun = taken.isEmpty();

      for (int i = 0; i < ids.size(); i++) {
         if (!this.panelSpots.containsKey(ids.get(i))) {
            float h = heights.get(i);
            float[] spot = bundledSpot(keys.get(i));
            if (spot == null && firstRun) {
               spot = panelPosFor(i, ids.size());
            }

            if (spot == null || coveredArea(spot[0], spot[1], h, taken) > 0.0F) {
               spot = freeSpot(h, taken);
            }

            this.panelSpots.put(ids.get(i), spot);
            taken.add(new float[]{spot[0], spot[1], AnubisWidgets.s(212.0F), h});
         }
      }
   }

   private static float[] bundledSpot(String key) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float[] spot = BundledDefaults.panelSpot(key, sw, sh);
      if (spot == null) {
         return null;
      }

      spot[0] = Math.max(0.0F, Math.min(spot[0], sw - AnubisWidgets.s(212.0F)));
      spot[1] = Math.max(0.0F, Math.min(spot[1], sh - AnubisWidgets.s(42.0F)));
      return spot;
   }

   private static float[] panelPosFor(int index, int panelCount) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = AnubisWidgets.s(212.0F);
      float gap = AnubisWidgets.s(14.0F);
      int columns = Math.max(1, (int)((sw - AnubisWidgets.s(40.0F) + gap) / (w + gap)));
      int inRow = Math.min(columns, panelCount);
      float rowW = inRow * (w + gap) - gap;
      float startX = Math.max(AnubisWidgets.s(20.0F), (sw - rowW) * 0.5F);
      return new float[]{startX + index % columns * (w + gap), AnubisWidgets.s(28.0F) + index / columns * sh * 0.45F};
   }

   private static float[] freeSpot(float h, List<float[]> taken) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = AnubisWidgets.s(212.0F);
      float gap = AnubisWidgets.s(14.0F);
      float margin = AnubisWidgets.s(20.0F);
      float bottom = sh - AnubisWidgets.s(70.0F) - gap;
      float highest = Float.MAX_VALUE;

      for (float[] rect : taken) {
         highest = Math.min(highest, rect[1]);
      }

      float[] rightMost = null;

      for (float[] rect : taken) {
         boolean topRow = rect[1] < highest + AnubisWidgets.s(42.0F);
         if (topRow && (rightMost == null || rect[0] + rect[2] > rightMost[0] + rightMost[2])) {
            rightMost = rect;
         }
      }

      float[] beside = rightMost == null ? new float[]{margin, AnubisWidgets.s(28.0F)} : new float[]{rightMost[0] + rightMost[2] + gap, rightMost[1]};
      List<float[]> scan = new ArrayList<>();
      scan.add(new float[]{margin, AnubisWidgets.s(28.0F)});

      for (float[] rect : taken) {
         scan.add(new float[]{rect[0] + rect[2] + gap, rect[1]});
         scan.add(new float[]{rect[0], rect[1] + rect[3] + gap});
      }

      scan.sort(Comparator.<float[]>comparingDouble(spotx -> spotx[1]).thenComparingDouble(spotx -> spotx[0]));
      scan.addFirst(beside);

      for (float[] spot : scan) {
         boolean onScreen = spot[0] >= 0.0F && spot[1] >= 0.0F && spot[0] + w <= sw - margin && spot[1] + h <= bottom;
         if (onScreen && coveredArea(spot[0], spot[1], h, taken) == 0.0F) {
            return spot;
         }
      }

      float[] best = null;
      float bestArea = Float.MAX_VALUE;

      for (float[] spot : scan) {
         float x = Math.max(0.0F, Math.min(spot[0], sw - w - margin));
         float y = Math.max(0.0F, Math.min(spot[1], sh - h - margin));
         float area = coveredArea(x, y, h, taken);
         if (area < bestArea) {
            best = new float[]{x, y};
            bestArea = area;
         }
      }

      return best;
   }

   private static float coveredArea(float x, float y, float h, List<float[]> taken) {
      float w = AnubisWidgets.s(212.0F);
      float m = AnubisWidgets.s(14.0F) * 0.5F;
      float area = 0.0F;

      for (float[] r : taken) {
         float overlapW = Math.min(x + w, r[0] + r[2]) + m - Math.max(x, r[0]);
         float overlapH = Math.min(y + h, r[1] + r[3]) + m - Math.max(y, r[1]);
         if (overlapW > 0.0F && overlapH > 0.0F) {
            area += overlapW * overlapH;
         }
      }

      return area;
   }

   private static Map<String, float[]> savedPanelSpots() {
      Map<String, float[]> spots = new HashMap<>();
      String ini = ImGui.getIO().getIniFilename();
      if (ini != null && Files.isRegularFile(Path.of(ini))) {
         String section = "[Window][";

         try {
            String window = null;

            for (String line : Files.readAllLines(Path.of(ini))) {
               if (line.startsWith("[")) {
                  boolean panel = line.startsWith(section + "##panel_") && line.endsWith("]");
                  window = panel ? line.substring(section.length(), line.length() - 1) : null;
               } else if (window != null && line.startsWith("Pos=")) {
                  String[] xy = line.substring(4).split(",");
                  spots.put(window, new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])});
               }
            }
         } catch (IOException | RuntimeException e) {
            spots.clear();
         }

         return spots;
      } else {
         return spots;
      }
   }

   private List<ClientModule> modulesFor(ModuleCategory category) {
      return ModuleRegistry.inCategory(category);
   }

   private List<ClientModule> searchModules(String query) {
      int revision = ModuleRegistry.revision();
      if (revision != this.cachedRegistryRevision) {
         this.cachedRegistryRevision = revision;
         this.cachedGlobalSearch = "";
         this.cachedGlobalResults = List.of();
      }

      if (query.equals(this.cachedGlobalSearch)) {
         return this.cachedGlobalResults;
      }

      List<ClientModule> matches = new ArrayList<>();

      for (ClientModule module : ModuleRegistry.all()) {
         if (module.name().toLowerCase(Locale.ROOT).contains(query)) {
            matches.add(module);
         }
      }

      this.cachedGlobalSearch = query;
      this.cachedGlobalResults = List.copyOf(matches);
      return this.cachedGlobalResults;
   }

   private static String normalizeSearch(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
   }

   private void renderTabBar(float dt, float open) {
      ClickGuiRenderer.Tab[] tabs = ClickGuiRenderer.Tab.values();
      ImFont font = ImGui.getFont();
      float h = AnubisWidgets.s(44.0F);
      float inner = AnubisWidgets.s(4.0F);
      float padX = AnubisWidgets.s(20.0F);
      float[] widths = new float[tabs.length];
      float total = inner * 2.0F;

      for (int i = 0; i < tabs.length; i++) {
         widths[i] = AnubisWidgets.textWidth(font, tabs[i].label) + padX * 2.0F;
         total += widths[i];
      }

      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float x = (sw - total) * 0.5F;
      float y = sh - h - AnubisWidgets.s(26.0F) + AnubisWidgets.s(16.0F) * (1.0F - open);
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(total, h, 1);
      ImGui.pushStyleVar(0, Math.max(0.01F, open));
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, h * 0.5F);
      ImGui.pushStyleColor(2, GuiPalette.tabBarBg());
      if (ImGui.begin("##tabbar", 319)) {
         this.tabBarRect = new float[]{x, y, total, h};
         if (this.tab == ClickGuiRenderer.Tab.THEME) {
            this.outlineSurface(ThemeStore.get().tabBar, x, y, total, h, h * 0.5F);
         }

         drawShadow(x, y, total, h, h * 0.5F, 4);
         ImDrawList dl = ImGui.getWindowDrawList();
         SurfaceBackground.draw(dl, ThemeStore.get().tabBar, x, y, total, h, h * 0.5F);
         dl.addRect(x, y, x + total, y + h, AnubisWidgets.c(318767103), h * 0.5F, 0, 1.0F);
         float targetX = inner;

         for (int i = 0; i < this.tab.ordinal(); i++) {
            targetX += widths[i];
         }

         float targetW = widths[this.tab.ordinal()];
         if (this.tabIndicatorX < 0.0F) {
            this.tabIndicatorX = targetX;
            this.tabIndicatorW = targetW;
         }

         this.tabIndicatorX = smooth(this.tabIndicatorX, targetX, dt, 18.0F);
         this.tabIndicatorW = smooth(this.tabIndicatorW, targetW, dt, 18.0F);
         float pillH = h - inner * 2.0F;
         dl.addRectFilled(
            x + this.tabIndicatorX,
            y + inner,
            x + this.tabIndicatorX + this.tabIndicatorW,
            y + inner + pillH,
            AnubisWidgets.c(GuiPalette.accent()),
            pillH * 0.5F
         );
         float tx = inner;

         for (int i = 0; i < tabs.length; i++) {
            ImGui.setCursorPos(tx, inner);
            if (ImGui.invisibleButton("##tab_" + tabs[i].name(), widths[i], pillH)) {
               this.switchTab(tabs[i]);
            }

            float hover = AnubisWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
            float centre = x + tx + widths[i] * 0.5F;
            float cover = Math.max(0.0F, 1.0F - Math.abs(centre - (x + this.tabIndicatorX + this.tabIndicatorW * 0.5F)) / Math.max(1.0F, widths[i] * 0.5F));
            int color = AnubisTheme.lerpColor(AnubisTheme.lerpColor(-4542808, -461582, hover), -1, cover);
            float tw = AnubisWidgets.textWidth(font, tabs[i].label);
            AnubisWidgets.text(
               dl, font, x + tx + (widths[i] - tw) * 0.5F, y + (h - AnubisWidgets.lineHeight(font)) * 0.5F, color, tabs[i].label, this.tabBarGuard
            );
            tx += widths[i];
         }
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(3);
   }

   private float[] beginCard(String id, float alpha, float view) {
      return this.beginCard(id, alpha, view, ImGui.getIO().getDisplaySizeX() * 0.5F);
   }

   private float[] beginCard(String id, float alpha, float view, float centreX) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float reserved = AnubisWidgets.s(86.0F);
      float w = Math.min(sw - AnubisWidgets.s(32.0F), AnubisWidgets.s(460.0F));
      float h = Math.min(sh - reserved - AnubisWidgets.s(32.0F), AnubisWidgets.s(760.0F));
      float x = Math.max(AnubisWidgets.s(16.0F), centreX - w * 0.5F);
      float y = Math.max(AnubisWidgets.s(16.0F), (sh - reserved - h) * 0.5F + AnubisWidgets.s(8.0F)) + AnubisWidgets.s(24.0F) * (1.0F - view);
      ImGui.setNextWindowPos(x, y, 1);
      ImGui.setNextWindowSize(w, h, 1);
      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.cardBg());
      if (this.cardTextLift) {
         ImGui.pushStyleColor(0, -1);
         ImGui.pushStyleColor(1, -2042416);
      }

      ImGui.begin(id, 8511);
      drawShadow(x, y, w, h, cardRounding(), 6);
      ImDrawList dl = ImGui.getWindowDrawList();
      Surface cards = ThemeStore.get().cards;
      SurfaceBackground.draw(dl, cards, x, y, w, h, cardRounding());
      dl.addRect(x, y, x + w, y + h, AnubisWidgets.c(318767103), cardRounding(), 0, 1.0F);
      float top = SurfaceBackground.hasImageBanner(cards)
         ? Math.max(AnubisWidgets.s(22.0F), SurfaceBackground.bannerHeight(cards) - AnubisWidgets.s(44.0F))
         : AnubisWidgets.s(22.0F);
      return new float[]{w, h, top, x, y};
   }

   private static float panelRounding() {
      return AnubisWidgets.s(GuiPalette.rounding());
   }

   public static float cardRounding() {
      return AnubisWidgets.s(Math.min(28.0F, GuiPalette.rounding() * 2.2F));
   }

   private void endCard() {
      ImGui.end();
      ImGui.popStyleColor(this.cardTextLift ? 3 : 1);
      ImGui.popStyleVar(4);
   }

   public static void drawCardTitle(String title, float x, float centreY) {
      drawCardTitle(title, x, centreY, false);
   }

   private static void drawCardTitle(String title, float x, float centreY, boolean guard) {
      ImFont font = ImGuiManager.getInstance().titleFont();
      AnubisWidgets.text(
         ImGui.getWindowDrawList(),
         font,
         ImGui.getWindowPosX() + x,
         ImGui.getWindowPosY() + centreY - AnubisWidgets.lineHeight(font) * 0.5F,
         -461582,
         title,
         guard
      );
   }

   public static float mutedParagraph(String text, float x, float y, float wrapX) {
      ImGui.setCursorPos(x, y);
      ImGui.pushTextWrapPos(wrapX);
      ImGui.pushStyleColor(0, ImGui.getStyleColorVec4(1));
      ImGui.textUnformatted(text);
      ImGui.popStyleColor();
      ImGui.popTextWrapPos();
      return ImGui.getCursorPosY();
   }

   public static void cardDivider(float pad, float w, float localY) {
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      ImGui.getWindowDrawList().addLine(wx + pad, wy + localY, wx + w - pad, wy + localY, AnubisWidgets.c(268435455), 1.0F);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view) {
      this.renderModuleCard(module, dt, alpha, view, false);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view, boolean preview) {
      this.renderModuleCard(module, dt, alpha, view, preview, !preview);
   }

   private void renderModuleCard(ClientModule module, float dt, float alpha, float view, boolean preview, boolean showBack) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float[] size;
      if (preview) {
         size = this.beginCard("##preview_card", alpha, view, (sw - AnubisWidgets.s(408.0F)) * 0.5F);
         this.cardRect = new float[]{size[3], size[4], size[0], size[1]};
         this.outlineSurface(ThemeStore.get().cards, size[3], size[4], size[0], size[1], cardRounding());
         ImGui.pushStyleVar(1, 1.0F);
         ImGui.beginDisabled();
      } else {
         ImGui.getBackgroundDrawList().addRectFilled(0.0F, 0.0F, sw, sh, AnubisWidgets.withAlpha(1426063360, view));
         size = this.beginCard("##module_card", alpha, view);
      }

      float w = size[0];
      float h = size[1];
      float pad = AnubisWidgets.s(24.0F);
      float top = size[2];
      float btn = AnubisWidgets.s(32.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY();
      float titleX = pad;
      if (!preview && showBack) {
         ImGui.setCursorPos(pad - AnubisWidgets.s(4.0F), top);
         if (AnubisWidgets.iconButton("##back", btn)) {
            this.closeSettings();
         }

         AnubisWidgets.chevronLeft(
            dl, wx + pad - AnubisWidgets.s(4.0F) + btn * 0.5F - AnubisWidgets.s(1.0F), wy + top + btn * 0.5F, AnubisWidgets.s(5.0F), -461582
         );
         titleX = pad + btn + AnubisWidgets.s(8.0F);
      }

      drawCardTitle(module.name(), titleX, top + btn * 0.5F, this.cardGuard);
      if (module.isAction()) {
         float openW = AnubisWidgets.s(72.0F);
         float openH = AnubisWidgets.s(30.0F);
         ImGui.setCursorPos(w - pad - openW, top + (btn - openH) * 0.5F);
         if (AnubisWidgets.chipButton("##open", "Open", openW, openH, true)) {
            module.onClick();
         }
      } else {
         float swW = AnubisWidgets.s(46.0F);
         float swH = AnubisWidgets.s(26.0F);
         ImGui.setCursorPos(w - pad - swW, top + (btn - swH) * 0.5F);
         float swX = ImGui.getCursorScreenPosX();
         float swY = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##enabled", swW, swH)) {
            module.toggle();
         }

         AnubisWidgets.drawSwitch(dl, swX, swY, swW, swH, AnubisWidgets.animate(ImGui.getItemID(), module.isEnabled(), 14.0F));
      }

      float y = top + btn + AnubisWidgets.s(12.0F);
      String description = module.description();
      if (description != null && !description.isBlank()) {
         y = mutedParagraph(description, pad, y, w - pad) + AnubisWidgets.s(4.0F);
      }

      y += AnubisWidgets.s(12.0F);
      cardDivider(pad, w, y);
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##body", w, Math.max(1.0F, h - y - 1.0F - AnubisWidgets.s(10.0F)), false, 24)) {
         float contentW = w - pad * 2.0F;
         float labelY = ImGui.getCursorPosY() + AnubisWidgets.s(16.0F);
         this.sectionLabel("Settings", pad);
         if (!preview && module.hasModifiedSettings()) {
            this.renderResetAllChip(module, pad, contentW, labelY);
         }

         boolean any = false;

         for (Setting setting : module.settings()) {
            if (setting.isVisible() && (!module.isAction() || setting != module.showInHud())) {
               any = true;
               ImGui.pushID(setting.id());
               this.renderSetting(module, setting, pad, contentW);
               ImGui.popID();
            }
         }

         if (!any) {
            mutedParagraph("This module has no settings.", pad, ImGui.getCursorPosY() + AnubisWidgets.s(8.0F), w - pad);
         }

         this.sectionLabel("Keybind", pad);
         ImGui.pushID("##keybind_row");
         this.renderKeybindRow(module, pad, contentW);
         ImGui.popID();
         if (isHudModule(module)) {
            this.renderHudActions(pad, contentW);
         }

         if (module.inConfigs()) {
            this.sectionLabel("Config", pad);
            this.renderModuleConfigActions(module, pad, contentW);
         }

         ImGui.dummy(1.0F, AnubisWidgets.s(18.0F));
         this.applySmoothScroll("card_" + module.id(), dt);
      }

      ImGui.endChild();
      if (preview) {
         ImGui.endDisabled();
         ImGui.popStyleVar();
      }

      this.endCard();
   }

   private void renderResetAllChip(ClientModule module, float pad, float contentW, float labelY) {
      float afterY = ImGui.getCursorPosY();
      String key = "reset:" + module.id();
      String label = this.isConfirming(key) ? "Confirm reset" : "Reset all";
      ImFont small = ImGuiManager.getInstance().smallFont();
      boolean pushed = pushFont(small);
      float chipW = AnubisWidgets.textWidth(small, label) + AnubisWidgets.s(18.0F);
      float chipH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(8.0F);
      ImGui.setCursorPos(pad + contentW - chipW, labelY + (ImGui.getTextLineHeight() - chipH) * 0.5F);
      if (AnubisWidgets.chipButton("##reset_all", label, chipW, chipH, this.isConfirming(key)) && this.confirmClick(key)) {
         module.resetSettings();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Restore every setting to its default");
      }

      if (pushed) {
         ImGui.popFont();
      }

      ImGui.setCursorPosY(afterY);
   }

   private boolean confirmClick(String key) {
      if (this.isConfirming(key)) {
         this.pendingConfirm = null;
         return true;
      } else {
         this.pendingConfirm = key;
         this.pendingConfirmAt = System.nanoTime();
         return false;
      }
   }

   private boolean isConfirming(String key) {
      return key.equals(this.pendingConfirm) && System.nanoTime() - this.pendingConfirmAt < 3000000000L;
   }

   private void sectionLabel(String text, float pad) {
      ImGui.setCursorPos(pad, ImGui.getCursorPosY() + AnubisWidgets.s(16.0F));
      boolean pushed = pushFont(ImGuiManager.getInstance().smallFont());
      ImGui.pushStyleColor(0, ImGui.getStyleColorVec4(1));
      ImGui.textUnformatted(text);
      ImGui.popStyleColor();
      if (pushed) {
         ImGui.popFont();
      }

      ImGui.setCursorPosY(ImGui.getCursorPosY() + AnubisWidgets.s(4.0F));
      this.dividerPending = false;
   }

   private float rowStart(float pad, float contentW) {
      float localY = ImGui.getCursorPosY();
      if (this.dividerPending) {
         float wx = ImGui.getWindowPosX();
         float sy = ImGui.getCursorScreenPosY();
         ImGui.getWindowDrawList().addLine(wx + pad, sy, wx + pad + contentW, sy, AnubisWidgets.c(268435455), 1.0F);
      }

      this.dividerPending = true;
      return localY;
   }

   public static void rowLabel(String label, float pad, float startY, float rowH) {
      ImGui.setCursorPos(pad, startY + (rowH - ImGui.getTextLineHeight()) * 0.5F);
      ImGui.textUnformatted(label);
   }

   private void openSettings(ClientModule module) {
      this.settingsModule = module;
      this.viewChangedAt = System.nanoTime();
      this.cardJustOpened = true;
   }

   private void closeSettings() {
      if (this.settingsModule != null) {
         this.viewChangedAt = System.nanoTime();
      }

      this.settingsModule = null;
      this.listeningModule = null;
      this.listeningKeySetting = null;
      this.activeColorModule = null;
      this.activeColorSetting = null;
      this.colorPickerOpen.set(false);
      this.blockLists.close();
      this.itemPickers.close();
      this.itemColorWindowOpen = false;
   }

   private void closeCardOnOutsideClick() {
      if (this.cardJustOpened) {
         this.cardJustOpened = false;
      } else if (this.settingsModule != null && this.listeningModule == null && this.listeningKeySetting == null) {
         if (ImGui.isMouseClicked(0) && !ImGui.isWindowHovered(4) && !ImGui.isAnyItemActive()) {
            this.closeSettings();
         }
      }
   }

   private void renderFriendsCard(float dt, float alpha, float view) {
      float[] size = this.beginCard("##friends_card", alpha, view);
      float w = size[0];
      float h = size[1];
      float pad = AnubisWidgets.s(24.0F);
      float top = size[2];
      float btn = AnubisWidgets.s(32.0F);
      FriendStore store = FriendStore.getInstance();
      List<String> friends = store.all();
      drawCardTitle("Friends", pad, top + btn * 0.5F, this.cardGuard);
      String count = friends.size() == 1 ? "1 player" : friends.size() + " players";
      ImFont small = ImGuiManager.getInstance().smallFont();
      AnubisWidgets.text(
         ImGui.getWindowDrawList(),
         small,
         ImGui.getWindowPosX() + w - pad - AnubisWidgets.textWidth(small, count),
         ImGui.getWindowPosY() + top + (btn - AnubisWidgets.lineHeight(small)) * 0.5F,
         -4542808,
         count,
         this.cardGuard
      );
      float y = mutedParagraph("Add players by their exact username.", pad, top + btn + AnubisWidgets.s(12.0F), w - pad) + AnubisWidgets.s(14.0F);
      float addW = AnubisWidgets.s(84.0F);
      float inputH = ImGui.getTextLineHeight() + AnubisWidgets.s(16.0F);
      ImGui.setCursorPos(pad, y);
      ImGui.pushStyleVar(11, AnubisWidgets.s(12.0F), AnubisWidgets.s(8.0F));
      ImGui.setNextItemWidth(w - pad * 2.0F - addW - AnubisWidgets.s(8.0F));
      if (this.focusFriendInput) {
         ImGui.setKeyboardFocusHere();
         this.focusFriendInput = false;
      }

      ImGui.inputTextWithHint("##friend_input", "Username", this.friendInput, 16);
      boolean submitted = AnubisWidgets.submittedWithEnter();
      ImGui.popStyleVar();
      ImGui.setCursorPos(w - pad - addW, y);
      if (AnubisWidgets.chipButton("##friend_add", "Add", addW, inputH, true)) {
         submitted = true;
      }

      if (submitted) {
         this.tryAddFriend(store);
      }

      y += inputH + AnubisWidgets.s(8.0F);
      if (this.friendMessage != null && elapsed(this.friendMessageAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, -10856974);
         ImGui.textUnformatted(this.friendMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      y += AnubisWidgets.lineHeight(small) + AnubisWidgets.s(10.0F);
      cardDivider(pad, w, y);
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##friend_list", w, Math.max(1.0F, h - y - 1.0F - AnubisWidgets.s(10.0F)), false, 24)) {
         if (friends.isEmpty()) {
            String empty = "No friends yet";
            ImGui.setCursorPos((w - ImGui.calcTextSize(empty).x) * 0.5F, AnubisWidgets.s(40.0F));
            ImGui.textDisabled(empty);
         } else {
            ImGui.dummy(1.0F, AnubisWidgets.s(8.0F));

            for (String name : friends) {
               this.renderFriendRow(store, name, pad, w - pad * 2.0F);
            }
         }

         ImGui.dummy(1.0F, AnubisWidgets.s(12.0F));
         this.applySmoothScroll("friends", dt);
      }

      ImGui.endChild();
      this.endCard();
   }

   private void renderFriendRow(FriendStore store, String name, float pad, float contentW) {
      ImGui.pushID("friend_" + name);
      float rowH = AnubisWidgets.s(48.0F);
      float localY = ImGui.getCursorPosY();
      ImGui.setCursorPos(pad, localY);
      float x0 = ImGui.getCursorScreenPosX();
      float y0 = ImGui.getCursorScreenPosY();
      ImGui.setNextItemAllowOverlap();
      ImGui.invisibleButton("##row", contentW, rowH);
      boolean rowHovered = ImGui.isMouseHoveringRect(x0, y0, x0 + contentW, y0 + rowH);
      float hover = AnubisWidgets.animate(ImGui.getItemID(), rowHovered, 18.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      if (hover > 0.01F) {
         dl.addRectFilled(
            x0 - AnubisWidgets.s(8.0F),
            y0,
            x0 + contentW + AnubisWidgets.s(8.0F),
            y0 + rowH,
            AnubisWidgets.c(AnubisWidgets.withAlpha(218103807, hover)),
            AnubisWidgets.s(10.0F)
         );
      }

      float avatarR = AnubisWidgets.s(15.0F);
      float acx = x0 + avatarR;
      float acy = y0 + rowH * 0.5F;
      int avatarColor = AVATAR_COLORS[Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), AVATAR_COLORS.length)];
      dl.addCircleFilled(acx, acy, avatarR, AnubisWidgets.c(avatarColor), MobilePerformance.uiSegments(32, avatarR));
      ImFont category = ImGuiManager.getInstance().categoryFont();
      String initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
      AnubisWidgets.text(dl, category, acx - AnubisWidgets.textWidth(category, initial) * 0.5F, acy - AnubisWidgets.lineHeight(category) * 0.5F, -1, initial);
      ImFont font = ImGui.getFont();
      AnubisWidgets.text(dl, font, x0 + avatarR * 2.0F + AnubisWidgets.s(12.0F), y0 + (rowH - AnubisWidgets.lineHeight(font)) * 0.5F, -461582, name);
      float removeSize = AnubisWidgets.s(28.0F);
      ImGui.setCursorPos(pad + contentW - removeSize, localY + (rowH - removeSize) * 0.5F);
      float rx = ImGui.getCursorScreenPosX();
      float ry = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##remove", removeSize, removeSize)) {
         store.remove(name);
      }

      float removeHover = AnubisWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
      float rcx = rx + removeSize * 0.5F;
      float rcy = ry + removeSize * 0.5F;
      if (removeHover > 0.01F) {
         dl.addCircleFilled(
            rcx,
            rcy,
            removeSize * 0.5F,
            AnubisWidgets.c(AnubisWidgets.withAlpha(-10856974, 0.18F * removeHover)),
            MobilePerformance.uiSegments(32, removeSize * 0.5F)
         );
      }

      AnubisWidgets.cross(
         dl, rcx, rcy, AnubisWidgets.s(4.5F), AnubisTheme.lerpColor(AnubisWidgets.withAlpha(-4542808, 0.25F + 0.75F * hover), -10856974, removeHover)
      );
      ImGui.setCursorPos(0.0F, localY + rowH + AnubisWidgets.s(4.0F));
      ImGui.popID();
   }

   private void tryAddFriend(FriendStore store) {
      String name = this.friendInput.get().trim();
      if (!FriendStore.isValidName(name)) {
         this.friendMessage = "Use 1-16 letters, digits or _ (Bedrock names start with .)";
      } else if (store.isFriend(name)) {
         this.friendMessage = name + " is already on your list";
      } else {
         store.add(name);
         this.friendInput.set("");
         this.friendMessage = null;
      }

      this.friendMessageAt = System.nanoTime();
      this.focusFriendInput = true;
   }

   private void renderConfigCard(float dt, float alpha, float view) {
      float[] size = this.beginCard("##config_card", alpha, view);
      float w = size[0];
      float h = size[1];
      float pad = AnubisWidgets.s(24.0F);
      float top = size[2];
      float btn = AnubisWidgets.s(32.0F);
      float contentW = w - pad * 2.0F;
      ModuleConfigStore store = ModuleConfigStore.getInstance();
      ConfigProfiles profiles = ConfigProfiles.getInstance();
      ImFont small = ImGuiManager.getInstance().smallFont();
      drawCardTitle("Config", pad, top + btn * 0.5F, this.cardGuard);
      String status = store.isReadOnly() ? "Read-only" : (store.hasUnsavedChanges() ? "Saving..." : "Saved");
      int statusColor = store.isReadOnly() ? -10856974 : -4542808;
      AnubisWidgets.text(
         ImGui.getWindowDrawList(),
         small,
         ImGui.getWindowPosX() + w - pad - AnubisWidgets.textWidth(small, status),
         ImGui.getWindowPosY() + top + (btn - AnubisWidgets.lineHeight(small)) * 0.5F,
         statusColor,
         status,
         this.cardGuard
      );
      if (store.isReadOnly()
         && ImGui.isMouseHoveringRect(
            ImGui.getWindowPosX() + w - pad - AnubisWidgets.s(80.0F),
            ImGui.getWindowPosY() + top,
            ImGui.getWindowPosX() + w - pad,
            ImGui.getWindowPosY() + top + btn
         )) {
         showTooltip("modules.json could not be read. Fix or delete it.");
      }

      float y = mutedParagraph("Changes save automatically. Profiles can be shared with a code.", pad, top + btn + AnubisWidgets.s(12.0F), w - pad)
         + AnubisWidgets.s(14.0F);
      float gap = AnubisWidgets.s(8.0F);
      float chipH = ImGui.getTextLineHeight() + AnubisWidgets.s(16.0F);
      float chipW = (contentW - gap * 2.0F) / 3.0F;
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##cfg_save", "Save all", chipW, chipH, true)) {
         this.saveAllConfigs(store);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Save everything now");
      }

      ImGui.setCursorPos(pad + chipW + gap, y);
      if (AnubisWidgets.chipButton("##cfg_folder", "Open folder", chipW, chipH, false)) {
         this.openProfilesFolder(profiles);
      }

      ImGui.setCursorPos(pad + (chipW + gap) * 2.0F, y);
      boolean resetArmed = this.isConfirming("reset-all-modules");
      if (AnubisWidgets.chipButton("##cfg_reset", resetArmed ? "Confirm" : "Reset all", chipW, chipH, resetArmed) && this.confirmClick("reset-all-modules")) {
         for (ClientModule module : ModuleRegistry.all()) {
            module.resetSettings();
         }

         this.setConfigMessage("Every module setting is back to default", false);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Reset all module settings. Keybinds stay.");
      }

      y += chipH + AnubisWidgets.s(18.0F);
      cardDivider(pad, w, y);
      y += AnubisWidgets.s(14.0F);
      float createW = AnubisWidgets.s(84.0F);
      ImGui.setCursorPos(pad, y);
      ImGui.pushStyleVar(11, AnubisWidgets.s(12.0F), AnubisWidgets.s(8.0F));
      ImGui.setNextItemWidth(contentW - createW - gap);
      if (this.focusProfileInput) {
         ImGui.setKeyboardFocusHere();
         this.focusProfileInput = false;
      }

      ImGui.inputTextWithHint("##profile_input", "New profile name", this.profileInput);
      boolean submitted = AnubisWidgets.submittedWithEnter();
      ImGui.popStyleVar();
      ImGui.setCursorPos(w - pad - createW, y);
      if (AnubisWidgets.chipButton("##profile_create", "Create", createW, chipH, true)) {
         submitted = true;
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Save your current modules as a new profile");
      }

      if (submitted) {
         this.tryCreateProfile(profiles);
      }

      y += chipH + AnubisWidgets.s(8.0F);
      float halfW = (contentW - gap) * 0.5F;
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##profile_import", "Import file", halfW, chipH, false)) {
         this.importProfileFile();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Import a profile .json file. You can also drop it here.");
      }

      ImGui.setCursorPos(pad + halfW + gap, y);
      if (AnubisWidgets.chipButton("##profile_paste", "Paste code", halfW, chipH, false)) {
         this.pasteProfileCode();
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Load a share code from your clipboard. Your current settings go to Auto backup.");
      }

      y += chipH + AnubisWidgets.s(8.0F);
      if (this.configMessage != null && elapsed(this.configMessageAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, this.configMessageError ? -10856974 : -4542808);
         ImGui.textUnformatted(this.configMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      y += AnubisWidgets.lineHeight(small) + AnubisWidgets.s(10.0F);
      cardDivider(pad, w, y);
      List<ConfigProfiles.Profile> list = profiles.list();
      ImGui.setCursorPos(0.0F, y + 1.0F);
      if (ImGui.beginChild("##profile_list", w, Math.max(1.0F, h - y - 1.0F - AnubisWidgets.s(10.0F)), false, 24)) {
         if (list.isEmpty()) {
            String empty = "No profiles yet";
            ImGui.setCursorPos((w - ImGui.calcTextSize(empty).x) * 0.5F, AnubisWidgets.s(40.0F));
            ImGui.textDisabled(empty);
         } else {
            ImGui.dummy(1.0F, AnubisWidgets.s(8.0F));
            this.profileList.render(profiles, list, store.activeProfile(), pad, contentW);
         }

         ImGui.dummy(1.0F, AnubisWidgets.s(12.0F));
         float target = this.profileList.takeScrollTarget();
         if (!Float.isNaN(target)) {
            this.scroll.computeIfAbsent("profiles", k -> new float[2])[1] = target;
         }

         this.applySmoothScroll("profiles", dt);
      }

      ImGui.endChild();
      this.endCard();
   }

   private void tryCreateProfile(ConfigProfiles profiles) {
      try {
         String name = profiles.create(this.profileInput.get());
         this.profileInput.set("");
         this.profileList.open(name);
         this.setConfigMessage("Created " + name + " from the current modules", false);
      } catch (ProfileException e) {
         this.setConfigMessage(e.getMessage(), true);
      }

      this.focusProfileInput = true;
   }

   private void importProfileFile() {
      if (FileDialogs.busy()) {
         this.setConfigMessage("A file window is already open", true);
      } else {
         FileDialogs.openProfile("Import an Anubis profile", this::importProfile);
      }
   }

   private void importProfile(Path path) {
      try {
         String name = ConfigProfiles.getInstance().importFile(path);
         this.profileList.open(name);
         this.setConfigMessage("Imported " + name + " - press Load to use it", false);
      } catch (ProfileException e) {
         this.setConfigMessage(e.getMessage(), true);
      }
   }

   private void pasteProfileCode() {
      ConfigProfiles profiles = ConfigProfiles.getInstance();

      try {
         String name = profiles.importCode(MinecraftClient.getInstance().keyboard.getClipboard());
         ConfigProfiles.Profile imported = profiles.find(name);
         profiles.backup(imported == null ? Set.of(ConfigProfiles.Part.MODULES) : imported.parts());
         profiles.load(name);
         this.profileList.open(name);
         this.setConfigMessage("Loaded " + name + " - your previous settings are in Auto backup", false);
      } catch (ProfileException e) {
         this.setConfigMessage(e.getMessage(), true);
      }
   }

   private void saveAllConfigs(ModuleConfigStore store) {
      store.save();
      ThemeStore.get().save();
      HudTheme.get().save();
      HudLayoutStore.getInstance().save();
      FriendStore.getInstance().save();
      if (store.isReadOnly()) {
         this.setConfigMessage("modules.json is unreadable, so it was left untouched", true);
      } else {
         this.setConfigMessage("Everything saved", false);
      }
   }

   private void openProfilesFolder(ConfigProfiles profiles) {
      try {
         Path dir = profiles.directory();
         Files.createDirectories(dir);
         Util.getOperatingSystem().open(dir);
      } catch (IOException | RuntimeException e) {
         this.setConfigMessage("Could not open the profiles folder", true);
      }
   }

   private void setConfigMessage(String message, boolean error) {
      this.configMessage = message;
      this.configMessageError = error;
      this.configMessageAt = System.nanoTime();
   }

   private void renderThemeStudio(float dt, float alpha, float view) {
      ThemeStore theme = ThemeStore.get();
      float preview = easeOut(elapsed(this.previewChangedAt, 260.0F));
      this.panelRects.clear();
      this.cardRect = null;
      this.inspectMode = true;
      if (this.themeSurface == theme.cards) {
         ClientModule sample = previewModule();
         if (sample != null) {
            this.renderModuleCard(sample, dt, alpha * preview, Math.min(view, preview), true);
         }
      } else {
         this.renderModulePanels(dt, alpha * preview);
      }

      this.inspectMode = false;
      this.renderInspector(theme, dt, alpha);
      this.highlightSurfaces(theme);
   }

   private void renderInspector(ThemeStore theme, float dt, float alpha) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = AnubisWidgets.s(360.0F);
      float h = Math.min(sh - AnubisWidgets.s(86.0F) - AnubisWidgets.s(40.0F), AnubisWidgets.s(760.0F));
      ImGui.setNextWindowPos(sw - w - AnubisWidgets.s(24.0F), AnubisWidgets.s(24.0F), 4);
      ImGui.setNextWindowSize(w, h, 1);
      if (this.focusInspector) {
         ImGui.setNextWindowFocus();
         this.focusInspector = false;
      }

      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.abgr(theme.cards.color.argbWithAlpha(255)));
      int flags = 59;
      if (ImGui.begin("##theme_inspector", flags)) {
         float x = ImGui.getWindowPosX();
         float y = ImGui.getWindowPosY();
         this.inspectorRect = new float[]{x, y, w, h};
         drawShadow(x, y, w, h, cardRounding(), 6);
         ImGui.getWindowDrawList().addRect(x, y, x + w, y + h, AnubisWidgets.c(318767103), cardRounding(), 0, 1.0F);
         float pad = AnubisWidgets.s(20.0F);
         float top = AnubisWidgets.s(20.0F);
         drawCardTitle("Theme", pad, top + AnubisWidgets.s(14.0F));
         float ty = top + AnubisWidgets.s(34.0F);
         if (this.themeMessage != null && System.nanoTime() - this.themeMessageAt < 3000000000L) {
            ImGui.setCursorPos(pad, ty);
            ImGui.pushTextWrapPos(w - pad);
            ImGui.pushStyleColor(0, this.themeMessageError ? -10856974 : -461582);
            ImGui.textUnformatted(this.themeMessage);
            ImGui.popStyleColor();
            ImGui.popTextWrapPos();
            ty = ImGui.getCursorPosY();
         } else {
            ty = mutedParagraph("Click a panel or card on screen to edit it.", pad, ty, w - pad);
         }

         ty = this.renderSurfaceTabs(theme, pad, ty + AnubisWidgets.s(12.0F), w - pad * 2.0F) + AnubisWidgets.s(14.0F);
         cardDivider(pad, w, ty);
         ImGui.setCursorPos(0.0F, ty + 1.0F);
         if (ImGui.beginChild("##inspector_body", w, Math.max(1.0F, h - ty - 1.0F - AnubisWidgets.s(10.0F)), false, 24)) {
            float contentW = w - pad * 2.0F;
            if (this.themeSurface == null) {
               this.renderGlobalOptions(theme, pad, contentW);
            } else {
               this.renderSurfaceOptions(this.themeSurface, pad, contentW);
            }

            ImGui.dummy(1.0F, AnubisWidgets.s(18.0F));
            this.applySmoothScroll("inspector", dt);
         }

         ImGui.endChild();
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(4);
   }

   private float renderSurfaceTabs(ThemeStore theme, float pad, float y, float contentW) {
      String[] labels = new String[]{"Global", "Panels", "Cards", "Tab bar"};
      Surface[] targets = new Surface[]{null, theme.panels, theme.cards, theme.tabBar};
      int selected = 0;

      for (int i = 1; i < targets.length; i++) {
         if (targets[i] == this.themeSurface) {
            selected = i;
         }
      }

      float h = AnubisWidgets.s(32.0F);
      ImGui.setCursorPos(pad, y);
      int clicked = AnubisWidgets.segmented("##surface", labels, selected, contentW, h);
      if (clicked >= 0) {
         this.selectSurface(targets[clicked]);
      }

      return y + h;
   }

   private void renderGlobalOptions(ThemeStore theme, float pad, float contentW) {
      this.sectionLabel("Presets", pad);
      this.renderPresetRow(theme, pad, contentW);
      this.sectionLabel("Colors", pad);
      this.settingRows(pad, contentW, theme.accent);
      this.sectionLabel("Layout", pad);
      this.settingRows(pad, contentW, theme.rounding, theme.moduleDividers, theme.backgroundDim, theme.menuBlur);
      this.sectionLabel("Interface", pad);
      this.settingRows(pad, contentW, theme.uiScale, theme.font, theme.animationSpeed, theme.animateLines, theme.customMainMenu, theme.communityPopup);
      this.sectionLabel("Share", pad);
      this.renderThemeActions(theme, pad, contentW);
   }

   private void renderSurfaceOptions(Surface surface, float pad, float contentW) {
      this.sectionLabel("Fill", pad);
      this.settingRows(pad, contentW, surface.color, surface.opacity);
      this.sectionLabel("Background", pad);
      this.settingRows(pad, contentW, surface.background);
      if (surface.isImage()) {
         this.renderImageGallery(surface, pad, contentW);
      }

      this.settingRows(
         pad,
         contentW,
         surface.mapping,
         surface.coverage,
         surface.headerHeight,
         surface.imageOpacity,
         surface.imageScale,
         surface.imageX,
         surface.imageY,
         surface.lineOpacity
      );
      if (surface.isScreenMapped()) {
         mutedParagraph(
            "Screen mapping pins the background to the screen, so each surface is a window onto it. Drag a panel to see another part.",
            pad,
            ImGui.getCursorPosY() + AnubisWidgets.s(12.0F),
            pad + contentW
         );
      }
   }

   private void selectSurface(Surface surface) {
      if (surface != this.themeSurface) {
         ThemeStore theme = ThemeStore.get();
         if (surface == theme.cards != (this.themeSurface == theme.cards)) {
            this.previewChangedAt = System.nanoTime();
         }

         this.themeSurface = surface;
         this.focusInspector = true;
      }
   }

   private void highlightSurfaces(ThemeStore theme) {
      if (!AndroidSystemPicker.blocksEditor()) {
         float mx = ImGui.getIO().getMousePosX();
         float my = ImGui.getIO().getMousePosY();
         boolean blocked = contains(this.inspectorRect, mx, my) || ImGui.isPopupOpen("", 3072);
         Surface hovered = null;
         if (!blocked) {
            for (float[] rect : this.panelRects) {
               if (contains(rect, mx, my)) {
                  hovered = theme.panels;
               }
            }

            if (contains(this.cardRect, mx, my)) {
               hovered = theme.cards;
            }

            if (contains(this.tabBarRect, mx, my)) {
               hovered = theme.tabBar;
            }
         }

         this.hoveredSurface = hovered;
         if (hovered != null) {
            ImDrawList fg = ImGui.getForegroundDrawList();
            boolean tabBar = hovered == theme.tabBar;
            String hint = hovered.label + (tabBar ? "  ·  right-click to edit" : "  ·  click to edit");
            ImFont small = ImGuiManager.getInstance().smallFont();
            float chipW = AnubisWidgets.textWidth(small, hint) + AnubisWidgets.s(18.0F);
            float chipH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(10.0F);
            float cx = Math.min(mx + AnubisWidgets.s(14.0F), ImGui.getIO().getDisplaySizeX() - chipW - AnubisWidgets.s(4.0F));
            float cy = Math.min(my + AnubisWidgets.s(18.0F), ImGui.getIO().getDisplaySizeY() - chipH - AnubisWidgets.s(4.0F));
            fg.addRectFilled(cx, cy, cx + chipW, cy + chipH, GuiPalette.accent(), chipH * 0.5F);
            AnubisWidgets.text(fg, small, cx + AnubisWidgets.s(9.0F), cy + AnubisWidgets.s(5.0F), -1, hint);
            boolean clicked = tabBar ? ImGui.isMouseClicked(1) : ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1);
            if (clicked) {
               this.selectSurface(hovered);
            }
         }
      }
   }

   private void outlineSurface(Surface surface, float x, float y, float w, float h, float rounding) {
      int color;
      if (surface == this.themeSurface) {
         color = AnubisWidgets.withAlpha(GuiPalette.accent(), 0.8F);
      } else {
         if (surface != this.hoveredSurface) {
            return;
         }

         color = -2130706433;
      }

      float e = AnubisWidgets.s(3.0F);
      float reach = e + AnubisWidgets.s(2.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      dl.pushClipRect(x - reach, y - reach, x + w + reach, y + h + reach, false);
      dl.addRect(x - e, y - e, x + w + e, y + h + e, color, rounding + e, 0, AnubisWidgets.s(1.5F));
      dl.popClipRect();
   }

   private void recordPanelRect() {
      if (this.inspectMode) {
         float x = ImGui.getWindowPosX();
         float y = ImGui.getWindowPosY();
         float w = ImGui.getWindowWidth();
         float h = ImGui.getWindowHeight();
         this.panelRects.add(new float[]{x, y, w, h});
         this.outlineSurface(ThemeStore.get().panels, x, y, w, h, panelRounding());
      }
   }

   private static boolean contains(float[] rect, float x, float y) {
      return rect != null && x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3];
   }

   private static ClientModule previewModule() {
      ClientModule fallback = null;

      for (ClientModule module : ModuleRegistry.all()) {
         if (fallback == null) {
            fallback = module;
         }

         if (module.settings().size() >= 3) {
            return module;
         }
      }

      return fallback;
   }

   private void settingRows(float pad, float contentW, Setting... settings) {
      for (Setting setting : settings) {
         if (setting.isVisible()) {
            ImGui.pushID(setting.id());
            this.renderSetting(null, setting, pad, contentW);
            ImGui.popID();
         }
      }
   }

   private void renderPresetRow(ThemeStore theme, float pad, float contentW) {
      float rowH = AnubisWidgets.s(46.0F) + AnubisWidgets.s(6.0F);
      float startY = this.rowStart(pad, contentW);
      float size = AnubisWidgets.s(28.0F);
      float gap = AnubisWidgets.s(12.0F);
      ImDrawList dl = ImGui.getWindowDrawList();

      for (int i = 0; i < ThemeStore.PRESETS.size(); i++) {
         ThemeStore.Preset preset = ThemeStore.PRESETS.get(i);
         ImGui.setCursorPos(pad + i * (size + gap), startY + (rowH - size) * 0.5F);
         float cx = ImGui.getCursorScreenPosX() + size * 0.5F;
         float cy = ImGui.getCursorScreenPosY() + size * 0.5F;
         if (ImGui.invisibleButton("##preset" + i, size, size)) {
            theme.applyPreset(preset);
         }

         boolean hovered = ImGui.isItemHovered();
         float hover = AnubisWidgets.animate(ImGui.getItemID(), hovered, 18.0F);
         boolean selected = theme.matchesPreset(preset);
         dl.addCircleFilled(
            cx,
            cy,
            size * 0.5F - AnubisWidgets.s(3.0F),
            AnubisWidgets.c(GuiPalette.abgr(preset.accent())),
            MobilePerformance.uiSegments(32, size * 0.5F - AnubisWidgets.s(3.0F))
         );
         if (selected) {
            dl.addCircle(cx, cy, size * 0.5F, AnubisWidgets.c(-1), MobilePerformance.uiSegments(32, size * 0.5F), AnubisWidgets.s(1.5F));
         } else if (hover > 0.01F) {
            dl.addCircle(
               cx,
               cy,
               size * 0.5F,
               AnubisWidgets.c(AnubisWidgets.withAlpha(-1, 0.35F * hover)),
               MobilePerformance.uiSegments(32, size * 0.5F),
               AnubisWidgets.s(1.2F)
            );
         }

         if (hovered) {
            showTooltip(preset.name());
         }
      }

      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   private void renderImageGallery(Surface surface, float pad, float contentW) {
      List<String> files = ThemeImages.get().files();
      int columns = 3;
      float gap = AnubisWidgets.s(8.0F);
      float r = AnubisWidgets.s(8.0F);
      float tileW = (contentW - gap * (columns - 1)) / columns;
      float tileH = tileW * 0.6F;
      float startY = ImGui.getCursorPosY() + AnubisWidgets.s(4.0F);
      ImDrawList dl = ImGui.getWindowDrawList();
      ImFont small = ImGuiManager.getInstance().smallFont();
      String deleted = null;
      int count = files.size() + 1;

      for (int i = 0; i < count; i++) {
         ImGui.setCursorPos(pad + i % columns * (tileW + gap), startY + i / columns * (tileH + gap));
         float sx = ImGui.getCursorScreenPosX();
         float sy = ImGui.getCursorScreenPosY();
         String name = i == 0 ? null : files.get(i - 1);
         String deleteKey = name == null ? null : "image-delete:" + name;
         boolean armed = deleteKey != null && this.isConfirming(deleteKey);
         float del = AnubisWidgets.s(20.0F);
         float dx = sx + tileW - del - AnubisWidgets.s(5.0F);
         float dy = sy + AnubisWidgets.s(5.0F);
         boolean overTile = ImGui.isWindowHovered(128) && ImGui.isMouseHoveringRect(sx, sy, sx + tileW, sy + tileH);
         boolean deleteShown = deleteKey != null && (armed || overTile);
         boolean deleteHovered = false;
         float deleteHover = 0.0F;
         if (deleteShown) {
            ImGui.setCursorScreenPos(dx, dy);
            if (ImGui.invisibleButton("##tiledel" + i, del, del) && this.confirmClick(deleteKey)) {
               deleted = name;
            }

            deleteHovered = ImGui.isItemHovered();
            deleteHover = AnubisWidgets.animate(ImGui.getItemID(), deleteHovered, 18.0F);
            armed = this.isConfirming(deleteKey);
            if (deleteHovered) {
               showTooltip(armed ? "Click again to delete" : "Delete image");
            }

            ImGui.setCursorScreenPos(sx, sy);
         }

         boolean clicked = ImGui.invisibleButton("##tile" + i, tileW, tileH);
         if (!PerformanceModule.optimizationsEnabled() || ImGui.isItemVisible()) {
            boolean hovered = ImGui.isItemHovered() || deleteHovered;
            float hover = AnubisWidgets.animate(ImGui.getItemID(), hovered, 18.0F);
            if (i == 0) {
               dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, AnubisWidgets.c(AnubisTheme.lerpColor(-13226454, -12240588, hover)), r);
               int glyph = AnubisTheme.lerpColor(-4542808, -461582, hover);
               float cx = sx + tileW * 0.5F;
               float cy = sy + tileH * 0.5F - AnubisWidgets.s(8.0F);
               dl.addLine(cx - AnubisWidgets.s(7.0F), cy, cx + AnubisWidgets.s(7.0F), cy, AnubisWidgets.c(glyph), AnubisWidgets.s(1.8F));
               dl.addLine(cx, cy - AnubisWidgets.s(7.0F), cx, cy + AnubisWidgets.s(7.0F), AnubisWidgets.c(glyph), AnubisWidgets.s(1.8F));
               String label = "Add image";
               AnubisWidgets.text(dl, small, cx - AnubisWidgets.textWidth(small, label) * 0.5F, cy + AnubisWidgets.s(12.0F), glyph, label);
               if (clicked) {
                  ThemeImages.get().browseAsync();
               }
            } else {
               ThemeImages.Texture texture = ThemeImages.get().preview(name);
               boolean broken = texture == null && ThemeImages.get().isBroken(name);
               dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, AnubisWidgets.c(-13226454), r);
               if (texture != null) {
                  float[] uv = SurfaceBackground.coverUv(texture.width(), texture.height(), tileW, tileH, 0.5F);
                  dl.addImageRounded(texture.id(), sx, sy, sx + tileW, sy + tileH, uv[0], uv[1], uv[2], uv[3], AnubisWidgets.c(-1), r, 240);
               } else if (broken && !armed) {
                  float cx = sx + tileW * 0.5F;
                  float cy = sy + tileH * 0.5F - AnubisWidgets.s(8.0F);
                  AnubisWidgets.cross(dl, cx, cy, AnubisWidgets.s(5.0F), -4542808);
                  String label = "Can't load";
                  AnubisWidgets.text(dl, small, cx - AnubisWidgets.textWidth(small, label) * 0.5F, cy + AnubisWidgets.s(10.0F), -4542808, label);
               }

               if (ThemeImages.isGif(name)) {
                  String badge = "GIF";
                  float badgePad = AnubisWidgets.s(4.0F);
                  float badgeH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(2.0F);
                  float badgeW = AnubisWidgets.textWidth(small, badge) + badgePad * 2.0F;
                  float bx = sx + AnubisWidgets.s(5.0F);
                  float by = sy + tileH - badgeH - AnubisWidgets.s(5.0F);
                  dl.addRectFilled(bx, by, bx + badgeW, by + badgeH, AnubisWidgets.c(AnubisWidgets.withAlpha(-16777216, 0.6F)), AnubisWidgets.s(4.0F));
                  AnubisWidgets.text(dl, small, bx + badgePad, by + AnubisWidgets.s(1.0F), -1, badge);
               }

               if (armed) {
                  String ask = "Delete?";
                  dl.addRectFilled(sx, sy, sx + tileW, sy + tileH, AnubisWidgets.c(AnubisWidgets.withAlpha(-16777216, 0.6F)), r);
                  AnubisWidgets.text(
                     dl, small, sx + (tileW - AnubisWidgets.textWidth(small, ask)) * 0.5F, sy + (tileH - AnubisWidgets.lineHeight(small)) * 0.5F, -1, ask
                  );
               }

               if (name.equals(surface.image.get())) {
                  dl.addRect(
                     sx - AnubisWidgets.s(1.0F),
                     sy - AnubisWidgets.s(1.0F),
                     sx + tileW + AnubisWidgets.s(1.0F),
                     sy + tileH + AnubisWidgets.s(1.0F),
                     AnubisWidgets.c(GuiPalette.accent()),
                     r + AnubisWidgets.s(1.0F),
                     0,
                     AnubisWidgets.s(2.0F)
                  );
               } else if (hover > 0.01F) {
                  dl.addRect(sx, sy, sx + tileW, sy + tileH, AnubisWidgets.c(AnubisWidgets.withAlpha(-1, 0.4F * hover)), r, 0, 1.0F);
               }

               if (deleteShown) {
                  float fade = armed ? 1.0F : hover;
                  float dcx = dx + del * 0.5F;
                  float dcy = dy + del * 0.5F;
                  int disc = armed ? -10856974 : AnubisTheme.lerpColor(AnubisWidgets.withAlpha(-16777216, 0.55F), GuiPalette.accent(), deleteHover);
                  dl.addCircleFilled(dcx, dcy, del * 0.5F, AnubisWidgets.c(AnubisWidgets.withAlpha(disc, fade)), MobilePerformance.uiSegments(32, del * 0.5F));
                  AnubisWidgets.cross(dl, dcx, dcy, AnubisWidgets.s(3.5F), AnubisWidgets.withAlpha(-1, fade));
               }

               if (clicked) {
                  if (armed) {
                     this.pendingConfirm = null;
                  } else {
                     surface.image.set(name);
                  }
               }

               if (!deleteHovered && ImGui.isItemHovered(65536)) {
                  showTooltip(broken ? name + "\nCouldn't load this image" : name);
               }
            }
         }
      }

      if (deleted != null) {
         this.deleteImage(deleted);
      }

      int rows = (count + columns - 1) / columns;
      float hintY = startY + rows * (tileH + gap) - gap + AnubisWidgets.s(10.0F);
      String hint = "Drop an image on the screen or ";
      String link = "choose an image";
      float hintW = AnubisWidgets.textWidth(small, hint);
      float linkW = AnubisWidgets.textWidth(small, link);
      float lineH = AnubisWidgets.lineHeight(small);
      float wx = ImGui.getWindowPosX();
      float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
      AnubisWidgets.text(dl, small, wx + pad, wy + hintY, -4542808, hint);
      ImGui.setCursorPos(pad + hintW, hintY);
      if (ImGui.invisibleButton("##openfolder", linkW, lineH)) {
         ThemeImages.get().openFolder();
      }

      float linkHover = AnubisWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
      AnubisWidgets.text(dl, small, wx + pad + hintW, wy + hintY, AnubisTheme.lerpColor(GuiPalette.accent(), GuiPalette.accentHover(), linkHover), link);
      ImGui.setCursorPos(0.0F, hintY + lineH + AnubisWidgets.s(8.0F));
   }

   private void renderThemeActions(ThemeStore theme, float pad, float contentW) {
      float gap = AnubisWidgets.s(8.0F);
      float h = AnubisWidgets.s(34.0F);
      float buttonW = (contentW - gap * 2.0F) / 3.0F;
      float y = ImGui.getCursorPosY() + AnubisWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##copytheme", "Copy theme", buttonW, h, false)) {
         MinecraftClient.getInstance().keyboard.setClipboard(theme.exportString());
         this.showThemeMessage("Theme copied to the clipboard", false);
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (AnubisWidgets.chipButton("##pastetheme", "Paste theme", buttonW, h, false)) {
         boolean applied = theme.importString(MinecraftClient.getInstance().keyboard.getClipboard());
         this.showThemeMessage(applied ? "Theme applied from the clipboard" : "The clipboard doesn't contain an Anubis theme", !applied);
      }

      ImGui.setCursorPos(pad + (buttonW + gap) * 2.0F, y);
      if (AnubisWidgets.chipButton("##resettheme", "Reset", buttonW, h, false)) {
         theme.resetToDefaults();
         this.showThemeMessage("Theme reset to the defaults", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private void showThemeMessage(String message, boolean error) {
      this.themeMessage = message;
      this.themeMessageError = error;
      this.themeMessageAt = System.nanoTime();
   }

   public void onFilesDropped(List<Path> paths) {
      if (!paths.isEmpty()) {
         if (this.tab == ClickGuiRenderer.Tab.CONFIG) {
            for (Path path : paths) {
               if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                  this.importProfile(path);
                  return;
               }
            }
         }

         for (Path path : paths) {
            if (ThemeImages.isImage(path)) {
               if (this.tab == ClickGuiRenderer.Tab.HUD) {
                  this.importHudHeaderImage(path);
               } else {
                  this.switchTab(ClickGuiRenderer.Tab.THEME);
                  this.importHeaderImage(path);
               }

               return;
            }
         }

         if (this.tab == ClickGuiRenderer.Tab.HUD) {
            this.showHudMessage("Only PNG, JPG and GIF images can be used", true);
         } else {
            this.switchTab(ClickGuiRenderer.Tab.THEME);
            this.showThemeMessage("Only PNG, JPG and GIF images can be used", true);
         }
      }
   }

   private void importHudHeaderImage(Path path) {
      String name = ThemeImages.get().importFile(path);
      if (name == null) {
         this.showHudMessage("Only PNG, JPG and GIF images can be used", true);
      } else {
         String targetId = this.hudElementId != null ? this.hudElementId : "info_bar";
         Surface target = HudTheme.get().forElementId(targetId);
         if (target != null) {
            target.image.set(name);
            target.background.set("Image");
            this.selectHudElement(targetId);
            this.showHudMessage(name + " is now the " + target.label.toLowerCase(Locale.ROOT) + " background", false);
         }
      }
   }

   private void importHeaderImage(Path path) {
      String name = ThemeImages.get().importFile(path);
      if (name == null) {
         this.showThemeMessage("Only PNG, JPG and GIF images can be used", true);
      } else {
         ThemeStore theme = ThemeStore.get();
         Surface target = this.themeSurface != null ? this.themeSurface : theme.cards;
         target.image.set(name);
         target.background.set("Image");
         this.showThemeMessage(name + " is now the " + target.label.toLowerCase(Locale.ROOT) + " background", false);
      }
   }

   private void deleteImage(String name) {
      boolean deleted = ThemeImages.get().delete(name);
      String message = deleted ? "Deleted " + name : "Couldn't delete " + name;
      if (this.tab == ClickGuiRenderer.Tab.HUD) {
         this.showHudMessage(message, !deleted);
      } else {
         this.showThemeMessage(message, !deleted);
      }
   }

   private static void drawCheck(float x, float y, float progress, int accent) {
      ImDrawList dl = ImGui.getWindowDrawList();
      dl.addRectFilled(x, y, x + 16.0F, y + 16.0F, -13226454, 4.0F);
      dl.addRect(x + 0.5F, y + 0.5F, x + 16.0F - 0.5F, y + 16.0F - 0.5F, AnubisTheme.lerpColor(452984831, accent, progress), 4.0F);
      if (progress > 0.01F) {
         dl.addLine(x + 4.0F, y + 8.0F, x + 7.0F, y + 11.0F, accent, 2.0F * progress);
         dl.addLine(x + 7.0F, y + 11.0F, x + 12.0F, y + 5.0F, accent, 2.0F * progress);
      }
   }

   static float smooth(float from, float to, float dt, float speed) {
      return from + (to - from) * (1.0F - (float)Math.exp(-speed * Math.max(0.0F, dt)));
   }

   public static float animationDelta(float dt) {
      float speed = GuiPalette.animationSpeed();
      return speed <= 0.0F ? 1.0F : dt * speed;
   }

   public static float elapsed(long since, float durationMs) {
      float speed = GuiPalette.animationSpeed();
      return speed <= 0.0F ? 1.0F : Math.min(1.0F, (float)(System.nanoTime() - since) * speed / (durationMs * 1000000.0F));
   }

   public static float easeOut(float t) {
      float inv = 1.0F - t;
      return 1.0F - inv * inv * inv;
   }

   private void renderKeybindRow(ClientModule module, float pad, float contentW) {
      float rowH = AnubisWidgets.s(46.0F);
      float startY = this.rowStart(pad, contentW);
      boolean isListening = this.listeningModule == module;
      if (isListening) {
         if (this.listeningFirstFrame) {
            this.listeningFirstFrame = false;
         } else {
            int pressed = scanPressedKey();
            if (pressed == 256) {
               module.setKeybind(-1);
               this.listeningModule = null;
               isListening = false;
            } else if (pressed != -1) {
               module.setKeybind(pressed);
               this.listeningModule = null;
               isListening = false;
            }
         }
      }

      rowLabel("Key", pad, startY, rowH);
      float chipH = AnubisWidgets.s(30.0F);
      float keyW = AnubisWidgets.s(96.0F);
      float modeW = AnubisWidgets.s(76.0F);
      float gap = AnubisWidgets.s(8.0F);
      float chipY = startY + (rowH - chipH) * 0.5F;
      if (!module.isAction()) {
         ImGui.setCursorPos(pad + contentW - keyW - gap - modeW, chipY);
         if (AnubisWidgets.chipButton("##bindmode", module.isHoldMode() ? "Hold" : "Toggle", modeW, chipH, false)) {
            module.setHoldMode(!module.isHoldMode());
         }
      }

      String label = isListening ? "Press a key..." : (module.keybind() == -1 ? "None" : keyName(module.keybind()));
      ImGui.setCursorPos(pad + contentW - keyW, chipY);
      if (AnubisWidgets.chipButton("##bind", label, keyW, chipH, isListening)) {
         if (isListening) {
            this.listeningModule = null;
         } else {
            this.listeningModule = module;
            this.listeningFirstFrame = true;
            this.listeningKeySetting = null;
            this.listeningKeyFirstFrame = false;
         }
      }

      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   public boolean capturesKeys() {
      return this.listeningModule != null || this.listeningKeySetting != null || ImGuiManager.getInstance().typing();
   }

   public boolean handleEscape() {
      if (AndroidSystemPicker.handleEscape()) {
         return true;
      } else if (this.listeningKeySetting != null) {
         this.listeningKeySetting.setValue(-1);
         this.listeningKeySetting = null;
         this.listeningKeyFirstFrame = false;
         return true;
      } else if (this.listeningModule != null) {
         this.listeningModule.setKeybind(-1);
         this.listeningModule = null;
         this.listeningFirstFrame = false;
         return true;
      } else if (this.blockLists.closePicker()) {
         return true;
      } else if (this.itemPickers.closeWindow()) {
         return true;
      } else if (this.settingsModule != null) {
         this.closeSettings();
         return true;
      } else {
         return false;
      }
   }

   public static int scanPressedKey() {
      long handle = MinecraftClient.getInstance().getWindow().getHandle();

      for (int key = 32; key <= 348; key++) {
         if (AnubisKeys.getKey(handle, key) == 1) {
            return key;
         }
      }

      for (int button = 0; button <= 7; button++) {
         if (GLFW.glfwGetMouseButton(handle, button) == 1) {
            return 1000 + button;
         }
      }

      return -1;
   }

   private String keyLabel(int key) {
      String label = (String)this.keyLabels.get(key);
      if (label == null) {
         label = keyName(key);
         this.keyLabels.put(key, label);
      }

      return label;
   }

   public static String keyName(int key) {
      if (key >= 1000) {
         int button = key - 1000;

         return switch (button) {
            case 0 -> "LMB";
            case 1 -> "RMB";
            case 2 -> "MMB";
            default -> "MB" + (button + 1);
         };
      } else {
         if (key >= 39 && key <= 96 || key >= 161 && key <= 162) {
            String name = GLFW.glfwGetKeyName(key, -1);
            if (name != null && !name.isBlank()) {
               return name.toUpperCase(Locale.ROOT);
            }
         }
         return switch (key) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BKSP";
            case 260 -> "INS";
            case 261 -> "DEL";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 266 -> "PGUP";
            case 267 -> "PGDN";
            case 268 -> "HOME";
            case 269 -> "END";
            case 290 -> "F1";
            case 291 -> "F2";
            case 292 -> "F3";
            case 293 -> "F4";
            case 294 -> "F5";
            case 295 -> "F6";
            case 296 -> "F7";
            case 297 -> "F8";
            case 298 -> "F9";
            case 299 -> "F10";
            case 300 -> "F11";
            case 301 -> "F12";
            case 335 -> "NUM ENTER";
            case 340 -> "L SHIFT";
            case 341 -> "L CTRL";
            case 342 -> "L ALT";
            case 344 -> "R SHIFT";
            case 345 -> "R CTRL";
            case 346 -> "R ALT";
            default -> "K" + key;
         };
      }
   }

   private void renderModuleConfigActions(ClientModule module, float pad, float contentW) {
      float gap = AnubisWidgets.s(8.0F);
      float h = AnubisWidgets.s(36.0F);
      float buttonW = (contentW - gap) * 0.5F;
      float y = ImGui.getCursorPosY() + AnubisWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##copy_config", "Copy Config", buttonW, h, true)) {
         this.copyModuleConfig(module);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Copy this module's settings as a short code");
      }

      String key = "paste:" + module.id();
      boolean confirming = this.isConfirming(key);
      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (AnubisWidgets.chipButton("##paste_config", confirming ? "Confirm paste" : "Paste Config", buttonW, h, confirming) && this.confirmClick(key)) {
         this.pasteModuleConfig(module);
      }

      if (ImGui.isItemHovered(65536)) {
         showTooltip("Replace this module's settings with a code from your clipboard");
      }

      y += h + AnubisWidgets.s(8.0F);
      ImFont small = ImGuiManager.getInstance().smallFont();
      if (module.id().equals(this.moduleCodeFor) && this.moduleCodeMessage != null && elapsed(this.moduleCodeAt, 2500.0F) < 1.0F) {
         ImGui.setCursorPos(pad, y);
         boolean pushed = pushFont(small);
         ImGui.pushStyleColor(0, this.moduleCodeError ? -10856974 : -4542808);
         ImGui.textUnformatted(this.moduleCodeMessage);
         ImGui.popStyleColor();
         if (pushed) {
            ImGui.popFont();
         }
      }

      ImGui.setCursorPos(0.0F, y + AnubisWidgets.lineHeight(small));
   }

   private void copyModuleConfig(ClientModule module) {
      String code = ModuleCodes.copy(module);
      MinecraftClient.getInstance().keyboard.setClipboard(code);
      this.setModuleCodeMessage(module, "Copied, " + code.length() + " characters", false);
   }

   private void pasteModuleConfig(ClientModule module) {
      try {
         ModuleCodes.paste(module, MinecraftClient.getInstance().keyboard.getClipboard());
         this.setModuleCodeMessage(module, "Config pasted", false);
      } catch (ProfileException e) {
         this.setModuleCodeMessage(module, e.getMessage(), true);
      }
   }

   private void setModuleCodeMessage(ClientModule module, String message, boolean error) {
      this.moduleCodeFor = module.id();
      this.moduleCodeMessage = message;
      this.moduleCodeError = error;
      this.moduleCodeAt = System.nanoTime();
   }

   private void renderHudActions(float pad, float contentW) {
      float gap = AnubisWidgets.s(8.0F);
      float h = AnubisWidgets.s(36.0F);
      float buttonW = (contentW - gap) * 0.5F;
      float y = ImGui.getCursorPosY() + AnubisWidgets.s(14.0F);
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##editlayout", "Edit Layout", buttonW, h, true)) {
         MinecraftClient.getInstance().setScreen(new HudEditorScreen());
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (AnubisWidgets.chipButton("##resetlayout", "Reset Layout", buttonW, h, false)) {
         HudLayoutStore.getInstance().resetToDefaults(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private static boolean isHudModule(ClientModule module) {
      return module.category() == ModuleCategory.CLIENT && "HUD".equals(module.name());
   }

   private static ClientModule hudModule() {
      for (ClientModule module : ModuleRegistry.all()) {
         if (isHudModule(module)) {
            return module;
         }
      }

      return null;
   }

   private void renderHudStudio(float dt, float alpha, float view) {
      this.renderHudInspector(dt, alpha);
      this.highlightHudElements();
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      HudRenderer.getInstance().studioInput(sw, sh, hudStudioBlocked());
   }

   private static boolean hudStudioBlocked() {
      return ImGui.isWindowHovered(4) || ImGui.isAnyItemActive() || ImGui.isPopupOpen("", 3072);
   }

   private void renderHudInspector(float dt, float alpha) {
      float sw = ImGui.getIO().getDisplaySizeX();
      float sh = ImGui.getIO().getDisplaySizeY();
      float w = AnubisWidgets.s(360.0F);
      float h = Math.min(sh - AnubisWidgets.s(86.0F) - AnubisWidgets.s(40.0F), AnubisWidgets.s(760.0F));
      ImGui.setNextWindowPos(sw - w - AnubisWidgets.s(24.0F), AnubisWidgets.s(24.0F), 4);
      ImGui.setNextWindowSize(w, h, 1);
      if (this.focusHudInspector) {
         ImGui.setNextWindowFocus();
         this.focusHudInspector = false;
      }

      ImGui.pushStyleVar(0, alpha);
      ImGui.pushStyleVar(2, 0.0F, 0.0F);
      ImGui.pushStyleVar(14, 0.0F, 0.0F);
      ImGui.pushStyleVar(3, cardRounding());
      ImGui.pushStyleColor(2, GuiPalette.abgr(ThemeStore.get().cards.color.argbWithAlpha(255)));
      int flags = 59;
      if (ImGui.begin("##hud_inspector", flags)) {
         float x = ImGui.getWindowPosX();
         float y = ImGui.getWindowPosY();
         drawShadow(x, y, w, h, cardRounding(), 6);
         ImGui.getWindowDrawList().addRect(x, y, x + w, y + h, AnubisWidgets.c(318767103), cardRounding(), 0, 1.0F);
         float pad = AnubisWidgets.s(20.0F);
         float top = AnubisWidgets.s(20.0F);
         drawCardTitle("HUD", pad, top + AnubisWidgets.s(14.0F));
         float ty = top + AnubisWidgets.s(34.0F);
         if (this.hudMessage != null && System.nanoTime() - this.hudMessageAt < 3000000000L) {
            ImGui.setCursorPos(pad, ty);
            ImGui.pushTextWrapPos(w - pad);
            ImGui.pushStyleColor(0, this.hudMessageError ? -10856974 : -461582);
            ImGui.textUnformatted(this.hudMessage);
            ImGui.popStyleColor();
            ImGui.popTextWrapPos();
            ty = ImGui.getCursorPosY();
         } else {
            ty = mutedParagraph("Click an element on screen to edit it.", pad, ty, w - pad);
         }

         ty = this.renderHudElementTabs(pad, ty + AnubisWidgets.s(12.0F), w - pad * 2.0F, dt) + AnubisWidgets.s(14.0F);
         cardDivider(pad, w, ty);
         ImGui.setCursorPos(0.0F, ty + 1.0F);
         if (ImGui.beginChild("##hud_inspector_body", w, Math.max(1.0F, h - ty - 1.0F - AnubisWidgets.s(10.0F)), false, 24)) {
            float contentW = w - pad * 2.0F;
            this.dividerPending = false;
            if (this.hudElementId == null) {
               this.renderHudGeneralOptions(pad, contentW);
            } else {
               this.renderHudElementOptions(this.hudElementId, pad, contentW);
            }

            ImGui.dummy(1.0F, AnubisWidgets.s(18.0F));
            this.applySmoothScroll("hud_inspector", dt);
         }

         ImGui.endChild();
      }

      ImGui.end();
      ImGui.popStyleColor();
      ImGui.popStyleVar(4);
   }

   private float renderHudElementTabs(float pad, float y, float contentW, float dt) {
      @Environment(EnvType.CLIENT)
      record Item(String id, String label) {
      }

      List<Item> items = List.of(
         new Item(null, "General"),
         new Item("info_bar", HudTheme.get().infoBar.label),
         new Item("coordinates", HudTheme.get().coordinates.label),
         new Item("module_list", HudTheme.get().moduleList.label),
         new Item("potion_tracker", HudTheme.get().potions.label),
         new Item("region_map", HudTheme.get().regionMap.label),
         new Item("anti_vanish", HudTheme.get().antiVanish.label),
         new Item("staff_list", HudTheme.get().staffList.label),
         new Item("spotify", HudTheme.get().spotify.label),
         new Item("hotkeys", HudTheme.get().hotkeys.label),
         new Item("radar", HudRenderer.editorLabel("radar")),
         new Item("target_card", HudTheme.get().targetCard.label),
         new Item("armor_status", HudTheme.get().armorStatus.label),
         new Item("item_cooldowns", HudTheme.get().itemCooldowns.label),
         new Item("toggle_toasts", HudTheme.get().toasts.label)
      );
      ImFont small = ImGuiManager.getInstance().smallFont();
      boolean pushed = pushFont(small);
      float chipH = AnubisWidgets.s(24.0F);
      float gap = AnubisWidgets.s(6.0F);
      float rowGap = AnubisWidgets.s(6.0F);
      float curX = pad;
      float curY = y;

      try {
         for (Item item : items) {
            boolean selected = Objects.equals(item.id(), this.hudElementId);
            float chipW = AnubisWidgets.textWidth(small, item.label()) + AnubisWidgets.s(18.0F);
            if (curX + chipW > pad + contentW && curX > pad) {
               curX = pad;
               curY += chipH + rowGap;
            }

            ImGui.setCursorPos(curX, curY);
            if (AnubisWidgets.chipButton("##hudel_" + (item.id() == null ? "general" : item.id()), item.label(), chipW, chipH, selected)) {
               this.selectHudElement(item.id());
            }

            curX += chipW + gap;
         }
      } finally {
         if (pushed) {
            ImGui.popFont();
         }
      }

      return curY + chipH;
   }

   private void renderHudGeneralOptions(float pad, float contentW) {
      ClientModule hud = hudModule();
      if (hud != null) {
         this.sectionLabel("Settings", pad);
         this.settingRows(pad, contentW, hud.settings().toArray(new Setting[0]));
      }

      this.sectionLabel("Layout", pad);
      this.settingRows(pad, contentW, HudTheme.get().font, HudTheme.get().rounding);
      float y = ImGui.getCursorPosY() + AnubisWidgets.s(6.0F);
      float h = AnubisWidgets.s(34.0F);
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##resethudlayout", "Reset positions & sizes", contentW, h, false)) {
         HudLayoutStore.getInstance().resetToDefaults(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
         this.showHudMessage("HUD layout reset", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
      this.sectionLabel("Share", pad);
      this.renderHudThemeActions(pad, contentW);
   }

   private void renderHudElementOptions(String id, float pad, float contentW) {
      if (hudModule() instanceof HudModule hud) {
         List<Setting> own = hud.elementSettings(id);
         if (!own.isEmpty()) {
            this.sectionLabel(hudOptionsTitle(id), pad);
            this.settingRows(pad, contentW, own.toArray(new Setting[0]));
         }
      }

      if ("region_map".equals(id)) {
         ClientModule regionMap = ModuleRegistry.get(RegionMapModule.class);
         if (regionMap != null) {
            this.renderInlineModuleToggle(regionMap, pad, contentW);
            this.sectionLabel("Settings", pad);
            this.settingRows(pad, contentW, regionMap.settings().toArray(new Setting[0]));
         }
      }

      ClientModule owner = "radar".equals(id) ? ModuleRegistry.get(RadarModule.class) : ("hotkeys".equals(id) ? ModuleRegistry.get(HotkeysModule.class) : null);
      if (owner != null) {
         this.renderInlineModuleToggle(owner, pad, contentW);
         if (!owner.settings().isEmpty()) {
            this.sectionLabel("Settings", pad);
            this.settingRows(pad, contentW, owner.settings().toArray(new Setting[0]));
         }
      }

      Surface surface = HudTheme.get().forElementId(id);
      if (surface != null) {
         this.renderSurfaceOptions(surface, pad, contentW);
      }
   }

   private static String hudOptionsTitle(String id) {
      return switch (id) {
         case "toggle_toasts" -> "Style";
         case "target_card", "armor_status", "item_cooldowns" -> "Options";
         default -> "Show";
      };
   }

   private void renderInlineModuleToggle(ClientModule module, float pad, float contentW) {
      float rowH = AnubisWidgets.s(46.0F);
      float startY = this.rowStart(pad, contentW);
      rowLabel("Active", pad, startY, rowH);
      float swW = AnubisWidgets.s(40.0F);
      float swH = AnubisWidgets.s(22.0F);
      ImGui.setCursorPos(pad + contentW - swW, startY + (rowH - swH) * 0.5F);
      float sx = ImGui.getCursorScreenPosX();
      float sy = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##active", swW, swH)) {
         module.toggle();
      }

      AnubisWidgets.drawSwitch(ImGui.getWindowDrawList(), sx, sy, swW, swH, AnubisWidgets.animate(ImGui.getItemID(), module.isEnabled(), 16.0F));
      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   private void renderHudThemeActions(float pad, float contentW) {
      HudTheme theme = HudTheme.get();
      float gap = AnubisWidgets.s(8.0F);
      float h = AnubisWidgets.s(34.0F);
      float buttonW = (contentW - gap * 2.0F) / 3.0F;
      float y = ImGui.getCursorPosY() + AnubisWidgets.s(6.0F);
      ImGui.setCursorPos(pad, y);
      if (AnubisWidgets.chipButton("##copyhudtheme", "Copy theme", buttonW, h, false)) {
         MinecraftClient.getInstance().keyboard.setClipboard(theme.exportString());
         this.showHudMessage("HUD theme copied to the clipboard", false);
      }

      ImGui.setCursorPos(pad + buttonW + gap, y);
      if (AnubisWidgets.chipButton("##pastehudtheme", "Paste theme", buttonW, h, false)) {
         boolean applied = theme.importString(MinecraftClient.getInstance().keyboard.getClipboard());
         this.showHudMessage(applied ? "HUD theme applied from the clipboard" : "The clipboard doesn't contain an Anubis HUD theme", !applied);
      }

      ImGui.setCursorPos(pad + (buttonW + gap) * 2.0F, y);
      if (AnubisWidgets.chipButton("##resethudtheme", "Reset", buttonW, h, false)) {
         theme.resetToDefaults();
         this.showHudMessage("HUD theme reset to the defaults", false);
      }

      ImGui.setCursorPos(0.0F, y + h);
   }

   private void showHudMessage(String message, boolean error) {
      this.hudMessage = message;
      this.hudMessageError = error;
      this.hudMessageAt = System.nanoTime();
   }

   private void selectHudElement(String id) {
      if (!Objects.equals(id, this.hudElementId)) {
         this.hudElementId = id;
         this.focusHudInspector = true;
      }
   }

   private void highlightHudElements() {
      if (!AndroidSystemPicker.blocksEditor()) {
         float mx = ImGui.getIO().getMousePosX();
         float my = ImGui.getIO().getMousePosY();
         boolean dragging = HudRenderer.getInstance().isDragging();
         HudLayoutStore.HudElementState hovered = null;
         if (!dragging && !hudStudioBlocked()) {
            for (HudLayoutStore.HudElementState state : HudLayoutStore.getInstance().orderedElements()) {
               if (state.positioned && contains(new float[]{state.x, state.y, state.width, state.height}, mx, my)) {
                  hovered = state;
               }
            }
         }

         ImDrawList under = ImGui.getBackgroundDrawList();
         if (this.hudElementId != null) {
            HudLayoutStore.HudElementState selected = findHudElementState(this.hudElementId);
            if (selected != null) {
               this.outlineHudElement(under, selected, AnubisWidgets.withAlpha(GuiPalette.accent(), 0.8F));
            }
         }

         if (hovered != null) {
            if (!hovered.id.equals(this.hudElementId)) {
               this.outlineHudElement(under, hovered, -2130706433);
            }

            ImDrawList fg = ImGui.getForegroundDrawList();
            Surface surface = HudTheme.get().forElementId(hovered.id);
            String label = surface != null ? surface.label : HudRenderer.editorLabel(hovered.id);
            String hint = label + "  ·  click to edit, drag to move, scroll to resize";
            ImFont small = ImGuiManager.getInstance().smallFont();
            float chipW = AnubisWidgets.textWidth(small, hint) + AnubisWidgets.s(18.0F);
            float chipH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(10.0F);
            float cx = Math.min(mx + AnubisWidgets.s(14.0F), ImGui.getIO().getDisplaySizeX() - chipW - AnubisWidgets.s(4.0F));
            float cy = Math.min(my + AnubisWidgets.s(18.0F), ImGui.getIO().getDisplaySizeY() - chipH - AnubisWidgets.s(4.0F));
            fg.addRectFilled(cx, cy, cx + chipW, cy + chipH, GuiPalette.accent(), chipH * 0.5F);
            AnubisWidgets.text(fg, small, cx + AnubisWidgets.s(9.0F), cy + AnubisWidgets.s(5.0F), -1, hint);
            if (ImGui.isMouseClicked(0) || ImGui.isMouseClicked(1)) {
               this.selectHudElement(hovered.id);
            }
         }
      }
   }

   private void outlineHudElement(ImDrawList dl, HudLayoutStore.HudElementState state, int color) {
      float e = AnubisWidgets.s(3.0F);
      float rounding = HudRenderer.hudRounding() * HudRenderer.scaleOf(state);
      dl.addRect(state.x - e, state.y - e, state.x + state.width + e, state.y + state.height + e, color, rounding + e, 0, AnubisWidgets.s(1.5F));
   }

   private static HudLayoutStore.HudElementState findHudElementState(String id) {
      for (HudLayoutStore.HudElementState state : HudLayoutStore.getInstance().orderedElements()) {
         if (state.id.equals(id)) {
            return state;
         }
      }

      return null;
   }

   private void openColorPicker(ClientModule module, ColorSetting setting) {
      this.activeColorModule = module;
      this.activeColorSetting = setting;
      this.colorPickerOpen.set(true);
   }

   private void renderColorPickerWindow() {
      if (this.activeColorSetting != null) {
         if (!this.colorPickerOpen.get()) {
            this.activeColorSetting = null;
            this.activeColorModule = null;
         } else {
            ImGui.setNextWindowSize(270.0F, 330.0F, 4);
            ImGui.setNextWindowPos(260.0F, 90.0F, 4);
            int flags = 352;
            String owner = this.activeColorModule != null ? this.activeColorModule.name() + " / " : "";
            if (ImGui.begin("Color - " + owner + this.activeColorSetting.name() + "##color_picker", this.colorPickerOpen, flags)) {
               int pickerFlags = 135593984;
               if (ImGui.colorPicker4("##picker", this.activeColorSetting.value(), pickerFlags)) {
                  this.activeColorSetting.notifyChanged();
               }

               ImGui.setNextItemWidth(240.0F);
               if (ImGui.colorEdit4("RGBA", this.activeColorSetting.value(), pickerFlags)) {
                  this.activeColorSetting.notifyChanged();
               }

               if (ImGui.button("Close", 80.0F, 22.0F)) {
                  this.activeColorSetting.notifyChanged();
                  this.colorPickerOpen.set(false);
               }
            }

            ImGui.end();
            if (!this.colorPickerOpen.get()) {
               this.activeColorSetting = null;
               this.activeColorModule = null;
            }
         }
      }
   }

   private void renderItemColorPaletteWindow() {
      if (this.itemColorWindowOpen && this.activeItemColorPalette != null) {
         float dt = Math.max(ImGui.getIO().getDeltaTime(), 0.001F);
         ImGui.setNextWindowSize(340.0F, 500.0F, 4);
         ImGui.setNextWindowPos(340.0F, 100.0F, 4);
         int wFlags = 288;
         boolean open = ImGui.begin("Item Colors##icpalette", wFlags);
         if (!open) {
            ImGui.end();
            if (this.activeItemColorPalette != null) {
               this.activeItemColorPalette.notifyChanged();
            }

            this.itemColorWindowOpen = false;
         } else {
            ImGui.text("Default:");
            ImGui.sameLine();
            int defFlags = 131648;
            if (ImGui.colorButton("##icdefcol", this.activeItemColorPalette.defaultColor(), defFlags, 42.0F, 16.0F)) {
               this.editingItemDefaultColor = true;
               this.activeItemColorEntry = null;
               this.itemColorPickerOpen.set(true);
            }

            ImGui.separator();
            ImGui.text("Search:");
            ImGui.sameLine();
            ImGui.setNextItemWidth(200.0F);
            ImGui.inputText("##icsearch", this.itemSearchBuf);
            String search = this.itemSearchBuf.get().toLowerCase().trim();
            ImGui.separator();
            ImGui.beginChild("##iclist", 0.0F, 360.0F, false, 0);

            for (ItemColorListSetting.ItemEntry entry : this.activeItemColorPalette.entries()) {
               boolean matches = search.isEmpty() || entry.displayName().toLowerCase().contains(search) || entry.id().toLowerCase().contains(search);
               if (matches) {
                  float rowY = ImGui.getCursorPosY();
                  float entryH = 22.0F;
                  float availW = ImGui.getContentRegionAvailX();
                  ImGui.setCursorPos(4.0F, rowY + (entryH - 16.0F) * 0.5F);
                  ImGui.pushID(entry.id() + "_ictog");
                  this.renderItemEntryToggle(this.activeItemColorPalette, entry, dt);
                  ImGui.popID();
                  float textH2 = ImGui.getTextLineHeight();
                  ImGui.setCursorPos(24.0F, rowY + (entryH - textH2) * 0.5F);
                  ImGui.textUnformatted(entry.displayName());
                  float swatchX = availW - 46.0F;
                  if (swatchX < 96.0F) {
                     swatchX = 96.0F;
                  }

                  ImGui.setCursorPos(swatchX, rowY + (entryH - 16.0F) * 0.5F);
                  int csFlags = 131648;
                  ImGui.pushID(entry.id() + "_iccol");
                  if (ImGui.colorButton("##ic", entry.color(), csFlags, 42.0F, 16.0F)) {
                     this.activeItemColorEntry = entry;
                     this.editingItemDefaultColor = false;
                     this.itemColorPickerOpen.set(true);
                  }

                  ImGui.popID();
                  ImGui.setCursorPosY(rowY + entryH + 2.0F);
               }
            }

            ImGui.endChild();
            ImGui.separator();
            if (ImGui.button("Close##icclose", 80.0F, 22.0F)) {
               this.activeItemColorPalette.notifyChanged();
               this.itemColorWindowOpen = false;
               this.activeItemColorPalette = null;
            }

            ImGui.end();
            this.renderItemColorPicker();
         }
      }
   }

   private void renderItemColorPicker() {
      if (!this.itemColorPickerOpen.get()) {
         this.activeItemColorEntry = null;
         this.editingItemDefaultColor = false;
      } else if (this.activeItemColorEntry == null && !this.editingItemDefaultColor) {
         this.itemColorPickerOpen.set(false);
      } else {
         float[] target;
         String label;
         if (this.editingItemDefaultColor && this.activeItemColorPalette != null) {
            target = this.activeItemColorPalette.defaultColor();
            label = "Default Color";
         } else {
            target = this.activeItemColorEntry.color();
            label = this.activeItemColorEntry.displayName();
         }

         ImGui.setNextWindowSize(270.0F, 330.0F, 4);
         ImGui.setNextWindowPos(700.0F, 100.0F, 4);
         int wFlags = 352;
         if (ImGui.begin("Item Color - " + label, this.itemColorPickerOpen, wFlags)) {
            int pickerFlags = 135593984;
            ImGui.colorPicker4("##icpicker", target, pickerFlags);
            ImGui.setNextItemWidth(240.0F);
            if (ImGui.colorEdit4("RGBA##icce", target, pickerFlags) && this.activeItemColorPalette != null) {
               this.activeItemColorPalette.notifyChanged();
            }

            if (ImGui.button("Close##iccp", 80.0F, 22.0F)) {
               if (this.activeItemColorPalette != null) {
                  this.activeItemColorPalette.notifyChanged();
               }

               this.itemColorPickerOpen.set(false);
               this.activeItemColorEntry = null;
               this.editingItemDefaultColor = false;
            }
         }

         ImGui.end();
         if (!this.itemColorPickerOpen.get()) {
            this.activeItemColorEntry = null;
            this.editingItemDefaultColor = false;
         }
      }
   }

   private void renderItemEntryToggle(ItemColorListSetting palette, ItemColorListSetting.ItemEntry entry, float dt) {
      float tw = 16.0F;
      float th = 16.0F;
      float sx = ImGui.getCursorScreenPosX();
      float sy = ImGui.getCursorScreenPosY();
      if (ImGui.invisibleButton("##ietog", 16.0F, 16.0F)) {
         entry.enabled().set(!entry.enabled().get());
         palette.notifyChanged();
      }

      float target = entry.enabled().get() ? 1.0F : 0.0F;
      float current = this.itemEntryTogAnim.getOrDefault(entry, target);
      current = smooth(current, target, dt, 14.0F);
      this.itemEntryTogAnim.put(entry, current);
      drawCheck(sx, sy, current, GuiPalette.accent());
   }

   private void renderSetting(ClientModule module, Setting setting, float pad, float contentW) {
      float rowH = AnubisWidgets.s(46.0F);
      float startY = this.rowStart(pad, contentW);
      ImDrawList dl = ImGui.getWindowDrawList();
      if (setting instanceof BooleanSetting bs) {
         ImGui.setCursorPos(pad, startY);
         float sx = ImGui.getCursorScreenPosX();
         float sy = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##row", contentW, rowH)) {
            bs.set(!bs.get());
         }

         float hover = AnubisWidgets.animate(ImGui.getItemID(), ImGui.isItemHovered(), 18.0F);
         if (hover > 0.01F) {
            dl.addRectFilled(
               sx - AnubisWidgets.s(10.0F),
               sy + AnubisWidgets.s(3.0F),
               sx + contentW + AnubisWidgets.s(10.0F),
               sy + rowH - AnubisWidgets.s(3.0F),
               AnubisWidgets.c(AnubisWidgets.withAlpha(184549375, hover)),
               AnubisWidgets.s(10.0F)
            );
         }

         rowLabel(setting.name(), pad, startY, rowH);
         float swW = AnubisWidgets.s(40.0F);
         float swH = AnubisWidgets.s(22.0F);
         AnubisWidgets.drawSwitch(dl, sx + contentW - swW, sy + (rowH - swH) * 0.5F, swW, swH, AnubisWidgets.animate(ImGui.getID("##switch"), bs.get(), 16.0F));
      } else if (setting instanceof NumberSetting ns) {
         rowH = AnubisWidgets.s(64.0F);
         ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
         ImGui.textUnformatted(setting.name());
         ImFont small = ImGuiManager.getInstance().smallFont();
         String value = ns.formatted();
         float tw = AnubisWidgets.textWidth(small, value);
         float pillW = tw + AnubisWidgets.s(16.0F);
         float pillH = AnubisWidgets.lineHeight(small) + AnubisWidgets.s(8.0F);
         float wx = ImGui.getWindowPosX();
         float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
         float px = wx + pad + contentW - pillW;
         float py = wy + startY + AnubisWidgets.s(8.0F);
         dl.addRectFilled(px, py, px + pillW, py + pillH, AnubisWidgets.c(-13226454), pillH * 0.5F);
         AnubisWidgets.text(dl, small, px + AnubisWidgets.s(8.0F), py + AnubisWidgets.s(4.0F), -461582, value);
         ImGui.setCursorPos(pad - AnubisWidgets.s(7.0F), startY + AnubisWidgets.s(36.0F));
         if (AnubisWidgets.sliderFloat("##v", ns.value(), ns.min(), ns.max(), contentW + AnubisWidgets.s(14.0F), ns::snap)) {
            ns.notifyChanged();
         }
      } else if (setting instanceof ModeSetting ms) {
         rowLabel(setting.name(), pad, startY, rowH);
         float widest = 0.0F;

         for (String mode : ms.modes()) {
            widest = Math.max(widest, ImGui.calcTextSize(mode).x);
         }

         float dropW = Math.min(contentW * 0.6F, Math.max(AnubisWidgets.s(120.0F), widest + AnubisWidgets.s(44.0F)));
         float dropH = AnubisWidgets.s(30.0F);
         ImGui.setCursorPos(pad + contentW - dropW, startY + (rowH - dropH) * 0.5F);
         int picked = AnubisWidgets.dropdown("##mode", ms.get(), ms.modes(), dropW, dropH);
         if (picked >= 0) {
            ms.set(ms.modes().get(picked));
         }
      } else if (setting instanceof ColorSetting cs) {
         rowLabel(setting.name(), pad, startY, rowH);
         float swW = AnubisWidgets.s(44.0F);
         float swH = AnubisWidgets.s(24.0F);
         ImGui.setCursorPos(pad + contentW - swW, startY + (rowH - swH) * 0.5F);
         float sx = ImGui.getCursorScreenPosX();
         float sy = ImGui.getCursorScreenPosY();
         if (ImGui.invisibleButton("##color", swW, swH)) {
            this.openColorPicker(module, cs);
         }

         boolean hovered = ImGui.isItemHovered();
         float[] val = cs.value();
         int r = (int)(val[0] * 255.0F) & 0xFF;
         int g = (int)(val[1] * 255.0F) & 0xFF;
         int b = (int)(val[2] * 255.0F) & 0xFF;
         int a = val.length > 3 ? (int)(val[3] * 255.0F) & 0xFF : 255;
         dl.addRectFilled(sx, sy, sx + swW, sy + swH, AnubisWidgets.c(-13226454), AnubisWidgets.s(7.0F));
         dl.addRectFilled(sx, sy, sx + swW, sy + swH, AnubisWidgets.c(a << 24 | b << 16 | g << 8 | r), AnubisWidgets.s(7.0F));
         dl.addRect(sx, sy, sx + swW, sy + swH, AnubisWidgets.c(hovered ? 1728053247 : 654311423), AnubisWidgets.s(7.0F), 0, 1.0F);
         if (hovered) {
            showTooltip(String.format("#%02X%02X%02X", r, g, b));
         }
      } else if (setting instanceof KeySetting ks) {
         boolean isListening = this.listeningKeySetting == ks;
         if (isListening) {
            if (this.listeningKeyFirstFrame) {
               this.listeningKeyFirstFrame = false;
            } else {
               int pressed = scanPressedKey();
               if (pressed == 256) {
                  ks.setValue(-1);
                  this.listeningKeySetting = null;
                  isListening = false;
               } else if (pressed != -1) {
                  ks.setValue(pressed);
                  this.listeningKeySetting = null;
                  isListening = false;
               }
            }
         }

         rowLabel(setting.name(), pad, startY, rowH);
         float keyW = AnubisWidgets.s(96.0F);
         float chipH = AnubisWidgets.s(30.0F);
         String keyLabel = isListening ? "Press a key..." : (ks.value() == -1 ? "None" : keyName(ks.value()));
         ImGui.setCursorPos(pad + contentW - keyW, startY + (rowH - chipH) * 0.5F);
         if (AnubisWidgets.chipButton("##keyval", keyLabel, keyW, chipH, isListening)) {
            if (isListening) {
               this.listeningKeySetting = null;
            } else {
               this.listeningKeySetting = ks;
               this.listeningKeyFirstFrame = true;
               this.listeningModule = null;
               this.listeningFirstFrame = false;
            }
         }
      } else if (setting instanceof StringSetting ss && !ss.presets().isEmpty()) {
         rowH = this.itemPickers.renderPresets(module, ss, pad, contentW, startY);
      } else if (setting instanceof StringSetting ss && ss.isItemPicker()) {
         rowH = this.itemPickers.renderItem(module, ss, pad, contentW, startY);
      } else if (setting instanceof StringSetting ss && ss.enchantItem() != null) {
         rowH = this.itemPickers.renderEnchants(module, ss, pad, contentW, startY);
      } else if (setting instanceof StringSetting ss) {
         ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
         ImGui.textUnformatted(setting.name());
         ImGui.setCursorPos(pad, startY + AnubisWidgets.s(34.0F));
         ImGui.pushStyleVar(11, AnubisWidgets.s(12.0F), AnubisWidgets.s(8.0F));
         ImGui.setNextItemWidth(contentW);
         boolean edited = ss.hint() != null ? ImGui.inputTextWithHint("##str", ss.hint(), ss.getImString()) : ImGui.inputText("##str", ss.getImString());
         if (edited) {
            ss.notifyChanged();
         }

         ImGui.popStyleVar();
         rowH = AnubisWidgets.s(34.0F) + ImGui.getTextLineHeight() + AnubisWidgets.s(16.0F) + AnubisWidgets.s(12.0F);
      } else if (setting instanceof StringListSetting sls && sls.isItemPicker()) {
         rowH = this.itemPickers.renderList(module, sls, pad, contentW, startY);
      } else if (setting instanceof StringListSetting sls) {
         rowH = renderStringList(sls, pad, contentW, startY);
      } else if (setting instanceof ActionSetting actionSetting) {
         float h = AnubisWidgets.s(36.0F);
         ImGui.setCursorPos(pad, startY + (rowH + AnubisWidgets.s(6.0F) - h) * 0.5F);
         if (AnubisWidgets.chipButton("##action", setting.name(), contentW, h, false)) {
            actionSetting.run();
         }

         rowH += AnubisWidgets.s(6.0F);
      } else if (setting instanceof BlockColorListSetting bcls) {
         rowH = this.blockLists.render(module, bcls, pad, contentW, startY);
      } else if (setting instanceof ItemColorListSetting icls) {
         long enabled = icls.entries().stream().filter(en -> en.enabled().get()).count();
         if (listChip(setting.name(), "Edit (" + enabled + ")", pad, contentW, startY, rowH)) {
            this.activeItemColorPalette = icls;
            this.itemColorWindowOpen = true;
            this.activeItemColorEntry = null;
            this.editingItemDefaultColor = false;
         }
      }

      if (!(setting instanceof ActionSetting)) {
         this.renderResetAffordance(setting, pad, contentW, startY, rowH);
      }

      ImGui.setCursorPos(0.0F, startY + rowH);
   }

   private void renderResetAffordance(Setting setting, float pad, float contentW, float startY, float rowH) {
      if (this.tab != ClickGuiRenderer.Tab.THEME) {
         boolean modified = !setting.isDefault();
         float wx = ImGui.getWindowPosX();
         float wy = ImGui.getWindowPosY() - ImGui.getScrollY();
         boolean topLabel = setting instanceof NumberSetting
            || setting instanceof StringSetting
            || setting instanceof StringListSetting
            || setting instanceof BlockColorListSetting;
         float labelCy = topLabel ? startY + AnubisWidgets.s(10.0F) + ImGui.getTextLineHeight() * 0.5F : startY + rowH * 0.5F;
         if (modified) {
            float dx = wx + pad - AnubisWidgets.s(11.0F);
            float dy = wy + labelCy;
            float r = AnubisWidgets.s(3.0F);
            ImGui.getWindowDrawList().addCircleFilled(dx, dy, r, AnubisWidgets.c(GuiPalette.accent()), 12);
            if (ImGui.isMouseHoveringRect(dx - r * 2.0F, dy - r * 2.0F, dx + r * 2.0F, dy + r * 2.0F)) {
               showTooltip("Modified - right-click the row to reset");
            }
         }

         float x0 = wx + pad - AnubisWidgets.s(10.0F);
         float y0 = wy + startY;
         if (ImGui.isWindowHovered() && ImGui.isMouseClicked(1) && ImGui.isMouseHoveringRect(x0, y0, x0 + contentW + AnubisWidgets.s(20.0F), y0 + rowH)) {
            ImGui.openPopup("##setting_ctx");
         }

         ImGui.pushStyleVar(2, AnubisWidgets.s(6.0F), AnubisWidgets.s(6.0F));
         if (ImGui.beginPopup("##setting_ctx")) {
            ImGui.textDisabled(setting.name());
            ImGui.separator();
            if (modified) {
               if (ImGui.selectable("Reset to default")) {
                  setting.resetToDefault();
               }
            } else {
               ImGui.textDisabled("Already at default");
            }

            ImGui.endPopup();
         }

         ImGui.popStyleVar();
      }
   }

   private static float renderStringList(StringListSetting list, float pad, float contentW, float startY) {
      ImGui.setCursorPos(pad, startY + AnubisWidgets.s(10.0F));
      ImGui.textUnformatted(list.name());
      float y = startY + AnubisWidgets.s(34.0F);
      float gap = AnubisWidgets.s(6.0F);
      float fieldH = ImGui.getTextLineHeight() + AnubisWidgets.s(16.0F);
      float removeW = fieldH;
      int remove = -1;
      ImGui.pushStyleVar(11, AnubisWidgets.s(12.0F), AnubisWidgets.s(8.0F));

      for (int i = 0; i < list.size(); i++) {
         ImGui.pushID(i);
         ImGui.setCursorPos(pad, y);
         ImGui.setNextItemWidth(contentW - removeW - gap);
         if (ImGui.inputText("##entry", list.entry(i))) {
            list.edited();
         }

         ImGui.setCursorPos(pad + contentW - removeW, y);
         if (AnubisWidgets.chipButton("##remove", "x", removeW, fieldH, false)) {
            remove = i;
         }

         ImGui.popID();
         y += fieldH + gap;
      }

      ImGui.popStyleVar();
      if (remove >= 0) {
         list.remove(remove);
      }

      if (list.canAdd()) {
         float addH = AnubisWidgets.s(30.0F);
         ImGui.setCursorPos(pad, y);
         if (AnubisWidgets.chipButton("##add", "+ Add", contentW, addH, false)) {
            list.add();
         }

         y += addH;
      }

      return y - startY + AnubisWidgets.s(12.0F);
   }

   private static boolean listChip(String label, String chip, float pad, float contentW, float startY, float rowH) {
      rowLabel(label, pad, startY, rowH);
      float chipW = Math.max(AnubisWidgets.s(96.0F), ImGui.calcTextSize(chip).x + AnubisWidgets.s(28.0F));
      float chipH = AnubisWidgets.s(30.0F);
      ImGui.setCursorPos(pad + contentW - chipW, startY + (rowH - chipH) * 0.5F);
      return AnubisWidgets.chipButton("##listedit", chip, chipW, chipH, false);
   }

   public static void applyTheme() {
      ImGui.pushStyleVar(3, AnubisWidgets.s(10.0F));
      ImGui.pushStyleVar(12, AnubisWidgets.s(8.0F));
      ImGui.pushStyleVar(9, AnubisWidgets.s(10.0F));
      ImGui.pushStyleVar(21, AnubisWidgets.s(6.0F));
      ImGui.pushStyleVar(2, AnubisWidgets.s(10.0F), AnubisWidgets.s(10.0F));
      ImGui.pushStyleVar(11, AnubisWidgets.s(10.0F), AnubisWidgets.s(6.0F));
      ImGui.pushStyleVar(14, AnubisWidgets.s(8.0F), AnubisWidgets.s(6.0F));
      ImGui.pushStyleVar(4, 0.0F);
      ImGui.pushStyleVar(10, 1.0F);
      ImGui.pushStyleVar(18, AnubisWidgets.s(6.0F));
      ImGui.pushStyleVar(19, AnubisWidgets.s(3.0F));

      for (int i = 0; i < THEME_COLORS.length; i += 2) {
         ImGui.pushStyleColor(THEME_COLORS[i], THEME_COLORS[i + 1]);
      }

      int accent = GuiPalette.accent();
      int card = GuiPalette.cardBg();
      ImGui.pushStyleColor(2, GuiPalette.panelBg());
      ImGui.pushStyleColor(10, card);
      ImGui.pushStyleColor(11, card);
      ImGui.pushStyleColor(12, card);
      ImGui.pushStyleColor(18, accent);
      ImGui.pushStyleColor(19, accent);
      ImGui.pushStyleColor(20, accent);
      ImGui.pushStyleColor(23, accent);
      ImGui.pushStyleColor(26, accent);
      ImGui.pushStyleColor(51, AnubisWidgets.withAlpha(accent, 0.4F));
   }

   public static void removeTheme() {
      ImGui.popStyleColor(THEME_COLORS.length / 2 + 10);
      ImGui.popStyleVar(11);
   }

   public static boolean pushFont(ImFont font) {
      if (font == null) {
         return false;
      }

      ImGui.pushFont(font);
      return true;
   }

   @Environment(EnvType.CLIENT)
   private record CategoryPanel(ModuleCategory category, String id, String rowsId) {
   }

   @Environment(EnvType.CLIENT)
   private enum Tab {
      MODULES("Modules"),
      HUD("HUD"),
      FRIENDS("Friends"),
      THEME("Theme"),
      CONFIG("Config");

      final String label;

      Tab(String label) {
         this.label = label;
      }
   }
}
