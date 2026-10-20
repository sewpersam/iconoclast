# Iconoclast: naming, organisation and help spec

Audit of `app/src/main/kotlin/dev/iconoclast/app/` at commit `4d0df52`.
Read-only. Every row below describes what the code does, checked against `render/`.
Paths in tables are relative to `app/src/main/kotlin/dev/iconoclast/app/`.

Style target: dead simple, black and white, very readable, little on screen at once.
Each screen shows the few controls most people touch. Everything else sits behind one
"More options" row at the bottom of that screen. Nothing is deleted unless marked "Remove".

Already decided, organised around but not designed here: Solid / Gradient / Image fill
for icon and background, Linear / Radial / Angular gradient with a draggable ramp, full
colour picker, Image crop plus per-icon random shift / rotate / scale, the small effect set
with a Glass preset, Icon size up to the edges, removal of "Your apps / Samples" and
"Hold to compare".

Writing rules used for every label:

- Sentence case. Say what happens, not the technique.
- Switches are named for the "on" state, so on always means "yes, do this".
- A switch whose two states are two different things becomes a two-option choice.
- No graphics terms (matte, blend, octave, seed, miter, fBm, drawable, offset, exponent).
  Where a known term helps experts, it appears once at the end of the help, after the
  plain explanation.
- Units the user cannot picture (" u", 48-grid units) become "×" of the original, or %.
- Help text is one or two sentences: what it does, then what you will see.

---

## 1. Organisation

### 1.1 The model the owner asked for

One list of layers. Tap a layer, get that layer's properties and nothing else.
Each property lives in exactly one place. The Icon and Background tabs go away.

```
Home (themes)
 └─ Editor
     ├─ Preview (top, always visible; tap an icon = "This icon only" sheet)
     └─ Layers list (the only list on the editor)
          ├─ [user layers and effects above the icon]
          ├─ Icon                 ← tap: Icon properties
          ├─ [user layers and effects behind the icon]
          ├─ Background           ← tap: Background properties
          └─ + Add  (one button: opens "Add layer" with Colour / Texture / Effect)
     Tapping any row pushes a full-screen properties page with a Back arrow.
     The preview stays pinned above the properties page so changes are visible.
```

The list today is `LayersTab.kt` plus `LayerRow.kt`; it already holds every row.
`IconTab.kt` and `BackgroundTab.kt` duplicate parts of it. Under this spec both tabs
become the properties pages of the Icon row and the Background row. The tab strip
(`EditorScreen.kt:66`, `:133-137`) is removed. The inline expand-under-the-row panel
(`LayersTab.kt:120-122`) becomes a pushed page, so the list stays short and readable.

### 1.2 Icon lines and Icon fill: one row or two

Recommendation: **one "Icon" row**, with Lines and Fill as sections inside its page.

Reasons, from the code:

1. One choice makes both. `FillModes.apply` (`render/.../fill/FillModes.kt`) builds the
   fill mask and the lines mask together from the single Fill style. They are not
   independent things a user creates.
2. Each half goes dead in some styles. In "Lines only" there is no fill mask, so the
   Icon fill layer paints nothing. In "Solid shape" there is no lines mask
   (`GlyphMasks(Mask(silhouette), null)`), so the Icon lines colour paints nothing,
   yet `IconTab.kt:34` still shows "Line colour". Two rows would show two layers where
   one is empty. One page can simply hide the dead section.
3. Every shape setting (thickness, ends, corners, dots, size, position) changes both
   halves at once and every effect that reads the icon outline. They have one home only
   if the icon is one row.
4. The user thinks "the icon", not "the icon's fill body and its stroke mask".

Cost: today a user can drag another layer between Icon fill and Icon lines. That is
kept in the data model (two `PaintLayer`s stay). The list shows them as one Icon row;
a layer dropped onto the Icon row goes above it or below it. If an existing theme
already has a layer sitting between them, show the Icon row split in two for that theme
only ("Icon fill", "Icon lines"), so nothing is lost. This edge is rare.

### 1.3 What each properties page holds

Order on each page follows how a person thinks about it: what it looks like, what colour,
then where it shows, then the rare stuff under "More options".

**Icon** (row label "Icon"; from `IconTab.kt` and the Icon lines / Icon fill layers)

| Section | Visible | Under More options |
|---|---|---|
| Style | Fill style (4 choices with small pictures) | |
| Lines | Line colour (Solid/Gradient/Image), Line thickness | Line ends, Corners, Sharp corner limit, Dot size, Lines opacity, Lines mix mode |
| Fill (hidden in "Lines only") | Fill colour (Solid/Gradient/Image) | Gap around lines, Close small gaps, Switch to lines only below, Keep loose lines, Fill opacity, Fill mix mode |
| Size and position | Icon size | Move left or right, Move up or down |

Lines section hidden entirely in "Solid shape" style, since nothing draws there.

**Background** (from `BackgroundTab.kt` and the Background layer)

| Section | Visible | Under More options |
|---|---|---|
| Top | Show background (switch). Off hides everything below. | |
| Shape | Shape (pictures), Corner roundness or Squareness when the shape has one, Background size | launcher note |
| Colour | Background colour (Solid/Gradient/Image) | Opacity, Mix mode |
| Border | Border width | Border colour, Border opacity (shown once width > 0) |

The "Texture on top" switch (`BackgroundTab.kt:55`) is removed from this page. A texture
over the background is an ordinary layer with "Show on: Background"; it gets its own row,
labelled "Background texture" as today (`LayerStack.kt:143`). It is added from "+ Add".
With Image fill coming for the background, most people will not need it at all.

Border belongs here even though the renderer paints it at the effects marker
(`Layers.kt` `EffectsLayer`). The user sees it as the background's edge.

**Added colour or texture layer** (from `LayerControls.kt` + `PaintControl.kt`)

| Visible | Under More options |
|---|---|
| Fill (Solid/Gradient/Image), Opacity, Show on, Inside/Outside | Image placement, Zoom, Rotate, Same part on every icon, Cut out by brightness and its four settings, Mix mode |

**Effect** (from `EffectControls.kt`): the two or three settings that define it visible,
the rest under More options. Per-effect split is in the table (section 2, "Vis" column).

### 1.4 Not layer properties: where they go

| Control | Where now | Goes to | Why |
|---|---|---|---|
| Output icon resolution | `settings/AboutScreen.kt:46-49` | Stays in Settings | Applies to every theme's pack. |
| Theme name | Editor title `EditorScreen.kt:93`, Rename on Home | Editor title becomes tappable to rename | One theme-wide value. |
| Per-icon changes | `OverrideSheet.kt` | Stays a sheet from tapping an icon in the preview, titled "This icon only" | Per icon, not per layer. |
| Undo, Redo, Apply | `EditorScreen.kt:96-98` | Stay in the top bar | Whole theme. |
| Your apps / Samples | `EditorScreen.kt:170-180` | Remove (decided) | |
| Hold to compare | `EditorScreen.kt:181`, `:190-215` | Remove (decided) | |

### 1.5 Should not be exposed

| Control | File:line | Why |
|---|---|---|
| Seed numbers shown as raw integers | `EffectControls.kt:248`, `PaintControl.kt:122` | A number like -1834201 means nothing. Keep only a "Shuffle" button and reset. |
| Texture hash fallback name | `PaintControl.kt:90` (`sha256.take(8)`) | Shows "3fa9c01e". Use "Your image" or the file name. |
| Hex value beside every colour row | `ColorControl.kt:74` | Clutter at rest. Show it inside the colour picker only. |
| Grain size note in KB | `LayersTab.kt:161` | Users cannot act on "330 KB". Rephrase (table) or move into help. |
| "fx" swatch text | `LayerRow.kt:125` | Jargon. Use a small sparkle glyph or the effect's icon. |
| Clip target "Effects" | `LayerControls.kt:47` | Very advanced. Keep but only under More options, renamed. |
| "Sharp corner limit" | `IconTab.kt:86` | Almost never matters. More options only. |

---

## 2. Every control

Columns: **Where** = file:line. **Now** = current label. **New** = proposed label.
**Help** = long-press text. **Type** = control type change, if any.
**Vis** = V visible on its page, M under More options, X remove.

### 2.1 Editor shell

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 1 | EditorScreen.kt:93 | Theme name (title) | Theme name | Tap to rename this theme. | Title becomes tappable | V |
| 2 | EditorScreen.kt:94 | Back | Back arrow, a11y "Back to themes" | Goes back to your themes. Your changes are already saved. | Text button to icon button | V |
| 3 | EditorScreen.kt:96 | Undo | Undo | Takes back your last change. | | V |
| 4 | EditorScreen.kt:97 | Redo | Redo | Puts back the change you just undid. | | V |
| 5 | EditorScreen.kt:98 | Apply | Apply | Saves this theme, makes it the one in use, and opens the screen that builds and installs your icon pack. | | V |
| 6 | EditorScreen.kt:66,133 | Tabs: Icon / Background / Layers | (none) | | Remove tab strip; one Layers list | X |
| 7 | EditorScreen.kt:170 | Your apps / Samples | | | Remove (decided) | X |
| 8 | EditorScreen.kt:214 | Hold to compare / Plain | | | Remove (decided) | X |
| 9 | PreviewGrid.kt:72,95 | Tap icon (a11y "Override this icon") | a11y "Change this icon only" | Tap any icon to change it on its own, without touching the rest of the theme. | | V |

### 2.2 Layers list

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 10 | LayersTab.kt:130 | Add layer | + Add | Adds a new colour, image or effect on top of your icons. | Merge with 11 into one button | V |
| 11 | LayersTab.kt:131 | Add effect | (merged into + Add) | | Merge | V |
| 12 | LayerRow.kt:72 | Drag handle | Drag handle, a11y "Reorder" | Drag to move this layer up or down. Higher layers cover lower ones. | | V |
| 13 | LayerRow.kt:78 | Row tap ("Edit X") | Row tap opens properties | Tap to change this layer. | Expand-in-place to pushed page | V |
| 14 | LayerRow.kt:98 | Eye (Hide/Show) | Eye, a11y "Hide X" / "Show X" | Hides this layer without deleting it. Tap again to bring it back as it was. | | V |
| 15 | LayerRow.kt:105 | Remove | Delete, a11y "Delete X" | Deletes this layer. You can undo from the message that appears. | | V |
| 16 | LayerRow.kt:94 | Row summary e.g. "On the icon fill, Screen blend, 50% opacity" | e.g. "Inside icon fill · Screen · 50%" | (not interactive) | | V |
| 17 | LayerStack.kt:163 | Effect name "Scanlines" | Streaks | (see 30) | Rename everywhere | V |
| 18 | LayerStack.kt:165 | "Bevel and emboss" | Raised edge | (see 31) | Rename | V |
| 19 | LayerStack.kt:154-155 | "Linear gradient", "Radial gradient" row names | "Gradient" | Kind shown by the swatch. | Rename | V |

### 2.3 Add sheet (today two sheets)

| # | Where | Now | New | Help (shown as the row's second line) | Type | Vis |
|---|---|---|---|---|---|---|
| 20 | AddLayerSheet.kt:39 | Add layer (title) | Add | | One sheet, two groups: "Colour or image", "Effect" | V |
| 21 | AddLayerSheet.kt:23 | Solid colour / "One colour" | Colour / "One flat colour over your icons" | | | V |
| 22 | AddLayerSheet.kt:24 | Linear gradient / "Two colours along a line" | Gradient / "Colours blending into each other" | | Merge 22-23; Linear/Radial/Angular chosen on the page (decided) | V |
| 23 | AddLayerSheet.kt:25 | Radial gradient / "Two colours from a centre" | (merged into Gradient) | | Merge | X |
| 24 | AddLayerSheet.kt:27 | Texture / "An image from your photos or files" | Image / "A photo or pattern laid over your icons" | | | V |
| 25 | AddLayerSheet.kt:28 | Noise: fBm / "Soft clouds" | Clouds / "Soft random cloudy pattern" | | | V |
| 26 | AddLayerSheet.kt:29 | Noise: ridged / "Veins and ridges" | Veins / "Random lines like marble or rock" | | | V |
| 27 | AddLayerSheet.kt:30 | Noise: grain / "Fine grain" | Speckle / "Fine random dots" | | Overlaps the Grain effect, see 4.6 | V |
| 28 | AddEffectSheet.kt:34 | Add effect (title) | (group "Effect" in the one sheet) | | | V |
| 29 | AddEffectSheet.kt:18 | Brush strokes / "Painted strokes flicking off the icon" | Brush strokes / same | | | V |
| 30 | AddEffectSheet.kt:19 | Scanlines / "Straight textured lines trailing from the icon" | Streaks / "Lines trailing off the icon in one direction, like motion" | | | V |
| 31 | AddEffectSheet.kt:20 | Bevel and emboss / "Light and shade along the icon's edges" | Raised edge / "Makes the icon look raised, with a lit side and a shaded side" | | | V |
| 32 | AddEffectSheet.kt:21 | Drop shadow / "The icon's shadow on the background" | Shadow / same | | | V |
| 33 | AddEffectSheet.kt:22 | Inner shadow / "A shadow inside the icon" | Inner shadow / "Shading inside the icon's edges, so it looks pressed in" | | | V |
| 34 | AddEffectSheet.kt:23 | Outer glow / "A glow around the icon" | Glow / same | | | V |
| 35 | AddEffectSheet.kt:24 | Inner glow / "A glow from the icon's edges inward" | Inner glow / "Light along the inside of the icon's edges" | | | V |
| 36 | AddEffectSheet.kt:25 | Grain / "Monochrome noise over everything" | Film grain / "Fine grey speckle over the whole icon, like film" | | | V |

### 2.4 Image picker sheet

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 37 | TextureSheet.kt:48 | Texture (title) | Choose an image | | | V |
| 38 | TextureSheet.kt:50 | From photos / "The system photo picker" | From photos / "Pick from your gallery" | | | V |
| 39 | TextureSheet.kt:58 | From files / "Any image file, through the document picker" | From files / "Pick any image file on your phone" | | | V |
| 40 | TextureSheet.kt:65 | Built-in | Included patterns | | | V |
| 41 | TextureSheet.kt:78 | Imported | Your images | | | V |

### 2.5 Icon page: Style and Lines (IconTab.kt)

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 42 | IconTab.kt:33 | Section "Colour" | (gone; colour sits in Lines and Fill sections) | | | X |
| 43 | IconTab.kt:103-107 | Style: Outline / Invert fill / Silhouette / Outline + Silhouette | Fill style: Lines only / Cut-out / Solid shape / Solid shape with lines | Lines only draws just the icon's lines. Cut-out fills the closed shapes and cuts the lines out of them. Solid shape fills the whole icon in one colour. Solid shape with lines draws the lines over that shape. | Chips to 4 picture tiles showing a sample icon each way | V |
| 44 | IconTab.kt:110,142-147 | Fill style hint text | (moves into help of 43) | | Remove inline text | X |
| 45 | IconTab.kt:34,60 | Line colour | Line colour | The colour of the icon's lines. | Becomes Solid/Gradient/Image (decided) | V |
| 46 | IconTab.kt:66 | "Line colour: painted by a texture." | "Lines use an image." | | Gone once the page edits images directly | X |
| 47 | IconTab.kt:71 | More line colour options | (gone; everything is on this page) | | Remove cross-tab jump | X |
| 48 | IconTab.kt:79 | Line thickness (0.25–4, shown "1.00 u") | Line thickness, shown "1.0×" | How thick the icon's lines are. 1× is the original Arcticons thickness. | Unit " u" to "×" | V |
| 49 | IconTab.kt:81 | Line ends: Round / Flat / Square | Line ends: Round / Flat / Square | The shape of the open ends of lines. Flat stops exactly at the end; Square sticks out a little past it. | | M |
| 50 | IconTab.kt:83 | Corners: Round / Sharp / Cut | Corners: Round / Pointed / Flattened | How lines look where they meet at an angle. | Rename options | M |
| 51 | IconTab.kt:86 | Sharp corner limit (1–10) | Spike limit | Only for Pointed corners. Very narrow corners make long spikes; a lower value flattens them off sooner. | | M |
| 52 | IconTab.kt:89 | Dots: Grow with the lines / Keep their size | Dot size: Match line thickness / Keep original | Whether round dots in the icons grow and shrink with Line thickness or stay their original size. | | M |
| 53 | (new home) LayerControls.kt:62 for Icon lines | Opacity | Lines opacity | How see-through the lines are. 0% hides them. | | M |
| 54 | (new home) LayerControls.kt:64 for Icon lines | Blend | Lines mix mode | See 92. | | M |

### 2.6 Icon page: Fill

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 55 | IconTab.kt:35,60 | Fill colour | Fill colour | The colour inside the icon's shapes. | Solid/Gradient/Image (decided). Hidden in Lines only. | V |
| 56 | IconTab.kt:71 | More fill colour options | (gone) | | Remove | X |
| 57 | IconTab.kt:113 | Gap around lines (0–4 u) | Gap around lines, shown "1.0×" | Cut-out only. The empty space between the cut-out lines and the fill. Bigger shows more of the background through the lines. | Unit to "×" | M |
| 58 | IconTab.kt:117 | Close small gaps (0–2 u) | Close small gaps, shown "0.5×" | Shapes with a tiny break in their outline are treated as closed, so they still get filled. Raise it if some shapes stay empty. | Unit to "×" | M |
| 59 | IconTab.kt:121 | Use outline below (0–30%) | Switch to lines only below | Cut-out only. If an icon would end up with less fill than this, it is drawn as lines only, so very thin icons stay readable. | | M |
| 60 | IconTab.kt:123 | Inline note "Icons whose fill would cover…" | (moves into help of 59) | | Remove inline text | X |
| 61 | IconTab.kt:125 | Keep stray lines (switch) | Keep loose lines | Cut-out only. Lines that do not surround anything, like antennas or dots, are still drawn on top. Off removes them. | | M |
| 62 | (new home) LayerControls.kt:62 for Icon fill | Opacity | Fill opacity | How see-through the fill is. | | M |
| 63 | (new home) LayerControls.kt:64 for Icon fill | Blend | Fill mix mode | See 92. | | M |

### 2.7 Icon page: Size and position

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 64 | IconTab.kt:40 | Section "Size and position" | Size and position | | | V |
| 65 | IconTab.kt:134 | Icon size (50–100%) | Icon size | How big the drawing is inside the background shape. 100% reaches the shape's edges. Shrinking the background shrinks the icon with it. | Range extended to the edges (decided). Since schema 11 (icon-follows-bg, 2026-10-19) the % is of the background shape's square, so the icon shrinks with the shape. | V |
| 66 | IconTab.kt:136 | Move left or right (±10%) | Move left or right | Shifts every icon sideways. | | M |
| 67 | IconTab.kt:138 | Move up or down (±10%) | Move up or down | Shifts every icon up or down. | | M |

### 2.8 Background page (BackgroundTab.kt, ShapeControl.kt)

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 68 | BackgroundTab.kt:35 | Background (switch) | Show background | Off leaves only the icon drawing on a see-through tile. | | V |
| 69 | BackgroundTab.kt:37 | "Off: the icons have a transparent background." | "No background. Icons sit straight on your wallpaper." | | | V |
| 70 | BackgroundTab.kt:40 | Section "Shape" | Shape | | | V |
| 71 | ShapeControl.kt:28 | Shape: Circle / Squircle / Rounded square / Teardrop / Square | Shape: Circle / Soft square / Rounded square / Teardrop / Square | The outline of the tile behind each icon. | Chips to picture tiles | V |
| 72 | ShapeControl.kt:37 | Squircle exponent (2–12) | Squareness | Soft square only. Low is close to a circle, high is close to a square. | | V |
| 73 | ShapeControl.kt:41 | Corner radius (0–50%) | Corner roundness | Rounded square only. 0% is a sharp square, 50% is a circle. | | V |
| 74 | BackgroundTab.kt:43 | Note "The shape is drawn into each icon…" | "If your launcher also shapes icons, turn that off there to avoid a double edge." | | Move to help of 71; also shown once as a one-time tip | M |
| 75 | BackgroundTab.kt:47 | Size (70–100%) | Background size | How much of the tile the background shape fills. 100% touches the edges; smaller leaves your wallpaper around it. Its gradient or image shrinks with it, and so does the icon. | 50–100% since schema 10 (bg-size, 2026-10-19); visible under the shape, as Icon size is. The icon follows it since schema 11 | V |
| 76 | BackgroundTab.kt:49 | Section "Colour" | Colour | | | V |
| 77 | BackgroundTab.kt:51 | Background paint (via PaintControl) | Background colour | The colour behind the icon. | Solid/Gradient/Image (decided) | V |
| 78 | BackgroundTab.kt:53 | "The background is painted by a texture or noise. Change it in Layers." | (gone; page edits it directly) | | Remove | X |
| 79 | BackgroundTab.kt:55 | Texture on top (switch) | (removed from page; use + Add, Show on Background) | | Remove; duplicates a layer | X |
| 80 | BackgroundTab.kt:59 | Nested texture layer controls | (gone; that layer has its own row) | | Remove duplicate | X |
| 81 | BackgroundTab.kt:61 | Section "Border" | Border | | | V |
| 82 | BackgroundTab.kt:62 | Border width (0–10%) | Border width | A line just inside the edge of the background shape. 0% means no border. | | V |
| 83 | BackgroundTab.kt:65 | Border colour | Border colour | Colour of the border line. | Shown when width > 0 | M |
| 84 | BackgroundTab.kt:67 | Border opacity | Border opacity | How see-through the border is. | Shown when width > 0 | M |
| 85 | (Background layer) LayerControls.kt:62 | Opacity | Background opacity | How see-through the background is. 0% shows the wallpaper through it. | | M |
| 86 | (Background layer) LayerControls.kt:64 | Blend | Mix mode | See 92. | | M |

### 2.9 Added colour or image layer page (LayerControls.kt)

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 87 | LayerControls.kt:61 | Fill with (via PaintControl) | Fill | What this layer paints: a colour, a gradient or an image. | Solid/Gradient/Image (decided) | V |
| 88 | LayerControls.kt:62 | Opacity | Opacity | How see-through this layer is. 0% hides it, 100% is fully solid. | | V |
| 89 | LayerControls.kt:65 | Show on: Icon / Icon fill / Icon lines / Background / Everywhere / Effects | Show on: Whole icon / Icon fill / Icon lines / Background / Everywhere / Effects below | Limits this layer to one part of the picture. It only paints where that part is. | Chips to a short dropdown. "Effects" renamed, see 91 | V |
| 90 | LayerControls.kt:67 | Outside it instead (switch) | Inside / Outside | Inside paints only on the chosen part. Outside paints everywhere except that part, for example everywhere but the icon. | Switch to two-option segmented choice; Outside disabled for Everywhere | V |
| 91 | LayerControls.kt:47 | "Effects" option of Show on | Effects below | Paints only where the shadows, glows and strokes listed below this layer appear. | | M (option hidden until More options open) |
| 92 | LayerControls.kt:64,86 | Blend (Normal, Multiply, Screen, Overlay, Darken, Lighten, Color Dodge, Color Burn, Hard Light, Soft Light, Difference, Exclusion, Hue, Saturation, Color, Luminosity, Add) | Mix mode | How this layer mixes with what is under it. Normal just covers it. The others darken, lighten or tint what is below; try them and watch the preview. | Dropdown keeps names but adds a second line per item (see 2.14) | M |
| 93 | LayerControls.kt:104 | Mapping: Fit / Fill / Tile | Image placement: Show whole image / Cover the tile / Repeat | Show whole image fits all of it inside the tile and may leave empty edges. Cover fills the tile and crops the rest. Repeat tiles it like wallpaper. | | M |
| 94 | LayerControls.kt:106 | Scale (0.25–4×) | Zoom | Above 1× zooms in on the image; below 1× shrinks it. | Overlaps decided Image crop/scale, see 4.7 | M |
| 95 | LayerControls.kt:107 | Rotation (−180–180°) | Rotate | Turns the image around the centre of the tile. | | M |
| 96 | LayerControls.kt:109 | Same patch on every icon (switch) | Same part on every icon | On: every icon shows the same middle part of the image. Off: each icon shows a different part, so they all look a little different. | Overlaps decided per-icon random shift, see 4.7 | M |
| 97 | LayerControls.kt:115 | Use brightness as a mask (switch) | Cut out by brightness | Hides this layer where its own colours are dark and keeps it where they are light. Good for turning a photo or pattern into a stencil. (Experts: a luminance matte.) | | M |
| 98 | LayerControls.kt:118 | Mask style: Smooth (levels) / Hard (threshold) | Edges: Soft / Hard | Soft fades in from dark to light. Hard is fully on or fully off, with nothing in between. | | M |
| 99 | LayerControls.kt:120 | Black point (0–100%) | Fully hidden below | Soft edges only. Parts darker than this are completely hidden. | | M |
| 100 | LayerControls.kt:123 | White point (0–100%) | Fully shown above | Soft edges only. Parts lighter than this are completely shown. | | M |
| 101 | LayerControls.kt:127 | Threshold (0–100%) | Cut-off brightness | Hard edges only. Lighter than this is shown, darker is hidden. | | M |
| 102 | LayerControls.kt:130 | Invert mask (switch) | Keep: Light parts / Dark parts | Picks which parts stay visible: the light ones or the dark ones. | Switch to segmented choice | M |
| 103 | LayerControls.kt:131 | Feather (0–5%) | Soften cut-out edge | Blurs the edge of the cut-out so it fades instead of stopping sharply. | | M |

### 2.10 Paint control (PaintControl.kt, ColorControl.kt)

Most of this is replaced by the decided Solid / Gradient / Image picker and full colour
picker. Rows record what exists so nothing is lost in the rebuild.

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 104 | PaintControl.kt:55 | Fill with: Colour / Gradient / Radial gradient / Texture / Noise | Fill: Solid / Gradient / Image | What this layer paints. | Decided; Noise becomes a pattern choice under Image, see 4.6 | V |
| 105 | PaintControl.kt:70 | Angle (0–360°) | Direction | Which way the gradient runs across the tile. | Linear only | V |
| 106 | PaintControl.kt:75 | Centre X | Centre left–right | Moves the middle of the round gradient sideways. | Radial only; better as a drag point on the preview | M |
| 107 | PaintControl.kt:76 | Centre Y | Centre up–down | Moves the middle of the round gradient up or down. | Radial only | M |
| 108 | PaintControl.kt:77 | Radius (10–150%) | Size | How far the round gradient spreads before reaching its last colour. | Radial only | V |
| 109 | PaintControl.kt:137-138 | Start colour / End colour | (replaced by the colour ramp, decided) | | | V |
| 110 | PaintControl.kt:93 | Change texture | Change image | Pick a different image for this layer. | | V |
| 111 | PaintControl.kt:101 | Texture colours: Own colours / One colour / Gradient map | Image colours: Original / Tint / Recolour | Original keeps the image's colours. Tint makes its light parts one colour you pick. Recolour swaps the image's darks and lights for the colours you pick. | | V |
| 112 | PaintControl.kt:113 | Noise: fBm / Ridged / Grain | Pattern: Clouds / Veins / Speckle | The kind of random pattern. | | V |
| 113 | PaintControl.kt:117 | Octaves (1–8) | Detail | More adds finer and finer detail to the pattern. | | M |
| 114 | PaintControl.kt:121-124 | Seed + number + New seed | Shuffle pattern | Makes a new random version of the same pattern. | Hide the number; keep button and reset | V |
| 115 | ColorControl.kt:84-88 | X hue / X saturation / X value sliders | Hue / Intensity / Brightness | Hue picks the colour, Intensity how strong it is, Brightness how light. | Replaced by full picker (decided); keep these names inside it | V |
| 116 | ColorControl.kt:133 | X hex | Hex code | Type a colour code like FF8800. Add two more digits in front to set see-through. | Inside the picker only | M |
| 117 | ColorControl.kt:118-123 | 14 swatches | (replaced by picker, decided) | | | X |

### 2.11 Effects (EffectControls.kt, LayersTab.kt)

Shared by all effects:

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 118 | LayersTab.kt:156 | Where: Behind the icon / On top of the icon | (removed as a setting; list position decides) | | Remove. Same thing as dragging past the Icon row. See 4.3 | X |
| 119 | EffectControls.kt:50 | Blend | Mix mode | See 92. | | M |
| 120 | EffectControls.kt:52 | Opacity | Strength | How strong the effect is. 0% hides it. | | V |

Streaks (now "Scanlines"):

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 121 | EffectControls.kt:94 | Angle | Direction | Which way the streaks run away from the icon. | Better as a dial | V |
| 122 | EffectControls.kt:95 | Length | Length | How far the streaks reach. | | V |
| 123 | EffectControls.kt:96 | Decay: Exponential / Linear | Fade: Long tail / Even | Long tail fades fast then leaves a faint trail. Even fades steadily to nothing at the full length. | | M |
| 124 | EffectControls.kt:126 | Paint from: Colour / (layer name) | Colour from: One colour / (layer name) | Paint the streaks one colour, or take their colours from one of your image or pattern layers. | | M |
| 125 | EffectControls.kt:138 | Streak colour | Streak colour | The colour of the streaks. | | V |
| 126 | EffectControls.kt:98 | Breakup (switch) | Broken streaks | On breaks the smooth streaks into rough brush-like marks. Off gives one smooth smear. | | V |
| 127 | BreakupPicker.kt:48 | Breakup source (Noise / built-ins / Imported / Import) | Mark pattern | The pattern used to break up the streaks. | | M |
| 128 | EffectControls.kt:104 | Breakup scale | Mark size | Bigger makes the marks larger and fewer. | | M |
| 129 | EffectControls.kt:105 | Stretch (1–32×) | Mark length | How long each mark is stretched along the streak. | | M |
| 130 | EffectControls.kt:106 | Density | Coverage | More lets more of the streak through; less leaves more gaps. | | V |
| 131 | EffectControls.kt:107 | Contrast | Mark edges | Low gives soft, blended marks. High gives crisp, hard marks. | | M |
| 132 | EffectControls.kt:108 | Breakup seed | Shuffle marks | Makes a new random version of the marks. | Hide number | M |

Brush strokes:

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 133 | EffectControls.kt:146 | Strokes (1–400) | Number of strokes | How many strokes flick off each icon. | | V |
| 134 | EffectControls.kt:148 | Length | Stroke length | How far each stroke reaches from the icon. | | V |
| 135 | EffectControls.kt:149 | Width | Stroke width | How wide each stroke is where it starts. | | V |
| 136 | EffectControls.kt:150 | Softness | Softness | Low gives crisp edges and bristle marks; high gives soft, smooth strokes. | | M |
| 137 | EffectControls.kt:151 | Blur with distance | Blur toward the tip | How much strokes blur as they move away from the icon. | | M |
| 138 | EffectControls.kt:152 | Fade | Paint runs out | How much a stroke fades along its length, like a brush running dry. | | M |
| 139 | EffectControls.kt:153 | Bristles | Dry brush marks | Adds the streaky lines a dry brush leaves, thinning out toward the tip. | | M |
| 140 | EffectControls.kt:154 | Randomness | Variety | How different the strokes are from one another in direction, length, width and shade. | | M |
| 141 | EffectControls.kt:156 | Bend (−100–100%) | Curl | Strokes curve one way or the other as they travel. 0 keeps them straight. | | M |
| 142 | EffectControls.kt:157 | Lean | Lean | How much all strokes lean toward one direction instead of straight out from the icon. | | M |
| 143 | EffectControls.kt:159 | Lean angle | Lean direction | The direction strokes lean toward. Only matters when Lean is above 0. | | M |
| 144 | EffectControls.kt:177 | Paint from | Colour from | As 124. Each stroke takes the colour where it starts. | | M |
| 145 | EffectControls.kt:189 | Stroke colour | Stroke colour | The colour of the strokes. | | V |
| 146 | EffectControls.kt:162 | Stroke seed | Shuffle strokes | A new random arrangement of strokes. | Hide number | M |

Raised edge (now "Bevel and emboss"):

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 147 | EffectControls.kt:196 | Style: Inner bevel / Emboss | Raised: Inside the edge / Across the edge | Inside rounds off the icon inside its outline. Across makes it stand up out of the background. | | V |
| 148 | EffectControls.kt:197 | Light angle | Light direction | Where the light comes from. The lit side faces it. | | V |
| 149 | EffectControls.kt:199 | Depth (0–4) | Height | How steep the raised edge looks. | | V |
| 150 | EffectControls.kt:200 | Size | Edge width | How wide the raised band along the edge is. | | M |
| 151 | EffectControls.kt:201 | Softness | Roundness | Rounds the raised edge so it looks smooth instead of chiselled. | | M |
| 152 | EffectControls.kt:202 | Highlight | Lit side colour | Colour of the side facing the light. Its see-through amount sets how strong it is. | | M |
| 153 | EffectControls.kt:203 | Highlight blend | Lit side mix mode | See 92. | | M |
| 154 | EffectControls.kt:204 | Shadow | Shaded side colour | Colour of the side away from the light. | | M |
| 155 | EffectControls.kt:205 | Shadow blend | Shaded side mix mode | See 92. | | M |

Shadow and Inner shadow:

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 156 | EffectControls.kt:215 | Light angle | Light direction | Where the light comes from. The shadow falls the other way. | | V |
| 157 | EffectControls.kt:217 | Distance | Distance | How far the shadow sits from the icon. | | V |
| 158 | EffectControls.kt:219 | Blur | Softness | How blurry the shadow's edge is. | | V |
| 159 | EffectControls.kt:220 | Shadow colour | Shadow colour | The colour of the shadow. | | M |

Glow and Inner glow:

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 160 | EffectControls.kt:230 | Size | Glow size | How far the glow reaches from the edge. | | V |
| 161 | EffectControls.kt:231 | Spread | Glow solidity | Low is a soft haze. High stays solid further out before fading. | | M |
| 162 | EffectControls.kt:232 | Glow colour | Glow colour | The colour of the glow. | | V |

Film grain:

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 163 | EffectControls.kt:238 | Amount | Amount | How strong the grain is. | | V |
| 164 | EffectControls.kt:239 | Grain size (1–4 px) | Grain size | Size of each speck. 1 is the finest. | Drop "px" | M |
| 165 | EffectControls.kt:240 | Grain seed | Shuffle grain | A new random grain pattern. | Hide number | M |
| 166 | LayersTab.kt:161 | "Grain at full amount adds about 330 KB per icon." | "Heavy grain makes the icon pack larger." | | Move into help of 163 | X |

### 2.12 This icon only (OverrideSheet.kt, SwapSearch.kt)

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 167 | OverrideSheet.kt:68 | "Drawn as the theme says" / "Overridden in this theme" | "Same as the rest" / "Changed for this icon" | | | V |
| 168 | OverrideSheet.kt:77 | Stroke weight (shown " u") | Line thickness, shown "×" | Line thickness for this icon only. | Rename to match 48 | V |
| 169 | OverrideSheet.kt:82 | Fill mode | Fill style | Fill style for this icon only. Same choices as the Icon page. | Rename to match 43 | V |
| 170 | OverrideSheet.kt:92 | Gap closing | Close small gaps | As 58, for this icon only. | Rename to match 58 | M |
| 171 | OverrideSheet.kt:100 | Texture offset X | Pattern position left–right | Moves which part of your images and patterns this icon shows. Grain and brush strokes shift with it too. | | M |
| 172 | OverrideSheet.kt:105 | Texture offset Y | Pattern position up–down | As above, up and down. | | M |
| 173 | OverrideSheet.kt:110 | Hide strokes and scanlines (switch) | Hide streaks and brush strokes | Leaves streaks and brush strokes off this icon only. Useful when they clash with its shape. | | V |
| 174 | OverrideSheet.kt:113 | "This theme has no brush strokes or scanlines." | "This theme has no streaks or brush strokes." | | | V |
| 175 | SwapSearch.kt:43 | "Drawable: the app's own" / "Drawable: x" | "Drawing: this app's own" / "Drawing: x" | | | V |
| 176 | SwapSearch.kt:47 | Use own | Use own drawing | Goes back to this app's own Arcticons drawing. | | V |
| 177 | SwapSearch.kt:53 | Search Arcticons drawables | Use a different drawing | Search all Arcticons drawings by name and pick one to use for this app. | | V |
| 178 | SwapSearch.kt:56 | "No drawable matches." | "No drawing with that name." | | | V |
| 179 | OverrideSheet.kt:123 | Reset to theme | Undo all changes to this icon | Puts this icon back to match the rest of the theme. | | V |

### 2.13 Home, Apply, Coverage, Settings, dialogs

| # | Where | Now | New | Help | Type | Vis |
|---|---|---|---|---|---|---|
| 180 | HomeScreen.kt:93 | Themes | Themes | | | V |
| 181 | HomeScreen.kt:101 | Coverage (+ badge) | Apps | Shows which of your apps get a themed icon, and new apps since your last Apply. | | V |
| 182 | HomeScreen.kt:104 | Apply | Apply | Builds an icon pack from the theme in use and installs it. | | V |
| 183 | HomeScreen.kt:106 | ⋮ More | ⋮ More | | | V |
| 184 | HomeScreen.kt:110 | Import theme | Import theme | Open a theme .zip someone shared or you exported. | | V |
| 185 | HomeScreen.kt:111 | Settings and about | Settings | | | V |
| 186 | HomeScreen.kt:118 | New theme | New theme | | | V |
| 187 | HomeScreen.kt:188 | Active (badge) | In use | | | V |
| 188 | HomeScreen.kt:195 | Set active | Use this theme | Makes this the theme the next Apply builds. | | V |
| 189 | HomeScreen.kt:196-200 | Rename / Duplicate / Export / Delete | Rename / Duplicate / Share / Delete | Share saves the theme and its images as a .zip. | Export renamed | V |
| 190 | HomeScreen.kt:161 | "This theme is removed from the phone. An installed pack keeps working." | "This deletes the theme. Icons already installed stay as they are." | | | V |
| 191 | ExportDialog.kt:33 | "Shares the theme and its textures as a .zip." | "Saves the theme and its images as a .zip file." | | | V |
| 192 | ExportDialog.kt:39 | Include the pack signing key (checkbox) | Include my pack key (for moving phones) | Only for moving to a new phone or reinstalling. Without it, a reinstalled Iconoclast cannot update your current pack. | | V |
| 193 | ExportDialog.kt:58-66 | Include the signing key? / Include the key | Keep this file private / Include the key | | | V |
| 194 | ImportKeyDialog.kt:17,26-27 | Restore the signing key? / Restore key / Theme only | Restore your pack key? / Restore key / Just the theme | | | V |
| 195 | ApplyScreen.kt:38 | Apply X | Apply X | | | V |
| 196 | ApplyScreen.kt:54 | See coverage | See which apps changed | | | V |
| 197 | ApplyScreen.kt:60 | Open X App info | Open X settings | | | V |
| 198 | ApplyScreen.kt:67 | Open settings | Allow installing | | | V |
| 199 | ApplyScreen.kt:68 | Uninstall old pack | Uninstall old pack | | | V |
| 200 | ApplyScreen.kt:70-71 | Restore from a backup / Make a new key | Restore from a backup / Start over with a new key | | | V |
| 201 | CoverageScreen.kt:66 | Re-apply | Apply again | | | V |
| 202 | CoverageScreen.kt:73-79 | Themed / Overridden / Not in Arcticons / Overrides kept for missing icons | Themed / Changed one by one / No Arcticons icon yet / Saved changes for missing icons | | | V |
| 203 | CoverageScreen.kt:76 | Request | Request icon | Opens the Arcticons request page so the icon can be drawn. | | V |
| 204 | AboutScreen.kt:76 | Output icon resolution | Icon quality | | | V |
| 205 | AboutScreen.kt:47-48 | Full, 432 px / Compact, 324 px | Sharp (larger pack) / Compact (smaller pack) | Keep the measured MB lines as the second line. | Drop px from the label | V |
| 206 | AboutScreen.kt:115 | Export signing key (section) | Moving to a new phone | | | V |

Total: 206 rows, covering every interactive control and every user-visible label or
note in the editor, its sheets, Home, Apply, Coverage, Settings and dialogs. Error and
progress messages in the view models were read; they are already plain and are not
rowed except where a control sits beside them.

### 2.14 Mix mode list, plain second lines

For the dropdown in 92. Names stay (they are what tutorials use); each gets one line.

| Mode | Second line |
|---|---|
| Normal | Covers what is below. |
| Multiply | Darkens, like stacking tinted glass. |
| Screen | Lightens, like projecting light. |
| Overlay | Adds contrast: darks darker, lights lighter. |
| Darken | Keeps whichever is darker. |
| Lighten | Keeps whichever is lighter. |
| Color Dodge | Strong brightening of what is below. |
| Color Burn | Strong darkening of what is below. |
| Hard Light | Strong contrast, led by this layer. |
| Soft Light | Gentle contrast. |
| Difference | Inverts where colours differ. |
| Exclusion | Softer version of Difference. |
| Hue | Takes only this layer's colour, keeps the shading below. |
| Saturation | Takes only this layer's colour strength. |
| Color | Tints what is below with this layer's colour. |
| Luminosity | Takes only this layer's lightness. |
| Add | Adds light; can blow out to white. |

---

## 3. Help pattern

### 3.1 What the user does

- **Long-press any control's label row** shows a short explanation bubble (the Help
  column). It stays until the user taps elsewhere.
- **Page headers** (Icon, Background, each effect) carry a small ⓘ button. Tapping it
  shows a slightly longer explanation of the whole page, same bubble style.
- Screen readers: the same text is offered as an action, "Show help", on the control.

Long-press goes on the label row, never on the slider track or switch itself. A slider
consumes the press as a drag, and a switch fires on tap, so the label is the only safe
anchor. The current `SliderControl`, `ChoiceControl`, `SwitchControl` and `ColorControl`
already put the label in its own `Row`; that row becomes the tooltip anchor.

### 3.2 Exact API, verified

Compose BOM `2026.09.00` (`gradle/libs.versions.toml`) resolves
`androidx.compose.material3:material3` **1.4.0**. Checked in the BOM pom and the cached
`material3-android-1.4.0` aar with `javap`.

Use, all in `androidx.compose.material3`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { RichTooltip(title = { Text(label) }) { Text(help) } },
    state = rememberTooltipState(isPersistent = true),
) { LabelRow(...) }
```

- `TooltipBox` overload with `onDismissRequest, focusable, enableUserInput, hasAction`
  is the non-deprecated one in 1.4.0. The two shorter overloads are `@Deprecated`.
- `TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition, Dp)` is the
  non-deprecated provider. `rememberPlainTooltipPositionProvider` and
  `rememberRichTooltipPositionProvider` are `@Deprecated` in 1.4.0. Do not use them.
- `TooltipAnchorPosition` has `Above, Below, Left, Right, Start, End`.
- `TooltipBox` is still `@ExperimentalMaterial3Api`. The editor already opts in
  (`EditorScreen.kt:74`).
- Trigger is built in. `internal/BasicTooltipKt.handleGestures` shows the tooltip after
  `ViewConfiguration.longPressTimeoutMillis` for `PointerType.Touch` and `Stylus`, and
  on hover for `Mouse`. `anchorSemantics` adds an accessibility `onLongClick` that shows
  it. No custom gesture code needed. Leave `enableUserInput = true`.
- Use `RichTooltip`, not `PlainTooltip`. Plain is for one or two words under an icon
  button. Rich has a title line and wraps a sentence, which is what the Help column is.
- `isPersistent = true` keeps the bubble open until dismissed, so a two-sentence text can
  be read. Without it the bubble times out.
- Icon buttons with no visible text (undo, redo, eye, delete, reorder, ⓘ) get a
  `PlainTooltip` with their one or two word name, same `TooltipBox`.
- For the ⓘ on page headers: an `IconButton` whose `onClick` calls
  `scope.launch { state.show() }` on the same `TooltipBox` state.

Build one wrapper, `HelpAnchor(label, help) { content }`, and route every
`SliderControl`, `ChoiceControl`, `SwitchControl`, `ColorControl`, `BlendControl` label
through it. Then the Help column becomes one `help: String` parameter per call site.
Help text lives in one file so it can be reviewed in one place.

### 3.3 Inline notes

Notes that sit permanently under controls today (`IconTab.kt:110,123`,
`BackgroundTab.kt:43`, `LayersTab.kt:161`) move into the long-press help. That keeps
screens short. Keep inline only what the user must read to avoid a mistake: the
"No background" line (69) and the empty-state lines.

---

## 4. Confusing things that are not labels

1. **Same property in two places.** Icon colours are on the Icon tab
   (`IconTab.kt:34-35`) and again inside the Icon lines / Icon fill rows of Layers
   (`LayersTab.kt:149`). Background colour is on the Background tab (`BackgroundTab.kt:51`)
   and in the Background row. The background texture switch (`BackgroundTab.kt:55`) adds a
   layer that also shows in Layers, and its full controls are drawn on both tabs
   (`BackgroundTab.kt:59`). The "More line colour options" button (`IconTab.kt:71`)
   silently switches tabs. Section 1 removes all of this.
2. **Line colour with no effect.** In Silhouette ("Solid shape") the renderer makes no
   lines mask (`FillModes.kt`, `SILHOUETTE -> GlyphMasks(body, null)`), so the line
   colour, its opacity and its blend do nothing, yet "Line colour" stays on screen
   (`IconTab.kt:34`). The Icon fill row likewise stays in Layers in Outline mode with
   nothing to paint. Hide the dead section.
3. **"Where" duplicates the list order.** Effect "Behind the icon / On top of the icon"
   (`LayersTab.kt:156`) is the same as dragging the row past the icon. Worse, Raised edge,
   Inner shadow and Inner glow only draw on the icon (`Effects.kt` header), so "Behind
   the icon" makes them invisible with no warning. Remove the setting. If one of those
   three is dragged below the Icon row, its summary should read "Hidden behind the icon".
4. **Icon size 100% is not the edge.** 100% fills the launcher safe circle, 61% of the
   tile (`Compositor.kt:24,196`). Users read 100% as "full". Decided extension should
   re-base the percentage on the tile.
5. **Opaque units.** Thickness, gap and gap closing show " u", 48-grid units
   (`IconTab.kt:79,113,117`, `OverrideSheet.kt:77,92`). Show "×" of the original.
6. **Two grains.** "Noise: grain" layer (`AddLayerSheet.kt:30`) and the Grain effect
   (`AddEffectSheet.kt:25`) look alike. The effect is grey speckle mixed with Overlay and
   counted per icon; the noise layer is a coloured pattern you place. Keep both, but name
   them apart (Speckle pattern vs Film grain) and put Speckle under Image patterns.
7. **Overlap with decided Image controls.** Layer "Scale", "Rotation" and "Same patch on
   every icon" (`LayerControls.kt:106-109`) do part of what the decided per-icon random
   shift / rotate / scale sliders do, and "Texture offset X/Y" in the per-icon sheet
   (`OverrideSheet.kt:100-105`) is a manual version of the random shift. The Image fill
   design must absorb these, not add a second set beside them.
8. **Per-icon offset moves more than images.** The override "Texture offset"
   also moves grain and brush strokes (`Effects.kt` Grain doc; BrushStrokes uses the same
   per-icon offset). The label suggests images only. Help text in 171 says so.
9. **Inconsistent names for one setting.** Main editor vs per-icon sheet: "Line thickness"
   vs "Stroke weight", "Style" vs "Fill mode", "Close small gaps" vs "Gap closing". Use
   one name each (rows 168-170).
10. **"Scanlines" is the wrong word.** The effect is a directional smear trailing off the
    icon (`Effects.kt` Streaks). Scanlines means horizontal TV lines. The add sheet even
    describes it differently from its name. Rename Streaks.
11. **The effects marker row never shows.** `LayerStack.rows` filters the `EffectsLayer`
    out, so its label "Effects" and summary "Background border" (`LayerRow.kt:157`) are
    dead strings. The border is reachable only from the Background tab. Fine under this
    spec; drop the dead strings.
12. **The "Effects" clip target** (`LayerControls.kt:47`) depends on which effects sit
    below the layer in the list, so moving the layer changes what it paints. Too subtle
    to show by default. More options only.
13. **Seed numbers.** Shown as raw integers beside a "New seed" button
    (`EffectControls.kt:248`, `PaintControl.kt:122`). Users cannot use the number. Show
    only Shuffle and reset.
14. **Grain cost note** (`LayersTab.kt:161`) appears in the middle of the settings in
    KB. Users cannot act on it; fold into help.
15. **Switching fill kind can lose a gradient.** Picking a kind equal to the
    default resets to the default paint entirely (`PaintControl.kt:59`), while other
    kinds convert and keep colours (`:61`). Switching Gradient to Colour and back can
    therefore lose a custom gradient. The decided Solid / Gradient / Image rebuild should
    remember the last value of each kind.

---

## Open points for the coordinator

- The one-row Icon recommendation (1.2) splits the row only for themes that already
  have a layer between fill and lines. If that edge is judged not worth code, the
  alternative is to stop allowing drops between them and migrate such layers above.
- Mix mode names were kept with a plain second line rather than renamed, since the
  standard names are what any online guide uses. Renaming them all is possible but makes
  outside help useless.
- Row 205 "Icon quality": the measured MB figures are for one preset; kept as is.
