# Editor UX audit

Counted from the editor as it opens. A drag counts as one action.
"Before" is v0.1 at 7bed40b. "After" is branch `editor-ux`.

| Task | Before | After | What changed |
|---|---|---|---|
| Change icon line colour | 4: Layers tab, scroll, Icon lines row, Colour row, then drag 3 HSV sliders | 1: tap a swatch on the Icon tab | Icon colours lead the first tab. One-tap swatches. Sliders and hex stay. |
| Change icon fill colour | 4, as above, Icon fill row | 1 | Same, shown when the fill style uses it. |
| Change fill style | 2: Fill tab, chip | 1: chip | Shape and Fill merged into Icon. |
| Change background colour | 3: Background tab, Colour row, sliders | 2: tab, swatch | Swatches. |
| Change background shape | 2 | 2 | Shape moved to the top of its tab. |
| Add a texture layer | 4: Layers, Add layer, Texture, pick | 4 | Unchanged. Its settings open on add. |
| Add an effect | 3: Effects, Add effect, pick | 3: Layers, Add effect, pick | Effects live in the layer list. |
| Tune an effect | 2 | 2 | Unchanged. |
| Tune an effect from the Layers list | Not possible. The row selected but showed nothing. | 1 | Fixed. |
| Put an effect on top of the icon | 2 to 4 arrow taps, often one that moved past the hidden marker with no visible change | 2: row, "On top of the icon" | Named choice replaces nudging. |
| Move a layer 3 places | 3 to 4 arrow taps | 1 drag | Drag handle. Marker hidden, so every step shows. |
| Hide a layer, then show it again | 4: row, opacity to 0, then back and re-find the old value | 2: eye, eye | Eye restores the opacity it had. |
| Compare | 1 hold | 1 hold | Labelled "Hold to compare". |
| Override an icon | 1 | 1 | Tapped icon also becomes the big preview. |
| Apply this theme | 5: Back, menu, Set active, Apply, Apply | 2: Apply in editor bar, Apply | Editor sets the theme active. |
| Switch preview to samples | 1 | 1 | Segmented button. |

## Findings

- **Same concept split across tabs.** Icon colour lived in Layers. Line
  and fill settings sat on two tabs. Effects were on their own tab but
  also listed in Layers, where they could not be edited.
- **Data model leaking.** Rows read "Normal, 100%, clip Icon lines". The
  effects marker showed as a row, "Border; new effects go above it".
  The marker also counted as a reorder step, so some arrow taps did
  nothing visible.
- **Indirect controls.** Arrow buttons for order. Opacity drag to hide.
  An effect's side of the icon set by counting swaps. Apply hid behind
  three home-screen taps.
- **Layout.** Five tabs overflowed and cut "Shape" off. A 232 dp grid of
  small icons took the top third. Tiny previews made the look hard to read.

## Layout now

- Top: one 104 dp icon at 256 px, a three-row strip of the rest beside it.
  168 dp tall in total, down from 232 dp.
- Under it: Your apps / Samples, Hold to compare.
- Tabs: Icon, Background, Layers. Three fixed tabs fit a phone.
- Icon tab: Colour, Lines, Fill, Size and position.
- Layers tab: Add layer, Add effect, then rows: handle, swatch, name,
  plain summary, eye, remove. Rows hide the effects marker.

## Kept on purpose

- Every spec 2.3 parameter, undo and redo, compare, overrides, coverage.
- Delete stays one tap with snackbar undo.
- TalkBack reorders through each row's move up and move down actions.
- A hidden paint layer is stored as `"visible": false`, schema 6. Its
  opacity is kept, so showing it after a restart brings it back as it was.
- Editor Apply saves the theme and makes it active before it opens Apply.
