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

Texture layers read `renderer.textures`, a `TextureStore`. Put every texture a
theme uses (`theme.textureHashes()`) before rendering it; a missing texture
throws `IllegalStateException` rather than render a different icon.

```kotlin
val png = TextureStore.encode(width, height, premultipliedRgba)  // canonical texture file
val hash = renderer.textures.put(png)                             // lowercase hex SHA-256
renderer.textures.put(hash, File(dir, "$hash.png").readBytes())  // reload, hash checked
```

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
fixed order, `StrictMath` for every non-trivial function (`math/StrictFloat`
routes sqrt, ceil and floor through it), no fused multiply-add, no platform
library in the pixel path, and every random choice seeded (`math/SplitMix64`,
`IconSeed`). `DeterminismRulesTest` scans the main sources for platform
`Math`, unseeded randomness and android imports. `png/Deflate` is our own compressor,
because `java.util.zip.Deflater` gives bytes that depend on the zlib build
behind it: zlib-ng on Fedora, Chromium's zlib on Android. It has two pinned
efforts. Icons use `FAST`: hash chains of 32 links (`png/Lz77`), no lazy
matching, and per block the cheaper of the fixed and dynamic Huffman codes.
Texture files use `CANONICAL`, the M5 encoder unchanged (512 links, lazy,
dynamic only), because a texture's hash names those bytes.

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
     reopens the gap between two thin stroke ends, so it is not used. The
     regions are labelled at every distinct squared stroke distance up to
     the closing squared, and each level's inside is grown up to the next
     level, so sealing is monotone in the closing however small the step.
   - Keep lone lines: stroke coverage beyond the reach of any fill is drawn
     on top in the line paint. The reach is `(gap + stroke / 2) * sqrt 2 +
     stroke / 2 + 1 px`, enough to clear a right-angled fill corner, ramped
     over one pixel. This keeps F-Droid's antennas. The camera's dot and
     Chrome's inner lines read as gaps in the fill.
3. **Background.** The shape's coverage and the border's, cached. The paint
   is the stack's background layer.
4. **Layers and effects** (`layer/StackEvaluator`), below. The border is
   drawn at the effects layer.
5. **Background shape alpha** clips the whole image. Shapes: circle, square,
   rounded square, squircle `|x|^n + |y|^n = 1`, and AOSP's teardrop, three
   half-size corners and the bottom right at 15%. A disabled background means
   no paint and no clip.

## Layers

`Theme.layers` is spec 2.3's stack, `Layers.kt`. A `PaintLayer` has a source,
a clip target or its inverse, a blend mode, an opacity, a texture mapping and
an optional matte. Each effect is an `EffectLayer`, below. One `EffectsLayer`
marker paints the border and is where a new effect goes by default,
`Theme.withEffect`. The JSON form and every range are in
`docs/theme-format.md`.

Per pixel, coverage = clip x matte x opacity; the source times the coverage
is composited into the premultiplied buffer.

- **Clip targets.** `ICON` is the fill body union the lines, `b + l - b l`,
  cached under `EffectKey`. `ICON_FILL` and `ICON_LINES` are the two masks
  alone. `BACKGROUND` is every pixel the shape touches, since step 5 applies
  its anti-aliased edge once; empty with the background off. `EFFECTS` is
  the union, `e + c - e c`, of the coverage times opacity of every enabled
  effect layer below. `CANVAS` is everything.
- **Base stack.** `Theme.baseLayers(background, body, line)`: the background
  paint on `BACKGROUND`, effects, a solid layer on `ICON_FILL`, one on
  `ICON_LINES`. M4's glyph paint was exactly this, so all M4 goldens,
  including Plain and Jade, come out byte-identical.
- **Sources** (`Paint`). Solid, linear and radial gradients as before;
  `Texture` by hash, optionally gradient-mapped by luminance; `Noise`.
- **Blend modes** (`layer/BlendModes`). The 17 of spec 2.3, from W3C
  Compositing and Blending Level 1 section 10.1 on unpremultiplied colours,
  `C = c / a` (0 at a = 0): `co = cs (1 - ab) + cb (1 - as) + as ab B(Cb, Cs)`,
  `ao = as + ab - as ab`, co clamped to [0, ao]. Hue, saturation, color and
  luminosity use the spec's Lum, ClipColor, SetLum and SetSat. Normal is
  plain source-over; Add is Porter-Duff plus, clamped to 1. Each mode is
  tested against a separate double-precision transcription.
- **Mapping** (`layer/Mapping`). A pixel centre is turned about the canvas
  centre and scaled to texels: FIT fits the turned texture in the canvas,
  FILL fits the turned canvas in the texture, TILE spans the short side and
  repeats. Bilinear on premultiplied texels; FIT and FILL are transparent
  outside the texture. The per-icon offset is the first two SplitMix64
  outputs from `IconSeed.of(name)`; FILL moves the window within the room
  the texture leaves, so the patch stays inside it; TILE shifts by the
  offset times the texture; lock centres it. Noise uses the same turn and
  scale, 8 lattice cells across the canvas (432 for grain), offset by up to
  256 cells. Grain is box-filtered, each pixel the area average of the cells
  under it, with whole-cell offsets, so a 128 px preview is the 432 px grain
  box-filtered down. The period keeps lattice coordinates below 2^16 at 8 octaves
  and scale 0.25, so floats are spaced at most 1/256 cell; 4096 left the top
  octaves stair-stepped at 1/16 cell. The lattice hash is seeded, not wrapped:
  the same seed gives the same field everywhere.
- **Noise** (`layer/Noise`). Value noise on a lowbias32 lattice hash with
  Perlin's quintic fade; fBm (lacunarity 2, gain 0.5, Musgrave); ridged,
  `(1 - |2n - 1|)^2` per octave; grain, one hashed value per cell.
- **Matte** (`layer/Mattes`). Luminance of the source before any gradient
  map: `0.3 r + 0.59 g + 0.11 b` of the premultiplied texels, the raw noise
  value, or the colour's. Then levels or threshold, invert, and a separable
  Gaussian feather. A black-and-white texture with stops `(C, C)` becomes a
  layer of colour C wherever the texture is light.

## Effects

Spec 2.3 "Effects", `Effects.kt` and `effect/`. Each effect is an
`EffectLayer(id, effect, enabled, blend, opacity)` at its own place in the
stack. `effect/EffectPaint` turns it into one output or two (the bevel's
highlight and shadow): coverage per pixel and a colour, composited like a
paint layer and added to `EFFECTS`. Every effect reads the icon mask, body
union lines, through `effect/EffectSource`, and what it derives from it is
cached under the icon's `EffectKey` with an `EffectKind`: `BLUR` by sigma, a
`Mask`; `SIGNED_DISTANCE`, a `Field`. Masks are only read.

- **Streaks** (`Streaks`, `Smear`). The mask is turned into a frame whose
  rows run along the streak, each row smeared causally, and turned back,
  bilinear. Linear decay by two prefix sums, exponential by a one-tap
  recursion: O(1) per pixel at any length. No other curves: an arbitrary
  one costs a tap per pixel of length. Breakup multiplies the smear in the
  same frame: a texture tiled and stretched along the streak, or fBm, each
  icon its own patch, through density and contrast. The paint is the linked
  paint layer's source, unclipped (`StackEvaluator.sourceColors`), or a
  colour.
- **Bevel and emboss** (`Bevel`, `mask/SignedDistance`). Height from the
  signed sub-pixel distance to the mask's edge, Gustavson and Strand's
  anti-aliased EDT: each edge pixel's edge point from its coverage and
  gradient, the nearest by Felzenszwalb's transform with the argmin, then
  the edge points within two pixels. A binary EDT is off by up to 0.495 px
  and bands a lit bevel; this one is within 0.098 px, gradient within 2.8
  degrees (`SignedDistanceTest`; side by side in
  `docs/verification/m6/t9_sdf_binary_vs_antialiased.png`). Light at 30
  degrees altitude.
- **Drop shadow, inner shadow, outer and inner glow** (`DropShadow`,
  `InnerShadow`, `Glows`). The mask's blur, moved away from the light or
  levelled by the spread, outside or inside the icon.
- **Grain** (`Grain`). A wrapper over the `GRAIN` noise source: one code
  path, cells a fixed fraction of the canvas.

`mask/Blur` is the one separable Gaussian: the matte feather, unchanged, and
the effects' blur, zero outside the canvas, sampled taps up to sigma 3 px,
above that three extended box passes per axis by running sums, whose
fractional end weight makes the variance exactly sigma squared at a cost
independent of the radius.

Built-in brushes (`texture/BuiltInBrushes`): bristle, dry brush and chalk,
generated from seeded noise, named by the hash of their canonical PNG, always
in the store, never exported.

## Textures

`texture/TextureStore` keeps decoded textures by the SHA-256 of their
canonical PNG, apart from the mask cache. `put` decodes any 8-bit RGBA PNG,
re-encodes it with the project's `PngEncoder` and hashes those bytes, so the
same pixels get the same hash whichever encoder wrote the file;
`bytes(hash)` returns the canonical file for export. An evicted texture
loads back through the app's loader, checked by the SHA-256 of the file's
own bytes with no re-encode, one load at a time. It decodes through `png/Inflate`, a pure-Kotlin RFC 1950/1951
decoder after puff.c, and `png/PngDecoder`, and refuses a side over 2048 px.

## Mask cache

`MaskCache` holds geometry masks keyed by (drawable name, shape params,
size), fill bodies keyed by (drawable, shape params, fill params, size),
invert fill's lone lines, masks derived from the fill output keyed by
`EffectKey` (fill key, fell back, keep lone lines), and background shape
masks. It is an LRU bounded at 64 MB. Entries are `Mask`, read-only: one
instance serves every render. The fill entry holds the body alone and the
geometry is re-attached on read, so the bound is the memory held. A paint,
layer or background-colour change reuses all of them; `CompositorTest`
asserts that. Textures and noise never enter it.

## Premultiplied alpha

`render` returns premultiplied RGBA, as spec 3.3 asks. PNG stores straight
alpha, so `PngEncoder` unpremultiplies with integer rounding,
`(c * 255 + a / 2) / a`. Re-premultiplying with rounding, which is what
Android does when it decodes a PNG to a bitmap, gives back the renderer's
bytes exactly. `PngEncoderTest` checks every (c, a) pair.

## Tests

`./gradlew :render:test` runs the unit tests and the goldens at 432 px under
`src/test/resources/golden/`, compared byte for byte as PNG files: 24 sample
icons in each of the four fill modes, the two pocket cases, the Plain and
Jade presets (made by the M4 renderer, reproduced by the layer stack), and
three layer looks on six samples over the original test texture
`textures/cracks.png`, and `layers_blends` on two samples for the nine
blend modes the looks leave out; since M6, one set per effect on four
samples (`effect_*`, streaks and bevel in two variants each) and the
combined jade-like look on six (`effects_jade`). 208 PNGs; `GoldenSet.CASES` lists them, and
`ArtByteIdentityTest` renders the same set on ART. The
sample icons are copied into `samples.bin` so the test does not need the
converter. After an intended change to the output:

```
./gradlew :render:test -PupdateGoldens
```

rewrites the goldens and the sample bundle. Look at the new PNGs before
committing them.

`./gradlew :render:benchmarkRenderer` times the spec 3.3 budgets on the JVM,
per fill mode, for the layered Jade preset, each effect alone and the
combined jade-like look, cold and with a warm cache, and the PNG encode
alone per icon. `-Prows=INVERT,JADE_LIKE` limits the rows. M5 numbers are in
`docs/verification/m5.md`, M6 in `docs/verification/m6.md`.
