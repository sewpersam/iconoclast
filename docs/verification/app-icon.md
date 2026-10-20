# App icon and bundled glyph (2026-10-19)

His words: "I'd like if we could get an icon for our app pack and then also, like, have it included ... so that when you customize all the themes for the other apps, it also does ours app. But it's bundled with our app."

## Change

- Glyph `iconsource/bundled/iconoclast.svg`: a cracked icon tile in Arcticons line style, 48 grid, 1-unit round strokes (`app-icon/glyph.png`). CC BY-SA 4.0, Iconoclast contributors.
- The converter reads `iconsource/bundled/*.svg` beside Arcticons' SVGs; drawable `iconoclast`. The build fails if a bundled name shadows an Arcticons one.
- `iconsource/bundled/appfilter.xml` maps `dev.iconoclast.app/dev.iconoclast.app.MainActivity` to it, compiled with Arcticons' appfilter and winning over it.
- `InstalledApps` no longer drops Iconoclast itself, so every pack carries its entry and the Neo shotgun re-points it like any app.
- Launcher icon: adaptive vector, white glyph on black, monochrome layer for themed icons (`res/mipmap-anydpi/ic_launcher.xml`).
- F-Droid `fastlane/.../images/icon.png`: same glyph, 512 px, from `app-icon/store-icon.svg`.
- Credits: pack NOTICE, bundle NOTICE, THIRD_PARTY_LICENSES.

## Proof

| Check | Result |
|---|---|
| `BundledGlyphTest` | 2 tests. `iconoclast` is in the icon set; the launcher activity resolves to it; Arcticons entries still resolve |
| Same test, bundled entries left out of the table | 1 of 2 fails |
| `:iconsource` 14, `:app` 149, `:pack` 30 unit tests; `:app:lintDebug` | 0 failures; no icon lint findings |
| Emulator, before the `InstalledApps` fix | pack appfilter had no `dev.iconoclast.app` entry; Neo result "23 apps. 1 has no icon in the pack" |
| After | pack appfilter `ComponentInfo{dev.iconoclast.app/dev.iconoclast.app.MainActivity}` -> `slot_0012`; "Neo shows the pack's icon on 24 apps" |
| Jade in Neo's drawer, after the shotgun | `app-icon/drawer-jade.png`, `iconoclast-jade.png` |
| Neon in Neo's drawer | `app-icon/drawer-neon.png`, `iconoclast-neon.png`, `apply-neon-result.png`. Neon carries the test image background from the update check below |
| Launcher icon | `app-icon/launcher-icon-appinfo.png`, Android's App info |
| ART identity | renderer untouched by this branch; last run on icon-follows-bg, 538 PNGs, 0 differ |

## Update keeps themes

On icondesign36, Neo 0.9.3 home.

1. Installed the phone asset then current, sha256 `0f38cbbd...`, release key.
2. Made: Jade at Background size 70% with a per-app override on Arcticons (Line thickness 3.5x, Lines only); Neon with an image background picked from photos and cropped; a new theme with Brush strokes; Brush B in use. Applied Brush B; the shotgun ran.
3. `adb install -r` of this branch's release build, same key. `firstInstallTime` unchanged, `lastUpdateTime` moved: an update, no uninstall.

| After the update | Result |
|---|---|
| Themes | all 7 listed, Brush B still In use (`update-home-before-after.png`, 0.01% pixels differ: the clock) |
| Override | Arcticons still "changed for this icon" in Jade |
| Image | Neon's image background intact |
| Schema 11 migration | Jade's Background size still 70%; Icon size now reads 70% of the shape, was 0.8 of the safe zone; preview pixel-identical to before (`update-jade-before-after.png`, RMSE 0) |
| Shotgun | accessibility service still enabled and rebound after the update; Apply Jade then Neon each ran 24 of 24 |

Nothing was lost.
