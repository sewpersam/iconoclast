# Plan: icon-designer milestone M6

Spec: `PROMPT.md`, the binding authority. Read `docs/followups.md` in full:
its "M6 readiness" list is the ordered set of changes this milestone makes
first, before any effect feature.

## Global constraints (apply to every task)

- Same as `docs/plan-m5.md`, `docs/plan-m4.md` and `docs/plan-m0-m3.md`
  global constraints, in full.
- Effects are pure Kotlin in `:render`, byte-deterministic, StrictMath or
  plain arithmetic per pixel. Cached masks are read-only; effects copy
  before writing.
- Every changed golden is named with the reason; ART byte identity re-runs
  on icondesign36 whenever goldens change.
- The phone budget stands: Apply of 300 icons at 432 px under 20 s on
  icondesign36, measured by PhoneBudgetTest, now with the Jade preset as
  built in this milestone (texture, streaks, bevel if used).
- The Jade preset look is his taste call. The agent builds it to the spec
  description; the PM sends him the screenshot; nothing downstream waits on
  it except the preset's final values.
- Gate evidence in `docs/verification/m6.md`, screenshots under
  `docs/verification/m6/`.

## Task 9: effects engine

Implements spec §2.3 "Effects" (the whole block), the M6 readiness items
in docs/followups.md, and the PNG encode budget.

- Readiness items 1 to 10 first, in order, each its own commit with a test.
  Byte-neutral items leave goldens unchanged; say which items changed which
  goldens.
- PNG encode speed: textured icons cost about 190 ms each in the own Deflate
  on the JVM, which breaks the 20 s Apply budget. Make the encoder fast
  enough: hash-chain LZ77 with a bounded chain length and lazy matching off,
  or a documented lower-effort mode for all output. It must stay
  byte-deterministic across JVM and ART; every golden regenerates once, all
  named as "encoder change", ART identity re-run. Record before and after
  per-icon encode times for solid and textured icons.
- Effects per spec, each as an EffectLayer with on/off, stack position,
  blend mode, opacity:
  - Streaks: smear the icon mask along an angle 0 to 360, length 0 to 60% of
    canvas, decay curve (define the allowed set), breakup multiplied by a
    stretched brush or noise texture, built-in brushes bundled by hash,
    density and contrast; paint taken from a linked texture layer by id.
  - Bevel/emboss from a signed sub-pixel distance field: light angle,
    depth, size, softness, highlight and shadow colours with their own
    blend modes.
  - Drop shadow: angle, distance, blur, colour, opacity.
  - Inner shadow. Outer glow. Inner glow.
  - Grain: decide per readiness item 10 and document.
- Goldens: one set per effect on four icons, plus one combined "jade-like"
  set. Determinism test per effect.
- Benchmark rows for each effect and for the combined look; the combined
  look's Apply projection must fit the phone budget before Task 10 runs it
  on the device.
- Document every effect's parameters and ranges in docs/theme-format.md
  (schema v3).

## Task 10: Effects tab, presets, taste gate

Implements spec §2.2 Effects tab, §2.4 starter presets, and the M6 gate row
of §5.

- Effects tab: enable it; list of effects with add, remove, reorder, on/off,
  per-effect controls from Task 9's model, reusing the M4 controls.
- Jade preset authored to the spec description: black rounded square,
  procedural jade texture (fBm or ridged noise through a jade gradient
  map, with veins), stroke weight thicker than 1 (pick in the 1.5 to 2.0
  range), invert fill, low-opacity textured streaks between icon and
  background, and nothing else unless the look needs it. Save it as the
  preset JSON.
- Third preset: one more that shows the range of the tool; the agent picks
  and names it, the user approves or rejects.
- Plain Arcticons preset unchanged.
- Gate per §5 M6: emulator screenshots of the Jade preset in the editor on
  the sample set and on installed apps, applied in Lawnchair at full size
  with a cropped close-up, plus the same for the third preset. PhoneBudgetTest
  re-run with the Jade preset; numbers in m6.md.
- The PM sends him the screenshots. His answer sets the preset's final
  values; record them when they arrive.
