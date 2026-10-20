# Follow-ups carried out of M0-M3

Extracted from the execution ledger on 2026-10-10. Triage from the whole-branch review: items marked FIX NOW were fixed in the final wave; everything else can wait for the milestone named.

## Deferred minors
- Task 1: review round 0: 2 Important (wrapper jar; m0.md misquotes KGP table), 2 Minor (namespace scheme; LICENSE provenance). Ruling: wrapper jar finding parked, constraint amended in plan commit — F-Droid verifies wrapper checksums and builds with its own gradle — cost if wrong: remove jar later. Fix round 1 dispatched for finding 2 + namespace minor.
- Task 2: minor (deferred): verify task registered in :render reaches into :iconsource via evaluationDependsOn + reflective outputDir; cleaner to register in :iconsource or the plugin.
- Task 2: minor (deferred): verify task declares only the report as output; inkscape cache and diff dir undeclared, inkscape version not an input.
- Task 2: minor (deferred): diff PNGs threshold at alpha 128 while gate is soft.
- Task 2: minor (deferred): non-uniform stroke scaling (geometric mean) and isConformal untested; 0 occurrences at this pin.
- Task 2: minor (deferred): !important silently stripped; none at this pin.
- Task 2: minor (deferred): partial opacity dropped; unrecoverable without format bump. Carry to Task 3 brief.
- Task 2: minor (deferred): IconSource.open heap-reads ~9 MB eagerly.
- Task 2: minor (deferred): fail-the-build path on a broken SVG untested end to end; a fixture-dir test of ConvertIconsTask would cover it.
- Task 3: minor (deferred): dot gap scales with stroke weight (GlyphRasterizer.kt:45,52), use spec.transform.scale — real small bug.
- Task 3: minor (deferred): goldens compare pixels not bytes; add byte compare.
- Task 3: minor (deferred): m2.md 1x evidence from pre-fix states; re-run or reword.
- Task 3: minor (deferred): fill cache recomputes on fallbackThreshold/keepLoneLines; outline-mode double-counts bytes. DONE in M4 Task 6, 21b60f5: FillKey split, LoneLinesKey, geometry counted once.
- Task 3: minor (deferred): exterior pockets count as enclosed when closing > gap (FillModes.kt:117). DONE in M4 Task 6, 1b705fb (exterior pockets stay outside) and cb7c192 (sealing monotone in the closing).
- Task 3: minor (deferred): unsynchronized MaskCache counters; two-point closed subpaths stroked open; short antenna stubs.
- Task 4: minor (deferred): install session has no timeout or cancel path; UI could stay Working.
- Task 4: minor (deferred): progress count can go backwards briefly (4 threads).
- Task 4: minor (deferred): m3.md lacks screenshots for the Neo stub failure and restart-alone/force-stop failures; pack-activity shot does not distinguish launch from foreground.
- Task 4: minor (deferred): §6.6 apply-intent research incomplete (Neo runtime receiver, Candybar-style intents).
- Task 4: minor (deferred): PackInstallSession.result int-to-result mapping untested on JVM.

## Rulings made on his behalf
- Ruling: working name "Icon Designer", ids dev.icondesigner.app / .pack — user owns the real name — cost if wrong: one rename commit.
- Ruling: spike heavy binaries gitignored, kept on disk — history stays small — cost if wrong: re-add later.
- Ruling: tasks run sequentially, M1 before M2, since M2 reads M1's bundle format — cost: wall clock.
- Ruling: work lands directly on main; single-lineage repo with no peers on it — cost if wrong: none, history is linear.
- Ruling: gradle-wrapper.jar stays checked in — F-Droid builds accept the wrapper, checksum recorded — cost if wrong: swap to system gradle later.
- Ruling: compileSdk/targetSdk 37 accepted, since current Compose BOM needs it and platform 37 is released — cost if wrong: one-line downgrade.
- Ruling: Temurin 21 via gradle-daemon-jvm.properties accepted, java-21-openjdk dir on this box is empty — cost: none.
- Task 1: review round 0: 2 Important (wrapper jar; m0.md misquotes KGP table), 2 Minor (namespace scheme; LICENSE provenance). Ruling: wrapper jar finding parked, constraint amended in plan commit — F-Droid verifies wrapper checksums and builds with its own gradle — cost if wrong: remove jar later. Fix round 1 dispatched for finding 2 + namespace minor.
- Ruling: soft IoU (min/max alpha, 384px box-filtered to 96px) accepted as the gate metric — thresholded IoU measures inkscape's own AA, not the converter — cost if wrong: thin-stroke converter errors hidden; reviewer asked to judge.
- Ruling: Opus trailer on Task 2 commits stays; no history rewrite — cost: none.
- Ruling: opacity dropped at conversion stands — the restyle supplies its own paint and 26 shapes are affected — cost if wrong: format version bump later.
- Ruling: mutation (c) surviving the gate is accepted — the error is 1.5% of radius on 291 arcs, below 96px IoU resolution, and the unit test covers it — cost if wrong: none visible at icon scale.
- Ruling: production rasterizer is pure Kotlin scanline in :render main, no awt, no android.graphics — same bytes on JVM and phone is a §3.3 requirement and goldens must match the device — cost if wrong: performance budget miss, implementer told to measure and report BLOCKED rather than switch.
- Ruling: own minimal PNG encoder for byte identity across platforms — cost if wrong: a few hundred lines to delete.
- Task 4: dispatched implementer ae33e800e7df5f453 (opus). Ruling: EC P-256 key; template built by Gradle task following spike/pack.py; minimal two-button Apply screen — cost if wrong: small.
- Ruling: res/xml/appfilter.xml stub dropped — it can never carry real content and an empty stub made Neo show stock icons; spec §3.4 line superseded — cost if wrong: a launcher that reads only res/xml gets nothing, which it would anyway.
- Ruling: Neo guidance "restart then toggle System icons" ships on two screenshot-backed successes — cost if wrong: one extra tap for Neo users.
- Ruling: cold-preview miss (164-194 ms, emulator, debuggable) is accepted at M3 and owned by M4's slider-fps gate — a JIT warm-up on first render is not a renderer defect — cost if wrong: one visible stutter on editor open.

## Risks for M4 to M8 (from the whole-branch review)
- Cached masks are shared mutable FloatArrays; any layer or effect that writes in place corrupts the cache. Make read-only explicit or copy at the layer boundary.
- MaskCache key is an Int drawable index; per-icon overrides and custom glyphs need a non-colliding key space.
- Theme has no serialisation, version field or stable ids yet; M7 export needs a deliberate format and -0f vs 0f affects data-class keys.
- Previews at 128 px re-rasterise geometry rather than downscale the 432 px output; thin looks may differ. Decide before the M4 fps gate.
- GlyphPaint is the M2 stand-in for pipeline step 4; M5 replaces it and churns every golden and the ART identity set.
- Pack versionCode is fixed at 1; a template change can never signal a schema change.
- setRequestUpdateOwnership ties pack updates to this designer install; key export in M7 must cover ownership loss.
- Cold first preview render misses the 150 ms budget (164-194 ms on emulator); owned by the M4 slider-fps gate.
- M8 reproducibility assumes the same aapt2 build-tools 36.0.0 on every builder.
- evaluationDependsOn plus reflective property reads in app, pack and render build files will break under configuration cache or isolated projects.

## M4 additions (2026-10-10)

### Deferred minors from M4
- Task 5: minor (deferred): directory not fsynced after atomic rename; stale .tmp files never cleaned.
- Task 5: minor (deferred): export cache dir keeps one stale JSON.
- Task 5: minor (deferred): nine image descriptions per card is noisy in TalkBack; one description per card.
- Task 5: minor (deferred): rename/delete dialog state uses remember, lost on rotation.
- Task 5: minor (deferred): -0f in-memory theme misses the preview cache once.
- Task 5: minor (deferred): exportJson and active() unused in app code.
- Task 5: minor (deferred): stale NavController lambda in create(onCreated) after activity recreation.
- Task 6: minor (deferred): outline fill cache entries count 0 bytes but retain the geometry array; bound is loose.
- Task 6: minor (deferred): reset glyph text may be spoken alongside its contentDescription; use clearAndSetSemantics.
- Task 6: minor (deferred): EditorViewModel.SAMPLES duplicates GoldenSet.SAMPLES; RgbaImage-to-Bitmap conversion exists twice.
- Task 6: minor (deferred): warm-up render costs ~400 ms CPU on every process start including UI-less starts.
- Task 6: minor (deferred): TalkBack never actually exercised.

### Rulings made in M4
- Ruling: M4 split into storage+home (T5) then editor (T6) — cost: none.
- Ruling: Jade preset uses a solid jade colour until M5 textures exist — cost: preset re-authored at M6 anyway.
- Ruling: third preset deferred to M6 — cost: none.
- Ruling: name is Iconoclast (his pick); ids dev.iconoclast.app and dev.iconoclast.app.pack, Kotlin packages dev.iconoclast.* — full rename is Task 5 commit 1 — cost if wrong: one more rename.
- Ruling: no preview renders below 128 px; the 64 px plan number is withdrawn — the fill decision depends on raster resolution, so a smaller preview lies — cost if wrong: preview memory and drag cost, Task 6 measures it.
- Ruling: spec 30 fps budget is the UI frame rate during drag (60 fps, pass); preview update rate is its own row with a 10 Hz floor on emulator debug build (14 Hz, pass) — cost if wrong: a real phone is faster, not slower.
- Ruling: gap closing is shown in both silhouette modes too, amending the brief — the renderer reads it there — cost: none.
- Ruling: cold preview miss (212-227 ms) accepted as first-run cost, same as M3; warm matches M3 — cost if wrong: one stutter on editor open.

### Fixed since the M3 list
- Dot gap scaling: DONE in M3 final wave, f6c6411.
- Golden byte compare: DONE in M3 final wave, b5d3832.
- m2.md 1x wording: DONE in M3 final wave, 1fbf724.
- MaskCache counters now increment under the lock; only test reads are unsynchronised.

### M5 readiness, from the M4 whole-milestone review (do these first, in order)

All ten done in M5; confirmed by the M5 whole-milestone review.

1. Masks become read-only at the cache boundary; cache the fill core without the geometry reference and re-attach on read (also fixes the loose 64 MB bound). DONE in M5 Task 7, 0a01bd5.
2. The renderer gets a stable icon identity: render takes the drawable name or a name-derived seed; offset-dependent layer keys use it. DONE in M5 Task 7, db079f1.
3. Effect keys include the fallback: (FillKey, fellBack, keepLoneLines), following the LoneLinesKey pattern. DONE in M5 Task 7, 0e1eeba.
4. Textures stay out of the mask cache: own store keyed by SHA-256, canonical PNG from the project encoder, pure-Kotlin inflate in :render. DONE in M5 Task 7, e1937c6, 4b79a1f.
5. Compositor becomes a stack evaluator: source RGBA, clip mask, mask adjustments, blend(mode, opacity); Paint gains Texture and Noise; all 17 blend modes in Kotlin with W3C formulas, android.graphics only for UI names. DONE in M5 Task 7, 60aaa55, d6ee247.
6. Define the "icon" clip target as body union lines, with sub-targets or two default layers so Plain and Jade reproduce. DONE in M5 Task 7, d6ee247.
7. Schema v2: bump CURRENT_SCHEMA, append a pure v1ToV2 step mapping glyph and background.paint into the base stack. DONE in M5 Task 7, d6ee247.
8. StrictMath or plain arithmetic on every per-pixel path; seeded PRNG for noise. DONE in M5 Task 7, 52731e4.
9. Sealing is not strictly monotone below one step: step over the distinct strokeD2 values <= r² instead of ceil(r) samples. DONE in M5 Task 7, 2ef0b37.
10. Declare lifecycle-viewmodel-compose and lifecycle-runtime-compose in the version catalog. DONE in M5 Task 7, 01acd7b.

## M5 additions (2026-10-10)

### Fixed from the M5 whole-milestone review

Texture reload hashes the file without a re-encode, one load at a time;
home preview survives a texture lost mid-render; Effects clip target and
inverse clip on the whole canvas hidden; noise offset period 256; a
layer switched to a texture starts at scale 2; texture presence cached;
golden set covers all 17 blend modes; textured benchmark row; zip and hash
guarantees documented. Details: `.superpowers/sdd/plan-m5/final-fix-report.md`.

### Deferred from M5

- PNG encode of a textured 432 px icon takes about 190 ms in the pure-Kotlin
  `Deflate` (benchmark `JADE_TEXTURED`: render + PNG 7.4x solid Jade, 4
  threads 7.2x). Rendering itself is 1.66x. Owned by M6 before textured Apply
  is gated: faster match search or a lower effort level, bytes still pinned.
  DONE in M6 Task 9, 7d0f85d: 187.1 to 17.1 ms.

### M6 readiness, from the M5 whole-milestone review (do these first, in order)
1. Each effect its own Layer subtype EffectLayer(id, effect, enabled, blend, opacity), marker kept as border anchor and default insertion point, schema v3 with pure v2→v3. DONE in M6 Task 9, dbf900c.
2. Define EFFECTS as a clip target before re-enabling it. DONE in M6 Task 9, 74d68ae.
3. EffectKey gains a kind, typed MaskCache method, allow a non-coverage float Field. DONE in M6 Task 9, ff6fb77.
4. Streaks and bevel read the icon mask (body union lines), not masks.body. DONE in M6 Task 9, c012dcb.
5. textureHashes() covers effect textures and linked layers, built-in brushes bundled by hash and always present. DONE in M6 Task 9, 406cf6d.
6. Expose layer source evaluation under mapping and per-icon offset, unclipped, for streak paint. DONE in M6 Task 9, bb115f7.
7. Signed sub-pixel EDT for bevel, validated on the binary build first. DONE in M6 Task 9, 62306a2, 15fb2d0.
8. Shared separable Gaussian, box passes or EDT falloff for large radii. DONE in M6 Task 9, 569b61a.
9. Streak smear via axis rotation plus IIR or prefix sums, decide allowed decay curves. DONE in M6 Task 9, 164d8e2.
10. Decide whether Grain is a preset noise layer or a real effect, and fix grain cell size to canvas-relative so previews match Apply. DONE in M6 Task 9, f003f02.

### Deferred minors from M5
- Task 7: minor (deferred): one-pass union-find + sampled-function EDT formulation of sealing would remove ~50 labellings at closing 2.
- Task 7: minor (deferred): noise offset reaches 4096 cells; float ulp stair-steps the top octave; period ~256 fixes it. DONE in the M5 fix wave: period 256.
- Task 7: minor (deferred): sealing monotone test exempts pixels within sqrt2 px of a stroke; enclosed can still lose near-stroke pixels via pocket classification.
- Task 7: minor (deferred): Mask read-only is type-enforced outside :render, convention inside.
- Task 7: minor (deferred): readiness item 10 (lifecycle deps declared) has no test.
- Task 8: minor (deferred): texture cleanup policy (mark-and-sweep at cold start when unreadable is empty); not in spec.
- Task 8: minor (deferred): ThemeTransfer.read loads up to 64 MB before unzip; stream instead. DONE in the M6 review fix wave, 7d64143.
- Task 8: minor (deferred): inverse clip on Whole canvas yields an empty layer; disable the switch for CANVAS. DONE in the M5 fix wave: the switch is hidden for the whole canvas.
- Task 8: minor (deferred): removing base Background or Icon lines layer is allowed; taste call on a guard.
- Task 8: minor (deferred): ColorControl open state kept by position; two info logs remain.
- Task 8: minor (deferred): threshold matte, grain and feather never driven on device.
- Residual from the M5 fix wave: TextureStore.entry() clears onDisk only when the file is missing, not when it is corrupt or undecodable; and the Mapping.kt noise KDoc says 16 cells where 32 is used. Both carried into M6 Task 9 commit 1. DONE in M6 Task 9, ec3d723.
- Textured Apply: PNG encode is about 190 ms per icon in the own Deflate on the JVM, 7.4x the solid case; breaks the 20 s phone budget. M6 Task 9 fixes the encoder. DONE in M6 Task 9, 7d0f85d: 187.1 to 17.1 ms.

### Rulings made in M5
- Ruling: M5 split engine (T7) then UI+import+zip (T8) — cost: none.
- Ruling: effects position is a marker layer rendering nothing until M6 — cost: none.
- Ruling: one texture offset per icon, shared by all layers — literal spec, keeps colour and matte registered — cost: none.
- Ruling: missing texture throws in :render; Task 8 checks theme.textureHashes() against the store before preview and pack build — cost: none.
- Ruling: per-icon offset — TILE wraps; FIT/FILL move within room; default texture scale 2.0 for new texture layers so the default has room; lock pins — spec says every icon gets a different patch — cost if wrong: one default and a golden regen.

## M6 additions (2026-10-11)

### Fixed from the M6 whole-milestone review

Apply streams the pack: icons to cache files, the zip rewrite and the
signer file to file; theme import streams the content URI; new theme
id through a channel; one sample list and one bitmap conversion;
PngEncoder effort required; ART identity covers the built-in brushes;
preview noise fields cached, Jade drag 9.3 to 13 Hz. Details:
`.superpowers/sdd/plan-m6/final-fix-report.md`.

### Deferred minors from M6
- Blurred masks land slightly outside [0, 1]; paintEffect clamps, the masks themselves do not.
- No matcher test at distance 32767.
- Cold Apply of JADE_LIKE measured 21.9 s, over 20 s; logged, not gated, as in M3 to M5. A precedent to watch.
- A missing imported breakup texture shows a blank "Imported" swatch.
- Grain size is labelled px.
- EffectRow duplicates LayerRow.
- The EFFECTS clip target has no UI cue.
- The built-in brush set is not forward compatible: freeze it, or bump the schema when adding a brush.
- The streak fallback colour shows when the linked layer is hidden for a missing texture.

### Rulings made in M6
- Ruling: preset-id rule (a changed look ships under a new id) applies from the first public release; before that, preset-jade keeps its id.
- Ruling: grain is not capped. The grain row states the measured cost: about 330 KB per icon at full amount.
- Ruling: taste verdict received 2026-10-11. Jade and Neon defaults approved as shipped ("Love the defaults"); no value changes.

### M7 readiness, from the M6 whole-milestone review (do these first, in order)
1. Split icon identity in the renderer: key geometry, fill and effects by the geometry's own drawable name and pass the seed name (the slot) separately, so a swap cannot poison the shared MaskCache. DONE in M7 Task 11, fd123fa.
2. One per-icon offset in StackInput replacing the four derivations (Mappings.texture, Mappings.noise, Streaks breakup, Grain), decide whether grain follows the override. DONE in M7 Task 11, ae7f686: grain follows it.
3. Schema v4 with an `overrides` map keyed by appfilter drawable name, pure v3→v4 adding an empty map, overrides resolved to an effective Theme per icon so Theme stays override-free and cache keys invalidate naturally, "hide streaks" drops Streaks EffectLayers, overrides whose name left the pin are kept and flagged. DONE in M7 Task 11, 5c092dd.
4. PackBuilder takes a per-slot theme and icon, overrides per drawable, one render per drawable. DONE in M7 Task 11, 06aafdf.
5. Coverage screen persists the applied component set and theme id on successful install and diffs on open. Data DONE in M7 Task 11, d91431b; screen DONE in M7 Task 12.
6. Export and import add the key entry (PKCS12 plus password) behind the warning, gate is render every golden theme from the imported document and compare bytes. DONE in M7 Task 11, fa4c81e, 587763f; the warning dialog DONE in M7 Task 12.
7. Output resolution setting capped or exempted from the budget with goldens at each offered size, grain cells are 1/432 so grain washes out below 432. DONE in M7 Task 11, fdf67c3: 432 and 324 only, grain cells follow the size, budget stated at 432; the setting UI DONE in M7 Task 12.
8. Settings/About licence list generated from `:app:dependencies --configuration releaseRuntimeClasspath`, committed, and THIRD_PARTY_LICENSES.md extended to every runtime dependency with its licence. DONE in M7 Task 12, 9708eef: `:app:writeDependencyLicenses` and `checkDependencyLicenses` under `check`.

### M6 residual and rulings (ledger, 2026-10-11)
- Residual: ZipRewriter.rewrite streams straight to the final output path; a failure leaves a truncated file. PackSigner uses tmp+rename. Carried into M7 Task 11 commit 1: write to .tmp and rename. DONE in M7 Task 11, 48a4609.
- Ruling: M5 fix-wave residuals (corrupt-file onDisk, KDoc typo) folded into T9 commit 1 instead of a second fix wave — cost: none.
- Ruling: streak smear uses a hold model (max-decay) instead of normalised integration — his reference is a brush streak and thin lines must streak — cost if wrong: goldens regen once more.
- Ruling: streaks link to icon-fill; no hidden source layer — the model already reads the linked source regardless of clip/opacity — cost: none.
- Ruling: preset seeding by seeded-id set; never overwrite, never resurrect; a changed look ships under a new id — cost: none.
- Ruling: preset-id rule (changed look ships under a new id) applies from the first public release; nothing is released, so preset-jade keeps its id now — cost: none today, and the ruling is written into followups.

## M7 addendum (2026-10-11)

- Built-in brushes are layer textures too: texture sheet Built-in section, golden `brush_background`. Evidence: `docs/verification/m7.md`, "Addendum".
- Fixed: signing key files synced before the rename (4fab086); an empty key was found on icondesign36.
- Deferred: a key file that exists but is empty or damaged fails every Apply with a raw keystore error. The app should say the key is damaged and offer restore from a backup. Not built here: it changes the key-loss flow, spec 6.5.

## M8 additions (2026-10-11)

### Done in M8
- M7 addendum deferral: a damaged signing key is named, never overwritten, restorable from a backup, or replaced by a new key on request (Task 18).
- Risk "M8 reproducibility assumes the same aapt2 build-tools 36.0.0 on every builder": still true across machines; on this machine the release APK is byte-identical across clones, online and offline, and CPU counts.

### Deferred from M8
- Setting a damaged key aside replaces an older `*.damaged` pair. Two damages in a row keep only the second; both were unreadable anyway.
- No app launcher icon. The APK ships the system default; fastlane `icon.png` is the Jade render of Settings. Taste call, his.
- No phone screenshots in fastlane. The listing works without them; they wait for his approval of the UI as public.
- `iconoclast.sourceUrl` stays empty while the repo is private; About says so. Fill it when the repo goes public, together with the recipe's first real submission.
- The recipe is a draft. It needs a public repo, then a merge request to fdroiddata; `fdroid lint` and `fdroid build` were not run (fdroidserver is not installed here and is not a pip-only install with its build server).
- The template pack is built by `aapt2` and `d8` from build-tools 36.0.0 found through `sdk.dir` or `ANDROID_HOME`, not through AGP. F-Droid's build server must have that build-tools version; if it does not, the recipe needs `sdkmanager 'build-tools;36.0.0'` in a `prebuild` line.

### Rulings made in M8
- Ruling: release tags are `v<versionName>`, F-Droid `UpdateCheckMode: Tags` with the versionCode read from `app/build.gradle.kts` — cost if wrong: one recipe line.
- Ruling: recipe `License: GPL-3.0-or-later` from the README statement and the GPLv3 text in LICENSE — cost if wrong: one recipe line.
- Ruling: `Categories: [Icon Pack]`, the existing fdroiddata category — cost if wrong: one line.
- Ruling: store graphics are Iconoclast renders of the approved Jade and Neon presets, credited CC BY-SA 4.0; nothing taken from the Arcticons repo directly — cost if wrong: two PNGs replaced.
- Ruling: the SUSS list is fetched, not committed, since it is AGPL-3.0; the Gradle check carries ten group prefixes from it and the script runs the full list — cost if wrong: a new non-free group passes the Gradle check until the script is run.
- Ruling: "Make a new key" offered beside restore, so a phone without a backup is not stuck; it sets the damaged files aside and the existing spec 6.5 mismatch flow follows — cost if wrong: one button removed.
- Ruling: shallow submodule clone for the offline and reproducibility proofs, since the full Arcticons history is 4.1 GB on tmpfs; the checked-out tree is identical — cost if wrong: none to the result, the pinned commit is what builds.

## Perf pass (2026-10-13)

### Deferred
- Startup reads about 20 ms of disk on the main thread: `AppContainer` dirs and `AppSettings` prefs, StrictMode in debug builds. Minor.
- The renderer allocates fresh float buffers per layer; GC ran 0.8 s of 3 s during a drag trace before the on-screen-only change. Buffer reuse would cut it, bytes unchanged. Not needed for the 16 ms drag measured after.
- The release APK is now R8-built. A full `--rerun-tasks` rebuild gave the same APK bytes on this machine; the M8 two-clone compare is not rerun yet.

### Open for him
- Invert fill with every layer hidden but Icon lines shows only lone lines (antennas, dots, stubs). The outlines in Invert are the gaps in the fill, not painted lines. Outline presets show full outlines. Pinned by `LinesOnlyTest`. Taste call: keep, or rename the row in Invert, or change what the lines layer means there.

## Look pass (2026-10-13)

### Rulings
- Ruling: random placement seeds from the slot's drawable name (IconSeed) plus the mapping's `seed`, not the package name. One drawable is drawn once and shared by every package that maps to it, spec 2.5; a package seed would break that — cost if wrong: one more input to `Mappings.place` and per-package slots.
- Ruling: `lockOffset`, zoom, rotate and the per-icon offset absorbed into one placement set, schema 8: `randomShift` 0/1 is the old lock; old themes render byte-identically (`ThemeMigrationTest.version7MappingsBecomeRandomShiftAndRenderTheSame`) — cost if wrong: none to old themes.
- Ruling: crop is stored as fractions in the mapping, never as a re-encoded image; a new photo opens the crop step at once, Crop on the image row reopens it — cost if wrong: a dialog flow.
- Ruling: Glass is a one-tap look in the Add sheet built from ordinary layers (`render/.../Glass.kt`), not a starter theme. A starter needs his taste call.
- Ruling: Blur blurs only the theme's own layers below it; help text and the Add sheet say it cannot blur the wallpaper.

### Deferred
- The open colour picker's square takes vertical drags, so a swipe that starts on it recolours instead of scrolling. Scrolling works from the bars, labels and rows around it.
- Blur set to Everywhere softens the background shape's own edge from inside; the shape clip keeps the outline crisp.
- Pinch in the crop step: fingers apart shrinks the frame (zooms in on the image). Corner drags are the main path.
- Latency was measured with the emulator's 30 launchable apps, not 150; the editor draws only on-screen cells, so the count changes scrolling, not edit latency.

## Phone feedback round 1, pack side (2026-10-13)

### Rulings
- Ruling: an installed component with no exact appfilter entry takes the drawable Arcticons uses most for its package, ties to the lower index — cost if wrong: a multi-icon package shows its most common icon.
- Ruling: http image links are fetched over https, since Android blocks cleartext — cost if wrong: an http-only site fails with a download error.
- Ruling: one line shows for 1.5 s before each system screen Apply opens — cost if wrong: one constant.
- Ruling: the share target takes images only, not text links, so Iconoclast stays out of every text share sheet — cost if wrong: one intent filter.

### Deferred
- Superseded by "Fresh pack per Apply" below. Neo keeps an app's cached icon until that app updates if the pack is switched before a restart. The guidance gives the order; the pack cannot clear Neo's cache.
- Superseded by "Fresh pack per Apply" below. Ruling (2026-10-14, pack-coverage): "apps from Obtainium, Aurora, GrapheneOS Store and system apps stay unthemed" is this Neo cache, not pack content. On icondesign36 the pack themed GOS Camera 93, VLC 3.7.1, Metro 6.1.0 and Mullvad 2026.12 once Neo was restarted and the pack switched. Each Apply updates the pack in place under the same name. Neo keeps the first-parsed appfilter in memory for the life of its process (`IconPackProvider` never evicts), and its icon cache key holds the pack's name, not its version. A switch made before a real restart re-caches the old icons, and only an app update clears them. Play auto-updates heal Play apps; the rest stay stale. Reproduced: stale after restart alone, healed by `install -r` of that app alone, and the restart-then-switch order themed all 20. No pack change was made. The durable fix is a new pack package name per Apply, which costs an install and an uninstall prompt and a pack re-pick in every launcher; that trade is open — cost if wrong: his phone needs the restart-then-switch order once per Apply.
- The "Request icon" link left with the coverage screen.
- Apply's status line counts apps by package; a work-profile copy of an app counts once.

## Phone feedback round 1, editor side (2026-10-13)

### Rulings made without him (he said decide)
- Paint swipes became Brush strokes' Direction, All one way, per the later ruling; switching to it starts from the picked sheet default: three broad diagonal swipes at 30 degrees (`EffectControls.SWIPES`). New strokes are Painted; schema 8 strokes migrate as Classic.
- Realism: option 2, the procedural model. No CC0 brush scans were vendored; the built-in brushes are generated noise, not scans.
- Frost blurs what is under the fill and the fill itself, so the slider shows on an opaque image too.
- See-through is the layer's opacity shown the other way; no second transparency field.
- Cut through to wallpaper erases what is below, then the fill paints at its opacity: 100% cut with 100% See-through is a clean hole.
- Overlay starts with Stay inside off: it sits on top and its description promises a wash across everything.
- Cover's smallest scale is `2^randomScale x (1 + 0.5 randomShift)` in the editor (raised with the random sliders); the renderer guarantees only per-icon scale >= 1.
- Blur is no longer in the Add sheet (Frost replaced it); existing blur layers stay editable.
- Clip legacy: targets the switch cannot express stay and are named on the page as an older setting.

### Look pass rough edges
- Colour picker square: a drag that leaves the touch slop mostly up or down scrolls the page; a 250 ms rest first, or a sideways drag, picks. Verified on the AVD.
- Crop pinch: spreading the fingers already makes the image bigger in the frame (the frame shrinks over the fixed image). Left as is; not exercised on the AVD, adb cannot pinch.

### Deferred
- Fixed in the PM review: outlined buttons now use `OutlinedAction` (text colour label and outline when enabled). Jade edits dropped from 240-320 ms to 30-77 ms for the on-screen grid, docs/verification/feedback-1.md.
- Editing a brush stroke's own settings on Jade still takes about 200 ms for the grid on the 4-core AVD; next lever is a cropped distance blur or fewer preview strokes while dragging.

## Brush texture pass (2026-10-14)

His words: "it just looks like random streaks. It should actually look like a paintbrush texture."

### Rulings
- Ruling: Painted strokes carry paint with a body, after Hertzmann, "Fast Paint Texture" (NPAR 2002). Each stroke writes a height field beside its colour: thickness, a blob at touch-down, narrow edge ridges, two octaves of bristle grooves along it. `effect/Impasto.kt` lights it with one fixed upper-left light and a small wet highlight. The tail breaks into dry-brush lines along the grooves instead of going see-through; each groove lays a slightly different tone. Classic is untouched, byte for byte.
- Ruling: procedural stays. RealBrush (Lu et al. 2013) needs scanned exemplars and texture synthesis per stroke; the height model reads as paint at 128 to 432 px without vendoring anything. No CC0 scan added.
- Ruling: Painted edges are crisper than the Softness slider gives Classic (15% of its band), and Painted takes 10% of the swipe tip blur. Paint does not blur; a soft edge read as airbrush.
- Ruling: grooves sit 0.65% of the canvas apart, never under 1.6 px, so the 128 px preview keeps a coarser version of the texture instead of aliasing it away.
- Changed on purpose: the 18 Painted goldens (`fb_strokes_parallel`, `fb_strokes_corner`, `fb_strokes_painted`). Every other golden is byte-identical, including `ThemeMigrationTest.version8RendersTheSameAsSchema9`.

### Evidence
- Sheets: `docs/verification/brush-texture/compare-128.png`, `compare-216.png`, `compare-432.png`, before on the left, after on the right. Regenerate the after half: `./gradlew :render:paintSheet -Pout=<dir> -Pprefix=after`.
- Stroke edit, 24 icons at 128 px, JVM, `:render:strokeProfile`: Jade Classic 83 to 84 ms, Jade Painted 135 to 131 ms, Painted swipes 26 to 28 ms.

### Deferred
- `ArtByteIdentityTest` not run in this pass: the emulator was in use by another task. New code is plain float arithmetic plus `StrictMath.sqrt`, as the rest of the renderer. Run it before the next phone release; 18 goldens changed.
- `./gradlew :render:test -PupdateGoldens` fails in a fresh worktree: `GoldenTest.writeSamples` cannot find a sample icon in the converted bundle there. The 18 goldens were rewritten from the committed `samples.bin` instead.

## Fresh pack per Apply (2026-10-16)

His pick, option B: "That's fine. B." Evidence: `docs/verification/pack-rename.md`.

### Rulings
- Ruling: every Apply installs the pack as `dev.iconoclast.app.pack.g<NNNNNN>`, patched into a fixed-length placeholder in the template's manifest and arsc, then uninstalls every other pack through the system prompt. The legacy `dev.iconoclast.app.pack` is removed by the first Apply. Supersedes the Neo-cache deferred entries above — cost if wrong: an install and an uninstall prompt per Apply.
- Ruling: Neo 0.9.3 still needs one Restart per Apply. Its Icon Packs list is built once per process, so a pack installed after Neo started is not listed until it restarts. Restart then pick is order-proof: picking first changes nothing. The old System-icons switch and its stale-app trap are gone. Lawnchair and Kvaesitso need only the pick; their force stop steps were dropped — cost if wrong: one tap more or less in a launcher's guidance.
- Ruling: the counter never goes back: next generation is above the stored one and every installed pack. If app data is cleared and every pack uninstalled, a name can repeat; Neo would then see a name it may have cached — cost if wrong: one more Apply.

### Deferred
- Superseded by "Neo pick race" below. Phone in Neo keeps its stock icon after the first Restart and pick, as before this change; a second Restart themes it. The guidance says so.

## Neo pick race (2026-10-16)

Evidence: `docs/verification/neo-pick-race.md`. His phone kept stock icons for store and system apps after Install, Restart, pick.

### Rulings
- Ruling: the cause is in Neo 0.9.3, not the pack. A pick clears Neo's icon DB and redraws while the new pack's appfilter is still parsing on a background thread; `resolveIconEntry` never waits for it, so the first icons drawn (home screen, dock) keep their own and stay cached until each app updates. A slow phone loses many. A Restart builds the pack before the loader and redraws everything. The Neo guidance now ends with Restart once more, as a step. The earlier "Neo caches icons keyed by pack name" reading was wrong: the DB has no pack key, and pack-rename only hid this for most apps on a fast emulator — cost if wrong: one extra Restart per Apply.
- Ruling: Copy diagnostics sits at the foot of the Apply screen as a text button, puts the report on the clipboard and in logcat (`Iconoclast` tag, `diag:` lines). It names no app data beyond package names, installers and update times — cost if wrong: one button.

### Deferred
- His phone has not run the new step yet. If store apps still stay stock after Restart, pick, Restart, the pasted diagnostics decide between a missing component (`pack=MISSING`) and Neo.

## Neo custom icons (2026-10-16)

Evidence: `docs/verification/neo-custom-icons.md`. Restarts did not theme system, Obtainium, VLC, Mullvad or Brave on his phone.

### Rulings
- Ruling: appfilter reordering dropped. Main already writes installed components first, his diagnostics show every component in the pack, and restarts do not help, so it is not the pick race — cost if wrong: one more round.
- Superseded by "Neo custom icons fix" below. Ruling: the lead cause is Neo per-app custom icons pointing at a removed pack. Neo checks them before the pack and 0.9.3 cannot delete them. The Apply screen reads a Neo backup to list them and writes a clean copy to restore; the original file is never written — cost if wrong: two text buttons.
- Superseded by "Neo custom icons fix" below. Ruling: after restoring the clean copy, the guidance is Icon Packs: System icons, then Iconoclast Pack. A Restart alone leaves the cached stock icon — measured.

### Deferred
- Superseded: his backup came in, 25 custom icons, all Arcticons. If it lists no custom icons, the drawable-load lines in Copy diagnostics decide the pack side.

## Neo custom icons fix (2026-10-19)

Evidence: `docs/verification/neo-override-fix.md`. His backup: 25 custom icons, all into Arcticons, Day & Night and Black.

### Rulings
- Ruling: Neo 0.9.3 draws a custom icon over any picked pack and has no way to remove one. Arcticons looked right only because his custom icons point at Arcticons; under System icons they still show Arcticons, measured. No pack-side change can reach them — cost if wrong: none, read in source and measured.
- Superseded by "Neo shotgun" below. Ruling: Iconoclast re-points them through Neo's own exported icon screen (deep link per app) and an accessibility service that taps the pack's icon. He ticks the apps once; every Apply re-points them, since each Apply renames the pack and renumbers drawables. Backup and restore removed — cost if wrong: one Settings switch and one list.
- Ruling: the service acts only on Neo windows, only while a run waits on one app, with no overlay, notification or gesture. It checks the row count against the computed position and presses Back on a mismatch — cost if wrong: an app left on its old icon, reported in the result line.
- Ruling: rejected avenues. Installing a pack under Arcticons' package names impersonates another project. A stable second pack would need a second install per Apply and drawable names that never move. Re-pointing every app pins Neo to Iconoclast under any pack. Detecting custom icons by screenshot is fragile — cost if wrong: he ticks apps by hand once.
- Superseded by "Neo shotgun" below. Ruling: a re-pointed app keeps the Iconoclast icon under any Neo pack, as his Arcticons ones did. Unticking stops future re-points; Neo 0.9.3 cannot undo one — cost if wrong: re-tick and point at another pack in Neo by hand.

### Deferred
- Android may grey out the switch for an app installed outside a store ("restricted setting"). The on-screen line gives the App info, Allow restricted settings path. Not reproduced: the emulator installs through adb sessions.
- Neo loads custom icons asynchronously at start, so dock icons can miss them after a Restart (Phone under System icons). Not acted on.
- Superseded by "Neo shotgun": work profile apps are pointed too, keyed by user id.

## Background size (2026-10-19)

His request: a Size control for the background shape, like the icon's Size. Evidence: `docs/verification/bg-size.md`.

### Rulings
- Ruling: Background size moves out of More options to sit under the shape, as Icon size sits on the Icon page. Same slider, % of the tile, reset button and long-press help — cost if wrong: one line moved back.
- Ruling: range 50% to 100%, was 70% to 100%. 50% is about the default icon's own box (80% icon size is 49% of the tile). Below that the shape is a dot behind a trimmed icon — cost if wrong: one constant, `MIN_BACKGROUND_SIZE`.
- Ruling: no growing past 100%. The PNG is the tile, so past full the canvas cuts every shape flat on all four sides: a square with rounded corners, which Rounded square and Squareness already draw, and the border would be cut off at the edges — cost if wrong: a range change and a border rule.
- Ruling: the background's own gradient or image is laid out on the shape's square (schema 10), so it shrinks with the shape; crop, Cover and random shift, turn and zoom are measured against that square. Patterns, other layers shown on the background, effects, finish grain and rim, and the border keep the canvas's scale, so grain, marks and edge widths match the icon's at every size — cost if wrong: one frame argument in `StackEvaluator.source`.
- Ruling: the shape still clips the icon, as it did at 70%. Help says to shrink the icon to match. Not auto-linked: the icon's Size is its own property — cost if wrong: an icon trimmed until he moves its slider.
- Ruling: schema 9 themes below 100% migrate so they look as before: gradient points and image scale move into the shape's frame. A randomly placed image redraws its patch in the new room. At 100% only the version changes — cost if wrong: an old shrunk image background shifts its patch.

## Neo shotgun (2026-10-19)

His call: "just do the shotgun". Evidence: `docs/verification/neo-shotgun.md`.

### Rulings
- Ruling: every launchable app the pack has an icon for is pointed at the pack, after every Apply with Neo as home. No list. The tick list and its saved apps are removed on first start — cost if wrong: none, nothing else read them.
- Ruling: apps the pack has no icon for are skipped, never pointed at System. A stale Arcticons custom icon on such an app stays: Neo 0.9.3 cannot unset it — cost if wrong: that app looks off until Arcticons covers it in the pack.
- Ruling: under Neo's System icons, every pointed app stays Iconoclast. Picking a pack in Neo no longer changes them; only an Apply does — cost if wrong: none, Neo 0.9.3 has no undo.
- Ruling: the run also starts when the Apply screen opens and the installed pack is not on every app yet. Covers the service just turned on, Neo just made home, and a run cut off. Done apps are saved per pack — cost if wrong: a run he did not expect on opening Apply.
- Ruling: Neo not home, nothing runs and the section is hidden — cost if wrong: he makes Neo home and opens Apply.
- Ruling: service off, one line and a Turn it on button. After an Apply, Accessibility opens by itself once, never again — cost if wrong: one more tap.
- Ruling: lock or Home mid-run, the run waits and never presses Back on another app. It goes on when Neo's screen or the Apply screen is back. The next screen opens only after the Apply screen holds 50 ms, else Home was stolen back — cost if wrong: 6 s on 116 apps.
- Ruling: work profile apps are pointed too. Neo keys custom icons by component and user id, and its deep link takes the id. Paused profiles are skipped — cost if wrong: the profile's apps keep their old icon.
- Ruling: the row is walked page by page, since Compose exposes only on-screen icons. One re-walk on a miscount, then Back — cost if wrong: that app reported as missed.

### Deferred
- Time grows with app count: about 0.35 s per app, about 70 s for 200. Not cut further: each app is one Neo screen and one database write in Neo.
- Force-stopping Iconoclast turns its accessibility service off. Android does this; the Apply screen then shows the Turn it on line again.
