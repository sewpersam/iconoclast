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

### M7 readiness, from the M6 whole-milestone review (do these first, in order)
1. Split icon identity in the renderer: key geometry, fill and effects by the geometry's own drawable name and pass the seed name (the slot) separately, so a swap cannot poison the shared MaskCache.
2. One per-icon offset in StackInput replacing the four derivations (Mappings.texture, Mappings.noise, Streaks breakup, Grain), decide whether grain follows the override.
3. Schema v4 with an `overrides` map keyed by appfilter drawable name, pure v3→v4 adding an empty map, overrides resolved to an effective Theme per icon so Theme stays override-free and cache keys invalidate naturally, "hide streaks" drops Streaks EffectLayers, overrides whose name left the pin are kept and flagged.
4. PackBuilder takes a per-slot theme and icon, overrides per drawable, one render per drawable.
5. Coverage screen persists the applied component set and theme id on successful install and diffs on open.
6. Export and import add the key entry (PKCS12 plus password) behind the warning, gate is render every golden theme from the imported document and compare bytes.
7. Output resolution setting capped or exempted from the budget with goldens at each offered size, grain cells are 1/432 so grain washes out below 432.
8. Settings/About licence list generated from `:app:dependencies --configuration releaseRuntimeClasspath`, committed, and THIRD_PARTY_LICENSES.md extended to every runtime dependency with its licence.
