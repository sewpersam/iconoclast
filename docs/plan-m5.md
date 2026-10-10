# Plan: icon-designer milestone M5

Spec: `PROMPT.md`, the binding authority. Read `docs/followups.md` in full:
its "M5 readiness" list is the ordered set of changes this milestone makes
first, before any layer feature.

## Global constraints (apply to every task)

- Same as `docs/plan-m4.md` and `docs/plan-m0-m3.md` global constraints, in
  full.
- The renderer stays pure Kotlin and byte-deterministic. No android.graphics
  in `:render`. Blend modes are implemented in Kotlin from the W3C
  compositing formulas; `android.graphics.BlendMode` names appear only in
  UI mapping tables. Per-pixel math uses StrictMath or plain arithmetic.
- Every golden that changes is named with the reason. ART byte identity is
  re-run on icondesign36 whenever goldens change.
- Textures never enter the mask cache. They live in their own store keyed by
  SHA-256 of the canonical PNG.
- Gate evidence goes in `docs/verification/m5.md`, screenshots under
  `docs/verification/m5/` from icondesign36.

## Task 7: layer engine in the renderer and the theme document

Implements spec §2.3 "Layers" (the whole block), the §3.3 step 4 slot, the
§2.4 texture reference by content hash, and all ten "M5 readiness" items in
docs/followups.md.

- Readiness items 1 to 3, 8, 9 and 10 first, each its own commit with its
  test. Goldens must not change for items 1 to 3 and 8 to 10; item 9 may
  change goldens only where sealing was non-monotone, named.
- Layer model in `:render` Theme: an ordered `layers` list. Each layer:
  source (solid, linear gradient, radial gradient, texture by hash,
  procedural noise with fBm, ridged and grain variants, seeded), clip target
  (icon, icon fill, icon lines, background, effect output placeholder,
  inverse of each, whole canvas), blend mode (the 17 in the spec table),
  opacity, texture mapping (fit, fill, tile; scale; rotation; per-icon
  offset seeded by the drawable name hash; lock), mask adjustments (levels
  or threshold on luminance, feather radius, invert). Exact ranges you
  choose are documented in `docs/theme-format.md` and reflected in the UI
  in Task 8.
- The base stack `background -> effects placeholder -> icon` is expressed as
  layers. Plain and Jade are re-expressed as layer stacks and must render
  byte-identically to their M4 goldens; that equality is the migration
  test. Schema v2 with a pure v1ToV2 step.
- Compositor becomes a stack evaluator per followups item 5. The effects
  position is a marker layer that M6 fills; it renders nothing now.
- Texture store in `:render`: canonical PNG in, decoded through a
  pure-Kotlin inflate, sampled with bilinear filtering under the mapping
  params. Downscale-on-import is the Android side's job in Task 8; the
  store asserts the longest side is at most 2048.
- Tests: every blend mode against the W3C formula on fixed pixel pairs,
  including the four non-separable modes; noise determinism by seed;
  texture mapping offset determinism by name; migration equality; goldens
  for three layer looks built from a bundled black-and-white test texture
  (ship a small one under test resources with a NOTICE saying it is
  original).
- Performance: re-run the JVM benchmark; the layered Jade must stay within
  2x of the M4 Jade render time, reported in m5.md.

## Task 8: Layers tab, texture import, zip export and import

Implements spec §2.2 Layers tab, §2.4 import and export with textures, and
the M5 gate row of §5.

- Layers tab in the editor: ordered list with add, remove, reorder (drag
  handle or up/down buttons, your call, native Material components), per
  layer the source, clip, blend, opacity, mapping and mask controls from
  Task 7's model. Enable the tab.
- Texture import through the system photo picker, with the SAF document
  picker as fallback; copy into app storage, downscale to a longest side of
  2048, encode with the project encoder, store by SHA-256; no storage
  permission.
- Background paint gains "texture layer" per spec §2.3.
- Export becomes a `.zip` with `theme.json` and the textures it uses; import
  validates, migrates and stores textures by hash. Replace the M4 JSON-only
  export. Round-trip test: export, import, render, bytes equal.
- Jade preset: still solid jade in this milestone; the procedural jade and
  streaks are M6. Do not author the preset look.
- Gate per §5 M5: a black-and-white base texture plus blend modes reproduces
  at least three visibly distinct looks on the emulator, screenshots of
  each in the editor and one applied pack in Lawnchair. Also a screenshot
  of the import picker flow and of an exported zip re-imported.
