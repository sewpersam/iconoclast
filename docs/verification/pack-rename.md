# Fresh pack package per Apply (2026-10-16)

His pick, option B: each Apply installs a new copy of the pack and removes the old one.

AVD icondesign36, AOSP 36, unit `icondesign-emu`, `-gpu host`, headless. Neo 0.9.3,
Lawnchair, Kvaesitso. Screenshots by `adb exec-out screencap`, drawer crops.

## Mechanism

- Template built as `dev.iconoclast.app.pack.g000000`. Apply patches that string, same
  length, in the binary manifest and `resources.arsc` (`PackName.patch`). Exactly one
  occurrence each, else it fails. The manifest is inflated with `Inflater`; nothing deflates.
- Next generation: above the stored last one and every pack still installed. Stored in
  shared prefs `pack/generation`, written with `commit()` after a successful install.
- Packs found by `org.adw.launcher.THEMES`, which the manifest now queries instead of
  one fixed package. The legacy `dev.iconoclast.app.pack` counts as generation 0.
- After the install succeeds, every other pack gets the system uninstall prompt. A
  cancel leaves it; the result screen offers "Remove the old copy", and the next Apply
  asks again.
- Update ownership and `UPDATE_PACKAGES_WITHOUT_USER_ACTION` removed: nothing is ever
  updated in place now.

## Broken first, on main

`neo-main-vs-branch-no-restart.png`, panels 1 and 2: main applied Neon, Neo picked it.
Then main applied Jade (silent update), Neo switched to System icons and back, no
restart. Every icon stayed Neon.

## Neo on the branch

Neo 0.9.3 builds its Icon Packs list once per process: `themeIconPackGlobal.entries` in
`OmegaPreferences`, a singleton. A pack installed after Neo started is not in the list.
`neo-picker-stale.png`: the entry labelled Iconoclast Pack still pointed at the removed
package; picking it wrote nothing (`pref_icon_pack_package` unchanged). So the fresh
name alone cannot skip Neo's Restart.

`neo-branch-sequence.png`, left to right:
1. Jade applied as g000002, picked without restart: still Neon.
2. Neo Restart, then pick: all Jade.
3. Neon applied as g000003, picked before restart: still Jade, nothing harmed.
4. Restart, then pick: all Neon.
5. Mullvad installed while Neon g000003 active: stock icon.
6. "Apply Neon to 1 new app" as g000004, Restart, pick: Mullvad themed.

Phone keeps its stock icon after the first Restart and pick (panels 2, 4, 6), as on
main. One more Restart themes it: `neo-second-restart-phone.png`.
`neo-final-jade.png`: final build, g000009, same result.

## Lawnchair and Kvaesitso

Both list the new pack live and reset to System icons when the old one is removed.
Picking the new pack once switched every icon with no force stop; launcher pid unchanged.
`lawnchair-neon-to-jade.png`, `kvaesitso-jade-to-neon.png`.

## Cancel path

Jade applied, uninstall prompt cancelled: g000006 and g000007 both installed, the screen
said an older copy is still installed and showed "Remove the old copy". Tapping it gave
one prompt; OK left only g000007.

## Tests

- `PackNameTest` (6): fixed-length names, legacy and foreign names, counter, patch in
  both encodings, and the real template renamed: `aapt2 dump badging` shows the new
  package and the label "Iconoclast Pack", `dump resources` shows the new package,
  badging otherwise identical, zipalign passes.
- `BuiltPackTest`: the package name is part of the recipe.
- `ArtByteIdentityTest` on icondesign36: 2 of 2 passed.
