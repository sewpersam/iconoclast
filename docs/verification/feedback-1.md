# Phone feedback round 1, editor and renderer (2026-10-13)

Branch `fb-editor`, schema 9. Sheets in this folder:

- `feedback.png`: every new golden look, `GoldenSet.FEEDBACK`.
- `strokes.png`: brush strokes Classic beside Painted (Jade, Brush B), All one way variations, outward from a corner.
- `knockout.png`, `knockout-dark.png`: lines cut through to a light and a dark wallpaper.
- `screens.png`: release build on the icondesign36 AVD, every changed page.

Regenerate the sheets: `./gradlew :render:feedbackSheets -Pout=<dir>` (also prints JVM timings).

## Byte identity

- Every golden before this round renders byte for byte: `:render:test` (GoldenTest) and
  `ThemeMigrationTest.version8RendersTheSameAsSchema9`, which writes every golden theme as
  schema 8, migrates it and renders every golden.
- ART: `ArtByteIdentityTest`, 508 PNGs (412 before + 96 new), 0 differ.
- Changed on purpose: none of the old goldens. New: 16 `fb_*` looks x 6 samples.

## Preview latency, release build on the AVD (IconoclastPerf, edit to frame)

| Edit | Hero | Full grid |
|---|---|---|
| Noise pattern, Detail | 34 to 47 ms | 46 to 62 ms |
| Cells pattern, Size | 34 to 47 ms | 46 to 60 ms |
| Waves pattern, Size | 34 to 47 ms | 46 to 50 ms |
| Image Adjust, Warmth | 27 to 37 ms | 31 to 44 ms |
| All one way strokes, Angle | 77 to 90 ms | 119 to 136 ms |
| Jade, fill See-through, no frost (reference) | 98 to 113 ms | 236 to 249 ms |
| Jade, fill Frost on (Glass) | 120 to 137 ms | 277 to 321 ms |

Jade's own 45 Classic strokes dominate its numbers; Frost adds 15 to 70 ms to them.
Cells first measured 220 ms: StrictMath.sqrt is a native call on ART, nine per pixel. They
now compare squared distances, two roots per pixel, same bytes.
