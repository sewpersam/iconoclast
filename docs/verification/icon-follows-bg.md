# Icon follows the background (2026-10-19)

PM ruling under his "use your judgment": shrinking the Background size shrinks the icon with it.

## Change

- Renderer: the icon is laid out on the background shape's square, not the canvas. Icon size, Move left or right and Move up or down are fractions of that square (`IconRenderer.iconFrame`). Background off: the square is the canvas.
- Lines, fill and gaps scale with the icon, as Icon size already did. Effects, grain, patterns and the border keep the canvas's scale, as in bg-size.
- Mask cache keys carry the frame, so one renderer can draw both sizes.
- Schema 11, `MigrationV10ToV11.kt`: a saved theme below 100% gets `iconScale / size` and `offset / size`, so its icon keeps its size and place. Capped at the ranges, which only an icon already wider than its shape, or an offset past 10% of the new square, reaches.
- Help: Icon size "How big the drawing is inside the background shape. 100% reaches the shape's edges. Shrinking the background shrinks the icon with it." Background size: "Its gradient or image shrinks with it, and so does the icon."

## Proof

| Check | Result |
|---|---|
| `./gradlew :render:test` | 167 tests, 0 failures |
| Goldens | Every 100% golden and every 324 px golden byte-identical. The 60 below 100% re-rendered: `outline_silhouette` (92%, 24), `fb_clip_below` (85%, 6), `bg_*` (30) |
| `IconFollowsBackgroundTest` | 4 tests. Lines at 50% of 256 px equal the whole icon at 128 px, offsets too; the default icon loses under 0.5 px to the shape at 100/85/70/50%, four shapes, every fill mode; background off ignores size; one cache keeps sizes apart |
| Same suite, `iconFrame` forced to 1 | 3 of 4 fail |
| `./gradlew :app:testDebugUnitTest` | 153 tests, 0 failures |
| `MigrationV10ToV11Test` | 4 tests. Every 100% or background-off golden migrates unchanged; below 100% the migrated icon mask matches the old one within 0.05 coverage (6 icons, 3 shapes, 85/70/50%, outline and invert); caps; background off untouched |
| Same suite, migration a no-op | 3 of 4 fail |
| ART identity on icondesign36 | `compared 538 PNGs, 0 differ` |

## Pictures

- `icon-follows-bg/icon-follows-bg.png`: before (schema 10 layout, the icon trimmed at 50%) and now, default, gradient and pattern with border, at 100, 85, 70 and 50%. `./gradlew :render:iconFollowsSheet`.
