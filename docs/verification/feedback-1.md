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

## Speed pass (PM review)

Release build on the AVD (4 cores, 2 preview threads), edit to frame, IconoclastPerf. "Grid"
is every cell on screen; the margin cells around them follow and are skipped when a newer
edit is waiting.

| Jade edit | Before: hero / grid | After: hero / grid |
|---|---|---|
| Fill See-through, no Frost | 98 to 113 / 236 to 249 ms | 29 to 33 / 30 to 46 ms |
| Fill Frost | 120 to 137 / 277 to 321 ms | 32 to 50 / 46 to 77 ms |
| Fill See-through, Frost on | 117 to 123 / 301 to 306 ms | 34 to 53 / 57 to 68 ms |
| Brush stroke Length (strokes redrawn) | 122 to 168 / 289 to 385 ms | 89 to 109 / 172 to 226 ms |

What changed, pack bytes untouched (GoldenTest, ArtByteIdentityTest 508/508):
- Streaks and brush strokes are cached at preview sizes under everything they read
  (`EffectOutputKey`: icon mask, effect, blend, offset, seed, linked source and mapping), so
  editing any other layer reuses them. Pack-size renders are never cached.
- The preview draws the cells on screen first and shows them, then the margin.
- The stroke raster skips pixels beyond a segment's widest edge before taking a root, and
  takes the segment's length root once: the same values, far fewer native StrictMath calls on ART.

Editing a stroke's own settings still redraws every stroke: about 200 ms for the grid on the
AVD, with a 0.7 sigma distance blur (5 Gaussian passes) and the raster as the cost
(`:render:strokeProfile -Pjfr=<file>`). A phone with 8 cores runs 4 preview threads, not 2.
