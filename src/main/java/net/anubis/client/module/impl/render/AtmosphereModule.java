package net.anubis.client.module.impl.render;

import com.example.client.mixin.AccessorAtmosphereClientLevel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public final class AtmosphereModule extends ClientModule {
   private static final String CUSTOM = "Custom";
   private static final String AURORA_ALWAYS = "Always";
   private static final String AURORA_COLD = "Cold Biomes";
   private static final String AURORA_OFF = "Off";
   private static final float MOON_SCALE = 1.4F;
   private static final AtmosphereModule.Theme[] THEMES = new AtmosphereModule.Theme[]{
      new AtmosphereModule.Theme("Borealis", 198414, 729648, 13073302, 3080090, 9063935, 13163263),
      new AtmosphereModule.Theme("Nebula", 459791, 2233916, 14053304, 16732120, 5991679, 15124735),
      new AtmosphereModule.Theme("Anubis", 657157, 2890512, 15239498, 16757306, 16730670, 16769200),
      new AtmosphereModule.Theme("Frost", 264724, 1454154, 11057384, 8382719, 15135999, 14544639),
      new AtmosphereModule.Theme("Crimson", 721926, 3018260, 15227490, 16724821, 16751164, 16765640)
   };
   private final ModeSetting theme = this.addSetting(
      "theme", new ModeSetting("Theme", "Borealis", "Borealis", "Nebula", "Anubis", "Frost", "Crimson", "Custom")
   );
   private final ColorSetting nightColor = this.addSetting("night-color", new ColorSetting("Night Sky Color", -16047568));
   private final ColorSetting sunsetColor = this.addSetting("sunset-color", new ColorSetting("Sunset Color", -3703914));
   private final ColorSetting auroraColor = this.addSetting("aurora-color", new ColorSetting("Aurora Color", -13697126));
   private final ColorSetting auroraTopColor = this.addSetting("aurora-top-color", new ColorSetting("Aurora Top Color", -7713281));
   private final ModeSetting skyShader = this.addSetting(
      "sky-shader", new ModeSetting("Sky Shader", "Off", "Off", "Space", "Northern Lights", "Ringed Planet", "Black Hole")
   );
   private final NumberSetting shaderDaytime = this.addSetting("sky-shader-daytime", new NumberSetting("Daytime Strength", 1.0F, 0.0F, 1.0F).step(0.05F));
   private final NumberSetting shaderBrightness = this.addSetting("sky-shader-brightness", new NumberSetting("Shader Brightness", 1.0F, 0.2F, 2.0F).step(0.1F));
   private final NumberSetting shaderSpeed = this.addSetting("sky-shader-speed", new NumberSetting("Shader Speed", 1.0F, 0.0F, 3.0F).step(0.1F));
   private final NumberSetting shaderStars = this.addSetting("sky-shader-stars", new NumberSetting("Shader Stars", 1.0F, 0.0F, 2.0F).step(0.1F));
   private final NumberSetting shaderEffect = this.addSetting("sky-shader-effect", new NumberSetting("Nebula / Aurora Strength", 1.0F, 0.0F, 2.0F).step(0.1F));
   private final NumberSetting shaderBand = this.addSetting("sky-shader-galaxy", new NumberSetting("Galaxy Band", 1.0F, 0.0F, 2.0F).step(0.1F));
   private final BooleanSetting gradientSky = this.addSetting("gradient-sky", new BooleanSetting("Gradient Sky", true));
   private final BooleanSetting keepInRain = this.addSetting("keep-in-rain", new BooleanSetting("Keep In Rain", true));
   private final BooleanSetting celestialGlow = this.addSetting("sun-moon-glow", new BooleanSetting("Sun & Moon Glow", true));
   private final BooleanSetting stars = this.addSetting("stars", new BooleanSetting("Stars", true));
   private final NumberSetting starBrightness = this.addSetting("star-brightness", new NumberSetting("Star Brightness", 1.3F, 0.5F, 2.0F).step(0.1F));
   private final BooleanSetting milkyWay = this.addSetting("milky-way", new BooleanSetting("Milky Way", true));
   private final NumberSetting milkyWayBrightness = this.addSetting(
      "milky-way-brightness", new NumberSetting("Milky Way Brightness", 1.0F, 0.2F, 2.0F).step(0.1F)
   );
   private final BooleanSetting shootingStars = this.addSetting("shooting-stars", new BooleanSetting("Shooting Stars", true));
   private final ModeSetting aurora = this.addSetting("aurora", new ModeSetting("Aurora", "Always", "Always", "Cold Biomes", "Off"));
   private final NumberSetting auroraBrightness = this.addSetting("aurora-brightness", new NumberSetting("Aurora Brightness", 1.0F, 0.2F, 2.0F).step(0.1F));
   private final NumberSetting auroraSpeed = this.addSetting("aurora-speed", new NumberSetting("Aurora Speed", 1.0F, 0.2F, 3.0F).step(0.1F));
   private final BooleanSetting cloudColors = this.addSetting("cloud-colors", new BooleanSetting("Cloud Colors", true));
   private final BooleanSetting endSky = this.addSetting("end-sky", new BooleanSetting("End Sky", true));
   private final BooleanSetting netherColors = this.addSetting("nether-colors", new BooleanSetting("Nether Colors", true));
   private final AtmosphereSky sky = new AtmosphereSky();
   private final AtmosphereStars starField = new AtmosphereStars();
   private final AtmosphereAurora auroraMesh = new AtmosphereAurora();
   private final AtmosphereShaderSky shaderSky = new AtmosphereShaderSky();
   private final MatrixStack world = new MatrixStack();
   private float night;
   private float rainBrightness = 1.0F;
   private float auroraHere;
   private long lastFrame;
   private boolean moonScaled;
   private boolean shaderRendered;

   public AtmosphereModule() {
      super("atmosphere", "Atmosphere", "A custom sky with stars and an aurora.", ModuleCategory.VISUALS);
      this.nightColor.visibleWhen(() -> this.theme.is("Custom"));
      this.sunsetColor.visibleWhen(() -> this.theme.is("Custom"));
      this.auroraColor.visibleWhen(() -> this.theme.is("Custom"));
      this.auroraTopColor.visibleWhen(() -> this.theme.is("Custom"));
      this.shaderDaytime.visibleWhen(this::shaderOn);
      this.shaderBrightness.visibleWhen(this::shaderOn);
      this.shaderSpeed.visibleWhen(this::shaderOn);
      this.shaderStars.visibleWhen(this::shaderOn);
      this.shaderEffect.visibleWhen(this::shaderOn);
      this.shaderBand.visibleWhen(this::shaderOn);
      this.stars.visibleWhen(() -> !this.shaderOn());
      this.starBrightness.visibleWhen(() -> this.stars.get() && !this.shaderOn());
      this.milkyWay.visibleWhen(() -> !this.shaderOn());
      this.milkyWayBrightness.visibleWhen(() -> this.milkyWay.get() && !this.shaderOn());
      this.aurora.visibleWhen(() -> !this.shaderOn());
      this.auroraBrightness.visibleWhen(() -> !this.aurora.is("Off") && !this.shaderOn());
      this.auroraSpeed.visibleWhen(() -> !this.aurora.is("Off") && !this.shaderOn());
   }

   public static AtmosphereModule get() {
      AtmosphereModule module = ModuleRegistry.get(AtmosphereModule.class);
      return module != null && module.isEnabled() ? module : null;
   }

   @Override
   protected void onDisable() {
      this.sky.close();
      this.starField.close();
      this.auroraMesh.close();
      this.shaderSky.close();
      this.shaderRendered = false;
      this.lastFrame = 0L;
      this.auroraHere = 0.0F;
   }

   public void extract(ClientWorld level, float partialTick, SkyRenderState state) {
      this.shaderRendered = false;
      this.night = MathHelper.clamp(state.starBrightness * 2.0F, 0.0F, 1.0F);
      float rain = 1.0F - state.rainGradient;
      float thunder = level.getThunderGradient(partialTick);
      if (this.keepInRain.get()) {
         this.night = Math.max(this.night, clearNight(state.sunAngle) * rain);
         state.starBrightness = Math.max(state.starBrightness, this.night * 0.5F);
         state.rainGradient = 1.0F;
         rain = 0.0F;
         thunder = 0.0F;
      }

      this.rainBrightness = 1.0F - rain;
      AtmosphereModule.Theme colours = this.currentTheme();
      int flashTicks = ((AccessorAtmosphereClientLevel)level).anubis$atmosphereSkyFlash();
      this.sky
         .update(
            state.sunAngle,
            state.moonAngle,
            rain,
            thunder,
            flashTicks > 0 ? Math.min(1.0F, (flashTicks - partialTick) / 2.0F) : 0.0F,
            colours.nightZenith,
            colours.nightHorizon,
            colours.twilight
         );
   }

   public boolean keepsSkyInRain() {
      return this.keepInRain.get();
   }

   private static float clearNight(float sunAngle) {
      float dark = MathHelper.clamp(0.75F - 2.0F * MathHelper.cos(sunAngle), 0.0F, 1.0F);
      return dark * dark;
   }

   public boolean skyDisc() {
      if (this.gradientSky.get() && this.sky.ready()) {
         if (AtmosphereQuality.mobile() && this.shaderOn() && this.shaderVisibility() >= 1.0F) {
            this.renderShaderSky();
            if (!this.shaderRendered) {
               this.sky.renderDome(this.world);
            }

            return true;
         } else {
            this.sky.renderDome(this.world);
            this.renderShaderSky();
            return true;
         }
      } else {
         return false;
      }
   }

   public void afterSkyDisc() {
      this.renderShaderSky();
   }

   private void renderShaderSky() {
      if (this.shaderOn()) {
         AtmosphereModule.Theme colours = this.currentTheme();
         this.shaderRendered = this.shaderSky
            .render(
               this.skyShader.get(),
               this.shaderVisibility(),
               this.shaderBrightness.get(),
               this.shaderSpeed.get(),
               this.shaderStars.get(),
               this.shaderEffect.get(),
               this.shaderBand.get(),
               colours.auroraLow,
               colours.auroraHigh,
               colours.milkyWay,
               colours.nightZenith,
               colours.nightHorizon
            );
      }
   }

   private boolean shaderOn() {
      return !this.skyShader.is("Off");
   }

   private boolean shaderAurora() {
      return this.skyShader.is("Northern Lights");
   }

   private float shaderVisibility() {
      return MathHelper.lerp(this.night, this.shaderDaytime.get(), 1.0F) * (0.4F + 0.6F * this.rainBrightness);
   }

   public boolean replacesSunrise() {
      return this.gradientSky.get() && this.sky.ready();
   }

   public boolean stars(MatrixStack pose) {
      if (this.shaderRendered) {
         return true;
      }

      float clear = this.night * this.rainBrightness;
      if (this.milkyWay.get()) {
         this.starField.renderBand(pose, clear * this.milkyWayBrightness.get(), this.currentTheme().milkyWay);
      }

      if (!this.stars.get()) {
         return false;
      }

      this.starField.renderStars(pose, clear, this.starBrightness.get());
      return true;
   }

   public void beforeMoon(MatrixStack pose) {
      this.moonScaled = this.celestialGlow.get();
      if (this.moonScaled) {
         pose.push();
         pose.scale(1.4F, 1.0F, 1.4F);
      }
   }

   public void afterMoon(MatrixStack pose) {
      if (this.moonScaled) {
         pose.pop();
      }

      this.moonScaled = false;
   }

   public void afterCelestials() {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : Math.min((float)(now - this.lastFrame) / 1.0E9F, 0.1F);
      this.lastFrame = now;
      if (this.sky.ready()) {
         if (this.celestialGlow.get()) {
            this.sky.renderHalos(this.world, this.rainBrightness, this.night);
         }

         if (this.shootingStars.get()) {
            this.sky.renderMeteors(this.world, this.night, this.rainBrightness);
         }

         this.auroraHere = this.auroraHere + (this.auroraTarget() - this.auroraHere) * Math.min(1.0F, dt * 0.5F);
         if (this.auroraHere > 0.01F && this.night > 0.01F && !this.shaderRendered) {
            AtmosphereModule.Theme colours = this.currentTheme();
            float time = (float)(now % 3600000000000L) / 1.0E9F * this.auroraSpeed.get();
            float intensity = this.auroraHere * this.night * (0.35F + 0.65F * this.rainBrightness) * this.auroraBrightness.get();
            this.auroraMesh.render(this.world, intensity, time, colours.auroraLow, colours.auroraHigh);
         }
      }
   }

   private float auroraTarget() {
      if (this.aurora.is("Off")) {
         return 0.0F;
      }

      if (this.aurora.is("Always")) {
         return 1.0F;
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return 0.0F;
      }

      BlockPos at = BlockPos.ofFloored(mc.gameRenderer.getCamera().getCameraPos());
      return ((Biome)mc.world.getBiome(at).value()).isCold(at, mc.world.getSeaLevel()) ? 1.0F : 0.0F;
   }

   public void fog(Camera camera, ClientWorld level, Vector4f color) {
      if (level.getRegistryKey() == World.OVERWORLD) {
         if (this.gradientSky.get() && this.sky.ready()) {
            Vector3fc look = camera.getHorizontalPlane();
            int rgb = this.sky.horizonToward(look.x(), look.z());
            color.x = (rgb >> 16 & 0xFF) / 255.0F;
            color.y = (rgb >> 8 & 0xFF) / 255.0F;
            color.z = (rgb & 0xFF) / 255.0F;
         }

         if (this.shaderOn()) {
            this.shaderFog(color);
         }
      } else if (level.getRegistryKey() == World.NETHER && this.netherColors.get()) {
         float luma = 0.2126F * color.x + 0.7152F * color.y + 0.0722F * color.z;
         color.x = MathHelper.clamp((luma + (color.x - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
         color.y = MathHelper.clamp((luma + (color.y - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
         color.z = MathHelper.clamp((luma + (color.z - luma) * 1.45F) * 1.12F, 0.0F, 1.0F);
      }
   }

   private void shaderFog(Vector4f color) {
      int horizon = this.currentTheme().nightHorizon;
      float scale = this.shaderAurora() ? 0.85F : 0.3F;
      float amount = this.shaderVisibility() * 0.85F;
      color.x = MathHelper.lerp(amount, color.x, (horizon >> 16 & 0xFF) / 255.0F * scale);
      color.y = MathHelper.lerp(amount, color.y, (horizon >> 8 & 0xFF) / 255.0F * scale);
      color.z = MathHelper.lerp(amount, color.z, (horizon & 0xFF) / 255.0F * scale);
   }

   public int clouds(int argb) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.cloudColors.get() && this.sky.ready() && mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD) {
         float golden = this.sky.golden();
         float dark = this.sky.nightness();
         int target = ColorHelper.lerp(golden, ColorHelper.lerp(dark, 16777215, this.currentTheme().nightHorizon), this.sky.twilightGlow());
         float amount = Math.max(golden * 0.75F, dark * 0.55F);
         return keepBrightness(argb, target, amount);
      } else {
         return argb;
      }
   }

   public boolean endSky() {
      if (!this.endSky.get()) {
         return false;
      }

      long now = System.nanoTime();
      this.sky.renderEndDome(this.world);
      MatrixStack turning = new MatrixStack();
      turning.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)(now % 1200000000000L) / 1.0E9F * 0.3F));
      this.starField.renderNebulae(turning, 1.0F);
      this.starField.renderStars(turning, 1.0F, 1.2F);
      return true;
   }

   private AtmosphereModule.Theme currentTheme() {
      for (AtmosphereModule.Theme each : THEMES) {
         if (this.theme.is(each.name)) {
            return each;
         }
      }

      int horizon = this.nightColor.rgb();
      return new AtmosphereModule.Theme(
         "Custom", AtmosphereSky.scale(horizon, 0.3F), horizon, this.sunsetColor.rgb(), this.auroraColor.rgb(), this.auroraTopColor.rgb(), 15263999
      );
   }

   private static int keepBrightness(int argb, int target, float amount) {
      if (amount <= 0.0F) {
         return argb;
      }

      float r = (argb >> 16 & 0xFF) / 255.0F;
      float g = (argb >> 8 & 0xFF) / 255.0F;
      float b = (argb & 0xFF) / 255.0F;
      float tr = (target >> 16 & 0xFF) / 255.0F;
      float tg = (target >> 8 & 0xFF) / 255.0F;
      float tb = (target & 0xFF) / 255.0F;
      float scale = luminance(r, g, b) / Math.max(luminance(tr, tg, tb), 0.001F);
      r = MathHelper.lerp(amount, r, Math.min(1.0F, tr * scale));
      g = MathHelper.lerp(amount, g, Math.min(1.0F, tg * scale));
      b = MathHelper.lerp(amount, b, Math.min(1.0F, tb * scale));
      return argb & 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
   }

   private static float luminance(float r, float g, float b) {
      return 0.2126F * r + 0.7152F * g + 0.0722F * b;
   }

   private static int channel(float value) {
      return MathHelper.clamp(Math.round(value * 255.0F), 0, 255);
   }

   @Environment(EnvType.CLIENT)
   private record Theme(String name, int nightZenith, int nightHorizon, int twilight, int auroraLow, int auroraHigh, int milkyWay) {
   }
}
