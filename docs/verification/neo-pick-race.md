# Neo pick race (2026-10-16)

His report after pack-rename: Install, Restart, pick. Aqua, Brave, Mullvad, Proton Mail,
Metro, OnlyOffice, VLC, GOS Camera, Calculator, Gallery and Phone keep their stock icons.

## Cause, in Neo 0.9.3 source

- Picking a pack calls `reloadIcons`, which clears Neo's icon DB and reloads the model.
- The new pack's `CustomIconPack` is built on the first icon lookup. Its appfilter is
  parsed on a background coroutine (`IconPack.startLoad`).
- `CustomIconProvider.resolveIconEntry` reads `componentMap` without
  `loadBlocking()`. Icons looked up before the parse reaches them get no entry and keep
  the app's own icon. Home screen and dock load first.
- Those stock icons go into the icon DB under a valid state. They stay until the app
  updates or the DB is cleared again. Play apps update often; store and system apps do not.
- Restart is the cure: at process start `IconPackChangeReceiver` builds the pack before the
  loader runs, and `verifyIconChanged` sees the stored `pack:` state differ, clears the DB
  and redraws every icon.

Installer is not a factor. Arcticons was immune only because it stayed picked across
many restarts and app updates.

## Measured on icondesign36, Neo 0.9.3

`neo-pick-race/icondb.sh` hashes each row of Neo's `app_icons.db` and compares it with a
System-icons baseline. Equal hash means a stock icon.

| Run | Stock launcher icons |
|---|---|
| Real pack, Neo started on System icons, pick | Phone |
| Same process, pick System icons, then the pack again | none |
| Real pack, restart with the pack set, 3 runs | none |
| Pack with 40000 dummy appfilter rows first (slow parse, as a phone), pick | 13 of 20: Phone, Camera, GOS Camera, Gallery, Metro, Calendar, Contacts, Clock, Files, Messaging, Search, Lawnchair, WebView |
| Same, pick System icons, then the pack again | none |
| Same, then Neo restarted twice | none |
| Branch build: Apply Jade (g000011), Restart, pick | Phone |
| Then Restart once more | none |
| VLC and Mullvad reinstalled with an installer set, System icons, Restart, pick, Restart | none |

`neo-pick-race/padded-pick-vs-second-reload.png`: the 40000-row pack after the pick, then
after one more reload. Left matches his list.

## Change

- Neo guidance: Restart once more after the pick, as a step, not a fallback.
- Apply screen: Copy diagnostics, one line per launchable app with installer, profile,
  last update, each launcher component, its drawable in the installed pack's appfilter
  (`MISSING` when absent) and the Arcticons drawable Iconoclast resolves now. On
  icondesign36 every component was in the pack, `missing ... 0`.

No pack bytes changed.
