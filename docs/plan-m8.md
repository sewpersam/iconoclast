# Plan: icon-designer milestone M8

Spec: `PROMPT.md`, the binding authority: the M8 row of §5, §0 ("F-Droid
clean from the first commit"), §2.1 (no proprietary dependencies), §3.2 (the
submodule exists so F-Droid can build offline). Read `docs/followups.md` in
full: its "M7 addendum" deferral is Task 18.

## Global constraints (apply to every task)

- Same as `docs/plan-m7.md` and the plans it points to, in full.
- Every F-Droid claim rests on a page read on 2026-10-11, cited below.
  Nothing is quoted from memory.
- No new dependency. If one changes, `:app:writeDependencyLicenses` reruns
  and its output is committed.
- The release build F-Droid runs is `assembleRelease` with no signing config:
  AGP writes `app-release-unsigned.apk`, F-Droid signs it.
- Gate evidence in `docs/verification/m8.md`, screenshots under
  `docs/verification/m8/` from icondesign36. Only Task 18 needs the emulator.

### Sources read

| Topic | URL | What it fixes here |
|---|---|---|
| Inclusion policy | https://f-droid.org/docs/Inclusion_Policy/ | FLOSS licence (DFSG, FSF, OSI); prebuilt binaries only from trusted Maven repos (Maven Central, Google Maven named) and the Android SDK; 100% FLOSS toolchain. |
| Descriptions, graphics, screenshots | https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/ | `fastlane/metadata/android/en-US/`: `short_description.txt` (max 80, mandatory), `full_description.txt` (max 4000, mandatory), `title.txt` (max 50), `changelogs/<versionCode>.txt` (literal, no padding, max 500), `images/icon.png`, `images/featureGraphic.png` (landscape), `images/phoneScreenshots/`. en-US is the fallback locale. |
| Reproducible builds | https://f-droid.org/docs/Reproducible_Builds/ | v2/v3 signatures cover every byte, so the unsigned APK must match exactly; AGP zeroes zip timestamps; pin build-tools; known nondeterminism in `baseline.prof`/`baseline.profm` (fix: disable the ArtProfile tasks); `META-INF/version-control-info.textproto` (vcsInfo) is kept, build from a clean tree at the tag. |
| Build metadata reference | https://f-droid.org/docs/Build_Metadata_Reference/ | Recipe fields: `License` (one SPDX id), `Categories` (a list), `RepoType`, `Repo`, `Builds` with `versionName`, `versionCode`, `commit` (full hash, not a tag), `subdir`, `submodules: true` (runs `git submodule update --init --recursive`), `gradle: [yes]`; `AutoUpdateMode: Version` with `UpdateCheckMode: Tags`; `UpdateCheckData` to read the version code from a file and the name from the tag. |
| Categories | https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml | `Icon Pack` exists. |
| Scanner | https://gitlab.com/fdroid/fdroidserver/-/raw/master/fdroidserver/scanner.py | Every SUSS `gradle_signatures` entry is an error regex `'.*' + sig`, case-insensitive. Flags an APK signing block `0x504B4453`, "Dependency metadata". |
| Non-free signatures | https://fdroid.gitlab.io/fdroid-suss/suss.json | The list the scanner downloads (AGPL-3.0, so it is fetched, never committed). |

## Task 14: fastlane metadata and the F-Droid recipe

- `fastlane/metadata/android/en-US/`: `title.txt` "Iconoclast",
  `short_description.txt`, `full_description.txt` with the Arcticons CC BY-SA
  4.0 credit, `changelogs/1.txt` for versionCode 1, `images/icon.png` (the
  launcher icon at 512 px). A `featureGraphic.png` only if it can be made
  from the app's own renders (the golden PNGs are Iconoclast output from
  Arcticons geometry, CC BY-SA 4.0 with credit) and credited; it is optional,
  so it is skipped if that is in doubt. No screenshots yet: the UI is not
  his-approved for public listing.
- A metadata test in `build-logic` checks the length limits and that a
  changelog exists for the app's current versionCode.
- `fdroid/dev.iconoclast.app.yml`: fdroiddata-style draft. `License:
  GPL-3.0-or-later` (LICENSE is the GPLv3 text; README states "or later"),
  `Categories: [Icon Pack]`, `RepoType: git`, `Repo:
  https://github.com/sewpersam/iconoclast.git`, one `Builds` entry with
  `subdir: app`, `submodules: true`, `gradle: [yes]`, `commit:` the full
  hash of the tag; `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`,
  `UpdateCheckData: app/build.gradle.kts|versionCode\s*=\s*(\d+)||v(.+)`.
- F-Droid hygiene in `app/build.gradle.kts`: `dependenciesInfo`
  `includeInApk = false` and `includeInBundle = false` (the scanner rejects
  the encrypted dependency block).
- Tag scheme `v<versionName>`, documented in README. `v0.1` is applied to
  the commit that passes every gate, last, after the evidence commit.

## Task 15: dependency review

- Reports: `:app:dependencies --configuration releaseRuntimeClasspath`,
  `buildEnvironment` for the root and every module (AGP, Kotlin, build-logic).
- Origins: `fdroid/check_origins.py` asks Google Maven and Maven Central for
  every resolved `group:name:version` POM; zero may be found in neither.
  Settings declare only `google()` and `mavenCentral()` with
  `FAIL_ON_PROJECT_REPOS`, so nothing else can be the origin; a build-logic
  test pins that no build file declares any other repository.
- Non-free: the same script runs every suss.json gradle signature against
  every coordinate, as the scanner does. Broken first on a fake report with
  play-services and Crashlytics.
- `checkDependencyLicenses` extended: fails on a licence outside a free
  SPDX allowlist and on a coordinate in a non-free group list taken from
  suss.json (Play Services, Firebase, Crashlytics, Fabric, Facebook, Play
  Core, billing, install referrer, ads). Tests for both, failing first.

## Task 16: offline build

- Fresh clone of `origin` into the scratchpad with `--recurse-submodules`
  (`--shallow-submodules` to fit tmpfs: the full Arcticons history is 4.1 GB;
  the checked-out tree is identical), `ANDROID_HOME` set, no
  `local.properties`. `./gradlew assembleDebug` (M0 gate) and
  `assembleRelease` online once.
- A second fresh clone builds `./gradlew --offline assembleRelease` inside
  `unshare -rn` (a network namespace with only loopback), so any task that
  reaches the network fails, Gradle or not.
- Any failure is fixed in the build and the proof rerun.

## Task 17: reproducibility

- Two fresh clones of the same commit, release APK from each, separate
  Gradle daemons stopped between, compared by sha256 of the whole file and of
  every zip entry (`unzip`), plus `diffoscope` from a venv if pip can install
  it without system packages.
- Fix cheap nondeterminism found (ART profiles, timestamps, ordering), and
  rerun. The verdict is reported as measured, with every differing entry and
  its cause.
- Cross-machine limits stated: same JDK, same build-tools 36.0.0 (the
  template's aapt2 and d8), same SDK platform.

## Task 18: damaged signing key

- `PackKeyStore` distinguishes no key, a good key, and a damaged key (files
  present but empty, truncated, wrong password, or the store without its
  password file). A damaged key throws `DamagedKeyException`, never a raw
  keystore error, and is never overwritten silently.
- `restore` over a damaged key sets the damaged files aside
  (`*.damaged`), writes the backup, and reports RESTORED.
- Apply checks the key before drawing. A damaged key shows "The pack signing
  key on this phone is damaged" with two actions: restore from a theme
  backup (opens the import picker; the import offers the key because the
  phone has no usable one) and make a new key (sets the damaged files
  aside; the existing spec 6.5 mismatch flow then handles the installed
  pack).
- Fresh install (no files, or a password with no store) keeps creating a key
  on first Apply, unchanged; a test pins it.
- Failing-first JVM tests in `:pack` and `:app`; emulator screenshots of the
  message, the restore and the Apply that follows.

## M8 review

Self-review section "M8 additions" in `docs/followups.md`: deferred items,
rulings made on his behalf with the cost if wrong.
