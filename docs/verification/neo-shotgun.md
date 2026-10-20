# Neo shotgun (2026-10-19)

His call: no way to unset Neo custom icons from outside Neo, so re-point every app.
Screens: `neo-shotgun/`.

## Change

- Every launchable app the pack has an icon for gets the Iconoclast Pack icon in Neo.
  No list, no picking. The tick list and its saved apps are gone.
- Runs after every Apply with Neo as home. Also when the Apply screen opens and the
  installed pack is not on every app yet: service just turned on, Neo just made home,
  a run cut off.
- Service off: one line, a Turn it on button, the restricted-settings line. After the
  first Apply it opens Accessibility once by itself.
- Progress on the Apply screen: "Giving every app in Neo the pack's icon: n of N".
- Result line: "Neo shows the pack's icon on N apps. M have no icon in the pack."
- Apps with no pack entry are skipped. Nothing is pointed at System.
- Each launcher activity is its own Neo icon. A package with two gets two.
- Work profile: Neo keys by component and user id; the deep link carries the id.
  Open profiles are included, paused ones skipped.
- Lock or Home mid-run: the run waits. Unlock, or open Iconoclast again: it goes on.
  Done apps are saved per pack, so a killed run resumes where it stopped.
- Neo's row shows only on-screen icons to accessibility. The service walks the row page
  by page, counts every icon, then taps the pack's. One re-walk on a miscount.
- The service acts only on Neo's `ComposeActivity` window in front, titled with the app.

## Measured on icondesign36, Neo 0.9.3

Setup: 90 extra apps with real Arcticons-covered components, 3 with two launcher
activities. 117 launcher activities. Packs: Arcticons, Black, Day & Night, Material You,
Alpha, Mono, Zulu test packs. Iconoclast is icon 7 of 9, past the screen edge.
12 apps set to Arcticons in Neo's own icon screen, as his phone has.

Broken first, main f11cd43, nothing ticked:

| Step | Result |
|---|---|
| Apply Neon, pick Iconoclast, Restart | Camera, Clock x3, Contacts stay Arcticons line icons (`1-broken-main-drawer.png`) |

Branch:

| Step | Result |
|---|---|
| Open Apply, service off | One line and Turn it on (`2-service-off-line.png`) |
| Turn it on, back | Run starts by itself; progress n of 116 (`3-run-in-flight.png`) |
| Run, 6 packs | 116 of 116, targets 1.0 s, screens 37.1 s |
| Neo DB | 116 rows on the pack; drawables equal the pack appfilter, 0 mismatches |
| Drawer, no Neo restart | All Neon, the 12 Arcticons ones too (`4-after-shotgun-drawer.png`) |
| Apply Neon again, 9 packs | 116 of 116 in 45.3 s; 2 re-walks, 0 missed (`5-after-apply-result.png`) |
| Pick per Neo's guidance | All Neon (`6-new-look-drawer.png`, `6b-...page2.png`) |
| Work profile, 11 apps | 127 of 127 in 48.5 s; rows keyed `#10` |
| Lock mid-run 12 s | 2 taps after lock, 0 while locked, resumes on unlock |
| Home mid-run, 3 times | Home stays; 0 taps while away; resumes when Iconoclast opens |
| Iconoclast killed mid-run | Service turned back on, Apply screen opened: finishes, 116 of 116 |

Time: about 0.32 to 0.39 s per app. His 200 apps: about 65 to 80 s.

## Neo Icon Packs: System icons, after the shotgun

Every pointed app keeps the Iconoclast icon (`7-system-icons-drawer.png`). Neo 0.9.3
draws a custom icon before any picked pack and cannot remove one. Picking another pack
in Neo no longer changes these apps. Only a later Apply moves them, to the new pack.

Camera, a dock app, showed its stock icon under System icons. Its row is on the pack.
That is Neo's known startup race for dock icons, not the shotgun.

## Found while measuring

- Home pressed as Neo's screen closed: Iconoclast resumes for 3 ms and launched the next
  screen. The accessibility exemption let that pull the task over home. Fixed: the next
  screen opens only after the Apply screen stays in front 50 ms.
- First walk read 7 or 8 of 9 icons now and then: a page read mid-scroll. One app missed in the Home test run before the re-walk; none after.
- Force-stopping Iconoclast turns its accessibility service off. Android does this.

## Tests

- `:app:testDebugUnitTest`: 145 tests, 0 failures.
