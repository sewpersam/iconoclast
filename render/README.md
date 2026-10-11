# :render

Turns one Arcticons drawable and a theme into a PNG. Pure Kotlin on the JVM,
no Android dependency, so the app and the JVM tests run the same code.

```kotlin
val renderer = IconRenderer()                   // share one; it owns the mask cache
val png: ByteArray = renderer.renderPng(name, source.icon(index), theme, OUTPUT_SIZE)
val image: RgbaImage = renderer.render(name, source.icon(index), theme, 128)  // premultiplied RGBA
```

`name` is the Arcticons drawable name, the icon's stable identity. It keys the
mask cache and seeds every per-icon choice through `IconSeed`, so it must
always name the same icon. `IconRenderer` is thread-safe.

## Output size: 432 px

Every pack PNG is 432 by 432. That is the full 108 dp adaptive-icon canvas at
xxxhdpi, the largest bitmap a launcher asks for, and what the spike shipped.
Spec 3.3 allows a smaller size only after a crispness check on a 420 dpi
screenshot, which needs the emulator. That check belongs to Task 4. Until it
shows a smaller size is as crisp, 432 stays. The cost is small: the JVM
benchmark averages 10 to 15 KB per icon with the default themes, so 300 icons
take about 4 MB, and render plus encode of 300 icons takes 1.6 s on 4 threads.

The 48-unit icon box is scaled to the adaptive safe zone, 66/108 of the canvas,
times the theme's icon scale. Offsets are fractions of the canvas.

## Why a software rasterizer

Spec 3.3 wants the same theme to give the same bytes, and the goldens made on
the JVM must match the phone. `android.graphics` and `java.awt` each
anti-alias in their own way, so neither can be the reference for the other.
`:render` therefore rasterizes everything itself:

- `geom/PathFlattener`: cubics to polylines, within 0.05 px.
- `geom/Stroker`: a stroke is a union of convex pieces: a quad per segment, a
  wedge per join on the outer side, a cap per open end. Miter limit as SVG
  defines it. Every piece winds +1, so a nonzero fill paints their union.
- `raster/Scanline`: 16 sample rows per pixel row, exact horizontal coverage
  on each, nonzero or even-odd. Each primitive is its own pass, composited
  source-over as an SVG renderer paints them.

Determinism rules, kept throughout: single-precision IEEE arithmetic in a
fixed order, `StrictMath` for every trig call, no fused multiply-add, no
platform library in the pixel path. `png/Deflate` is our own compressor,
because `java.util.zip.Deflater` gives bytes that depend on the zlib build
behind it: zlib-ng on Fedora, Chromium's zlib on Android.

`raster/ScanlineRasterizer` is the production `IconRasterizer`. The M1
conversion gate runs it; see `docs/verification/m2.md`. `AwtRasterizer` stays
in the `verify` source set as a second reference and never ships.

## Pipeline

`Compositor.kt`, in the order of spec 3.3:

1. **Geometry mask.** The line art under the shape parameters: stroke weight,
   caps, joins, miter limit, icon scale, offset. Filled dots, fill-only
   primitives up to 2.5 units across, scale with the stroke weight about
   their centre unless the theme keeps them.
2. **Fill mode mask** (`fill/FillModes`). Outputs a body mask and a line mask.
   - Outline: lines only.
   - Silhouette: everything that is not outside, solid. The outer edge keeps
     the geometry's anti-aliasing.
   - Outline + Silhouette: the silhouette as body, the outline as lines.
   - Invert fill, spec 1.4: enclosed regions minus the strokes widened by the
     gap. The cut is a second rasterization with every stroke `2 * gap`
     wider, round caps and joins, so the fill edge is exactly anti-aliased.
     Below the fallback threshold, fill area over the outline's area, the
     icon renders as Outline.
   - Gap closing. Strokes are dilated by the closing radius, the remaining
     regions labelled, and each side grown back by the radius. Where both
     sides reach, the enclosed side wins. A plain closing on the pixel grid
     reopens the gap between two thin stroke ends, so it is not used.
   - Keep lone lines: stroke coverage beyond the reach of any fill is drawn
     on top in the line paint. The reach is `(gap + stroke / 2) * sqrt 2 +
     stroke / 2 + 1 px`, enough to clear a right-angled fill corner, ramped
     over one pixel. This keeps F-Droid's antennas. The camera's dot and
     Chrome's inner lines read as gaps in the fill.
3. **Background.** Paint (solid, linear or radial gradient, interpolated
   premultiplied) and a border inside the shape's edge.
4. **Layers and effects.** In M2 the body mask is painted in
   `GlyphPaint.body` and the line mask in `GlyphPaint.line`. The layer stack
   replaces `drawGlyph`; it paints the same masks.
5. **Background shape alpha** clips the whole image. Shapes: circle, square,
   rounded square, squircle `|x|^n + |y|^n = 1`, and AOSP's teardrop, three
   half-size corners and the bottom right at 15%. A disabled background means
   no paint and no clip.

The texture paint is not in M2. It arrives with textures and layers.

## Mask cache

`MaskCache` holds geometry masks keyed by (drawable, shape params, size),
fill masks keyed by (drawable, shape params, fill params, size), and
background shape masks. It is an LRU bounded at 64 MB. A paint, background or
glyph-colour change reuses all of them; `CompositorTest` asserts that.

## Premultiplied alpha

`render` returns premultiplied RGBA, as spec 3.3 asks. PNG stores straight
alpha, so `PngEncoder` unpremultiplies with integer rounding,
`(c * 255 + a / 2) / a`. Re-premultiplying with rounding, which is what
Android does when it decodes a PNG to a bitmap, gives back the renderer's
bytes exactly. `PngEncoderTest` checks every (c, a) pair.

## Tests

`./gradlew :render:test` runs the unit tests and the goldens: 24 sample
icons in each of the four fill modes at 432 px, under
`src/test/resources/golden/`, compared pixel for pixel after decoding. The
sample icons are copied into `samples.bin` so the test does not need the
converter. After an intended change to the output:

```
./gradlew :render:test -PupdateGoldens
```

rewrites the goldens and the sample bundle. Look at the new PNGs before
committing them.

`./gradlew :render:benchmarkRenderer` times the spec 3.3 budgets on the JVM.
