# Theme file format

A theme is a recipe, spec 2.4: the parameters the renderer reads, never images.
One UTF-8 JSON file per theme, `filesDir/themes/<id>.json` on the phone. Export
shares the same JSON. Code: `app/src/main/kotlin/dev/iconoclast/app/theme/`.

## Schema version 1

```json
{
    "schemaVersion": 1,
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
            "paint": { "type": "solid", "color": "#FF000000" },
            "border": null
        },
        "glyph": { "line": "#FF00A86B", "body": "#FF00A86B" }
    }
}
```

| Field | Meaning |
|---|---|
| `schemaVersion` | Integer. Missing means version 0. Newer than the app's version: refused. |
| `id` | Random UUID. Presets use fixed ids, `preset-plain-arcticons` and `preset-jade`. Import assigns a fresh one. |
| `name` | Shown on the themes home. |
| `created`, `modified` | Epoch milliseconds. |
| `theme` | The `Theme` from `:render`, field for field. Ranges and meanings are in `render/.../Theme.kt` and spec 2.3. |

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
- `background.paint.type`: `solid` with `color`; `linear` with `x0`, `y0`, `x1`,
  `y1` and `stops`; `radial` with `cx`, `cy`, `radius` and `stops`. A stop is
  `{ "offset": 0.0, "color": "#FF000000" }`, offsets ascending.
- `background.border`: `null` or `{ "width", "color", "opacity" }`.
- Out-of-range values fail the whole file. Import shows the reason.

No textures yet. M5 adds texture references by content hash and the `.zip`
export, with a schema bump.

## Migration

`migrate(json)` reads any known version and upgrades it one step at a time.
Every load and every import goes through it.

Version 0 is synthetic, never shipped. It exists so the mechanism is tested
before a real bump: no `schemaVersion`, `id` or timestamps, `title` instead of
`name`, `theme.shape.stroke` instead of `strokeWeight`. Fixture:
`app/src/test/resources/themes/v0.json`.

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
