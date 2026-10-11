# Theme file format

A theme is a recipe, spec 2.4: the parameters the renderer reads, never images.
One UTF-8 JSON file per theme, `filesDir/themes/<id>.json` on the phone. Export
shares a `.zip` holding that JSON as `theme.json` and the textures it uses. Code: `app/src/main/kotlin/dev/iconoclast/app/theme/`.

## Schema version 2

```json
{
    "schemaVersion": 2,
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
    }
}
```

| Field | Meaning |
|---|---|
| `schemaVersion` | Integer. Missing means version 0. Newer than the app's version: refused. |
| `id` | Random UUID. Presets use fixed ids, `preset-plain-arcticons` and `preset-jade`. Import assigns a fresh one. |
| `name` | Shown on the themes home. |
| `created`, `modified` | Epoch milliseconds. |
| `theme` | The `Theme` from `:render`, field for field. Ranges and meanings are in `render/.../Theme.kt`, `render/.../Layers.kt` and spec 2.3. |

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

`{ "type": "effects", "id" }` is the effects slot of the base stack. It paints
nothing until M6 fills it, except the background border.

`{ "type": "paint", ... }`:

| Field | Values |
|---|---|
| `id` | Non-blank string, unique in the stack, stable across edits and reordering. The base stack uses `background`, `effects`, `icon-fill`, `icon-lines`; new layers should use a UUID. |
| `source` | A paint, below. |
| `clip` | `CANVAS`; `BACKGROUND`: inside the background shape, empty when the background is off (the shape's anti-aliased edge is applied once, by step 5); `ICON`: fill body union lines, `b + l - b l`; `ICON_FILL`, `ICON_LINES`: the fill stage's body and lines alone; `EFFECTS`: the effects' output, empty until M6. |
| `clipInverse` | `true` uses `1 - coverage`. |
| `blend` | `NORMAL`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `DARKEN`, `LIGHTEN`, `COLOR_DODGE`, `COLOR_BURN`, `HARD_LIGHT`, `SOFT_LIGHT`, `DIFFERENCE`, `EXCLUSION`, `HUE`, `SATURATION`, `COLOR`, `LUMINOSITY`, `ADD`. W3C Compositing and Blending Level 1. The platform names: `NORMAL` is `SRC_OVER`, `ADD` is `PLUS`, the rest are equal; all 17 exist in `android.graphics.BlendMode` at compileSdk 37. |
| `opacity` | 0.0 to 1.0. |
| `mapping` | Texture and noise placement; ignored by solid and gradient sources. `mode`: `FIT` (all of the texture shows), `FILL` (covers the canvas, crops), `TILE` (short side spans the canvas, repeats). `scale` 0.25 to 4.0, above 1 zooms in. `rotation` -180.0 to 180.0 degrees about the canvas centre. `lockOffset`: `false` gives each icon its own patch, seeded by its drawable name; `true` gives every icon the centred patch. The per-icon offset: `TILE` translates the texture plane by the offset times the texture size, wrapped; `FIT` and `FILL` move the canvas window within the room between it and the texture, `FILL` only where the texture is larger so the patch never leaves it, `FIT` either way so a smaller texture moves inside the canvas and stays whole. A square texture at scale 1 under `FILL` leaves no room, so the editor starts every new texture layer at `scale` 2.0. Noise shifts its field by the offset. |
| `matte` | `null`, or mask adjustments that turn the source's luminance into alpha: `{ "mode", "black", "white", "threshold", "invert", "feather" }`. `mode` `LEVELS` maps `black` to 0 and `white` to 1 (both 0.0 to 1.0, black below white); `THRESHOLD` gives 1 where luminance reaches `threshold` (0.0 to 1.0). `invert` flips it. `feather` 0.0 to 0.05 of the canvas is a Gaussian blur radius. Luminance is read before any gradient map: `0.3 r + 0.59 g + 0.11 b` of the premultiplied texels for a texture (transparent reads as black), the raw noise value for noise, the colour's for solids and gradients. So a black-and-white texture with single-colour `stops` paints that colour wherever the texture is light. |

Per pixel, coverage = clip x matte x opacity, and the source scaled by the
coverage is blended into the stack.

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
returns them, so an exported file always matches its hash. A theme export (`.zip`, Task 8) carries
`theme.json` and every file in `Theme.textureHashes()`. On load, each file is
checked against its hash (`TextureStore.put(hash, bytes)`); a theme whose
texture is missing does not render.

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

To add version N+1: bump `ThemeDocument.CURRENT_SCHEMA`, append a step to
`STEPS` that rewrites the JSON object from N to N+1, add a version N fixture
and a test, and update this file.

## Storage

- `filesDir/themes/<id>.json`: one per theme. Written to a temp file in the
  same directory, then renamed over the target, so a crash leaves the old or
  the new file, never half of one.
- `filesDir/themes/active`: the active theme's id, plain text, same atomic write.
- `filesDir/themes/seeded`: present once the starter presets were seeded. A
  deleted preset stays deleted.
- `filesDir/textures/<sha256>.png`: one canonical PNG per imported texture,
  written once, atomically. A texture is present when its file is. The
  renderer's store keeps at most 64 MB of decoded textures and files in memory,
  least recently used first out, and loads evicted ones back from here.

## Export and import

Export writes `<name>.zip` with `theme.json`, then `textures/<sha256>.png` for
each hash in `Theme.textureHashes()`, entry times fixed at 1980-01-01, shared
as `application/zip` through the FileProvider. Code: `ThemeZipExport.kt`.

Import takes a `.zip` or a bare theme JSON (`ThemeZipImport.kt`). A zip must
hold `theme.json`; the theme is migrated and validated; each texture it uses
must be in `textures/` and match its hash after `TextureStore.put`
canonicalises it, or already be on the phone. Other entries are ignored and
never read. A bare JSON imports only when its textures are on the phone.
Limits: 64 MB per file, 256 KB for `theme.json`, 24 MB per texture entry,
64 MB decompressed in total. Only `textures/<sha256>.png` entries for hashes
the theme uses are read. The whole archive is validated before any texture or
theme is stored.
Any failure refuses the whole import with a message.

A theme whose texture is missing never reaches the renderer: previews hide
that layer and say so, Apply stops with a message.
