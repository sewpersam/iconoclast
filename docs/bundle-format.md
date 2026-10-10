# Icon bundle format

The `build-logic` converter writes three assets into `:iconsource` under
`assets/arcticons/`. The format code is shared: `render/src/format/kotlin` is
compiled into both `:render`, which reads it, and `build-logic`, which writes
it. All integers are big-endian.

| File | Contents |
|---|---|
| `icons.bin` | Geometry of every icon |
| `names.txt` | Drawable names. Line i, 0-based, is icon i |
| `components.bin` | Appfilter: component string to icon index |
| `NOTICE.txt` | Arcticons credit and CC BY-SA 4.0 notice |

Icons are sorted by drawable name, so `names.txt` supports binary search.
The drawable index is the same in all three files.

## Canvas

Every icon is normalised to a 48 by 48 unit canvas, y down. A viewBox
other than `0 0 48 48` is scaled to fit, centred. The one file without a
viewBox uses its `width` and `height`, or 48 by 48 if those are missing.

## icons.bin

```
offset  size            field
0       u32             magic 0x49434E42 ("ICNB")
4       u16             version, 1
6       u16             reserved, 0
8       u32             icon count N
12      u32[N + 1]      byte offset of each icon record from file start;
                        entry N is the file length
...     records         icon records, in index order
```

Icon record:

```
u16                     primitive count
primitive[count]:
  u8                    flags
  u16                   stroke width, units of 1/1024 canvas unit
  u16                   miter limit, units of 1/64
  u16                   verb count V
  u8[V]                 verbs
  i16[2 * points]       x, y pairs, units of 1/256 canvas unit
```

`points` follows from the verbs, so a primitive needs no coordinate count.
Coordinates cover -128 to +128 units. The widest at pin `90a4343` reaches
67.1.

Flags:

| Bits | Meaning |
|---|---|
| 0 | stroked |
| 1 | filled |
| 2 | source fill rule is evenodd. Set on unfilled primitives too |
| 3-4 | cap: 0 butt, 1 round, 2 square |
| 5-6 | join: 0 miter, 1 round, 2 bevel |

A primitive can be stroked, filled, or both. Fill paints first, then the
stroke, as in SVG.

Verbs. All coordinates are absolute:

| Verb | Value | Points |
|---|---|---|
| MOVE | 0 | 1 |
| LINE | 1 | 1 |
| CUBIC | 2 | 3: control 1, control 2, end |
| CLOSE | 3 | 0 |

A drawing verb after CLOSE always gets an explicit MOVE first. A
zero-length LINE is a real segment: round and square caps draw it as a dot.

## What the converter resolves

- Styles cascade in this order: inherited parent values, then presentation
  attributes, then `<style>` rules, then the inline `style` attribute. Rules
  sort by specificity: ids, then classes, then type. Equal specificity goes
  to the later rule. Selectors are compound only, with type, class and id.
  `!important` is ignored. No icon at the pin uses it.
- Transforms bake into the coordinates: `matrix`, `translate`, `scale`,
  `rotate`, `skewX` and `skewY`, through every enclosing `<g>`. Stroke
  width scales by the square root of the transform's determinant.
- Every shape becomes path verbs: `path`, `line`, `rect` with `rx` and `ry`,
  `circle`, `ellipse`, `polyline` and `polygon`. Arcs and quadratics become
  cubics. An arc splits into pieces of at most 90 degrees.
- `stroke-dasharray` bakes into geometry. Each dash becomes its own open
  subpath, flattened to lines. If the shape is also filled, the fill stays
  a separate primitive with the original outline.
- Dropped: `<metadata>`, `<title>`, `<desc>`, `<defs>` content apart from
  `<style>`, and every element outside the SVG namespace, such as
  inkscape and sodipodi cruft. Shapes with `display:none`, `visibility`
  other than visible, `opacity:0`, or neither paint are dropped too.
- Not kept: colour and partial opacity. The restyle chooses its own paint.
  An unstyled shape gets SVG's default black fill and is flagged filled.

## components.bin

```
offset  size            field
0       u32             magic 0x49434E43 ("ICNC")
4       u16             version, 1
6       u16             reserved, 0
8       u32             entry count N
12      u32[N]          byte offset of each key from file start
12+4N   u16[N]          drawable index of each key
12+6N   keys            per key: u16 byte length, then UTF-8 bytes
```

Keys sort by unsigned UTF-8 bytes, so lookup is a binary search. A key is
`package/activity` without the `ComponentInfo{}` wrapper, or an appfilter
shortcut such as `:BROWSER`. `ComponentIndex.resolve` accepts the wrapped
form and the short form `package/.Activity` too.

The compiler skips `<calendar>` entries. It also skips any item whose
component is not `ComponentInfo{package/activity}`, with a dotted Java
package and an activity without `/`, braces or whitespace. Shortcuts are
the exception. Items that name an unknown drawable are skipped as well.
The first mapping of a component wins. Every skip is counted in
`iconsource/build/reports/arcticons/appfilter.txt` and summarised in the
build log. None of them fail the build.

## Reading it

```kotlin
val source = IconSource.open(context.assets)   // :iconsource, about 9 MB read
val index = source.resolve("ComponentInfo{org.fdroid.fdroid/org.fdroid.fdroid.views.main.MainActivity}")
val icon: Icon = source.icon(index)            // decodes this icon only
for (p in icon.primitives) { p.isStroked; p.isFilled; p.verbs; p.coords }
```

`IconBundle` in `:render` is pure JVM and decodes one icon per call, with
absolute reads only, so one instance can be shared across threads.
