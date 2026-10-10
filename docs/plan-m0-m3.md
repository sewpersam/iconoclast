# Plan: icon-designer milestones M0 to M3

Spec: `PROMPT.md` at the repo root. It is the binding authority. Each task
below names the spec sections it implements. Read those sections in full.
Also read `spike/FINDINGS.md`.

## Global constraints (apply to every task)

- Repo root: `/mnt/storage/Claude/icon-designer`. Never touch any path under
  `/mnt/storage` outside `/mnt/storage/Claude`.
- F-Droid clean from the first commit: no Google Play Services, no
  analytics, no crash reporters, no proprietary or non-free dependency. Every
  dependency must come from Maven Central or Google Maven with a free licence.
  No prebuilt binaries checked in except the pinned Arcticons submodule.
- Licences: app code GPL-3.0-or-later. Arcticons icons CC BY-SA 4.0, credited
  everywhere they are shipped, including inside every generated pack.
- Working app name is "Icon Designer", applicationId `dev.icondesigner.app`,
  pack package `dev.icondesigner.app.pack`. Both are placeholders the user
  will rename; keep each defined in exactly one place.
- minSdk 33. targetSdk and compileSdk: current stable (platform 36 is
  installed in `~/Android/Sdk`). Look up current stable AGP, Gradle, Kotlin,
  Compose BOM and apksig versions before pinning. Never guess versions.
- JDK: the default `java` is 25. Use `/usr/lib/jvm/java-21-openjdk` via
  `org.gradle.java.home` in `gradle.properties` or a toolchain, whichever
  the chosen AGP supports.
- Headless only. Never open any GUI window, emulator window, or Android
  Studio. Emulator launch command and AVD names are in spec §4. Never touch
  the AVD `picklr_test`. Stop the emulator with `adb emu kill` when done.
- Throttle: run `free -g` before heavy builds. Cap every batch loop. Gradle
  daemon memory at most 4 GB. Do not run two Gradle builds at once.
- Verify before claiming. Every gate passes on evidence written to
  `docs/verification/<milestone>.md`, with the command output or screenshot
  paths.
- Commit on `main` in this repo with small atomic commits. End every commit
  message with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Kotlin, no Java sources. Compose for UI. No speculative abstractions.

## Task 1: M0 repo scaffold

Implements spec §3.1, §3.2 (the submodule only), §4, and the M0 row of §5.

- Add Arcticons as a git submodule at `third_party/arcticons`, pinned to
  commit `90a4343510f35e33421f0a41df5debc29c3604d1` from
  `https://github.com/Arcticons-Team/Arcticons`, branch `main`. The repo is
  large; a full clone is acceptable but check `df -h /mnt/storage` first and
  report the size. Only `icons/white/` and `app/src/main/assets/appfilter.xml`
  are needed at build time.
- Gradle modules per §3.1: `:app`, `:render`, `:pack`, `:iconsource`,
  `:template-pack`, plus `buildSrc` or an included build `build-logic` for the
  converter. Empty but compiling modules with the right plugins and inter-
  module dependencies. `:render` is a pure Kotlin JVM module with no Android
  dependency.
- Version catalog `gradle/libs.versions.toml`. Pin versions you looked up,
  and note where you looked them up in `docs/verification/m0.md`.
- `LICENSE` (GPL-3.0), `THIRD_PARTY_LICENSES.md` with the Arcticons CC BY-SA
  4.0 attribution, `README.md` with a one-paragraph description, the build
  instructions, and the §7 future-work list.
- `.gitignore` already exists. Extend it, do not replace it.
- Gate: `./gradlew assembleDebug` succeeds. Also `./gradlew :render:test`
  runs with at least one trivial test. Record both outputs in
  `docs/verification/m0.md`.

## Task 2: M1 icon converter, bundle, appfilter compiler

Implements spec §1.5, §3.2, and the M1 row of §5.

- Gradle task in the build-logic module that converts every SVG in
  `third_party/arcticons/icons/white/` into one binary bundle plus a name
  index, placed in `:iconsource` assets at build time. Document the format
  in `docs/bundle-format.md`.
- Per primitive: absolute path commands with arcs converted to cubics, a
  stroke-or-fill flag, fill rule, original stroke width, miter limit. CSS
  classes, inline styles, presentation attributes and transforms resolved
  as §3.2 says. Metadata stripped. Handle the one file with no viewBox.
- Appfilter compiler: component to drawable-index map, calendar entries
  skipped, malformed entries counted and logged, never failing the build.
- Loader in `:iconsource`: open the bundle, look up a drawable by name,
  search names by substring, resolve a component string to a drawable.
- Conversion test per §3.2: rasterize every converted icon at 96 px with the
  `:render` code path (a minimal stroke rasterizer in `:render` is in scope
  for this task; Task 3 extends it) and compare to an inkscape reference.
  Check that `inkscape` is installed; if not, use `rsvg-convert` or
  `resvg` and say which. Gate: IoU >= 0.98 for at least 99.9% of icons.
  Make the comparison a Gradle verification task, not a unit test, since it
  takes minutes. Cap its parallelism to 4 workers.
- Report in `docs/verification/m1.md`: bundle size, index size, IoU pass
  rate, the failing icons, malformed appfilter count, converter run time.
- Make the conversion output cacheable: incremental Gradle task with inputs
  and outputs declared, so a no-change build does not reconvert.

## Task 3: M2 renderer

Implements spec §1.4, §2.3 Shape and Fill and Background tables, §3.3, and
the M2 row of §5. Layers and effects are later milestones; design the
pipeline so they slot in at step 4 of §3.3 but do not build them.

- Pure Kotlin in `:render`. Rasterization may use `android.graphics` only
  behind an interface so the JVM tests can use java.awt or a software
  rasterizer; decide and document the choice in the module README.
- Shape params, fill modes including invert fill and silhouette, lone-line
  keeping, fallback threshold, background shapes and paints, output size
  decision with justification.
- Felzenszwalb EDT, connected components, morphology via distance thresholds.
- Mask cache keyed by drawable, geometry params, size.
- Golden-image unit tests for 24 sample icons in each fill mode. Include
  F-Droid, Camera and Chrome for lone-line handling. Golden PNGs live under
  `render/src/test/resources/golden/`. Deterministic output: same input,
  same bytes, asserted in a test.
- Performance: measure 24 icons at 128 px and 300 icons at 432 px on the
  JVM and record the numbers in `docs/verification/m2.md`. The phone budget
  in §3.3 is verified in Task 4 on the emulator.

## Task 4: M3 pack generation, signing, install, restart flow

Implements spec §1.2, §1.3, §3.4, §3.5, §3.6, §6.1 to §6.7, and the M3 row
of §5. This is the feasibility checkpoint.

- `:template-pack` builds the template APK with aapt2 at build time, with a
  real tiny activity that opens the designer, the intent filter set from
  §3.4 plus any others you verified in launcher source, 1024 slot PNGs,
  stub `res/xml/appfilter.xml`, licence text in assets. Output copied into
  `:app` assets.
- `:pack`: zip rewriter with 4-byte alignment of STORED entries, apksig
  v2+v3 signing, key generation and storage per §3.5, PackageInstaller
  session per §3.6 with every status handled.
- Unit tests: zip alignment checked with `zipalign -c -p -v 4` from
  build-tools; signature verified with apksig's verifier; appfilter XML
  generation and escaping; deterministic unsigned APK bytes.
- `:app`: one screen with an "Apply plain theme" button and a second
  "Apply thick theme" button that uses stroke weight 2.0, so the update path
  is visibly testable. Progress bar, install handoff, launcher detection and
  the restart guidance table from §6.6.
- Coverage: installed launchable activities matched to the appfilter index,
  duplicate drawables sharing one slot, calendar entries falling back.
- Emulator verification on both AVDs, all three launchers from
  `spike/launchers/`, both flows from the M3 gate row. Screenshots under
  `docs/verification/m3/`. Also verify whether `assets/drawable.xml` makes
  the themed icons show in the Lawnchair and Neo icon pickers. Run
  `apksigner verify --print-certs` and `zipalign -c -p -v 4` on the produced
  APK pulled from the device.
- Report in `docs/verification/m3.md` with every screenshot path. Stop the
  emulator when finished.
