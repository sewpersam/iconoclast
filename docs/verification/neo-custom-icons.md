# Neo custom icons (2026-10-16)

His report after the pick-race round: system apps, Obtainium apps, VLC, Mullvad and Brave
never theme. Restarts do not help. Other apps switch live with each theme.

## Pack side, from his diagnostics

Every component in the pack, `missing ... 0`. Arcticons has one entry per stuck component,
in the same `ComponentInfo{package/full.Class}` form. Slots for stuck and themed apps come
from the same pack. Nothing in the pack differs per app. The appfilter-order idea
was dropped: main already writes installed components first, and restarts cure the race.

## Neo side, in 0.9.3 source

- `CustomIconProvider.resolveIconEntry` reads `IconOverrideRepository.overridesMap` first.
  An override is a custom icon set with long-press, Edit.
- `IconPackProvider.getDrawable` returns null when the override's pack is not installed.
  Neo then draws the app's own icon. Restarts, picks and theme changes never reach it.
- 0.9.3 cannot delete an override. "Remove Custom Icons" exists only on Neo HEAD.
  0.9.3, December 2023, is the latest release.
- Overrides live in `NeoLauncher.db`, table `IconOverride`. Neo's home screen backup
  carries that file. Restore writes each entry back by name.
- Pack-rename uninstalls the previous pack on each Apply. Any custom icon picked from an
  Iconoclast pack goes dangling at the next Apply.

## Measured on icondesign36, Neo 0.9.3

| Step | Result |
|---|---|
| Override row for VLC to `dev.iconoclast.app.pack` (not installed) | VLC stock |
| Apply Neon, Restart, pick, Restart | every app Neon, VLC stock (`dangling-override-vlc-stock.png`) |
| Neo backup, Custom location, Downloads | `.zbk` with 10 entries |
| Iconoclast, Check Neo backup | 2 custom icons, VLC `PACK GONE`, dialer to Arcticons `installed` |
| Save a copy without custom icons | original md5 unchanged; copy differs only in `NeoLauncher.db` (0 rows, integrity ok) and an empty `-shm` |
| Neo Restore of the copy, all boxes | 0 override rows, 5 home items as before |
| Restart | VLC still stock: the icon cache keeps it while the pack is unchanged |
| Icon Packs: System icons, then Iconoclast Pack | VLC themed (`after-clean-restore.png`) |

## Change

- Copy diagnostics loads each drawable as Neo does: `getIdentifier(name, "drawable", pkg)`
  then `getDrawableForDensity`. Each component shows id, size, opaque pixels and a pixel
  hash, for the pack and for Arcticons.
- Apply screen: Check Neo backup for custom icons, read only, lists every override.
  Save a copy without custom icons writes a new file and reads it back first.
- Neo guidance: do not give apps an Iconoclast icon by hand.

His phone has not run the check yet. Overrides are the only per-app Neo state
that survives restarts; whether he has them is unproven until he runs it.
