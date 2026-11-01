# Anubis 1.0 (decompiled)

> **Unofficial.** This is a decompiled and rebuilt copy of Anubis, a Fabric mod written by
> **4ldenz**. It is not affiliated with or endorsed by the original author. Anubis is licensed
> GPL-3.0-only (see [LICENSE](LICENSE)), and this redistribution is under the same license.
> The original source repository (https://github.com/4ldenz/Anubis-client) is no longer
> available.

Decompiled from `Anubis Client 1.0.jar` (Fabric, Minecraft 1.21.11,
SHA-256 `e60968d360d57e8cfcd68bb127f6f56c7801ddebcff331827102596a209b15f1`).
The previous release, 0.9.9.1, is in this repository's history.

- Remapped from intermediary to Yarn with tiny-remapper 0.13.0 and its mixin extension, so
  mixin targets are Yarn names too. Decompiled with Vineflower 1.12.0.
- 465 source files whose classes are byte-identical to 0.9.9.1 reuse that release's
  hand-fixed sources. The rest are new or changed in 1.0 and were fixed as described below.
- Build setup mirrors the original jar's manifest: Gradle 9.4.1, Loom 1.16.3, Loader 0.19.2.
  The exact Fabric API module and imgui-java 1.90.0 versions from the original
  `META-INF/jars` are pulled from Maven and bundled with `include`.

## Building

Needs a JDK 21+ (the code targets Java 21).

```
gradlew.bat build
```

Output: `build/libs/anubis-1.0+mc1.21.11.jar`. `gradlew.bat runClient` starts a dev client
(it adds the full Fabric API, which 1.0 declares as a dependency).

## What changed in 1.0 (from the jar)

- Added: Easy Place / schematic builder (works with Litematica), tunnel and area miners,
  elytra autopilot, crystal and sword bots, waypoints, Light ESP, Better Sus Chunk, Media Base
  Finder, Anti Ghost Block, a DonutBets event feed, Pay Guard and a startup build check.
- Removed: the account switcher and proxies (now a separate "Anubis Accounts" add-on), Player
  Bypass 2, Quantum, Prime Chunk Finder, Speed Mine, Riptide, and the Android support files.
- `SECURITY.md` (shipped in the jar, kept in `src/main/resources`) is the author's own
  description of the build check, Pay Guard, webhooks and the DonutBets feed.

## Notes

- **Minecraft names are Yarn** (`1.21.11+build.6`). `method_76304` (in
  `MixinAtmosphereRainFog`) has no Yarn name and is intentionally kept.
- Easy Place calls Litematica's `WorldSchematic`, which tiny-remapper could not see, so
  `method_8320/8321/8652` were renamed by hand to `getBlockState`, `getBlockEntity` and
  `setBlockState`. Litematica 0.26.14 and MaLiLib 0.27.19 (the newest 1.21.11 builds made with
  Loom 1.16.3) are compile-only dependencies; at runtime Litematica stays optional.
- Hand fixes after decompiling: mixin self-casts got `(Object)` (`(Entity)(Object)this`), two
  generic lambdas became `this::addSetting`, duplicate local names Vineflower put in one
  `switch` scope were renamed (`Breaker`, `Bridger`, `BuildScan`), and a raw `Property` in
  `BuildOrder.railShape` got its type.
- The startup build check (`security/OfficialBuild`) compares the jar's signature with the
  certificate fingerprint in `BuildConfig.SIGN_CERT_PARTS`. That array is emptied here (the
  official value is kept in a comment), which is how the check treats an unsigned local build:
  otherwise every jar built from this source would be stopped as "modified".
- Mixins live in `com.example.client.mixin` (the original package) and are statically
  remapped, as in the original build (no refmap).
- `SeedWorldgen.emptyLevel`'s lambda fails in Vineflower; its class is unchanged since 0.9.9.1,
  so the CFR-recovered version from that release is used.

## Base Finder (`anubis-modded` branch)

A new module in the **Base Finding** category that combines several signals into one ranked
list, in the style of Prestige V5's base finder. It is an original implementation; no code was
taken from Prestige or from the Glazed addon (whose license forbids redistribution).

Each loaded chunk gets a probability from independent signals:

| Signal | What it means |
|---|---|
| Player underground | Another player is (or recently was) well below the surface here |
| Hidden player activity | A player you can't see, at any depth: the server reports someone mining a block but never sent you that player, or blocks keep being dug/placed/used (doors, trapdoors, gates, barrels, levers) underground with no visible player within 8 blocks (explosions, fluids, falling blocks and growth are ignored). In the overworld also Player Bypass's inference: chunks outside your simulation distance keep ticking, or a hidden player hit a mob / picked up an item. Toggle **Hidden Players** |
| Player storage | Shulkers, ender chests, buried chests/barrels (lone ones on the surface count once, so scattered junk doesn't inflate a chunk) |
| Surface build | Concrete, stained glass, quartz or metal blocks lined up 3+ in a row above ground (walls, floors, pillars); single scattered blocks are ignored |
| Item frames / armor stands | Item frames, glow frames, armor stands and paintings (only players place these) |
| Pets / named mobs | Mobs tamed by someone else, name-tagged, or on a lead (wandering-trader llamas excluded) |
| Animal farm | 10+ animals of one type in one chunk (natural herds are 2-4; axolotls and bees excluded) |
| Signs / banners / heads | Any sign, hanging sign, banner or mob head (ignored inside strongholds, ancient cities, trial chambers, dungeons) |
| Lit nether portal | Ruined portals are never lit, so a lit portal was lit by a player |
| Buried farm | Farmland or crops well below the surface |
| Hidden light source | **Off by default** (setting *Hidden Lights (Light Leaks)*). Under servers that send the underground as fake deepslate (DonutSMP, ChunkVeil), reads the block light the server still sends (Anubis 1.0's `ServerBlockLight`): a fake solid block brighter than all its neighbours (13+) is a hidden torch or lantern. Lush caves, deep lava and stacked glow berries are filtered, but trial chambers and stray glow berries still light up, and servers that blank the light (ChunkVeil's default `sanitize-light`) give nothing |
| Dug-out room | A deep room with a flat solid floor and a flat solid ceiling over at least 5x5 blocks |
| Rotated deepslate | Natural deepslate is always vertical; sideways means placed |
| Hand-mined tunnel / shaft | Straight 1x2 corridors or 1x1 shafts with solid walls, deep underground |
| Crafted blocks, Redstone, Placed lights, Building blocks | Player blocks buried below the surface |
| Grown amethyst / kelp, long dripstone | Growth that only happens while players keep the chunk loaded |
| Obsidian | Weak signal, ignored next to ruined portals |

On servers that hide the underground below Y=0 as flat deepslate (DonutSMP and plugins like
ChunkVeil), those chunks are recognised: the panel says how many loaded chunks have their
underground hidden, block changes there aren't treated as digging, and rooms/tunnels aren't
looked for on the fake flat floor.

Chunks with strongholds, ancient cities, trial chambers, dungeons and ocean monuments have the
matching false positives suppressed. Flagged chunks get a soft tile at the depth of the
evidence and a dot; aim at the dot to see why the chunk was flagged. A panel (top left by
default) lists the most likely chunks with distance and direction.
