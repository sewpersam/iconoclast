# Build brief: Arcticons icon pack designer for Android

You are the technical lead on a new open source Android app. You own every
technical decision. The user owns taste: how it looks, the name, the presets,
what ships first. Read this whole brief before you plan anything. Then read
`/mnt/storage/Claude/icon-designer/spike/FINDINGS.md`.

---

## 0. The one-paragraph version

The user wants an **icon pack designer**, not an icon pack. He installs one
app. It holds the whole Arcticons line-icon library. He restyles it however he
likes: stroke weight, corners, invert fill, background shapes, uploaded
textures with Photoshop-style blend modes, and effects like brush streaks. He
saves the results as named themes. When he taps Apply, the app generates a real
icon pack app on the phone, signs it, and installs it. His launcher then uses
it like any other icon pack. The primary target is GrapheneOS, but it must work
on any Android 13+ phone and in any launcher that supports icon packs. It may
go on F-Droid later, so it must stay F-Droid clean from the first commit.

---

## 1. What is already proven

A spike on 2026-10-10 proved the risky parts on an Android 15 emulator. The
code, screenshots and harness are in `/mnt/storage/Claude/icon-designer/spike/`.
Treat everything in this section as settled fact. Don't re-run these
experiments unless you are changing the mechanism.

### 1.1 Why the designer must generate a second app

A launcher reads icons from an installed package's compiled resources. It does
this through `PackageManager.getResourcesForApplication` and resource lookups
by name. An installed APK is read-only and signature-locked, so the designer
can't change its own icons at runtime. Its private files aren't readable by the
launcher either. So the designer ships a **template pack APK** and, on Apply,
produces a modified copy as a separate installed app.

### 1.2 The pack pipeline, step by step, all verified

1. **Template build.** At build time, aapt2 produces a pack APK containing:
   - an activity with the icon pack intent filters
   - placeholder PNGs named `slot_NN` in `res/drawable-nodpi`
   - `res/xml/appfilter.xml`
   - `assets/appfilter.xml`

   See `spike/pack.py`.
2. **Detection.** `cmd package query-activities -a org.adw.launcher.THEMES`
   lists the installed template, and the launchers list it in their pickers.
3. **On-phone rewrite is a plain zip operation.**
   - Replace the bytes of `res/drawable-nodpi-v4/slot_NN.png` with new PNGs.
   - Replace `assets/appfilter.xml` with a newly generated plain-text XML file.
   - No aapt, no resource recompiling. The resource table stores file paths,
     so new bytes under an existing path just work.
4. **Align, then sign.** Zip-align to 4 bytes, with `resources.arsc` and the
   PNGs stored uncompressed. Then sign with apksig.
   - Signing was run **on the Android runtime itself**: apksigner's jar was
     dexed with d8 and run through `app_process` on the emulator, using a
     PKCS12 keystore. The device-signed APK verified and `pm install`ed with
     Success.
5. **Update in place.** Installing over the previous pack with the same key
   and the **same versionCode** is allowed. The md5 of the installed
   `base.apk` matched the new file.
6. **Assets-only appfilter works.** It was accepted by all three tested
   launchers: Neo Launcher 0.9.3, Lawnchair 15.0.0 Beta 3.0, and Kvaesitso
   1.41.0. The phone never has to write Android binary XML.

### 1.3 The one real constraint: launchers cache packs

After an in-place update, **all three launchers kept showing the old icons**.
Neo also stayed stale through:
- a versionCode bump
- reselecting the pack
- toggling to system icons and back
- uninstalling and reinstalling the pack

**Restarting the launcher process always fixed it.** Neo has a "Restart" entry
in its settings and Lawnchair has "Restart Lawnchair". Kvaesitso had no restart
string. So Apply must end with a guided launcher restart. See §6.6.

### 1.4 Invert fill works, done on the raster

The algorithm in `spike/render.py`:
1. Rasterize the strokes.
2. Morphologically close small gaps, so near-closed shapes seal.
3. Label connected regions of non-stroke pixels. Regions touching the image
   border are outside. All others are enclosed.
4. Take the enclosed regions, subtract the strokes dilated by the gap width,
   and anti-alias the result.

`spike/out/montage_jade.png` vs `spike/out/montage_plain.png` shows it on 12
real icons. It reads well.

Known failure modes:
- Lone lines vanish, because they enclose nothing. Examples: the F-Droid
  robot's antennas, the camera's small dot, some Chrome inner lines.
- Icons that are mostly unenclosed lines come out nearly empty.

### 1.5 Measured facts about Arcticons

Pinned at commit `90a4343510f35e33421f0a41df5debc29c3604d1`, 2026-10-05, from
`github.com/Arcticons-Team/Arcticons`, branch `main`:

- **Volume.** `icons/white/` holds **15,322 SVGs**, 62 MB on disk. The median
  file is 785 bytes and the largest is 21.7 KB.
- **Canvas.** 15,321 files use `viewBox="0 0 48 48"`. One file has no viewBox.
  Handle it.
- **Element counts:**
  - `path` 59,250
  - `line` 15,645
  - `circle` 8,878
  - `g` 4,401
  - `rect` 4,206, of which 3,665 use `rx`
  - `polyline` 3,415
  - `polygon` 1,213
  - `ellipse` 845
- **Transforms.** 734 elements carry a `transform`: 729 `translate`, 3
  `matrix`, 2 `rotate`.
- **Styling.** Mostly CSS classes in a `<style>` block: `fill:none`, white
  `stroke`, `stroke-linecap:round`, `stroke-linejoin:round`. The default
  stroke width is 1 user unit. There are also these variations:
  - 1,363 inline `style=""` attributes
  - 77 `fill=""` attributes
  - spaced CSS such as `fill: none`
  - `stroke-miterlimit` values of 10, 7 and 90
  - `opacity`
  - `isolation`
  - inkscape cruft
- **Not purely strokes.** **984 files contain filled elements**, usually
  small dots drawn as fill with `stroke-width:0px`. 91 use
  `fill-rule:evenodd`. The converter must keep a stroke-or-fill flag per
  primitive, and the restyle rules must handle filled primitives. For
  example, a filled dot scales with stroke weight and survives invert fill as
  solid.
- **Metadata.** 46 files carry a large C2PA `<metadata>` block. Strip it.
- **appfilter.** `app/src/main/assets/appfilter.xml` is identical to the
  `res/xml` copy.
  - It has 49,548 `<item component="ComponentInfo{pkg/activity}"
    drawable="name"/>` entries.
  - It maps to 14,028 distinct drawables, and every one has an SVG.
  - It also has 177 `<calendar component=… prefix=…/>` entries for dynamic
    date icons.
  - Some entries are malformed, for example `ComponentInfo{{…`. Parse
    defensively.
- **Licensing.** Code is GPL-3.0. **Icons are CC BY-SA 4.0.** The designer
  and every generated pack must credit Arcticons and carry that license.
  Generated icons are derivatives and stay CC BY-SA 4.0.

---

## 2. Product specification

### 2.1 Platforms and constraints
- **Target.** minSdk 33, Android 13. That guarantees AGSL `RuntimeShader` and
  every `BlendMode`. All GrapheneOS devices run 14 or newer. targetSdk is the
  current stable SDK.
- **No network required.** The library is bundled. An optional "check for new
  Arcticons icons" feature is a later idea and must be opt-in.
- **No proprietary dependencies.** No Google Play Services, no analytics, no
  crash reporters.
- **Phones first.** Tablet layout is a nice-to-have.

### 2.2 Screens

1. **Themes home.** A grid of saved themes, each card showing about 9 live
   preview icons. Actions:
   - new
   - duplicate
   - rename
   - delete, with confirm
   - export
   - import
   - set active

   The active theme is marked.
2. **Theme editor**, the core screen.
   - **Top:** a live preview grid. Default contents are the user's installed
     apps that Arcticons covers. A toggle switches to a fixed sample set of
     about 24 varied icons: dense, sparse, text-heavy, dotted, open-line.
     Tapping an icon opens the per-icon override sheet.
   - **Bottom:** a tabbed panel: **Shape**, **Fill**, **Background**,
     **Layers**, **Effects**.
   - Every control updates the preview in real time while dragging. Fall back
     to a lower preview resolution while a slider is moving if needed.
   - Undo and redo across the session.
   - A compare control: press and hold to show plain Arcticons.
3. **Per-icon override sheet.** Overrides one icon in this theme only:
   - stroke weight
   - fill mode
   - gap closing
   - texture offset
   - hide streaks
   - swap the drawable for any other Arcticons drawable, with a search over
     all 14,028 names

   A "reset to theme" button clears the overrides.
4. **Apply flow.**
   1. Render all covered installed apps, with a progress bar.
   2. Build and sign the pack.
   3. Hand off to the system install prompt.
   4. Detect the current launcher, then show the right next step: select the
      pack in launcher settings on first use, or restart the launcher on
      updates. See §6.6.
   5. Finish with a summary: N apps themed, M installed apps not covered by
      Arcticons.
5. **Coverage screen.** Lists installed launchable apps with status:
   - themed
   - not in Arcticons, with a link to Arcticons' icon request process
   - overridden

   Shows apps installed since the last Apply, with a re-apply call to action.
6. **Settings / About.**
   - Arcticons credit and license, plus this app's license
   - Export pack signing key, see §6.5
   - Output icon resolution
   - Open source licenses for dependencies

### 2.3 The style model

The parameters a theme stores, with ranges. Every slider shows its numeric
value and can be reset to its default.

**Shape** applies to the line art before anything else:
| Param | Range / options | Default |
|---|---|---|
| Stroke weight | 0.25 to 4.0, in 48-grid units | 1.0 |
| Line caps | butt / round / square | round |
| Line joins | miter / round / bevel | round |
| Miter limit | 1 to 10, shown only for miter joins | 4 |
| Icon scale in safe zone | 50% to 100% | 80% |
| Offset X/Y | ±10% | 0 |
| Filled dots | scale with stroke / keep original | scale |

**Fill:**
| Param | Options | Notes |
|---|---|---|
| Mode | Outline / Invert fill / Silhouette / Outline + Silhouette | Silhouette fills enclosed regions plus strokes. Invert fill is §1.4. |
| Gap width | 0 to 4 grid units | Invert fill only. This is the "adjust the whitespace" control. |
| Gap closing | 0 to 2 grid units | Seals near-closed shapes before the flood fill. |
| Fallback threshold | 0 to 30% | If the filled area is below this fraction of the outline's area, that icon renders in Outline mode. |
| Keep lone lines | on/off | When on, unenclosed strokes are drawn as strokes on top of the invert fill, so antennas and dots survive. |

**Background:**
| Param | Options |
|---|---|
| Enabled | on/off. Off means a transparent background. |
| Shape | circle, squircle with superellipse exponent, rounded square with a corner radius slider, teardrop, or square |
| Size | 70% to 100% of the canvas |
| Paint | solid colour, linear gradient, radial gradient, or texture layer |
| Border | width, colour, opacity |

Bake the shape into the PNG yourself. Don't rely on the launcher's shape
setting. Note it in the UI so users can turn off launcher masking if it
conflicts. Neo and Kvaesitso both have shape settings.

**Layers.** An ordered stack, like Photoshop. The base stack is
`background → effects → icon`, and the user can add layers anywhere.

Each layer has:
- **Source:** solid colour, gradient, uploaded texture, or procedural noise
  (fBm, ridged/veined, grain).
- **Clip to:** icon, background, effect output, the inverse of any of those,
  or the whole canvas.
- **Blend mode:** expose the Photoshop set, mapped to `android.graphics.BlendMode`:
  - Normal = SRC_OVER
  - Multiply = MULTIPLY
  - Screen = SCREEN
  - Overlay = OVERLAY
  - Darken = DARKEN
  - Lighten = LIGHTEN
  - Color Dodge = COLOR_DODGE
  - Color Burn = COLOR_BURN
  - Hard Light = HARD_LIGHT
  - Soft Light = SOFT_LIGHT
  - Difference = DIFFERENCE
  - Exclusion = EXCLUSION
  - Hue = HUE
  - Saturation = SATURATION
  - Color = COLOR
  - Luminosity = LUMINOSITY
  - Add = PLUS

  Check the enum in the SDK you compile against.
- **Opacity:** 0 to 100%.
- **Texture mapping:**
  - fit, fill or tile
  - scale and rotation
  - a **per-icon offset**, seeded by a hash of the drawable name, so every
    icon gets a different patch of the texture
  - a lock option, so every icon uses the same patch
- **Mask adjustments:** levels or threshold on the source's luminance, feather
  radius, invert. This is how a black-and-white base image becomes an alpha
  matte.

**Effects.** Each has an on/off toggle, sits at a position in the stack, and
supports blend mode and opacity:
- **Streaks.** This is the user's named reference: a jade pack with
  low-opacity brush streaks between icon and background.
  - Smear the icon's fill mask along an angle, 0 to 360°.
  - Length: 0 to 60% of the canvas.
  - Decay curve.
  - Breakup: the smear is multiplied by a stretched brush or noise texture,
    built-in or user-uploaded. Density and contrast are adjustable.
  - The streak takes its paint from the texture layer it's linked to, so it
    shows the jade texture at low opacity.
- **Bevel/emboss.** Built from a distance field of the fill mask. Controls:
  light angle, depth, size, softness, highlight and shadow colour and blend.
- **Drop shadow:** angle, distance, blur, colour, opacity.
- **Inner shadow.**
- **Outer glow / inner glow.**
- **Grain:** monochrome noise overlay.

### 2.4 Themes and textures
- **Themes are recipes, not images.** Store each theme as versioned JSON with
  a schema version field, plus references to texture files by content hash.
- **Importing a texture** uses the system photo picker or the SAF document
  picker, so no storage permission is needed. Copy the file into app storage,
  downscale it so the longest side is at most 2048 px, and store it by
  SHA-256.
- **Export** writes a `.zip` containing `theme.json` and the textures it
  uses. **Import** validates the schema and migrates older versions.
- **Starter presets.** Ship three:
  - "Plain Arcticons": white line on transparent
  - "Jade": invert fill, procedural jade, streaks, black rounded square
  - one more you think shows the range

  **The user approves the preset looks before release. That is a taste
  question.**

### 2.5 Coverage rules
- **Which apps.** Installed launchable activities, from `queryIntentActivities`
  with `ACTION_MAIN` and `CATEGORY_LAUNCHER`. Include work profile and private
  space apps if `LauncherApps` exposes them; investigate this. Match them
  against the parsed appfilter.
- **Several activities, one drawable.** Several components can map to the
  same drawable. Render the drawable once and point every matching component
  at that one slot.
- **Unmatched apps** keep their system icon by default. Optional later
  feature: the appfilter `iconback`/`iconmask`/`iconupon`/`scale` mechanism.
  It lets the pack restyle unknown apps by masking their original icon. Only
  ship it after you verify which of the three test launchers honour it.
- **Calendar icons** (`<calendar>` entries) are out of scope for v1. They
  need 31 date variants per prefix. Fall back to the static drawable.
- **New installs.** Android won't deliver `PACKAGE_ADDED` to manifest
  receivers for other apps since API 26. Don't rely on it. Instead, diff the
  installed apps against the last Apply whenever the app opens, and show a
  badge. An opt-in daily WorkManager check that posts a notification is
  acceptable.

---

## 3. Architecture

### 3.1 Gradle modules
| Module | Contents |
|---|---|
| `:app` | Compose UI, ViewModels, theme storage, Apply flow |
| `:render` | Icon geometry, invert fill, layer and effect compositor. No UI deps. Unit-testable on the JVM where possible. |
| `:pack` | Template asset access, zip rewriter, aligner, signer, installer session |
| `:iconsource` | Loader for the bundled icon data, the appfilter index, and drawable name search |
| `:template-pack` | Builds the template pack APK. Its output is copied into `:app` assets. |
| `buildSrc` or an included build | The SVG-to-bundle converter task and the appfilter compiler |

### 3.2 Build-time icon conversion
- **Source.** Arcticons comes in as a **git submodule** pinned to a commit, so
  F-Droid can build offline. Bumping the pin is a deliberate commit.
- **Converter.** Gradle task, Kotlin. For every SVG:
  1. Strip metadata.
  2. Resolve classes plus inline styles plus presentation attributes into
     per-primitive style. Take care with CSS specificity.
  3. Bake every transform into the geometry.
  4. Convert all element types into absolute path commands. Arcs become
     cubic beziers.
  5. Record per primitive: path data, a stroke or fill flag, the fill rule,
     the original stroke width, and the miter limit.

  Emit one compact binary bundle, with a format you document, plus a name
  index. Target an app download well under 20 MB.
- **Appfilter compiler.** Turns the appfilter into a compact
  `component → drawable index` map. Malformed entries are logged and skipped
  without failing the build.
- **Conversion test.** Rasterize every converted icon with the `:render` code
  path at 96 px. Compare each against a reference raster of the original SVG,
  made by inkscape at the same size and stroke. **Gate: IoU ≥ 0.98 for at
  least 99.9% of icons.** The build prints the failures, and you fix the
  converter for whole classes of failure rather than one icon at a time.

### 3.3 Renderer
- **Output size.** Decide it and justify it in the README. The spike used
  432 px, the full adaptive canvas at xxxhdpi, and got about 45 KB per
  textured PNG. Measure crispness on a 420-dpi screenshot before you pick
  something smaller.
- **Pipeline order:**
  1. geometry mask
  2. fill mode mask
  3. background
  4. layers and effects in stack order
  5. the alpha of the background shape

  The output is premultiplied RGBA, encoded as PNG.
- **Mask cache.** Cache intermediate masks keyed by (drawable, geometry
  params, size). Moving a texture slider must not recompute geometry.
- **Exact algorithms.** Implement them in Kotlin: Felzenszwalb exact Euclidean
  distance transform, connected-component labelling, and morphology via
  distance thresholds. Thresholds beat iterated dilation, which is slow and
  square-shaped.
- **Performance budgets** on a Pixel 6a-class phone. Verify on the emulator
  and state the numbers:
  - Preview: 24 icons at 128 px in under 150 ms.
  - Live slider drag: 30 fps or better.
  - Apply: 300 icons at full size in under 20 s, on background dispatchers,
    with progress shown.
- **Pure and deterministic.** The same theme always produces the same bytes.
  Seed every random source.

### 3.4 Template pack
- **Package name.** One fixed name derived from the designer's applicationId,
  e.g. `<appId>.pack`. Label: "<App name> Pack".
- **Activity.** Include **one real, tiny activity**. The spike used
  `hasCode="false"` with a fake class, which is fine for detection, but some
  launchers or dashboards launch the pack's activity and would crash. The
  real activity just opens the designer.
- **Intent filters.** Proven set:
  - `org.adw.launcher.THEMES`
  - `org.adw.ActivityStarter.THEMES`
  - `com.novalauncher.THEME`
  - `com.teslacoilsw.launcher.THEME`
  - `com.anddoes.launcher.THEME`
  - `com.dlto.atom.launcher.THEME`

  Also research and add others in common use. `app.lawnchair.icons.THEMED_ICON`
  was seen in Kvaesitso's code, but it means something else, so understand it
  before adding it.
- **Slots.** 1024 slots, `res/drawable-nodpi/slot_0000.png` up to
  `slot_1023.png`. Each is a 1x1 PNG, stored uncompressed via aapt2
  `--no-compress-regex`. Justify or change the count. If an install exceeds it,
  show a clear error.
- **appfilter.** `assets/appfilter.xml` is rewritten on Apply.
  `res/xml/appfilter.xml` is a minimal valid stub. Also generate
  `assets/drawable.xml` listing the used slots, so launcher "pick icon" pickers
  show the themed icons. Verify that it does in Lawnchair and Neo.
- **Pack app icon:** a generated preview of the theme.
- **License.** Include the CC BY-SA 4.0 text and Arcticons attribution in the
  pack's assets.

### 3.5 Rewriter and signer
- **Rewriting.** Read the template from the designer's assets and stream
  entries into a new zip:
  - Skip `META-INF/`.
  - Replace the slot PNGs and the generated assets.
  - Keep entry order.
  - Keep the PNGs and `resources.arsc` STORED.
  - **Align the data of every STORED entry to 4 bytes** by padding the local
    header extra field, the same thing `zipalign -p 4` does.

  Verify with `zipalign -c -p -v 4` in unit tests.
- **Signing.** Use `com.android.tools.build:apksig`, Apache-2.0, from Google
  Maven. F-Droid accepts that. Sign with v2 and v3 schemes, since v1 isn't
  needed at minSdk 33.
- **The key.** Generate it on first Apply, as EC P-256 or RSA 2048; decide
  which. Store it as PKCS12 in `noBackupFilesDir` with a random password kept
  in the same app sandbox.
  - **If the designer is uninstalled, the key is gone**, and the old pack can
    no longer be updated. See §6.5.
- **Determinism.** The same theme and the same installed-app set produce a
  byte-identical APK before signing.

### 3.6 Installer
- **API.** Use the `PackageInstaller` session API with
  `REQUEST_INSTALL_PACKAGES`. If the user hasn't granted "Install unknown
  apps" to the designer, send them to the settings toggle with a one-line
  explanation.
- **Confirmation.** Handle `STATUS_PENDING_USER_ACTION` by launching the
  confirmation intent. Report success and every failure code in plain words.
- **Signature mismatch.** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means a pack
  signed with a different key is installed. Explain it, offer to uninstall
  the old pack via `ACTION_DELETE`, then retry.

---

## 4. Repo, tooling, environment

- **Root.** `/mnt/storage/Claude/icon-designer/`. Create the git repo there,
  with the app in the repo and `spike/` kept as reference. Open a worktree per
  `shared/bin/wt` conventions if the repo grows parallel branches. Follow
  `/mnt/storage/Claude/CLAUDE.md` in full. In particular:
  - Run `ListAgents` and ping peers before starting. This is a **new,
    separate project**. Say so, and ignore cross-project chatter.
  - Every message to the user ends with the Decisions block and the binary
    Status line.
  - Never touch `/mnt/storage` outside `/mnt/storage/Claude`.
- **JDK.** The default `java` here is 25. Use the JDK that your chosen AGP
  version supports, probably `/usr/lib/jvm/java-21-openjdk`, through
  `org.gradle.java.home` or a toolchain.
- **Versions.** **Look up current stable versions** of AGP, Gradle, Kotlin,
  Compose BOM and apksig before pinning them. Don't guess version numbers.
- **SDK.** `~/Android/Sdk` has build-tools 35.0.0 and 36.0.0, platforms 35 and
  36, emulator 37.3.3, and NDK 27.1.
- **Emulator: this machine needs `-gpu host`.** Every software renderer
  segfaults since the Fedora 44 upgrade. Launch headless and detached:
  ```
  systemd-run --user --unit=icondesign-emu --collect ~/Android/Sdk/emulator/emulator \
    -avd icondesign36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu host -memory 4096
  ```
  Stop it with `adb emu kill`. To wait for boot, poll
  `adb shell getprop sys.boot_completed`.
- **AVDs.**
  - `icondesign36` is AOSP Android 36. It's the closest to GrapheneOS, so use
    it as the primary.
  - `icondesign` is Google APIs Android 35, with more stock apps to theme.
  - **Never touch `picklr_test`.** It belongs to another project.
- **Launchers under test.** APKs are in `spike/launchers/`:
  - Neo, `com.saggitt.omega`
  - Lawnchair, `app.lawnchair`
  - Kvaesitso, `de.mm20.launcher2.release`

  Set the home app with
  `adb shell cmd package set-home-activity <component>`.
- **UI driving.** `spike/ui.sh` provides `shot <name>`, `texts`, and
  `tap "<regex>"`.
  - Known menu paths:
    - **Neo:** long-press home, then Home settings, Profile, Icon Packs.
    - **Lawnchair:** long-press, then Home settings, General, Icon style.
    - **Kvaesitso:** More actions, then Settings, Grid and icons, scroll,
      Icon pack.
  - First-run permission dialogs appear. Dismiss them by text.
- **Headless only.** Never open a visible emulator window or any GUI window.
  If a window is ever unavoidable, never put it on the left monitor. All
  verification goes through adb screenshots, cropped with ImageMagick.
- **Throttle.** Check `free -g` before heavy builds. Cap batch render loops.
  The machine has frozen once from a runaway loop.

---

## 5. Milestones and acceptance gates

**Look at every screenshot yourself before you claim a gate passed.** A gate
passes on evidence, never on "it should work". Each gate's evidence goes in
`docs/verification/<milestone>.md`, with screenshot paths.

| # | Milestone | Gate |
|---|---|---|
| M0 | Repo scaffold, submodule, module layout, CI-free local build | `./gradlew assembleDebug` succeeds from a clean clone with the submodule. License files are in place. |
| M1 | Icon converter, bundle, appfilter compiler | IoU gate from §3.2. Bundle size reported. Malformed appfilter entries counted and logged. |
| M2 | Renderer: shape, fill modes, background | Golden-image unit tests for 24 sample icons in each fill mode. Lone-line handling shown on F-Droid, Camera and Chrome. |
| **M3** | **Pack generation, signing, install, restart flow, plus a minimal "Apply plain theme" button** | **On both AVDs, in all three launchers:** (1) fresh Apply, then select pack, then themed icons show; (2) change the theme, Apply, follow the restart guidance, then new icons show. Screenshots of each. `apksigner verify` and `zipalign -c` pass on the produced APK. **Stop and report at this milestone before building the editor.** |
| M4 | Theme storage plus editor with the Shape, Fill and Background tabs, live preview, undo | Slider-drag fps and preview timings measured and reported. |
| M5 | Layers, texture import, blend modes, masks | A black-and-white base texture plus blend modes reproduces at least 3 distinct looks. Screenshots. |
| M6 | Effects, streaks first | The Jade preset matches the user's description: black rounded square, jade texture, thicker icons, textured low-opacity streaks between icon and background. **Send him the screenshot for a taste check.** |
| M7 | Per-icon overrides, coverage screen, export and import, key export | Round-trip export and import of a theme with textures, verified byte-identical in render output. |
| M8 | F-Droid readiness | fastlane metadata structure, no non-free deps (`./gradlew dependencies` reviewed), build works offline after the first sync, reproducibility attempted and its result reported. |

Unit tests that must exist:
- zip alignment
- signature verification
- appfilter generation and escaping
- invert fill on fixtures
- theme JSON migration
- deterministic render output

---

## 6. Details people forget

1. **Escaping.** Package and activity names go into XML. Escape them.
2. **Alias activities.** Components with `$` or activity aliases need the
   exact ComponentInfo string from `queryIntentActivities`.
3. **Duplicate drawables.** Several components map to one drawable. Write it
   once and share the slot.
4. **The pack has no runtime state.** Everything is in its resources and
   assets.
5. **Key loss.** Offer "Export signing key" inside the theme backup, behind
   an explicit warning. On a fresh install with no key, the first Apply
   detects an existing pack signed by another key and walks the user through
   uninstalling it.
6. **Restart guidance table.** Keep a table of known launchers keyed by
   package. Each entry says: how to select the pack the first time, how to
   restart, and an optional deep link.
   - **Neo** has "Restart" in its settings; find the exact path.
   - **Lawnchair** has "Restart Lawnchair".
   - **Kvaesitso** has none.
   - **Unknown launchers** and Kvaesitso: deep link to App info with
     `ACTION_APPLICATION_DETAILS_SETTINGS` and say "tap Force stop".

   Research whether any of these launchers accept an intent that applies a
   pack or reloads icons, the way dashboards like Blueprint or Candybar do.
   Ship only what you verified on the emulator.
7. **GrapheneOS specifics.** Installing from an app uses the standard "Install
   unknown apps" permission. Don't use any privileged APIs. Assume no Google
   services. Test on the AOSP AVD.
8. **Accessibility.** Content descriptions on the preview icons. Sliders
   usable with TalkBack.

---

## 7. Out of scope for v1
- dynamic calendar icons
- widgets
- wallpapers
- Android 13 themed (monochrome) icon support
- non-Arcticons icon sources
- a cloud theme gallery
- the in-app Arcticons updater

Note these in the README as future work.

---

## 8. How to work and report

- **Decide technical questions yourself.** Implementation, libraries, structure,
  git: report what you chose and why in one line.
- **Ask the user only taste questions**, short and plain, when the decision is
  actually in front of you:
  - the app name
  - the starter preset looks
  - the default theme
  - which effects come after streaks
- **No speculative abstractions.** Build what this brief asks for, simply.
- **Verify before claiming.** For anything visual, the proof is a screenshot
  you looked at.
- **Never yield with "standing by".** A turn ends only with one of:
  - a finished milestone and its gate evidence
  - a named blocker plus exactly what unblocks it
  - a long-running job in flight that you've named, along with how you'll
    know it finished
- **After M3, stop and report before continuing.** That is the
  feasibility-in-a-real-app checkpoint.
