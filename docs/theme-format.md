# Theme file format

A theme is a recipe, spec 2.4: the parameters the renderer reads, never images.
One UTF-8 JSON file per theme, `filesDir/themes/<id>.json` on the phone. Export
shares a `.zip` holding that JSON as `theme.json`, the textures it uses and,
only on request, the signing key. Code: `app/src/main/kotlin/dev/iconoclast/app/theme/`.

## Schema version 4

```json
{
    "schemaVersion": 4,
    "id": "3f0c2b1e-8a4d-4c55-9a8e-1d2f3a4b5c6d",
    "name": "Jade",
    "created": 1791590400000,
    "modified": 1791590400000,
    "theme": {
        "shape": {
            "strokeWeight": 1.0, "cap": "ROUND", "join": "ROUND", "miterLimit": 4.0,
            "iconScale": 0.8, "offsetX": 0.0, "offsetY": 0.0, "dots": "SCALE_WITH_STROKE"
        },
        "fill": {
            "mode": "INVERT", "gapWidth": 1.0, "gapClosing": 0.5,
            "fallbackThreshold": 0.1, "keepLoneLines": true
        },
        "background": {
            "enabled": true,
            "shape": { "type": "roundedSquare", "cornerRadius": 0.2 },
            "size": 1.0,
            "border": null
        },
        "layers": [
            { "type": "paint", "id": "background",
              "source": { "type": "solid", "color": "#FF000000" },
              "clip": "BACKGROUND", "clipInverse": false, "blend": "NORMAL", "opacity": 1.0,
              "mapping": { "mode": "FILL", "scale": 1.0, "rotation": 0.0, "lockOffset": false },
              "matte": null },
            { "type": "effects", "id": "effects" },
            { "type": "effect", "id": "streaks", "enabled": true, "blend": "NORMAL", "opacity": 0.35,
              "effect": { "type": "streaks", "angle": 300.0, "length": 0.35, "decay": "EXPONENTIAL",
                          "breakup": { "texture": "529541caaef2a89958423ffa5e19474d7e8ef76a41c142e0bfcbb6834dd3bf30",
                                       "scale": 1.0, "stretch": 6.0, "density": 0.55, "contrast": 0.4, "seed": 0 },
                          "paintLayer": "icon-fill", "color": "#FFFFFFFF" } },
            { "type": "paint", "id": "icon-fill",
              "source": { "type": "solid", "color": "#FF00A86B" },
              "clip": "ICON_FILL", "clipInverse": false, "blend": "NORMAL", "opacity": 1.0,
              "mapping": { "mode": "FILL", "scale": 1.0, "rotation": 0.0, "lockOffset": false },
              "matte": null },
            { "type": "paint", "id": "icon-lines",
              "source": { "type": "solid", "color": "#FF00A86B" },
              "clip": "ICON_LINES", "clipInverse": false, "blend": "NORMAL", "opacity": 1.0,
              "mapping": { "mode": "FILL", "scale": 1.0, "rotation": 0.0, "lockOffset": false },
              "matte": null }
        ]
    },
    "overrides": {
        "fdroid": { "strokeWeight": 2.5, "fillMode": null, "gapClosing": null,
                    "textureOffset": { "x": 0.125, "y": 0.875 }, "hideStreaks": true,
                    "swapDrawable": null }
    }
}
```

| Field | Meaning |
|---|---|
| `schemaVersion` | Integer. Missing means version 0. Newer than the app's version: refused. |
| `id` | Random UUID. Presets use fixed ids, `preset-plain-arcticons`, `preset-jade` and `preset-neon`. Import assigns a fresh one. |
| `name` | Shown on the themes home. |
| `created`, `modified` | Epoch milliseconds. |
| `overrides` | Schema 4. Per-icon overrides by appfilter drawable name, sorted by name. See Overrides. |
| `theme` | The `Theme` from `:render`, field for field. Ranges and meanings are in `render/.../Theme.kt`, `render/.../Layers.kt`, `render/.../Effects.kt` and spec 2.3. |

Rules:

- Every field is written, defaults included, so a later change to a default in
  `:render` never changes a saved theme. Every field is required on read.
  Unknown keys are an error.
- Numbers are JSON numbers. `-0.0` reads and writes as `0.0`, so a parsed theme
  equals, and hashes like, one built with `0f`. NaN and infinities are refused.
- Colours are strings `#AARRGGBB`, straight alpha.
- Enums are the `:render` enum names: `cap` BUTT, ROUND, SQUARE; `join` MITER,
  ROUND, BEVEL; `dots` SCALE_WITH_STROKE, KEEP_ORIGINAL; `mode` OUTLINE, INVERT,
  SILHOUETTE, OUTLINE_SILHOUETTE.
- `background.shape.type`: `circle`, `square`, `teardrop`, `squircle` with
  `exponent`, `roundedSquare` with `cornerRadius`.
- `background.border`: `null` or `{ "width", "color", "opacity" }`. The border
  is drawn at the effects layer, on top of the layers below it.
- Out-of-range values fail the whole file. Import shows the reason.

### Layers

`theme.layers` is the stack, painted first to last, spec 2.3 and 3.3 step 4.
It holds exactly one `effects` layer, and ids are unique. Step 5, the
background shape's alpha, clips the result.

`{ "type": "effects", "id" }` is the effects marker of the base stack. It paints
the background border and is where a new effect layer goes by default
(`Theme.withEffect`: just above the marker and the effects already stacked on
it). Each effect is its own `effect` layer, below.

`{ "type": "paint", ... }`:

| Field | Values |
|---|---|
| `id` | Non-blank string, unique in the stack, stable across edits and reordering. The base stack uses `background`, `effects`, `icon-fill`, `icon-lines`; new layers should use a UUID. |
| `source` | A paint, below. |
| `clip` | `CANVAS`; `BACKGROUND`: inside the background shape, empty when the background is off (the shape's anti-aliased edge is applied once, by step 5); `ICON`: fill body union lines, `b + l - b l`; `ICON_FILL`, `ICON_LINES`: the fill stage's body and lines alone; `EFFECTS`: the union, `e + c - e c`, of the coverage times opacity of every enabled effect layer below this layer, empty below the first. |
| `clipInverse` | `true` uses `1 - coverage`. |
| `blend` | `NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `DARKEN`, `LIGHTEN`, `COLOR_DODGE`, `COLOR_BURN`, `HARD_LIGHT`, `SOFT_LIGHT`, `DIFFERENCE`, `EXCLUSION`, `HUE`, `SATURATION`, `COLOR`, `LUMINOSITY`, `ADD`. W3C Compositing and Blending Level 1. The platform names: `NORMAL` is `SRC_OVER`, `ADD` is `PLUS`, the rest are equal; all 17 exist in `android.graphics.BlendMode` at compileSdk 37. |
| `opacity` | 0.0 to 1.0. |
| `mapping` | Texture and noise placement; ignored by solid and gradient sources. `mode`: `FIT` (all of the texture shows), `FILL` (covers the canvas, crops), `TILE` (short side spans the canvas, repeats). `scale` 0.25 to 4.0, above 1 zooms in. `rotation` -180.0 to 180.0 degrees about the canvas centre. `lockOffset`: `false` gives each icon its own patch, seeded by its drawable name; `true` gives every icon the centred patch. The per-icon offset: `TILE` translates the texture plane by the offset times the texture size, wrapped; `FIT` and `FILL` move the canvas window within the room between it and the texture, `FILL` only where the texture is larger so the patch never leaves it, `FIT` either way so a smaller texture moves inside the canvas and stays whole. A square texture at scale 1 under `FILL` leaves no room, so the editor starts every new texture layer at `scale` 2.0. Noise shifts its field by the offset. |
| `matte` | `null`, or mask adjustments that turn the source's luminance into alpha: `{ "mode", "black", "white", "threshold", "invert", "feather" }`. `mode` `LEVELS` maps `black` to 0 and `white` to 1 (both 0.0 to 1.0, black below white); `THRESHOLD` gives 1 where luminance reaches `threshold` (0.0 to 1.0). `invert` flips it. `feather` 0.0 to 0.05 of the canvas is a Gaussian blur radius. Luminance is read before any gradient map: `0.3 r + 0.59 g + 0.11 b` of the premultiplied texels for a texture (transparent reads as black), the raw noise value for noise, the colour's for solids and gradients. So a black-and-white texture with single-colour `stops` paints that colour wherever the texture is light. |

Per pixel, coverage = clip x matte x opacity, and the source scaled by the
coverage is blended into the stack.

### Overrides (schema 4)

Spec 2.2 screen 3. One `IconOverride` per drawable name, stored beside the
theme, never inside it. Every field is written; `null`, and `hideStreaks`
false, keeps the theme's value. An empty override is never stored: "reset to
theme" removes the entry (`withOverride(name, null)`).

| Field | Overrides |
|---|---|
| `strokeWeight` | `theme.shape.strokeWeight`, 0.25 to 4.0. |
| `fillMode` | `theme.fill.mode`. |
| `gapClosing` | `theme.fill.gapClosing`, 0.0 to 2.0. |
| `textureOffset` | The icon's one per-icon offset, `{ "x", "y" }`, each in [0, 1). Texture and noise layers, the streak breakup and grain all follow it. Locked mappings keep the centre. |
| `hideStreaks` | `true` drops every Streaks effect layer for this icon. |
| `swapDrawable` | Draws another drawable's geometry in this icon's slot. The slot keeps its own seed name, so its offset does not move with the swap. |

Resolution (`EffectiveTheme.kt`): `effectiveTheme(name)` is the theme with
the override applied, `effectiveGeometry(name)` is the swap target or the name
itself. The renderer and `PackBuilder` see only that Theme and the two names,
`IconRenderer.render(geometryName, icon, seedName, theme, size, resolution)`.
Mask cache entries are keyed by the geometry name, so a swap reads the
target's masks and never writes under the slot's name. A pinned offset is
`Theme.textureOffset`, which only an effective theme sets; the serializer
refuses to save it.

Overrides whose name, or swap target, is not in the pinned icon set are kept,
so a later pin that brings the name back finds them. `staleOverrides(known)`
lists them for the coverage screen. `PackBuilder` draws a slot whose swap
target is missing in its own geometry.

### Effects (schema 3)

`{ "type": "effect", "id", "enabled", "blend", "opacity", "effect" }`, one per
effect, anywhere in the stack, spec 2.3 "Effects":

| Field | Values |
|---|---|
| `id` | As for paint layers: unique, stable. |
| `enabled` | `false` keeps the settings and paints nothing. |
| `blend` | One of the 17 blend modes, as for paint layers. The bevel ignores it: its highlight and shadow carry their own. |
| `opacity` | 0.0 to 1.0, times the effect's coverage. |
| `effect` | One of the objects below, tagged by `type`. |

Every effect reads the icon mask, fill body union lines (the `ICON` target),
cached with what it derives from it; never the body alone. Lengths are
fractions of the canvas, so previews and the pack agree. Angles are degrees
counterclockwise from the positive x axis, y up as on screen: 0 right, 90 up,
180 left, 270 down. Colours are `#AARRGGBB`, straight alpha. Where an effect
paints decides what shows: streaks, drop shadow and outer glow belong below
the icon layers; bevel, inner shadow and inner glow only show above them.
Each effect adds its coverage to the `EFFECTS` clip target.

`streaks`: the icon mask smeared along an angle, broken up, painted from a
linked layer. Code: `render/.../effect/Streaks.kt`, `Smear.kt`.

| Field | Values |
|---|---|
| `angle` | 0.0 to 360.0: the direction the streaks run from the icon. |
| `length` | 0.0 to 0.6 of the canvas. |
| `decay` | The smear holds the strongest paint dragged onto each pixel, `y = max(x, decayed y)` along the streak, so thin lines streak as strongly as solid bodies: a brush streak, not a motion blur. `LINEAR`: held paint loses 1/length per pixel and ends at the length. `EXPONENTIAL`: held paint times `exp(-3 / length)` per pixel, 5% left at the length, a faint tail beyond. No other curves: these two are one recursion, O(1) per pixel, after turning the mask to the streak's axis; an arbitrary curve costs a tap per pixel of length, about 260 per pixel at the longest 432 px streak, which the phone budget cannot pay. |
| `breakup` | `null`: an unbroken smear. Else `{ "texture", "scale", "stretch", "density", "contrast", "seed" }`: `texture` a texture hash (a built-in brush or an imported texture) or `null` for 4-octave fBm noise; `scale` 0.25 to 4.0, above 1 zooms in; `stretch` 1.0 to 32.0 along the streak; `density` 0.0 to 1.0, how much lets the streak through; `contrast` 0.0 to 1.0; `seed` any 32-bit integer, for the noise. The breakup's luminance t becomes `clamp((t - (1 - density)) / (1 - 0.95 contrast) + 0.5)` and multiplies the smear. A texture tiles with its short side spanning the canvas over `scale`; noise has 16 / scale cells across. Each icon gets its own patch. |
| `paintLayer` | `null` or the id of a paint layer whose source paints the streaks: under that layer's mapping and per-icon offset, gradient-mapped, but not its clip, matte or opacity. An id that names no paint layer (deleted, or not a paint layer) falls back to `color`. |
| `color` | The paint without a link. |

`bevel`: bevel and emboss from the signed sub-pixel distance to the icon
mask's edge (anti-aliased EDT after Gustavson and Strand), light at 30
degrees altitude. Code: `effect/Bevel.kt`, `mask/SignedDistance.kt`.

| Field | Values |
|---|---|
| `style` | `INNER`: rises inside the icon from its edge, `1 - (1 - t)^2`; `EMBOSS`: rises across the edge, half outside, half inside, smoothstep `3 t^2 - 2 t^3`, flat at both ends. |
| `lightAngle` | 0.0 to 360.0: where the light comes from. |
| `depth` | 0.0 to 4.0: steepness; 1 rises as far as the bevel is wide. |
| `size` | 0.001 to 0.05 of the canvas: the bevel's width. |
| `softness` | 0.0 to 1.0: blurs the surface with sigma `softness size / 2`, never less than 1.5 px at 432 px (scaled with the render), which keeps thin features from striating. |
| `highlight`, `highlightBlend` | Colour, its alpha the highlight's strength; blend mode. Defaults `#BFFFFFFF`, `SCREEN`. |
| `shadow`, `shadowBlend` | The same for the shaded side. Defaults `#BF000000`, `MULTIPLY`. |

`dropShadow`: the icon's shadow, knocked out under the icon.
`innerShadow`: a shadow inside the icon along the edges facing the light.
Code: `effect/DropShadow.kt`, `InnerShadow.kt`.

| Field | Values |
|---|---|
| `angle` | 0.0 to 360.0: where the light comes from; the shadow falls the other way. 120 puts it below right. |
| `distance` | 0.0 to 0.1 of the canvas. |
| `blur` | 0.0 to 0.1 of the canvas: Gaussian radius, sigma half of it. |
| `color` | The shadow colour. The layer's `opacity` is the shadow's opacity. |

`outerGlow`: a glow around the icon, outside it only. `innerGlow`: a glow from
the icon's edges inward. Code: `effect/Glows.kt`.

| Field | Values |
|---|---|
| `size` | 0.001 to 0.1 of the canvas: Gaussian radius, sigma half of it. |
| `spread` | 0.0 to 1.0: the blur divided by `1 - 0.99 spread`, clamped; 0 the plain blur, 1 solid out to where the blur fades. |
| `color` | The glow colour. |

`grain`: monochrome noise over the whole canvas. Grey `0.5 + (n - 0.5) amount`,
so a contrast blend (`OVERLAY`, `SOFT_LIGHT`) leaves the average alone; with
`NORMAL` it paints grey. Grain is source-atop: it lands only on what is already
painted, weighted by its alpha, and keeps that alpha, so with the background
off a transparent canvas stays transparent. Over pure white or black, overlay
and soft light change nothing; use multiply or screen there. Each icon gets its own patch. Code: `effect/Grain.kt`,
a wrapper over the `GRAIN` noise source, so grain has one code path.

| Field | Values |
|---|---|
| `amount` | 0.0 to 1.0: how far the grey swings from mid-grey. |
| `size` | 1.0 to 4.0: a cell's side in 1/432 of the canvas, a pack pixel at 432 px. |
| `seed` | Any 32-bit integer. |

Grain, as an effect or a `GRAIN` noise paint, is box-filtered: each pixel is
the area average of the cells under it, and the per-icon offset is whole
cells. So a 432 px pack render has one cell per pixel, and a 128 px preview
is that render box-filtered down rather than a coarser, harsher grain.

### Paints

`source` objects, tagged by `type`:

- `solid`: `color`.
- `linear`: `x0`, `y0`, `x1`, `y1`, `stops`. `radial`: `cx`, `cy`, `radius`
  (above 0), `stops`. Coordinates are fractions of the canvas. A stop is
  `{ "offset": 0.0, "color": "#FF000000" }`, offsets ascending, at least one.
- `texture`: `sha256`, the lowercase hex SHA-256 of the texture file, 64
  characters; `stops`: `null` keeps the texture's colours, a list maps its
  luminance through the stops and keeps its alpha.
- `noise`: `kind` `FBM`, `RIDGED` or `GRAIN`; `octaves` 1 to 8 (fBm and
  ridged); `seed`, any 32-bit integer; `stops`, the colours from noise 0 to 1.
  Default stops are black to white.

### Textures

A texture is referenced by content hash, spec 2.4. The file is a canonical
PNG: 8-bit RGBA written by the project's own encoder (`TextureStore.encode`)
from premultiplied pixels, so equal pixels give equal bytes and equal hashes
on every device. Longest side at most 2048 px; the importer downscales first.
The file name is `<sha256>.png`. `TextureStore.put` re-encodes whatever PNG
it is given and hashes the canonical bytes, and `TextureStore.bytes(hash)`
returns them, so an exported file always matches its hash. `Theme.textureHashes()` lists
paint layers' textures and streak breakup textures, switched-off effects
included; a streak's linked layer is a paint layer, so its texture is listed
already. A theme export (`.zip`, Task 8) carries `theme.json` and every file
in `Theme.textureHashes()` except the built-in brushes. An imported file is
canonicalised and checked against its hash (`TextureStore.put(hash, bytes)`).
A file the phone already stores is canonical by construction, so reloading
it checks the SHA-256 of its own bytes, without a re-encode. A theme whose
texture is missing does not render.

The canonical encoding is pinned: `TextureStore.encode` writes with
`Deflate.Effort.CANONICAL`, the M5 encoder unchanged, because the hash names
those bytes. Icons in a pack use the faster `Effort.FAST` since M6; that
changes pack PNG bytes, never texture files or their hashes.

Built-in brushes, for streak breakup, are textures every install has.
`render/.../texture/BuiltInBrushes.kt` generates them from seeded noise, 256
px square, opaque grey, tiling; no image file ships. Each is named by the
SHA-256 of its canonical PNG, like any texture:

| Brush | Hash |
|---|---|
| Bristle | `edd5479ea2f30e4f82555c5337e7b12b68bc462d98cce2c86568fa87ac5196cc` |
| Dry brush | `529541caaef2a89958423ffa5e19474d7e8ef76a41c142e0bfcbb6834dd3bf30` |
| Chalk | `01efda6942f1af4e3de7310633581f8e06357ebc32dfbadcc12d2c53fc38bdb2` |

The store always has them without a file, so a theme using one is never
missing a texture. Exports do not carry them, and imports never require
them; an archive entry for one is ignored. `BuiltInBrushesTest` regenerates
each and checks its hash.

What the hash guarantees: the stored file, and so the theme that names it,
is stable. It does not promise that the same source photo imported on two
phones gets one hash. Import scales the photo down and may convert its
colour profile through the platform's decoder, and those pixels can differ
between devices; different pixels give a different canonical file.

## Migration

`migrate(json)` reads any known version and upgrades it one step at a time.
Every load and every import goes through it.

Version 0 is synthetic, never shipped. It exists so the mechanism is tested
before a real bump: no `schemaVersion`, `id` or timestamps, `title` instead of
`name`, `theme.shape.stroke` instead of `strokeWeight`. Fixture:
`app/src/test/resources/themes/v0.json`.

Version 1 (M4) had `theme.background.paint` and `theme.glyph` with `line` and
`body` colours. `v1ToV2` (`MigrationV1ToV2.kt`) removes both and writes the
base stack: a `background` layer with the old paint clipped to `BACKGROUND`,
the `effects` layer, then solid `icon-fill` (old `body`, clipped to
`ICON_FILL`) and `icon-lines` (old `line`, clipped to `ICON_LINES`), all
normal, opaque, default mapping, no matte. That is `Theme.baseLayers` in
`:render`, and it renders byte for byte what M4 rendered:
`ThemeMigrationTest` migrates the M4 preset files
(`app/src/test/resources/themes/v1-*.json`), requires them to equal the schema 2
presets, and compares every render with the Plain and Jade goldens.

Version 2 (M5) had no effect layers. `v2ToV3` (`MigrationV2ToV3.kt`) only
sets `schemaVersion` to 3: a version 2 stack is already a valid version 3
stack, and it renders the same bytes. `ThemeMigrationTest` migrates the
schema 2 preset files (`app/src/test/resources/themes/v2-*.json`), requires
them to equal the schema 3 presets with only the version changed, and
compares every render with the Plain and Jade goldens.

Version 3 (M6) had no overrides. `v3ToV4` (`MigrationV3ToV4.kt`) adds an
empty `overrides` object and sets `schemaVersion` to 4; a version 3 file that
already has `overrides` is damaged. `ThemeMigrationTest` migrates the shipped
schema 3 starters (`app/src/test/resources/themes/v3-*.json`), requires the
JSON to equal the input plus the empty map and new version, with no clock and
no id, and compares every render with the Jade and Neon starter goldens. The
preset assets stay at schema 3 and migrate on every seed.

To add version N+1: bump `ThemeDocument.CURRENT_SCHEMA`, append a step to
`STEPS` that rewrites the JSON object from N to N+1, add a version N fixture
and a test, and update this file.

## Storage

- `filesDir/themes/<id>.json`: one per theme. Written to a temp file in the
  same directory, then renamed over the target, so a crash leaves the old or
  the new file, never half of one.
- `filesDir/themes/active`: the active theme's id, plain text, same atomic write.
- `filesDir/themes/seeded`: the ids of the starter presets this install has
  seeded, one per line. Each load seeds the presets not listed and adds them,
  so a preset added in an update arrives. A listed preset is never seeded
  again: a deleted one stays deleted, an edited one keeps its edits. A
  document that already has a preset's id is never overwritten. A file
  holding only `1`, from before M6, means the two M4 presets.
- `filesDir/coverage/last-apply.json`: the last successful Apply, written
  atomically after the install succeeds: `{ "version": 1, "themeId",
  "appliedAt", "components" }`, every launchable `package/class` seen then,
  sorted. `CoverageReport.of` diffs the installed apps against it on open. A
  missing or damaged file reads as never applied.
- `noBackupFilesDir/pack-signing.p12` and `pack-signing.pass`: the pack
  signing key, PKCS12 and its random password, spec 3.5. Never backed up.
- `filesDir/textures/<sha256>.png`: one canonical PNG per imported texture,
  written once, atomically. A texture is present when its file is. The
  renderer's store keeps at most 64 MB of decoded textures and files in memory,
  least recently used first out, and loads evicted ones back from here.

## Export and import

### Archive layout v2 (M7)

| Entry | When | Content |
|---|---|---|
| `theme.json` | always, first | The document, schema 4. |
| `textures/<sha256>.png` | one per non-built-in hash in `Theme.textureHashes()` | The canonical texture file. |
| `key/pack.p12` | only with the key | The signing key's PKCS12 store, the stored file's bytes. |
| `key/password` | only with the key | Its password, UTF-8, the stored file's bytes. |

Version 1 (M5) is the same without `key/`. There is no layout marker: a v1
reader skips `key/` unread, and a v2 reader reads a v1 archive as having no key.

Export writes `<name>.zip` with `theme.json`, then `textures/<sha256>.png` for
each hash in `Theme.textureHashes()`, then the two `key/` entries only when
the caller passes the key (`ThemeZipExport.write(doc, textures, key)`,
`HomeViewModel.export(id, includeKey)`), entry times fixed at 1980-01-01, shared
as `application/zip` through the FileProvider. Code: `ThemeZipExport.kt`.
The archive's bytes are deterministic per platform, not across platforms:
`java.util.zip` deflates through the platform's zlib, whose output may
differ. The entries inside are byte-identical everywhere: `theme.json` and
the canonical texture files.

Import takes a `.zip` or a bare theme JSON (`ThemeZipImport.kt`). A zip must
hold `theme.json`; the theme is migrated and validated; each texture it uses
must be in `textures/` and match its hash after `TextureStore.put`
canonicalises it, or already be on the phone. Other entries are ignored and
never read. A bare JSON imports only when its textures are on the phone.
Limits: 64 MB per file, 256 KB for `theme.json`, 24 MB per texture entry,
64 KB for `key/pack.p12`, 1 KB for `key/password`, 64 MB decompressed in
total. Only `textures/<sha256>.png` entries for hashes the theme uses and the
two `key/` entries are read. The key entries come both or neither, and the
store must open with the password and hold an EC key under the pack alias. The whole archive is validated before any texture or
theme is stored.
Any failure refuses the whole import with a message.

`ArchiveImport.store(archive, restoreKey, ...)` stores a checked archive. The
key is restored only when the caller passes `restoreKey = true`, which it does
only after the user confirmed, and only when this phone has no key
(`PackKeyStore.restore`). The same key already here is fine. A different key
here refuses the import before any texture or theme is stored, and the phone
keeps its key: never a silent overwrite. Without `restoreKey` the archive's
key is ignored. Then textures, then the theme under a new id.

The gate, spec 5 M7: `ExportRoundTripGateTest` exports every golden theme
with its textures and the key, imports each into an empty install, and renders
every golden sample from the imported document at its pack size; every PNG
equals its committed golden.

## Output resolution

`OutputResolution` in `:render`: `FULL`, 432 px, the default, and `COMPACT`,
324 px, for users short on storage. No other size is offered. Grain cells are
`1 / px` of the canvas at grain size 1, so a pack pixel is one grain cell at
either size and grain looks the same; a preview passes the resolution it
previews and box-filters those cells. Everything else is canvas-relative. The
phone budget is stated and measured at 432 only; 324 px has 56% of the pixels.
Goldens: `324/plain` and `324/preset_jade`, the 24 samples each. The setting
itself is Task 12's; `PackBuilder.build(..., resolution)` and
`IconRenderer.render(..., resolution)` take it.

A theme whose texture is missing never reaches the renderer: previews hide
that layer and say so, Apply stops with a message.
