# Phone feedback round 1: pack, Apply, import

icondesign36 (AOSP 36), Neo 0.9.3, debug builds installed as `dev.iconoclast.fbpack`
with pack `dev.iconoclast.fbpack.pack`, so the shared emulator's other installs were
untouched. Baseline is 9cf7dbd built the same way. Shots in `fb-pack/`.

## 1. Install flow

- Baseline: no install permission gives a dead end with an "Allow installing" button
  (`base-1-permission-problem`). A pack signed with another key gave a second dead end.
- Now: Apply says one line, opens the "Install unknown apps" switch, and continues when
  the user comes back with it on. The process survived the grant on API 36.
- Key mismatch, made real with `pm clear` (new key, old pack still installed): Apply drew
  the pack, said one line, and opened Android's uninstall prompt itself
  (`mm-4-uninstall-prompt`). OK went straight to the install prompt and the pack
  installed (`mm-6-installed`). Log: one render, `old pack uninstalled: true`,
  `install: Success`.
- Cancel at the uninstall prompt: "Nothing changed ... Tap Apply to try again"
  (`mm-7-cancelled`). Apply again reused the drawn pack (`built pack reused in 1 ms`).
- The fresh-install confirmation after an uninstall stays: Android asks for any install
  that the designer does not own yet.

## 2. Coverage

Neo (`CustomIconPack.kt`, `CustomIconProvider.kt` at bc028dd) matches the exact
`ComponentInfo{package/activity}` of `getLaunchIntentForPackage(package)`. The baseline
already counted by exact component, so its count matched the pack; it missed apps whose
installed activity Arcticons lists under an older or other build's name.

- Stub `de.danoeh.antennapod` with activity `.ui.NewSplashActivity`: baseline counted it
  not covered and Neo showed the stock icon (`base-5-neo-pick4`). Now it maps through its
  package, the pack writes its installed component, and Neo themes it (`new-4-crop`).
- The pack also carries every Arcticons entry of each drawn drawable (1807 entries here),
  and each package's launch-intent component, which Neo looks up.
- Count is by app (package), the way a launcher shows it.

Neo staleness, reproduced: Neo keeps the old pack in memory until it restarts, and caches
each icon until the pack setting changes or that app updates. Switching packs before a
restart cached the old icons again; they stayed after a later restart
(`new-3-neo-wrong-order-stale`) and the stub changed only when it was reinstalled
(`new-4-crop`). Restart first, then System icons and back, refreshed every app
(`new-5` to `new-6`). Phone alone once needed a second restart (`base-5` to `base-6`).
Neo's guidance now gives the restart-first order for the first selection too, since a
pack from an earlier install leaves its icons cached.

New apps: an app installed after Apply keeps its own icon. The Apply button gets a badge,
and the Apply screen offers "Apply <theme> to 1 new app" (`newapp-2-apply-to-new`); after
it and the Neo refresh the new app is themed (`newapp-3-crop`).

## 3. Applied state

Baseline: "Apply Jade" after Jade was applied (`base-4-still-says-apply-jade`). Now:
"Jade is applied", disabled (`new-2-installed`). Hiding a layer brings back
"Apply Plain Arcticons"; showing it again brings back the applied state.

## 4. Coverage screen

Removed, with the home "Apps" button. One quiet line remains: "1 app has no Arcticons
icon yet and keeps its own."

## 5. Image link and share

- "Paste image link" in the image sheet (`link-1-dialog`). An http link is fetched over
  https; a web page link says so (`link-2-page-error`); an image opens the crop step
  (`link-3-crop-step`). Limits: 20 MB, image or octet-stream content type, 10 s connect,
  15 s read, 30 s overall, 5 redirects. HttpURLConnection only.
- Share from Gallery lists "Add to an icon theme" (`share-0-chooser`); it opens the theme
  in use with a new image layer at the crop step (`share-1-crop`).

ArtByteIdentityTest: 2 tests passed on icondesign36. `./gradlew check` green.
