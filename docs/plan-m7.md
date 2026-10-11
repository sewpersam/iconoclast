# Plan: icon-designer milestone M7

Spec: `PROMPT.md`, the binding authority. Read `docs/followups.md` in full:
its "M7 readiness" list is the ordered set of changes this milestone makes
first, and its "M6 residual" is commit 1.

## Global constraints (apply to every task)

- Same as `docs/plan-m6.md` and the earlier plans it points to, in full.
- Overrides never leak into `Theme`: the stored document carries an
  `overrides` map and the renderer receives an effective per-icon Theme.
  Mask cache keys stay keyed by the geometry's own drawable name; the
  per-icon seed name is passed separately.
- Export and import must round-trip with byte-identical render output
  across every golden theme; that equality is the M7 gate.
- The signing key export is behind an explicit warning and never happens
  without a tap on that warning.
- Gate evidence in `docs/verification/m7.md`, screenshots under
  `docs/verification/m7/` from icondesign36.

## Task 11: identity split, overrides model, coverage data, export v2

Implements the M7 readiness items 1 to 7 in docs/followups.md, the M6
residual, spec §2.4 export and import with the key, §2.5 coverage rules,
§6.5 key loss, and the data half of §2.2 screens 3 and 5.

- Commit 1: ZipRewriter writes to `.tmp` and renames.
- Readiness items 1 to 4 in order, each with a test; schema v4; the
  effective-theme resolver with tests for every override field (stroke
  weight, fill mode, gap closing, texture offset, hide streaks, swap
  drawable); PackBuilder per-slot theme and icon.
- Drawable swap search: substring and prefix search over all 15,322 names at this pin (the spec's 14,028 counts distinct appfilter drawables; the swap offers every converted name)
  from the existing index, ranked prefix first; a JVM test for ranking.
- Coverage data: persist the applied component set, theme id and time on
  successful install; a diff on open producing installed-since-last-Apply
  and not-covered lists; "calendar entries fall back" preserved.
- Export v2: zip gains an optional `key/` entry (PKCS12 plus password file)
  only when the caller passes `includeKey = true`; import restores the key
  only when none exists locally and the user confirmed; the whitelist and
  bounds extend accordingly. Round-trip gate test over every golden theme.
- Output resolution: readiness item 7, decide cap or exemption, goldens at
  each offered size.
- Docs: theme-format.md v4 and the export v2 layout.

## Task 12: override sheet, coverage screen, settings and about

Implements spec §2.2 screens 3, 5 and 6, §6.5 UI, §6.8, and the M7 gate
row of §5.

- Tap an icon in the editor preview opens the per-icon override sheet with
  every override field, the swap search with live results, and "reset to
  theme". Overridden icons show a badge in the preview.
- Coverage screen: list with status themed / overridden / not in Arcticons
  (with a link to the Arcticons icon request process), the
  installed-since-last-Apply section with a re-apply call to action, and a
  badge on home when that list is non-empty.
- Settings / About: Arcticons credit and licence, app licence, output
  resolution setting, "Export signing key" behind a warning dialog (inside
  the theme export flow as the spec says), open source licences list
  generated per readiness item 8 and committed.
- Gate: emulator screenshots of the override sheet before and after a swap,
  the preview badge, an applied pack showing the overridden icon in
  Lawnchair, the coverage screen with all three statuses and the new-apps
  section after installing one more launcher APK, the About screen, the
  key export warning. Round-trip export with key then import on a cleared
  install, Apply, and the pack updates without a signature mismatch.
