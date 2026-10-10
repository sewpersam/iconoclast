# Plan: icon-designer milestone M4

Spec: `PROMPT.md` at the repo root, the binding authority. Read
`docs/followups.md` too: it carries the deferred items, the rulings already
made, and the risks this milestone must own.

## Global constraints (apply to every task)

- Same as `docs/plan-m0-m3.md` global constraints, in full: repo root,
  F-Droid clean, licences, placeholder name and ids defined once, minSdk 33,
  JDK 21, headless only, throttle, verify before claiming, small atomic
  commits on `main` with the Fable trailer, Kotlin and Compose, no
  speculative abstractions.
- The renderer stays pure Kotlin and byte-deterministic. Goldens are
  compared as bytes. Any renderer change re-runs `:render:test` and, if the
  glyph path changed, `verifyIconConversion`.
- Serialization: kotlinx.serialization JSON, Apache-2.0, from Maven Central.
  Look up the current stable version and record where.
- The Apply flow from M3 stays working at every commit, now applying the
  active theme instead of the two fixed buttons.
- Accessibility per spec §6.8: content descriptions on preview icons,
  sliders usable with TalkBack.
- Gate evidence goes in `docs/verification/m4.md` with screenshot paths
  under `docs/verification/m4/`. Screenshots are taken on `icondesign36`.

## Task 5: theme model, storage and the themes home

Implements spec §2.2 screen 1, §2.4 (themes as recipes, versioned JSON;
export and import of theme JSON without textures, since textures arrive in
M5), and the M4 storage half of §5.

- A versioned theme document: `schemaVersion`, name, id, created and
  modified timestamps, and the full `Theme` from `:render`. Serializers for
  the sealed `Paint` and `BackgroundShape` types. Floats serialize so that
  `-0f` and `0f` round-trip identically. Document the JSON in
  `docs/theme-format.md`.
- Migration: a `migrate(json): ThemeDocument` entry point that upgrades older
  schema versions. Seed it with a synthetic version 0 fixture so the
  mechanism is tested now, per the spec's required "theme JSON migration"
  unit test. Round trip test: serialize, parse, render one icon, bytes equal.
- Storage: one JSON file per theme under `filesDir/themes/<id>.json`, atomic
  writes, plus `active` pointer. A repository exposing a flow of themes and
  the active id. One app-scoped `IconSource` instance, hoisted out of the
  view model (followups: IconSource heap read).
- Starter presets seeded on first launch: "Plain Arcticons" and "Jade"
  (invert fill, jade solid colour as a stand-in until M5's texture, black
  rounded square). A third preset is deferred to M6 when effects exist.
  Presets are data in `app/src/main/assets/presets/*.json`, so they go
  through the same parser.
- Themes home screen per §2.2: grid of theme cards, each showing nine live
  preview icons rendered from a fixed sample set at 64 px, the active theme
  marked. Actions: new, duplicate, rename, delete with confirm, export (share
  sheet with the JSON), import (document picker, validated, migrated), set
  active. Tapping a card opens the editor route, which Task 6 fills; for now
  it opens a placeholder screen with the theme name.
- Navigation: a single-activity Compose app with navigation-compose, routes
  home, editor/{id}, apply. The Apply screen applies the active theme.
- Tests: serialization round trip, migration from the fixture, repository
  CRUD on a temp dir, preset files parse and render.
- Gate: screenshots of the home with the two presets, a renamed duplicate,
  the delete confirm, and an imported theme. Apply with the active theme
  still installs on icondesign36 in one launcher, one screenshot.

## Task 6: the editor

Implements spec §2.2 screen 2 for the Shape, Fill and Background tabs, the
live preview, undo and redo, the compare control, and the M4 gate row.

- Editor screen: preview grid on top, tabbed panel below with Shape, Fill,
  Background. Layers and Effects tabs exist as disabled placeholders so the
  tab row does not shift later.
- Preview contents: installed apps that Arcticons covers, from the coverage
  matcher, with a toggle to the fixed sample set of about 24 varied icons
  (dense, sparse, text-heavy, dotted, open-line). Tapping an icon is a
  no-op this milestone; the override sheet is M7.
- Every control updates the preview while dragging. Each slider shows its
  numeric value and has a reset to default. Shape, Fill and Background
  params per spec §2.3 with the exact ranges. Miter limit only shown for
  miter joins. Gap width and closing only in invert mode. Background paint
  offers solid, linear and radial gradient; the texture option is M5.
- Rendering strategy: a preview renderer on a background dispatcher with
  the mask cache; while a slider is moving, render at 64 px and coalesce
  requests so at most one render per frame is in flight; on release render
  at 128 px. Decide and document whether previews re-rasterise at preview
  size or downscale the 432 px output (followups: preview parity). The
  rule is that what the preview shows at rest must be the applied look.
- Fix before the gap closing slider ships: exterior pockets counted as
  enclosed when closing > gap (followups, FillModes.kt). Add a golden.
- Split `FillKey` so changing fallback threshold or keep-lone-lines does
  not recompute the geometry masks (followups).
- Undo and redo across the session: a bounded history of theme snapshots,
  coalescing a slider drag into one step on release.
- Compare: press and hold a button to show plain Arcticons in the preview.
- Autosave the theme through the repository on every committed change.
- Gate per §5 M4: measure on icondesign36 and record in
  `docs/verification/m4.md`: frames per second during a continuous
  stroke-weight drag over 3 seconds, measured through a frame-timing
  callback or `dumpsys gfxinfo`, and the preview render time for 24 icons
  at 128 px cold and warm. Budgets: 30 fps or better, 150 ms. Screenshots of
  each tab, of the compare hold, and of an undo.
