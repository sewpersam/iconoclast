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
