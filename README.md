# Anubis 0.9.9.1 (decompiled)

> **Unofficial.** This is a decompiled and rebuilt copy of Anubis, a Fabric mod written by
> **4ldenz**. It is not affiliated with or endorsed by the original author. Anubis is licensed
> GPL-3.0-only (see [LICENSE](LICENSE)), and this redistribution is under the same license.
> The original source repository (https://github.com/4ldenz/Anubis-client) is no longer
> available.

Decompiled from `Anubis Beta 0.9.9.1.jar` (Fabric, Minecraft 1.21.11).

- Decompiler: Vineflower 1.12.0. `SeedWorldgen.emptyLevel`'s lambda failed in Vineflower
  and was recovered with CFR 0.152, then checked against the bytecode by hand.
- Build setup mirrors the original jar's manifest: Gradle 9.4.1, Loom 1.16.3, Loader 0.19.2.
  The exact Fabric API module, imgui-java 1.90.0 and Netty 4.2.7 versions from the
  original `META-INF/jars` are pulled from Maven and bundled with `include`.

## Building

Needs a JDK 21+ (the code targets Java 21).

```
gradlew.bat build
```

Output: `build/libs/anubis-0.9.9.1+mc1.21.11.jar`.

## Notes

- **Minecraft names are Yarn** (`1.21.11+build.6`). The shipped jar used intermediary
  names (`class_1234`, `method_5678`); every one was translated with Yarn's official table.
  Mixin method selectors got full descriptors so overloaded Yarn names stay unambiguous.
  `method_76304` (in `MixinAtmosphereRainFog`) has no Yarn name and is intentionally kept.
  Eight Minecraft classes whose Yarn name clashed with another name in the same file
  (`Feature`, `Type`, `Entry`, `Unbaked`) are written fully qualified.
- The code needs Fabric API's `fabric-transitive-access-wideners-v1` (protected/private
  Minecraft members), which the original jar did not bundle, so players need Fabric API.
- Hand fixes after decompiling: mixin self-casts got `(Object)` (`(Entity)(Object)this`),
  generic lambdas became method references, two mangled try-with-resources blocks in
  `CustomGlintModule` and a rethrow in `EventBus` were rewritten.
- **Bug in the shipped jar:** `CharacterLayer.submit`, `PharaohHeaddressLayer.submit` and
  `SeedWorldgen`'s `getId`/`byId` kept Mojang names, so they never overrode Minecraft's
  methods. They are now `render`, `getRawId` and `get`, which is what the author intended.
- Mixins live in `com.example.client.mixin` (the original package) and are statically
  remapped, as in the original build (no refmap).
- `src/main/resources/net/anubis/compat/android-picker/` (`classes.dex` +
  `libanubis_picker.so`) and `io/imgui/java/native-bin/android-arm64/` are prebuilt
  Android-only binaries carried over as-is; there is no source for them here.
- Two lambdas were reported as "processed twice" by Vineflower
  (`ConnectionsPanel.importFile`, `AuctionHouse.listSome`). They decompiled, but are the
  first places to look if the compiler complains.

## Base Finder (`anubis-modded` branch)

A new module in the **Base Finding** category that combines several signals into one ranked
list, in the style of Prestige V5's base finder. It is an original implementation; no code was
taken from Prestige or from the Glazed addon (whose license forbids redistribution).

Each loaded chunk gets a probability from independent signals:

| Signal | What it means |
|---|---|
| Player underground | Another player is (or recently was) well below the surface here |
| Hidden player activity | A player you can't see, at any depth: the server reports someone mining a block but never sent you that player, or blocks keep being dug/placed underground with no visible player within 8 blocks (explosions, fluids, falling blocks and growth are ignored). In the overworld also Player Bypass 2's inference: chunks outside your simulation distance keep ticking, or a hidden player hit a mob / picked up an item. Toggle **Hidden Players** |
| Player storage | Shulkers, ender chests, buried chests/barrels (lone ones on the surface count once, so scattered junk doesn't inflate a chunk) |
| Surface build | Concrete, stained glass, quartz or metal blocks lined up 3+ in a row above ground (walls, floors, pillars); single scattered blocks are ignored |
| Rotated deepslate | Natural deepslate is always vertical; sideways means placed |
| Hand-mined tunnel / shaft | Straight 1x2 corridors or 1x1 shafts with solid walls, deep underground |
| Crafted blocks, Redstone, Placed lights, Building blocks | Player blocks buried below the surface |
| Grown amethyst / kelp, long dripstone | Growth that only happens while players keep the chunk loaded |
| Obsidian | Weak signal, ignored next to ruined portals |

Chunks with strongholds, ancient cities, trial chambers, dungeons and ocean monuments have the
matching false positives suppressed. Flagged chunks get a soft tile at the depth of the
evidence and a dot; aim at the dot to see why the chunk was flagged. A panel (top left by
default) lists the most likely chunks with distance and direction.
