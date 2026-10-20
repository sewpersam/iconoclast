# Neo custom icons fix (2026-10-19)

His report: VLC, Mullvad, Brave, system and Obtainium apps never take the Iconoclast
look. Switching Neo to Arcticons or System icons "has no issue".

## His phone, from NL_2026_10_19_19_40_07.zbk

- `IconOverride`: 25 rows, all Arcticons. 19 `com.donnnno.arcticons`,
  5 `.daynight` (VLC, Gallery, OnlyOffice, AntennaPod, Tor), 1 `.light` (Brave).
- Same shape as a row Neo writes from long-press, Customize, icon, tap a pack icon.
  Nothing else in 0.9.3 writes the table.
- Picked pack `dev.iconoclast.app.pack.g000007`.
- Neo's icon cache holds no `com.donnnno.*` app. Arcticons may not be installed now.
  Then those 25 apps show their own icon under every pack.

## Neo 0.9.3 source (tag 0.9.3, 022833f)

| Code | What it does |
|---|---|
| `CustomIconProvider.resolveIconEntry` | Custom icon first, for any picked pack. No compare with the picked pack. |
| `IconPackProvider.getDrawable` | Loads from the custom icon's own pack. Pack or drawable missing: the app's own icon, not the picked pack. |
| `IconOverrideRepository` | `deleteOverride`, `deleteAll` exist; nothing calls them. No UI removes one. |
| `EditIconScreen` | Top row: each listed pack's icon for the app. A tap writes the custom icon and finishes. |
| `ComposeActivity` | `exported="true"`, deep link `android-app://androidx.navigation//edit_icon/<pkg>/<class>#0/`. |
| `IconOverrideRepository.setOverride` | `reloadIcons`: clears Neo's icon DB and reloads, no restart. |
| `IconOverrideRepository` init | Loads the table asynchronously. Icons drawn first after a restart can miss it. |

So Arcticons is not special. His custom icons point at Arcticons, so Arcticons looks right.

## Measured on icondesign36, Neo 0.9.3

Arcticons, Black (`.light`), Day & Night, then Material You from F-Droid 15.3.2.
Custom icons set in Neo as a user does: VLC to Day & Night, Mullvad, Phone, Clock,
GOS Camera to Arcticons, Gallery to Day & Night, Contacts to Black.

Broken first, main 6f93412 (`broken-iconoclast-system-arcticons.png`):

| Neo picked pack, after Restart | The 7 apps show |
|---|---|
| Iconoclast (Neon) | Arcticons line icons, not Neon |
| System icons | Arcticons line icons. Phone was stock: the startup race above |
| Arcticons | Arcticons, looks uniform |

Fixed, branch (`fixed-point-apply-sixpacks.png`):

| Step | Result |
|---|---|
| Neo custom icons, tick 6 apps, Give them the pack icon | Accessibility settings open once; service turned on |
| Run | 6 Neo screens, 2 s total; log `5 icons, expected 5, tap 4: true` each |
| Neo DB | 6 rows now `g000012`, drawables equal the pack appfilter's |
| Drawer, no Neo restart | 6 Neon; GOS Camera, not ticked, still Arcticons |
| Apply Neon to 2 new apps | g000013 installed, g000012 removed, then the 6 re-pointed automatically |
| Neo DB | rows `g000013`, slot names changed (VLC `slot_0019` to `slot_0021`) |
| Restart, pick, Restart | all Neon |
| Material You installed: Iconoclast icon fully past the edge | `6 icons, expected 6, tap 5: true` for 7 apps |
| Service off | Neo screen per app with a line to tap icon N; Back skips; no hang |

`neo-row-and-chooser.png`: Neo's row with the pack icon past the edge, and the chooser.

## Change

- Apply screen: "Neo custom icons". He ticks the apps Neo keeps on a custom icon.
  Iconoclast opens Neo's own icon screen per app. `NeoIconService` taps the pack's icon.
- After every Apply with Neo as home, the ticked apps are re-pointed at the new pack.
  Needed: each Apply renames the pack and renumbers its drawables.
- Service: Neo windows only, acts only while a run waits on one app, no overlay,
  no notification, no gestures. Without it he taps each icon himself.
- Row position computed as Neo does: System, Lawnicons, packs by label, only packs with
  an icon for the app. The service checks the count before tapping, else Back.
- Neo backup check and clean copy removed.

## Tests

- `NeoRowTest` (3): Neo's list order, position, no position when absent or doubled.
- `:app:testDebugUnitTest`: 141 tests, 0 failures.
