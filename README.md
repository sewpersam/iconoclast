# Iconoclast

Iconoclast is an Android app for building your own icon pack from the
Arcticons line icons. You style the icons on the phone: stroke weight, fill,
background, textures and effects. The app then generates a real icon pack
APK, signs it on the device and installs it, so any launcher that supports
icon packs can use it. It needs no network and no Google services.

## Build

Requirements:

- JDK 21. Gradle picks it from `gradle/gradle-daemon-jvm.properties`, so a
  different default `java` on the path is fine.
- Android SDK with platform 37 and build-tools 36.0.0 or newer. Point
  `local.properties` at it: `sdk.dir=/path/to/Android/Sdk`.

```
git clone --recurse-submodules <repo-url> iconoclast
cd iconoclast
./gradlew assembleDebug
```

If you cloned without submodules, run `git submodule update --init` first.
The debug APKs land in `app/build/outputs/apk/debug/` and
`template-pack/build/outputs/apk/debug/`.

Modules:

| Module | Contents |
|---|---|
| `:app` | Compose UI, ViewModels, theme storage, Apply flow |
| `:render` | Icon geometry and compositor. Pure Kotlin JVM, no Android dependency |
| `:pack` | Template asset access, zip rewriter, aligner, signer, installer |
| `:iconsource` | Loader for the bundled icon data and the appfilter index |
| `:template-pack` | The icon pack APK template |
| `build-logic` | Included build: SVG-to-bundle converter, appfilter compiler |

Arcticons is a git submodule at `third_party/arcticons`, pinned to one
commit. Bumping the pin is a deliberate commit.

## Licence

App code is GPL-3.0-or-later, see `LICENSE`. The Arcticons icons are
CC BY-SA 4.0, see `THIRD_PARTY_LICENSES.md`.

## Future work

Out of scope for v1:

- dynamic calendar icons
- widgets
- wallpapers
- Android 13 themed (monochrome) icon support
- non-Arcticons icon sources
- a cloud theme gallery
- the in-app Arcticons updater
